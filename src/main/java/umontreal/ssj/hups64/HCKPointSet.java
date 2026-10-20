/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
 * method of Helmer, Christensen, and Kensler (HCK). The point count must be
 * {@code n = 2^k}, and the leading {@code k x k} block of each generating matrix
 * must be non-singular upper-triangular (NUT).
 *
 * <p>Construction derives the index XOR masks from the supplied generating
 * matrices without retaining them or consuming random numbers. Call
 * {@link #randomize()} before accessing coordinates. Each randomization generates
 * every coordinate in ordinary binary index order and stores its double value
 * in a dimension-major array. One integer work array is reused for each
 * dimension so that rounding cannot affect the generation of later points.
 * The construction and random-number consumption order are the same as in
 * {@link RQMCDigitalNet2HCKLong}.
 *
 * <p>Each integer coordinate is converted once during randomization using the
 * SSJ convention {@code z / 2^outputBits + 2^-55}. Coordinate access returns the
 * stored double directly. At high precision, distinct integers may round to the
 * same double, and an output may round to exactly {@code 1.0}. Full scrambling
 * requires the stream to provide sufficiently uniform and independent random
 * bits through {@link RandomStream#nextBitsLong(int)}.
 *
 * <p>The inherited default iterator accesses these stored coordinates. For RQMC
 * experiments, use this point set with {@link #getRandomization()}, or wrap the
 * pair in an {@link RQMCPointSet}.
 */
public final class HCKPointSet extends PointSet {
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int outputBits;
   private final int numLevels;
   private final double normFactor;
   // XOR mask for dimension j and level m, stored at j * numLevels + m.
   private final int[] invLastCol1;
   // Converted coordinates, stored by dimension for direct access.
   private final double[][] coordinates;
   // Keep exact integer coordinates between levels; reuse this work array for each dimension.
   private final long[] scrambledBits;
   private final PointSetRandomization randomization;

   /**
    * Prepares the XOR masks and allocates coordinate storage from a digital net.
    * Uses the net's current generating matrices; its separate digital shift is
    * ignored. Later changes to the net do not affect this point set.
    *
    * @param inputNet digital net supplying the NUT generating matrices
    * @param stream source of random bits
    * @param outputBits number of scrambled bits, from
    *        {@code max(1, log2(numPoints))} up to 62
    * @throws IllegalArgumentException if an argument is null, the point count is
    *         not a power of two, the sizes or precision are unsupported, or the
    *         leading generating matrices are not NUT
    */
   public HCKPointSet(DigitalNetBase2 inputNet, RandomStream stream, int outputBits) {
      this(requireInputNet(inputNet).genMat, inputNet.getNumPoints(),
            inputNet.getDimension(), inputNet.numCols, inputNet.outDigits,
            stream, outputBits);
   }

   /**
    * Prepares the XOR masks and allocates coordinate storage from packed
    * generating matrices, without generating points or consuming random numbers.
    *
    * <p>Column {@code c} of dimension {@code j} is stored at
    * {@code genMat[j * numCols + c]}. Row {@code r} occupies bit
    * {@code outDigits - 1 - r}, following {@link DigitalNetBase2}. Only the first
    * {@code k} rows and columns are needed, where {@code numPoints = 2^k}.
    * The array is neither modified nor retained, so later changes to it do not
    * affect this point set.
    *
    * @param genMat packed generating-matrix columns
    * @param numPoints number of points, a positive power of two
    * @param dim positive dimension of the point set
    * @param numCols number of columns per matrix, at least {@code log2(numPoints)}
    * @param outDigits number of bits used to pack each column, from
    *        {@code max(1, log2(numPoints))} up to 63
    * @param stream source of random bits
    * @param outputBits number of scrambled bits, from
    *        {@code max(1, log2(numPoints))} up to 62
    * @throws IllegalArgumentException if an argument is null, the point count is
    *         not a power of two, the sizes or precision are unsupported, or the
    *         leading generating matrices are not NUT
    */
   public HCKPointSet(long[] genMat, int numPoints, int dim, int numCols,
         int outDigits, RandomStream stream, int outputBits) {
      if (genMat == null || stream == null)
         throw new IllegalArgumentException("genMat and stream must not be null");
      if (numPoints <= 0 || Integer.bitCount(numPoints) != 1 || dim <= 0)
         throw new IllegalArgumentException("The point set must have 2^k points and a positive dimension");
      int k = Integer.numberOfTrailingZeros(numPoints);
      if (outputBits < 1 || outputBits > 62 || outputBits < k)
         throw new IllegalArgumentException("outputBits must be between max(1, k) and 62");
      if (outDigits < Math.max(1, k) || outDigits > 63)
         throw new IllegalArgumentException("The generating matrices must have at least k and at most 63 output digits");
      if (numCols < k || (long) dim * numCols > genMat.length)
         throw new IllegalArgumentException("The generating matrices must match their sizes");

      this.numPoints = numPoints;
      this.dim = dim;
      this.stream = stream;
      this.outputBits = outputBits;
      this.numLevels = k;
      this.normFactor = Math.scalb(1.0, -outputBits);
      this.invLastCol1 = new int[dim * k];

      // For each dimension, extract the leading k x k NUT generating matrix C.
      // For each level m, solve C v = e_m over GF(2) by back substitution to obtain
      // the m-th column of C^(-1). Clearing its diagonal bit gives HCK's XOR mask:
      // invLastCol1[j * k + m] = C^(-1)e_m XOR e_m.
      int[] rows = new int[k];
      for (int j = 0; j < dim; j++) {
         int dimensionOffset = j * k;
         Arrays.fill(rows, 0);
         for (int c = 0; c < k; c++) {
            long column = genMat[j * numCols + c];
            for (int r = 0; r < k; r++) {
               boolean bit = ((column >>> (outDigits - 1 - r)) & 1L) != 0L;
               if ((r == c && !bit) || (r > c && bit))
                  throw new IllegalArgumentException("The leading generating matrices must be NUT");
               // Pack row r in an int, with C[r,c] in bit c.
               if (bit)
                  rows[r] |= 1 << c;
            }
         }
         for (int m = 0; m < k; m++) {
            int em = 1 << m; // Unit vector e_m, packed in an int.
            int inverseColumn = em; // v_m = 1; v_r = 0 for r > m.
            // Back substitution computes v_r for r < m, where v = C^(-1)e_m.
            for (int r = m - 1; r >= 0; r--)
               // In GF(2), the dot product is the parity of the intersecting 1 bits.
               if ((Integer.bitCount(rows[r] & inverseColumn) & 1) != 0)
                  inverseColumn |= 1 << r; // Store the computed entry v_r = 1.
            invLastCol1[dimensionOffset + m] = inverseColumn ^ em; // Clear diagonal bit m.
         }
      }

      this.coordinates = new double[dim][numPoints];
      this.scrambledBits = new long[numPoints];
      this.randomization = new HCKRandomization();
   }

   private static DigitalNetBase2 requireInputNet(DigitalNetBase2 inputNet) {
      if (inputNet == null)
         throw new IllegalArgumentException("inputNet must not be null");
      return inputNet;
   }

   /**
    * Generates a fresh scrambling and stores the converted double coordinates.
    * The first point is uniform; at each doubling level, new coordinates are
    * drawn in dyadic strata adjacent to those of the previous points selected by
    * the precomputed XOR masks. Reuses the integer work array for each dimension
    * and uses the current stream without resetting it or allocating coordinate
    * storage.
    */
   public void randomize() {
      // The work array needs no clearing: each level reads only the prefix
      // already overwritten for the current dimension.
      for (int j = 0; j < dim; j++) {
         int dimensionOffset = j * numLevels;
         double[] output = coordinates[j];
         // Draw point 0's coordinate uniformly on the outputBits-bit grid.
         long z = stream.nextBitsLong(outputBits);
         scrambledBits[0] = z;
         output[0] = z * normFactor + EPSILON_HALF;

         // At level m, append 2^m points to the existing 2^m points.
         for (int m = 0, previousLength = 1; m < numLevels; m++, previousLength <<= 1) {
            int suffixBits = outputBits - m - 1; // Bits following the newly flipped bit.
            int xorMask = invLastCol1[dimensionOffset + m];

            for (int i = 0; i < previousLength; i++) {
               int newIndex = previousLength + i;
               // The mask uses only index bits 0..m-1, so this point already exists.
               long previous = scrambledBits[i ^ xorMask];
               // Keep the first m bits and flip the next bit to select the adjacent stratum.
               long adjacentStratum = (previous >>> suffixBits) ^ 1L;
               // Fill the remaining bits with fresh randomness; skip the draw if none remain.
               long suffix = suffixBits == 0 ? 0L : stream.nextBitsLong(suffixBits);

               // Combine the adjacent-stratum prefix and its random suffix.
               z = (adjacentStratum << suffixBits) | suffix;
               scrambledBits[newIndex] = z;
               output[newIndex] = z * normFactor + EPSILON_HALF;
            }
         }
      }
   }

   @Override
   public double getCoordinate(int i, int j) {
      return coordinates[j][i];
   }

   /**
    * Rejects random shifts. Use {@link #randomize()} or the adapter returned by
    * {@link #getRandomization()} to generate a new HCK scrambling.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void addRandomShift(int d1, int d2, RandomStream stream) {
      throw new UnsupportedOperationException("Use the associated HCK randomization");
   }

   /**
    * Returns the stream used for scrambling.
    *
    * @return the current scrambling stream
    */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream used for future randomizations, without changing existing
    * coordinates.
    *
    * @param stream source of random bits
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   /**
    * Returns the adapter for randomizing this point set in RQMC experiments.
    * Its stream accessors delegate to this point set. The adapter rejects any
    * point set other than this instance.
    *
    * @return the associated HCK randomization
    */
   public PointSetRandomization getRandomization() {
      return randomization;
   }

   @Override
   public String toString() {
      return "HCK full nested uniform scrambling with double storage (outputBits = "
            + outputBits + ")";
   }

   private final class HCKRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != HCKPointSet.this)
            throw new IllegalArgumentException("The HCK adapter can only randomize its associated point set");
         HCKPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return HCKPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         HCKPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return HCKPointSet.this.toString();
      }
   }
}
