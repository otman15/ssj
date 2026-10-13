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
 * Generates an Owen-scrambled base-2 digital point set using the sequential method
 * of Helmer, Christensen, and Kensler. The input net must have
 * non-singular upper-triangular (NUT) generating matrices.
 * This implementation requires the number of points to be a power of two, {@code n = 2^k}.
 * The construction follows Sections 3 and 4 of
 * <a href="https://doi.org/10.2312/sr.20211287">Stochastic Generation of
 * (t,s) Sample Sequences</a>.
 *
 * <p>The constructor derives the index XOR masks from the current generator
 * matrices. It does not store the input net directly or consume random numbers. Each
 * call to {@link #randomize()} generates the sequence afresh in ordinary binary
 * index order and writes it into the caller-supplied output cache.
 *
 * <p>Internal coordinates use integers so that rounding the output doubles
 * cannot change the strata used to generate later points. Output follows the
 * SSJ convention {@code z / 2^outputBits + EpsilonHalf}, with
 * {@code EpsilonHalf = 2^-55}. At high precision distinct integers may round to
 * the same double, and an output may round to exactly {@code 1.0}.
 *
 * <p>Full scrambling requires the random stream to provide sufficiently
 * uniform and independent random bits up to {@code outputBits}. This class
 * uses {@link RandomStream#nextLong(long, long)}.
 */
public final class RQMCDigitalNet2HCK extends RQMCPointSet {
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int dimension;
   private final int outputBits;
   private final int numLevels;
   private final double normFactor;
   private final long maxWord;

   // XOR mask used to select the previous point for dimension j at level m.
   private final int[][] truncatedInverseColumns;

   // Keep exact coordinates between stages; the output cache contains doubles.
   private final long[] scrambledBits;

   /**
    * Prepares an HCK randomization without generating any points.
    *
    * @param inputNet digital net supplying the NUT generator matrices
    * @param outputPoints caller-supplied cache for the scrambled sequence.
    *                      The constructor disables parent randomization on this cache.
    * @param stream source of random bits
    * @param outputBits number of scrambled output bits, from
    *        {@code max(1, log2(numPoints))} up to 62
    * @throws IllegalArgumentException if an argument is null, sizes or the
    *         output array are incompatible, the point count is not a power of
    *         two, the precision is unsupported, or the leading generator
    *         matrices are not NUT
    */
   public RQMCDigitalNet2HCK(DigitalNetBase2 inputNet, CachedPointSet outputPoints,
         RandomStream stream, int outputBits) {
      super(outputPoints, null);
      if (inputNet == null || outputPoints == null || stream == null)
         throw new IllegalArgumentException("inputNet, outputPoints and stream must not be null");

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
      if (outputPoints.getNumPoints() != n || outputPoints.getDimension() != dim)
         throw new IllegalArgumentException("The output cache must match the input net's sizes");

      double[][] output = outputPoints.getArray();
      if (output == null || output.length != n)
         throw new IllegalArgumentException("The output cache array must match the point count");
      for (int i = 0; i < n; i++)
         if (output[i] == null || output[i].length != dim)
            throw new IllegalArgumentException("The output cache array must match the dimension");

      this.stream = stream;
      this.dimension = dim;
      this.outputBits = outputBits;
      this.numLevels = k;
      this.normFactor = Math.scalb(1.0, -outputBits);
      this.maxWord = (1L << outputBits) - 1L;
      this.truncatedInverseColumns = new int[dim][k];

      // For each dimension, extract the leading k x k NUT generating matrix C.
      // For each level m, solve C v = e_m by back substitution to obtain
      // the m-th column of C^(-1). Clearing its diagonal bit gives
      // HCK's XOR lookup value: truncatedInverseColumns[j][m] = C^(-1)e_m XOR e_m.
      int[] rows = new int[k];
      for (int j = 0; j < dim; j++) {
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
            int inverseColumn = em; // v_m = 1
            for (int r = m - 1; r >= 0; r--) // Use back substitution to compute v_r for r < m, where v = C^(-1)e_m.
               // Count products equal to 1 and check their parity instead of XORing them.
               if ((Integer.bitCount(rows[r] & inverseColumn) & 1) != 0)
                  inverseColumn |= 1 << r; // computed vector v packed in an integer
            truncatedInverseColumns[j][m] = inverseColumn ^ em; // truncatedInverseColumns = C^(-1)e_m xor e_m
         }
      }

      this.scrambledBits = new long[n]; // Keep exact integer coordinates between levels to avoid double rounding.
      rand = new HCKRandomization();
      outputPoints.setRandomizeParent(false);
   }

   /**
    * Generates a new scrambled sequence in the supplied cache. The first point
    * is uniform; at each doubling level, new points are drawn in the stratum
    * adjacent to the ones selected by the precomputed XOR value. This method
    * allocates no objects in this class.
    */
   @Override
   public void randomize() {
      double[][] output = getPointSet().getArray();

      for (int j = 0; j < dimension; j++) {
         long z = stream.nextLong(0, maxWord);
         scrambledBits[0] = z;
         output[0][j] = z * normFactor + EPSILON_HALF;

         for (int m = 0, previousLength = 1; m < numLevels; m++, previousLength <<= 1) {
            int suffixBits = outputBits - m - 1;
            long maxSuffix = (1L << suffixBits) - 1L;

            for (int i = 0; i < previousLength; i++) {
               int newIndex = previousLength + i;
               long previous = scrambledBits[i ^ truncatedInverseColumns[j][m]];
               long adjacentStratum = (previous >>> suffixBits) ^ 1L;
               long suffix = suffixBits == 0 ? 0L : stream.nextLong(0, maxSuffix);

               z = (adjacentStratum << suffixBits) | suffix;
               scrambledBits[newIndex] = z;
               output[newIndex][j] = z * normFactor + EPSILON_HALF;
            }
         }
      }
   }

   /** Returns the caller-supplied output cache. */
   @Override
   public CachedPointSet getPointSet() {
      return (CachedPointSet) set;
   }

   /** Returns the stream used to generate the scrambled sequence. */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream used for future randomizations.
    *
    * @param stream source of random bits
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   @Override
   public String toString() {
      return "HCK full nested uniform scrambling (outputBits = " + outputBits + ")";
   }

   /** Adapter used by RQMCExperiment64 with this object's output cache. */
   private final class HCKRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != set)
            throw new IllegalArgumentException("The HCK adapter can only randomize its associated output cache");
         RQMCDigitalNet2HCK.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return RQMCDigitalNet2HCK.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         RQMCDigitalNet2HCK.this.setStream(stream);
      }

      @Override
      public String toString() {
         return RQMCDigitalNet2HCK.this.toString();
      }
   }
}
