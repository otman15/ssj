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
 * Applies full base-2 nested uniform scrambling to a snapshot of a point set
 * or a digital net, generating coordinates in original point order. Like
 * {@link CachedNUSPointSet}, construction sorts each dimension's integer
 * coordinates using the preprocessing idea of Friedel and Keller (2002).
 * It then finds, for each point, an earlier point sharing its longest binary
 * prefix and stores that parent and the first-difference mask.
 *
 * <p>During randomization, a point copies its parent's scrambled prefix, flips
 * the branching bit, and receives a fresh random suffix. No earlier point has
 * entered the new child subtree, so this generates the full NUS distribution.
 * Identical integer coordinates copy the parent's complete scrambled value.
 * This changes the traversal of the permutation tree: fixed-seed results
 * generally differ from {@code CachedNUSPointSet}, although both implement full
 * NUS. This is not the matrix-based construction of {@link HCKPointSet} and
 * requires neither NUT matrices nor a power-of-two point count.
 *
 * <p>Call {@link #randomize()} to fill the dimension-major double cache.
 * Each call consumes one {@link RandomStream#nextBitsLong(int)} word per point
 * per dimension, including duplicates and transitions with no random suffix.
 * Correct NUS requires sufficiently uniform and independent bits within and
 * between these words. No bit reservoir is used. Coordinates are deterministic
 * between randomizations and follow {@code z / 2^outputBits + 2^-55}. At high
 * precision, distinct integers can round to the same double or to {@code 1.0}.
 * A reusable integer work array retains exact parent values before conversion.
 *
 * <p>Construction consumes no randomness and does not retain the input point
 * set. Later changes to the input do not affect the prepared snapshot. For
 * RQMC experiments, use
 * {@code new RQMCPointSet(points, points.getRandomization())}.
 *
 * @see CachedNUSPointSet
 * @see HCKPointSet
 */
public final class NewCachedNusPointSet extends PointSet {
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
    * Prepares a snapshot of a finite point set. Coordinates must be in
    * {@code [0, 1)} and are converted to {@code floor(u * 2^outputBits)} before
    * sorting. Construction consumes no randomness.
    *
    * @param inputPoints point set supplying the original coordinates
    * @param stream source of random bits
    * @param outputBits number of retained and scrambled bits, from 1 to 62
    * @throws IllegalArgumentException if an argument is null, sizes or precision
    *         are invalid, or an input coordinate is not finite and in {@code [0, 1)}
    */
   public NewCachedNusPointSet(PointSet inputPoints, RandomStream stream, int outputBits) {
      this(inputPoints, null, stream, outputBits);
   }

   /**
    * Prepares a snapshot directly from a digital net's current generating
    * matrices, without converting coordinates through doubles. Points retain
    * their Gray-code indices. The net's output precision sets the scrambling
    * precision. A separate digital shift is ignored, as in the corresponding
    * {@link CachedNUSPointSet} constructor. Construction consumes no randomness.
    *
    * @param inputNet digital net supplying the generating matrices
    * @param stream source of random bits
    * @throws IllegalArgumentException if an argument is null, sizes or matrix
    *         dimensions are invalid, or the output precision is outside 1 to 62
    */
   public NewCachedNusPointSet(DigitalNetBase2 inputNet, RandomStream stream) {
      this(inputNet, inputNet, stream, inputNet == null ? 0 : inputNet.outDigits);
   }

   private NewCachedNusPointSet(PointSet inputPoints, DigitalNetBase2 inputNet,
         RandomStream stream, int outputBits) {
      if (inputPoints == null || stream == null)
         throw new IllegalArgumentException("inputPoints and stream must not be null");
      if (inputPoints.getNumPoints() <= 0 || inputPoints.getDimension() <= 0)
         throw new IllegalArgumentException("The input point set must have a positive point count and dimension");
      if (outputBits < 1 || outputBits > 62)
         throw new IllegalArgumentException("outputBits must be between 1 and 62");

      this.numPoints = inputPoints.getNumPoints();
      this.dim = inputPoints.getDimension();
      this.outputBits = outputBits;
      this.normFactor = Math.scalb(1.0, -outputBits);
      this.stream = stream;
      this.parents = new int[dim][numPoints];
      this.firstDiffMasks = new long[dim][numPoints - 1];
      this.coordinates = new double[dim][numPoints];
      this.scrambledBits = new long[numPoints];
      this.randomization = new NUSRandomization();
      prepare(inputPoints, inputNet);
   }

   /** Sorts each dimension, then builds its parent links by reverse deletion. */
   private void prepare(PointSet inputPoints, DigitalNetBase2 inputNet) {
      if (inputNet != null) {
         int requiredColumns = Integer.SIZE - Integer.numberOfLeadingZeros(numPoints - 1);
         if (inputNet.numCols < requiredColumns || inputNet.genMat == null
               || (long) dim * inputNet.numCols > inputNet.genMat.length)
            throw new IllegalArgumentException("The input net's generator matrices must match its sizes");
      }

      double scale = Math.scalb(1.0, outputBits);
      long[] bits = new long[numPoints];
      long[] sortedBits = new long[numPoints];
      int[] indices = new int[numPoints];
      int[] sortedIndices = new int[numPoints];
      int[] left = new int[numPoints];
      int[] right = new int[numPoints];
      int[] counts = new int[256];
      int[] positions = new int[256];

      for (int j = 0; j < dim; j++) {
         if (inputNet == null) {
            for (int i = 0; i < numPoints; i++) {
               double u = inputPoints.getCoordinate(i, j);
               if (!(u >= 0.0 && u < 1.0))
                  throw new IllegalArgumentException("Input coordinates must be finite and in [0, 1)");
               bits[i] = (long) (u * scale);
            }
         } else {
            bits[0] = 0L;
            int offset = j * inputNet.numCols;
            for (int i = 1; i < numPoints; i++)
               bits[i] = bits[i - 1] ^ inputNet.genMat[offset + Integer.numberOfTrailingZeros(i)];
         }
         for (int i = 0; i < numPoints; i++)
            indices[i] = i;

         // Stable base-256 radix sorting preserves original order for ties.
         for (int shift = 0; shift < outputBits; shift += 8) {
            Arrays.fill(counts, 0);
            for (int i = 0; i < numPoints; i++)
               counts[(int) ((bits[i] >>> shift) & 0xffL)]++;
            positions[0] = 0;
            for (int bin = 1; bin < 256; bin++)
               positions[bin] = positions[bin - 1] + counts[bin - 1];

            for (int i = 0; i < numPoints; i++) {
               int bin = (int) ((bits[i] >>> shift) & 0xffL);
               int destination = positions[bin]++;
               sortedBits[destination] = bits[i];
               sortedIndices[destination] = indices[i];
            }
            long[] bitBuffer = bits;
            bits = sortedBits;
            sortedBits = bitBuffer;
            int[] indexBuffer = indices;
            indices = sortedIndices;
            sortedIndices = indexBuffer;
         }

         int[] dimensionParents = parents[j];
         long[] masks = firstDiffMasks[j];
         // Initially store inverse ranks in the final parent array. The reverse
         // sweep replaces a rank only after that point no longer needs it.
         for (int r = 0; r < numPoints; r++) {
            dimensionParents[indices[r]] = r;
            left[r] = r - 1;
            right[r] = r + 1;
         }
         for (int i = numPoints - 1; i > 0; i--) {
            int r = dimensionParents[i];
            int previous = left[r];
            int next = right[r];
            int parent = -1;
            long difference = Long.MAX_VALUE;
            if (previous >= 0) {
               parent = indices[previous];
               difference = bits[r] ^ bits[previous];
            }
            if (next < numPoints) {
               long nextDifference = bits[r] ^ bits[next];
               if (nextDifference < difference) {
                  parent = indices[next];
                  difference = nextDifference;
               }
            }
            // Both remaining neighbors have original indices smaller than i.
            // The smaller XOR has a longest common prefix among earlier points.
            dimensionParents[i] = parent;
            long firstDifference = Long.highestOneBit(difference);
            masks[i - 1] = firstDifference == 0L ? 0L : (firstDifference << 1) - 1L;
            if (previous >= 0)
               right[previous] = next;
            if (next < numPoints)
               left[next] = previous;
         }
         dimensionParents[0] = -1;
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
      return "Cached full nested uniform scrambling (parent traversal, outputBits = " + outputBits + ")";
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
         if (p != NewCachedNusPointSet.this)
            throw new IllegalArgumentException("The NUS adapter can only randomize its associated point set");
         NewCachedNusPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return NewCachedNusPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         NewCachedNusPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return NewCachedNusPointSet.this.toString();
      }
   }
}
