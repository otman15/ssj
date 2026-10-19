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
 * Applies full base-2 nested uniform scrambling to a snapshot of cached points
 * or a digital net. Construction converts cached coordinates to
 * {@code floor(u * 2^outputBits)} or generates integer coordinates directly from
 * the net's current generator matrices. Both paths sort coordinates separately
 * in each dimension and retain only original indices and prefix-transition
 * masks. All {@code outputBits} bits are scrambled; original coordinates and
 * sorting buffers are not retained. The scrambling procedure follows the algorithm
 * of Friedel and Keller (2002), with deterministic preprocessing cached for
 * repeated randomizations.
 *
 * <p>The caller supplies the output cache, which may also be the input cache.
 * Every randomization uses the prepared snapshot, independently of previous
 * output values. Point counts, dimensions, and the output array's shape must
 * remain unchanged after construction. The adapter returned by
 * {@link #getRandomization()} is bound to this output cache.
 *
 * <p>Correct NUS requires sufficiently uniform and independent random bits,
 * within and between random words, up to the requested precision of 62 bits.
 * Random words are obtained through {@link RandomStream#nextBitsLong(int)}.
 * Implementations that scale a {@code nextDouble()} result cannot generally
 * supply 62 independent bits, and some supply fewer than 53.
 *
 * <p>Scaling an input double beyond its significant bits does not recover
 * additional information. Output follows the SSJ convention
 * {@code z / 2^outputBits + EpsilonHalf}, with {@code EpsilonHalf = 2^-55}.
 * Above 53 bits, distinct scrambled integers can yield the same double, and
 * rounding can yield exactly {@code 1.0}.
 */
public final class RQMCCachedNUS extends RQMCPointSet {
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int numPoints;
   private final int dimension;
   private final int outputBits;
   private final double normFactor;

   // To map each sorted coordinate position back to its original output row.
   private final int[][] originalIndices;

   // Reusable coordinate buffer indexed by dimension and original point order.
   private final double[][] scrambledCoordinates;

   // One mask per consecutive sorted coordinates in each dimension, including
   // the first differing bit and all lower bits. Including that bit lets
   // randomize() recover the branch to flip without retaining sorted coordinates.
   // Zero denotes identical integer values.
   private final long[][] firstDiffMasks;

   /**
    * Prepares full NUS without randomizing points or consuming stream values.
    * No separate reference to the input cache is retained; if it is also the
    * output cache, it remains reachable as the output. Subsequent changes to
    * the input cache do not alter the prepared snapshot. The randomization mode
    * operates directly on the cache rather than its parent.
    *
    * @param inputPoints  cache supplying the original coordinates
    * @param outputPoints caller-supplied output cache, possibly {@code inputPoints}
    * @param stream       source of random bits satisfying requirements above
    * @param outputBits   number of retained and scrambled bits, from 1 to 62
    * @throws IllegalArgumentException if an argument is null, sizes are
    *         nonpositive or incompatible, an array shape is incompatible,
    *         outputBits is outside 1 to 62, or an input coordinate is not
    *         finite and in {@code [0, 1)}
    */
   public RQMCCachedNUS(CachedPointSet inputPoints, CachedPointSet outputPoints,
         RandomStream stream, int outputBits) {
      this((PointSet) inputPoints, outputPoints, stream, outputBits);
   }

   /**
    * Prepares full NUS directly from a digital net's current generator matrices,
    * without constructing an intermediate input cache or converting through
    * doubles. Points retain their Gray-code indices. The net's {@code outDigits}
    * sets the scrambling precision directly.
    *
    * Construction consumes no random values and retains no direct reference to
    * the input net. The output cache may still reference it as its parent;
    * subsequent changes to the net's matrices do not alter the prepared snapshot.
    *
    * @param inputNet     digital net supplying the current generator matrices
    * @param outputPoints output cache with matching point count and dimension
    * @param stream       source of random bits satisfying the requirements above
    * @throws IllegalArgumentException if an argument is null, sizes or the output
    *         array shape are incompatible, the net's output precision is outside
    *         1 to 62, or its generator matrix
    *         dimensions cannot represent the requested points
    */
   public RQMCCachedNUS(DigitalNetBase2 inputNet, CachedPointSet outputPoints,
         RandomStream stream) {
      this((PointSet) inputNet, outputPoints, stream, inputNet == null ? 0 : inputNet.outDigits);
   }

   /** Initializes the fixed configuration and prepared tables for either input. */
   private RQMCCachedNUS(PointSet inputPoints, CachedPointSet outputPoints,
         RandomStream stream, int outputBits) {
      super(outputPoints, null);
      if (inputPoints == null || outputPoints == null || stream == null)
         throw new IllegalArgumentException("inputPoints, outputPoints and stream must not be null");
      if (inputPoints.getNumPoints() <= 0 || inputPoints.getDimension() <= 0)
         throw new IllegalArgumentException("The input point set must have a positive point count and dimension");
      if (outputPoints.getNumPoints() != inputPoints.getNumPoints()
            || outputPoints.getDimension() != inputPoints.getDimension())
         throw new IllegalArgumentException("The input point set and output cache must have the same sizes");
      if (outputBits < 1 || outputBits > 62)
         throw new IllegalArgumentException("outputBits must be between 1 and 62");

      this.numPoints = inputPoints.getNumPoints();
      this.dimension = inputPoints.getDimension();
      this.outputBits = outputBits;
      this.normFactor = Math.scalb(1.0, -outputBits);
      this.stream = stream;

      checkArrayShape(outputPoints.getArray(), "Output");
      originalIndices = new int[dimension][numPoints];
      scrambledCoordinates = new double[dimension][numPoints];
      firstDiffMasks = new long[dimension][numPoints - 1];
      prepare(inputPoints);

      rand = new NUSRandomization();
      outputPoints.setRandomizeParent(false);
   }

   private void checkArrayShape(double[][] points, String name) {
      if (points == null || points.length != numPoints)
         throw new IllegalArgumentException(name + " cache array must match the point count");
      for (int i = 0; i < numPoints; i++)
         if (points[i] == null || points[i].length != dimension)
            throw new IllegalArgumentException(name + " cache array must match the dimension");
   }

   /**
    * Obtains integer coordinates from either a cached point set or a digital net,
    * depending on the input type. Sorts the coordinates in each dimension and
    * retains only their original indices and first-difference masks.
    */
   private void prepare(PointSet inputPoints) {
      DigitalNetBase2 net = inputPoints instanceof DigitalNetBase2
            ? (DigitalNetBase2) inputPoints : null;
      double[][] input = null;
      if (net == null) {
         input = ((CachedPointSet) inputPoints).getArray();
         checkArrayShape(input, "Input");
      } else {
         int requiredColumns = Integer.SIZE - Integer.numberOfLeadingZeros(numPoints - 1);
         if (net.numCols < requiredColumns || net.genMat == null
               || (long) dimension * net.numCols > net.genMat.length)
            throw new IllegalArgumentException("The input net's generator matrices must match its sizes");
      }

      double scale = Math.scalb(1.0, outputBits);
      long[] bits = new long[numPoints];
      long[] sortedBits = new long[numPoints];
      int[] tempIndices = new int[numPoints];
      int[] counts = new int[256];
      int[] positions = new int[256];
      int numPasses = (outputBits + 7) / 8;

      for (int j = 0; j < dimension; j++) {
         // Choose the starting buffer so the final pass ends in originalIndices[j].
         int[] indices = (numPasses & 1) == 0 ? originalIndices[j] : tempIndices;
         int[] sortedIndices = (numPasses & 1) == 0 ? tempIndices : originalIndices[j];
         if (net == null) {
            for (int i = 0; i < numPoints; i++) {
               double u = input[i][j];
               // This interval check also rejects NaN and infinities.
               if (!(u >= 0.0 && u < 1.0))
                  throw new IllegalArgumentException("Input coordinates must be finite and in [0, 1)");
               bits[i] = (long) (u * scale);
            }
         } else {
            // Generate integer coordinates directly in Gray-code order.
            bits[0] = 0L;
            for (int i = 1; i < numPoints; i++) {
               int pos = Integer.numberOfTrailingZeros(i);
               bits[i] = bits[i - 1] ^ (net.genMat[j * net.numCols + pos]);
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
    * Scrambles the prepared snapshot into a reusable buffer, then writes output
    * rows in point order. Performs no sorting or preprocessing and allocates no
    * objects in this class. Consumes one random word per point per dimension,
    * including duplicate coordinates.
    */
   @Override
   public void randomize() {
      double[][] output = getPointSet().getArray();
      for (int j = 0; j < dimension; j++) {
         int[] indices = originalIndices[j];
         long[] masks = firstDiffMasks[j];
         double[] coordinates = scrambledCoordinates[j];

         // Under full NUS, the first scrambled coordinate is a uniform word;
         // its original bits can be absorbed into the random permutations.
         long z = stream.nextBitsLong(outputBits);
         coordinates[indices[0]] = z * normFactor + EPSILON_HALF;
         for (int i = 1; i < numPoints; i++) {
            long word = stream.nextBitsLong(outputBits);
            long mask = masks[i - 1];
            long lowerMask = mask >>> 1;
            long firstDiffBit = mask ^ lowerMask;

            // Preserve the common prefix, flip the branching bit, and randomize the suffix.
            // For duplicates, keep z unchanged but still consume a random word.
            z = ((z ^ firstDiffBit) & ~lowerMask) | (word & lowerMask);
            coordinates[indices[i]] = z * normFactor + EPSILON_HALF;
         }
      }

      for (int i = 0; i < numPoints; i++) {
         double[] point = output[i];
         for (int j = 0; j < dimension; j++)
            point[j] = scrambledCoordinates[j][i];
      }
   }

   /**
    * Returns the caller-supplied output cache.
    *
    * @return output cache
    */
   @Override
   public CachedPointSet getPointSet() {
      return (CachedPointSet) set;
   }

   /**
    * Returns the stream used for scrambling.
    *
    * @return randomization stream
    */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream used for scrambling. The new stream must satisfy the same
    * random-bit requirements as the constructor's stream.
    *
    * @param stream source of randomness
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   @Override
   public String toString() {
      return "Cached full nested uniform scrambling (outputBits = " + outputBits + ")";
   }

   /** Adapter providing PointSetRandomization compatibility, allowing SSJ code expecting
    *  a PointSetRandomization to use this class's randomization.
    */
   private final class NUSRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != set)
            throw new IllegalArgumentException("The NUS adapter can only randomize its associated output cache");
         RQMCCachedNUS.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return RQMCCachedNUS.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         RQMCCachedNUS.this.setStream(stream);
      }

      @Override
      public String toString() {
         return RQMCCachedNUS.this.toString();
      }
   }
}
