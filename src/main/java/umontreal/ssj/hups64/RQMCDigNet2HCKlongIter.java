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
 * Generates a fully Owen-scrambled base-2 digital point set using the sequential
 * method of Helmer, Christensen, and Kensler. The input net must have NUT
 * generator matrices and {@code n = 2^k} points. This class uses the same
 * construction and random-number consumption order as
 * {@link RQMCDigitalNet2HCK}, but retains all coordinates as integers instead of
 * storing converted doubles in a {@link CachedPointSet}.
 *
 * <p>Each call to {@link #randomize()} generates every coordinate. Coordinate
 * access converts the stored integer to a double using the SSJ convention
 * {@code z / 2^outputBits + 2^-55}. At high precision, distinct integers may
 * round to the same double, and an output may round to exactly {@code 1.0}.
 * Full scrambling requires the stream to provide sufficiently uniform and
 * independent random bits through {@link RandomStream#nextBitsLong(int)}.
 */
public final class RQMCDigNet2HCKlongIter extends RQMCPointSet {
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int dimension;
   private final int outputBits;
   private final int numLevels;
   private final double normFactor;
   private final int[] invLastCol1;
   private final long[][] scrambledBits;

   /**
    * Prepares the XOR masks and allocates integer coordinate storage without
    * generating points or consuming random numbers.
    *
    * @param inputNet digital net supplying the NUT generator matrices
    * @param stream source of random bits
    * @param outputBits number of scrambled bits, from
    *        {@code max(1, log2(numPoints))} up to 62
    * @throws IllegalArgumentException if an argument is null, the point count is
    *         not a power of two, the sizes or precision are unsupported, or the
    *         leading generator matrices are not NUT
    */
   public RQMCDigNet2HCKlongIter(DigitalNetBase2 inputNet,
         RandomStream stream, int outputBits) {
      super(null, null);
      if (inputNet == null || stream == null)
         throw new IllegalArgumentException("inputNet and stream must not be null");

      int n = inputNet.getNumPoints();
      int dim = inputNet.getDimension();
      if (n <= 0 || Integer.bitCount(n) != 1 || dim <= 0)
         throw new IllegalArgumentException("The input net must have 2^k points and a positive dimension");
      int k = Integer.numberOfTrailingZeros(n);
      if (outputBits < 1 || outputBits > 62 || outputBits < k)
         throw new IllegalArgumentException("outputBits must be between max(1, k) and 62");
      if (inputNet.outDigits < Math.max(1, k) || inputNet.outDigits > 63)
         throw new IllegalArgumentException("The input net must have at least k and at most 63 output digits");
      if (inputNet.numCols < k || inputNet.genMat == null
            || (long) dim * inputNet.numCols > inputNet.genMat.length)
         throw new IllegalArgumentException("The input net's generator matrices must match its sizes");

      this.stream = stream;
      this.dimension = dim;
      this.outputBits = outputBits;
      this.numLevels = k;
      this.normFactor = Math.scalb(1.0, -outputBits);
      this.invLastCol1 = new int[dim * k];

      // Each mask is the m-th column of C^(-1), with its diagonal bit cleared.
      int[] rows = new int[k];
      for (int j = 0; j < dim; j++) {
         int dimensionOffset = j * k;
         Arrays.fill(rows, 0);
         for (int c = 0; c < k; c++) {
            long column = inputNet.genMat[j * inputNet.numCols + c];
            for (int r = 0; r < k; r++) {
               boolean bit = ((column >>> (inputNet.outDigits - 1 - r)) & 1L) != 0L;
               if ((r == c && !bit) || (r > c && bit))
                  throw new IllegalArgumentException("The input net's leading generator matrices must be NUT");
               if (bit)
                  rows[r] |= 1 << c;
            }
         }
         for (int m = 0; m < k; m++) {
            int em = 1 << m;
            int inverseColumn = em;
            for (int r = m - 1; r >= 0; r--)
               if ((Integer.bitCount(rows[r] & inverseColumn) & 1) != 0)
                  inverseColumn |= 1 << r;
            invLastCol1[dimensionOffset + m] = inverseColumn ^ em;
         }
      }

      this.scrambledBits = new long[dim][n];
      set = new LongPointSet(n, dim, scrambledBits, normFactor);
      rand = new HCKRandomization();
   }

   /** Generates every integer coordinate for a fresh scrambling. */
   @Override
   public void randomize() {
      for (int j = 0; j < dimension; j++) {
         int dimensionOffset = j * numLevels;
         long[] coordinates = scrambledBits[j];
         long z = stream.nextBitsLong(outputBits);
         coordinates[0] = z;

         for (int m = 0, previousLength = 1; m < numLevels; m++, previousLength <<= 1) {
            int suffixBits = outputBits - m - 1;
            int xorMask = invLastCol1[dimensionOffset + m];

            for (int i = 0; i < previousLength; i++) {
               int newIndex = previousLength + i;
               long previous = coordinates[i ^ xorMask];
               long adjacentStratum = (previous >>> suffixBits) ^ 1L;
               long suffix = suffixBits == 0 ? 0L : stream.nextBitsLong(suffixBits);

               z = (adjacentStratum << suffixBits) | suffix;
               coordinates[newIndex] = z;
            }
         }
      }
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

   @Override
   public String toString() {
      return "HCK full nested uniform scrambling with integer storage (outputBits = "
            + outputBits + ")";
   }

   /** Point set backed by the generated integer coordinates. */
   private static final class LongPointSet extends PointSet {
      private final long[][] bits;
      private final double normFactor;

      private LongPointSet(int n, int dimension, long[][] bits, double normFactor) {
         numPoints = n;
         dim = dimension;
         this.bits = bits;
         this.normFactor = normFactor;
      }

      @Override
      public double getCoordinate(int i, int j) {
         return bits[j][i] * normFactor + EPSILON_HALF;
      }

      @Override
      public PointSetIterator iterator() {
         return new HCKLongIterator();
      }

      private final class HCKLongIterator extends DefaultPointSetIterator {
         @Override
         public double nextCoordinate() {
            if (curPointIndex >= numPoints || curCoordIndex >= dim)
               outOfBounds();
            return bits[curCoordIndex++][curPointIndex] * normFactor + EPSILON_HALF;
         }

         @Override
         public void nextCoordinates(double[] p, int d) {
            if (curCoordIndex + d > dim || (d > 0 && curPointIndex >= numPoints))
               outOfBounds();
            for (int j = 0; j < d; j++)
               p[j] = bits[curCoordIndex++][curPointIndex] * normFactor + EPSILON_HALF;
         }
      }

      @Override
      public void addRandomShift(int d1, int d2, RandomStream stream) {
         throw new UnsupportedOperationException("Use the associated HCK randomization");
      }
   }

   /** Adapter for use by {@code RQMCExperiment64}. */
   private final class HCKRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != set)
            throw new IllegalArgumentException("The HCK adapter can only randomize its associated point set");
         RQMCDigNet2HCKlongIter.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return RQMCDigNet2HCKlongIter.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         RQMCDigNet2HCKlongIter.this.setStream(stream);
      }

      @Override
      public String toString() {
         return RQMCDigNet2HCKlongIter.this.toString();
      }
   }
}
