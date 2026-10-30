/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package umontreal.ssj.hups64;

import java.util.Arrays;

import umontreal.ssj.rng.RandomStream;

/**
 * Applies full base-2 nested uniform scrambling to a digital net using algebraic
 * preparation of longest-prefix parents. This combines the parent traversal of
 * {@link NewCachedNusPointSet} with generating-matrix calculations in the spirit
 * of {@link HCKPointSet}. Singular and non-NUT matrices are supported.
 *
 * <p>Preparation follows SSJ's Gray-code point order. If the input matrix has
 * columns c[h], the effective binary-index columns are c[0] and
 * c[h] XOR c[h-1] for h greater than zero. Incremental Gaussian elimination
 * finds, for each highest index bit h, an index difference d[h] minimizing the
 * coordinate XOR. A linear pass selects the best available difference for each
 * point i and sets its parent to i XOR d[h]. No original coordinates, sorting,
 * stack, or tree are needed. With k index bits and w output bits, preparation
 * takes O(k*w + n) word operations per dimension and O(k+w) temporary words.
 *
 * <p>Each selected parent precedes its point and shares the longest available
 * binary prefix. Randomization copies the parent's scrambled prefix, flips the
 * first differing bit, and fills the suffix with fresh random bits. Duplicate
 * integer coordinates copy the complete scrambled parent value. Parent indices
 * may differ from {@code NewCachedNusPointSet}, but the first-difference masks
 * and scrambled coordinates match for the same matrices and random words.
 *
 * <p>Call {@link #randomize()} to fill the dimension-major double cache. Each
 * call consumes one {@link RandomStream#nextBitsLong(int)} word per point per
 * dimension, including duplicates and transitions with no random suffix.
 * Correct NUS requires sufficiently uniform and independent bits within and
 * between these words. Storage is reused between randomizations. Coordinates
 * follow {@code z / 2^outputBits + 2^-55}; at high precision, distinct integers
 * may round to the same double or to {@code 1.0}. A reusable integer work array
 * retains exact parent values before conversion.
 *
 * <p>Construction consumes no randomness and retains no reference to the input
 * net. Its current generating matrices are used, and its separate digital shift
 * is ignored, as in {@code NewCachedNusPointSet}. Later changes to the input net
 * do not affect this point set. For RQMC experiments, use
 * {@code new RQMCPointSet(points, points.getRandomization())}.
 *
 * @see NewCachedNusPointSet
 * @see HCKPointSet
 */
public final class FK02HCKNUSPointSet extends PointSet {
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int outputBits;
   private final double normFactor;
   private final int[][] parents;
   private final long[][] firstDiffMasks;
   private final double[][] coordinates;
   private final long[] scrambledBits;
   private final PointSetRandomization randomization;

   /**
    * Prepares parents directly from the net's current generating matrices.
    * Points retain SSJ's Gray-code indices. Any positive point count is
    * supported if enough columns are supplied; NUT matrices and a power-of-two
    * point count are not required. Construction consumes no randomness.
    *
    * @param inputNet digital net supplying the generating matrices
    * @param stream source of random bits
    * @throws IllegalArgumentException if an argument is null, sizes or matrix
    *         dimensions are invalid, output precision is outside 1 to 62, or a
    *         used matrix column contains bits outside the output precision
    */
   public FK02HCKNUSPointSet(DigitalNetBase2 inputNet, RandomStream stream) {
      if (inputNet == null || stream == null)
         throw new IllegalArgumentException("inputNet and stream must not be null");
      if (inputNet.getNumPoints() <= 0 || inputNet.getDimension() <= 0)
         throw new IllegalArgumentException("The input net must have a positive point count and dimension");
      if (inputNet.outDigits < 1 || inputNet.outDigits > 62)
         throw new IllegalArgumentException("The input net's output precision must be between 1 and 62");

      this.numPoints = inputNet.getNumPoints();
      this.dim = inputNet.getDimension();
      int requiredColumns = Integer.SIZE - Integer.numberOfLeadingZeros(numPoints - 1);
      if (inputNet.numCols < requiredColumns || inputNet.genMat == null
            || (long) dim * inputNet.numCols > inputNet.genMat.length)
         throw new IllegalArgumentException("The input net's generator matrices must match its sizes");

      this.outputBits = inputNet.outDigits;
      this.normFactor = Math.scalb(1.0, -outputBits);
      this.stream = stream;
      this.parents = new int[dim][numPoints];
      this.firstDiffMasks = new long[dim][numPoints - 1];
      this.coordinates = new double[dim][numPoints];
      this.scrambledBits = new long[numPoints];
      this.randomization = new NUSRandomization();
      prepare(inputNet, requiredColumns);
   }

