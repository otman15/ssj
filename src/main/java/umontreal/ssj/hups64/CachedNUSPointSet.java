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
 * or a digital net. Construction converts point-set coordinates to {@code floor(u * 2^outputBits)}
 * or generates integer coordinates directly from
 * the digital net's current generating matrices. In each dimension, it sorts
 * the integer coordinates and retains only their original indices and
 * prefix-transition masks. Scrambling follows the algorithm of Friedel and
 * Keller (2002).
 *
 * <p>Call {@link #randomize()} to fill this point set with scrambled doubles.
 * Each call uses the snapshot prepared at construction, independently of any
 * later changes to the input point set. For APIs that accept a {@link PointSet}
 * and a {@link PointSetRandomization}, pass this point set and the adapter
 * returned by {@link #getRandomization()}.
 *
 * <p>Correct scrambling requires sufficiently uniform and independent random
 * bits up to {@code outputBits}. Random words come from
 * the stream's {@link RandomStream#nextBitsLong(int)} method. Coordinates use the SSJ
 * convention {@code z / 2^outputBits + 2^-55}; at high precision, different
 * integers can round to the same double or to {@code 1.0}.
 */
public final class CachedNUSPointSet extends PointSet {
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int outputBits;
   private final double normFactor;
   private final int[][] originalIndices;
   private final long[][] firstDiffMasks;
   private final double[][] coordinates;
   private final PointSetRandomization randomization;

   /**
    * Prepares a snapshot of the current coordinates of any finite point set.
    * Each input coordinate must be in {@code [0, 1)} and is converted to
    * {@code floor(u * 2^outputBits)} before sorting. Construction consumes no
    * random numbers and does not retain the input point set.
    *
    * @param inputPoints point set supplying the original coordinates
    * @param stream source of random bits
    * @param outputBits number of retained and scrambled bits, from 1 to 62
    * @throws IllegalArgumentException if an argument is null, sizes or precision
    *         are invalid, or an input coordinate is not finite and in {@code [0, 1)}
    */
   public CachedNUSPointSet(PointSet inputPoints, RandomStream stream, int outputBits) {
      this(inputPoints, null, stream, outputBits);
   }

   /**
    * Prepares a snapshot directly from a digital net's current generating
    * matrices, without converting its coordinates through doubles. Points
    * retain their Gray-code indices. The net's {@code outDigits} determines
    * the scrambling precision. Construction consumes no random numbers and
    * does not retain the input net.
    *
    * @param inputNet digital net supplying the generating matrices
    * @param stream source of random bits
    * @throws IllegalArgumentException if an argument is null, sizes or matrix
    *         dimensions are invalid, or the net's output precision is outside 1 to 62
    */
   public CachedNUSPointSet(DigitalNetBase2 inputNet, RandomStream stream) {
      this(inputNet, inputNet, stream, inputNet == null ? 0 : inputNet.outDigits);
   }

   private CachedNUSPointSet(PointSet inputPoints, DigitalNetBase2 inputNet,
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
      this.originalIndices = new int[dim][numPoints];
      this.firstDiffMasks = new long[dim][numPoints - 1];
      this.coordinates = new double[dim][numPoints];
      this.randomization = new NUSRandomization();
      prepare(inputPoints, inputNet);
   }

   /**
    * Sorts each dimension's integer coordinates, retaining their original
    * indices and masks identifying the first differing bit of each adjacent pair.
    */
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
      int[] tempIndices = new int[numPoints];
      int[] counts = new int[256];
      int[] positions = new int[256];
      int numPasses = (outputBits + 7) / 8;

      for (int j = 0; j < dim; j++) {
         // Choose the starting buffer so the final pass ends in originalIndices[j].
         int[] indices = (numPasses & 1) == 0 ? originalIndices[j] : tempIndices;
         int[] sortedIndices = (numPasses & 1) == 0 ? tempIndices : originalIndices[j];
         if (inputNet == null) {
            for (int i = 0; i < numPoints; i++) {
               double u = inputPoints.getCoordinate(i, j);
               if (!(u >= 0.0 && u < 1.0))
                  throw new IllegalArgumentException("Input coordinates must be finite and in [0, 1)");
               bits[i] = (long) (u * scale);
            }
         } else {
            // Generate integer coordinates directly in Gray-code order.
            bits[0] = 0L;
            for (int i = 1; i < numPoints; i++) {
               int pos = Integer.numberOfTrailingZeros(i);
               bits[i] = bits[i - 1] ^ inputNet.genMat[j * inputNet.numCols + pos];
            }
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

         for (int i = 1; i < numPoints; i++) {
            long firstDifference = Long.highestOneBit(bits[i - 1] ^ bits[i]);
            // Equal integer coordinates share all permutation bits.
            firstDiffMasks[j][i - 1] = firstDifference == 0L
                  ? 0L : (firstDifference << 1) - 1L;
         }
      }
   }

   /**
    * Fills the cached doubles with a new scrambling of the prepared snapshot.
    * Consumes one random word per point per dimension, including duplicates.
    */
   public void randomize() {
      for (int j = 0; j < dim; j++) {
         int[] indices = originalIndices[j];
         long[] masks = firstDiffMasks[j];
         double[] output = coordinates[j];

         // Under full NUS, the first scrambled coordinate is a uniform word;
         // its original bits can be absorbed into the random permutations.
         long z = stream.nextBitsLong(outputBits);
         output[indices[0]] = z * normFactor + EPSILON_HALF;
         for (int i = 1; i < numPoints; i++) {
            long word = stream.nextBitsLong(outputBits);
            long mask = masks[i - 1];
            long lowerMask = mask >>> 1;
            long firstDiffBit = mask ^ lowerMask;

            // Preserve the common prefix, flip the branching bit, and randomize the suffix.
            // For duplicates, keep z unchanged but still consume a random word.
            z = ((z ^ firstDiffBit) & ~lowerMask) | (word & lowerMask);
            output[indices[i]] = z * normFactor + EPSILON_HALF;
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

   /** Returns the stream used for scrambling. */
   public RandomStream getStream() {
      return stream;
   }

   /** Sets the stream used for future randomizations. */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   /** Returns the randomization adapter bound to this point set. */
   public PointSetRandomization getRandomization() {
      return randomization;
   }

   @Override
   public String toString() {
      return "Cached full nested uniform scrambling (outputBits = " + outputBits + ")";
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
         if (p != CachedNUSPointSet.this)
            throw new IllegalArgumentException("The NUS adapter can only randomize its associated point set");
         CachedNUSPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return CachedNUSPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         CachedNUSPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return CachedNUSPointSet.this.toString();
      }
   }
}