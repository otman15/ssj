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

import umontreal.ssj.rng.RandomStream;

/**
 * Applies the historical FK02 nested uniform scrambling algorithm to a digital
 * net in base 2.
 *
 * <p>The input net is retained and the caller supplies the cache that will receive
 * the randomized points. The FK02 implementation generates and sorts the
 * coordinates on every randomization.
 *
 * <p>Scratch arrays are allocated during construction and reused on every
 * randomization. The algorithm preserves the behavior of
 * {@link DigitalNetBase2#nestedUniformScramble64(RandomStream, double[][], int)},
 * including its 31-bit input prefixes, random-stream calls, and conversion to
 * doubles. The adapter returned by {@link #getRandomization()} is bound to this
 * object's output cache. Point counts, dimensions, the input net's output
 * precision, and the output array's shape must remain unchanged after construction.
 */
public final class RQMCDigitalNet2FK02 extends RQMCPointSet {
   private final DigitalNetBase2 inputNet;
   private RandomStream stream;
   private final int numBits;
   private final int[] poslist;
   private final long[] bvlist;
   private final int[] counts;
   private final int[] binpos;

   /**
    * Constructs the FK02 structure without generating or randomizing points.
    * The output cache need not have the input net as its parent. Its randomization
    * mode is set to operate directly on the cache.
    *
    * @param inputNet     digital net whose points will be scrambled
    * @param outputPoints caller-supplied cache with the same point count and
    *                     dimension as the input net
    * @param stream       source of randomness
    * @param numBits      number of most significant bits to scramble; zero selects
    *                     the input net's output precision
    * @throws IllegalArgumentException if an argument is null, the point count or
    *         dimension is nonpositive, the point count exceeds the scratch-array
    *         capacity, the input and output sizes or array shape are incompatible,
    *         the input precision is outside 31 to 62 bits, or numBits is outside
    *         the input precision
    */
   public RQMCDigitalNet2FK02(DigitalNetBase2 inputNet, CachedPointSet outputPoints,
         RandomStream stream, int numBits) {
      super(outputPoints, null);
      if (inputNet == null || outputPoints == null || stream == null)
         throw new IllegalArgumentException("inputNet, outputPoints and stream must not be null");
      if (inputNet.getNumPoints() <= 0 || inputNet.getDimension() <= 0)
         throw new IllegalArgumentException("The input net must have a positive point count and dimension");
      if (inputNet.getNumPoints() > Integer.MAX_VALUE / 2)
         throw new IllegalArgumentException("The input net has too many points for the FK02 scratch arrays");
      if (outputPoints.getNumPoints() != inputNet.getNumPoints()
            || outputPoints.getDimension() != inputNet.getDimension())
         throw new IllegalArgumentException("The input net and output cache must have the same sizes");
      if (inputNet.outDigits < 31 || inputNet.outDigits > 62)
         throw new IllegalArgumentException("The input net's output precision must be between 31 and 62 bits");
      if (numBits < 0 || numBits > inputNet.outDigits)
         throw new IllegalArgumentException("numBits must be between zero and the input net's output precision");

      double[][] output = outputPoints.getArray();
      if (output == null || output.length != inputNet.getNumPoints())
         throw new IllegalArgumentException("The output cache array must match the input net's point count");
      for (int i = 0; i < output.length; i++)
         if (output[i] == null || output[i].length != inputNet.getDimension())
            throw new IllegalArgumentException("The output cache array must match the input net's dimension");

      this.inputNet = inputNet;
      this.stream = stream;
      this.numBits = numBits;
      this.poslist = new int[2 * inputNet.getNumPoints()];
      this.bvlist = new long[2 * inputNet.getNumPoints()];
      this.counts = new int[256];
      this.binpos = new int[256];
      this.rand = new FK02Randomization();
      outputPoints.setRandomizeParent(false);
   }

   /**
    * Generates the input net's coordinates, sorts them, applies FK02 scrambling,
    * and writes the randomized points into the output cache. Reuses the scratch
    * arrays without allocating new objects or modifying the input net.
    */
   @Override
   public void randomize() {
      int numPoints = inputNet.numPoints;
      int dim = inputNet.dim;
      int outDigits = inputNet.outDigits;
      int numCols = inputNet.numCols;
      long[] genMat = inputNet.genMat;
      double EpsilonHalf = inputNet.EpsilonHalf;
      CachedPointSet outputPoints = getPointSet();
      double[][] output = outputPoints.getArray();

      int numBits = this.numBits;
      if (numBits == 0)
         numBits = outDigits;
      double localNormFactor = 1.0 / Math.abs((double) (1L << outDigits));

      for (int j = 0; j < dim; ++j) {
         bvlist[0] = 0;
         poslist[0] = 0;
         // Generate coordinates in Gray-code order, retaining the first 31 bits.
         for (int i = 1; i < numPoints; i++) {
            int pos = Integer.numberOfTrailingZeros(i);
            bvlist[i] = bvlist[i - 1] ^ (genMat[j * numCols + pos] >>> (outDigits - 31));
            poslist[i] = i;
         }

         // Four stable radix-sort passes alternate between the two scratch halves.
         for (int b = 0; b < 4; b++) {
            for (int i = 0; i < 256; i++)
               counts[i] = 0;
            int m = (b % 2) * numPoints;
            int bb = 8 * b;
            long bv = 0xff << bb;
            for (int i = 0; i < numPoints; i++)
               counts[(int) (bvlist[m + i] & bv) >>> bb]++;
            binpos[0] = (1 - b % 2) * numPoints;
            for (int i = 0; i < 255; i++)
               binpos[i + 1] = binpos[i] + counts[i];
            for (int i = 0; i < numPoints; i++) {
               int pos = (int) ((bvlist[m + i] & bv) >>> bb);
               int k = binpos[pos]++;
               bvlist[k] = bvlist[m + i];
               poslist[k] = poslist[m + i];
            }
         }

         long bv = stream.nextBitsLong(numBits) << (outDigits - numBits);
         long bvlistL = bvlist[0] << (outDigits - 31);
         output[poslist[0]][j] = (bvlistL ^ bv) * localNormFactor + EpsilonHalf;
         for (int i = 1; i < numPoints; i++) {
            long bv2 = bvlist[i - 1] << (outDigits - 31);
            bvlistL = bvlist[i] << (outDigits - 31);
            bv2 ^= bvlistL;

            long mask = bv2 == 0L ? 0L : Long.highestOneBit(bv2) - 1L;
            
            bv2 = (stream.nextBitsLong(numBits) << (outDigits - numBits))
                  &  mask;
            bv ^= bv2;
            output[poslist[i]][j] = (bvlistL ^ bv) * localNormFactor + EpsilonHalf;
         }
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
    * Sets the stream used for scrambling.
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
      return "FK02 nested uniform scrambling (numBits = " + numBits + ")";
   }

   /** Compatibility adapter bound to this object's output cache. */
   private final class FK02Randomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != set)
            throw new IllegalArgumentException("The FK02 adapter can only randomize its associated output cache");
         RQMCDigitalNet2FK02.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return RQMCDigitalNet2FK02.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         RQMCDigitalNet2FK02.this.setStream(stream);
      }

      @Override
      public String toString() {
         return RQMCDigitalNet2FK02.this.toString();
      }
   }
}