   /** Minimizes each affine column coset, then constructs all parents in linear time. */
   private void prepare(DigitalNetBase2 inputNet, int numColumns) {
      // Each nonzero basis value has a distinct highest set bit. Its witness
      // records the index-bit combination yielding that value.
      long[] basis = new long[outputBits];
      int[] basisWitnesses = new int[outputBits];
      long[] minimumDifferences = new long[numColumns];
      int[] indexDifferences = new int[numColumns];
      long[] differenceMasks = new long[numColumns];

      for (int j = 0; j < dim; j++) {
         Arrays.fill(basis, 0L);
         int offset = j * inputNet.numCols;
         long previousColumn = 0L;
         for (int h = 0; h < numColumns; h++) {
            long column = inputNet.genMat[offset + h];
            if ((column >>> outputBits) != 0L)
               throw new IllegalArgumentException("Matrix columns must fit the input net's output precision");

            // A = C*G converts ordinary binary indices to SSJ's Gray-code order.
            long value = column ^ previousColumn;
            previousColumn = column;
            int witness = 1 << h;
            for (int bit = outputBits - 1; bit >= 0; bit--) {
               if ((value & (1L << bit)) != 0L && basis[bit] != 0L) {
                  value ^= basis[bit];
                  witness ^= basisWitnesses[bit];
               }
            }
            // Continue through all pivots, including those below a new pivot:
            // early insertion would not necessarily minimize the full XOR.
            minimumDifferences[h] = value;
            indexDifferences[h] = witness;
            long firstDifference = Long.highestOneBit(value);
            differenceMasks[h] = firstDifference == 0L ? 0L : (firstDifference << 1) - 1L;
            if (value != 0L) {
               int pivot = Long.SIZE - 1 - Long.numberOfLeadingZeros(value);
               basis[pivot] = value;
               basisWitnesses[pivot] = witness;
            }
         }

         int[] dimensionParents = parents[j];
         long[] masks = firstDiffMasks[j];
         dimensionParents[0] = -1;
         // Temporarily store the winning highest-bit label B(i) in the parent
         // array. Removing the lowest set bit leaves an already solved index.
         for (int i = 1; i < numPoints; i++) {
            int remaining = i & (i - 1);
            int best = Integer.numberOfTrailingZeros(i);
            if (remaining != 0) {
               int previousBest = dimensionParents[remaining];
               if (minimumDifferences[previousBest] <= minimumDifferences[best])
                  best = previousBest;
            }
            dimensionParents[i] = best;
            masks[i - 1] = differenceMasks[best];
         }
         // A witness has highest set bit h and lower bits only. As bit h of i
         // is one, XORing that witness always gives an earlier point.
         for (int i = 1; i < numPoints; i++)
            dimensionParents[i] = i ^ indexDifferences[dimensionParents[i]];
      }
   }

   /**
    * Generates a fresh full NUS scrambling in original point order. Reuses all
    * storage and consumes one random word per point per dimension, including
    * duplicates. Exact integer parent values are overwritten before each use.
    */
   public void randomize() {
      for (int j = 0; j < dim; j++) {
         int[] dimensionParents = parents[j];
         long[] masks = firstDiffMasks[j];
         double[] output = coordinates[j];
         long z = stream.nextBitsLong(outputBits);
         scrambledBits[0] = z;
         output[0] = z * normFactor + EPSILON_HALF;
         for (int i = 1; i < numPoints; i++) {
            long word = stream.nextBitsLong(outputBits);
            long mask = masks[i - 1];
            long lowerMask = mask >>> 1;
            long firstDiffBit = mask ^ lowerMask;
            long parent = scrambledBits[dimensionParents[i]];
            z = ((parent ^ firstDiffBit) & ~lowerMask) | (word & lowerMask);
            scrambledBits[i] = z;
            output[i] = z * normFactor + EPSILON_HALF;
         }
      }
   }

   @Override
   public double getCoordinate(int i, int j) {
      return coordinates[j][i];
   }

   @Override
   public PointSetIterator iterator() {
      return new NUSIterator();
   }

   @Override
   public void addRandomShift(int d1, int d2, RandomStream stream) {
      throw new UnsupportedOperationException("Use the associated NUS randomization");
   }

   /** @return the stream used for scrambling */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream used for future randomizations without changing coordinates.
    *
    * @param stream source of random bits
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   /** @return the randomization adapter bound to this point set */
   public PointSetRandomization getRandomization() {
      return randomization;
   }

   @Override
   public String toString() {
      return "Cached full nested uniform scrambling (matrix parents, outputBits = " + outputBits + ")";
   }

   private final class NUSIterator extends DefaultPointSetIterator {
      @Override
      public double nextCoordinate() {
         if (curPointIndex >= numPoints || curCoordIndex >= dim)
            outOfBounds();
         return coordinates[curCoordIndex++][curPointIndex];
      }

      @Override
      public void nextCoordinates(double[] p, int d) {
         if (d > dim - curCoordIndex || (d > 0 && curPointIndex >= numPoints))
            outOfBounds();
         for (int j = 0; j < d; j++)
            p[j] = coordinates[curCoordIndex++][curPointIndex];
      }
   }

   private final class NUSRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != FK02HCKNUSPointSet.this)
            throw new IllegalArgumentException("The NUS adapter can only randomize its associated point set");
         FK02HCKNUSPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return FK02HCKNUSPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         FK02HCKNUSPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return FK02HCKNUSPointSet.this.toString();
      }
   }
}
