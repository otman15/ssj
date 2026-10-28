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

import umontreal.ssj.rng.RandomStream;

/**
 * Computes Burley-scrambled points using 30 SSJ Sobol columns per dimension and
 * a single scrambled index shared by every dimension of a point.
 * Uses the 32-bit scrambling of Brent Burley's
 * <a href="https://jcgt.org/published/0009/04/01/paper.pdf">Practical Hash-based
 * Owen Scrambling</a> (2020), with the shared-index architecture inspired by
 * QuantLib. Every dimension uses its own SSJ Sobol generating matrix; there is
 * no four-dimensional padding or reuse.
 *
 * <p>Construction extracts the columns of
 * {@code new SobolSequence(30, 32, dim)}, independently of the requested point
 * count {@code 2^k}. It builds byte lookup tables from those columns without
 * generating coordinates, consuming randomness, or retaining the source net.
 * No columns are added. Storage is proportional to dimension and independent
 * of the number of points.
 *
 * <p>For point {@code i}, the matrix index is
 * {@code nestedUniformScramble(i, indexSeed) & 0x3fffffff}. The scramble uses
 * the original 32-bit arithmetic and bit reversals before retaining its low
 * 30 bits. The mask is fixed at 30 bits even when {@code k < 30}, so columns
 * beyond {@code k} still contribute. The same index is used in every dimension,
 * in ordinary binary order, without Gray coding or an index increment.
 *
 * <p>Call {@link #randomize()} before accessing coordinates. Each randomization
 * draws one fresh 32-bit {@code indexSeed} using
 * {@link RandomStream#nextBitsLong(int)}, then derives the seed of dimension
 * {@code j} as {@code hash_combine(indexSeed, j)}, using Burley's original
 * 32-bit hash and the global dimension number. All seed storage is reused.
 * Coordinate access consumes no randomness and is deterministic between
 * randomizations.
 *
 * <p>Coordinate scrambling uses all 32 bits. Its unsigned integer result
 * {@code z} is converted to {@code unsigned(z) * 2^-32 + 2^-55}, as in
 * {@link BurleyPointSet}. The lookup tables store reversed matrix contributions
 * to avoid a coordinate bit reversal; the first dimension uses the exact
 * radical-inverse shortcut. Each iterator caches only its current scrambled
 * index, sharing it across coordinates and refreshing it after randomization.
 * Direct {@link #getCoordinate(int, int)} access recomputes that index.
 *
 * <p>This is an SSJ variant, rather than a port of QuantLib's direction data,
 * seed expansion, index increment, or double conversion. For RQMC experiments,
 * use {@code new RQMCPointSet(points, points.getRandomization())}.
 *
 * @see BurleyPointSet
 * @see BurleyQuantLibPointSet
 */
public final class BurleySSJPointSet extends PointSet {
   private static final int NUM_COLUMNS = 30;
   private static final int INDEX_MASK = (1 << NUM_COLUMNS) - 1;
   private static final double NORM32 = 0x1.0p-32;
   private static final double EPSILON_HALF = 0x1.0p-55;

   private RandomStream stream;
   private final int[][] sobolByteTables;
   private final int[] coordinateSeeds;
   private final PointSetRandomization randomization;
   private int indexSeed;
   private long randomizationVersion;

   /**
    * Prepares 30-column SSJ Sobol matrices for all dimensions and allocates
    * reusable seed storage. No points are generated and no randomness is used.
    *
    * @param k number of points is {@code 2^k}, with {@code 0 <= k <= 30}
    * @param dim dimension, from 1 through SSJ's built-in Sobol limit of 360
    * @param stream source of random 32-bit seeds
    * @throws IllegalArgumentException if stream is null or a size is invalid
    */
   public BurleySSJPointSet(int k, int dim, RandomStream stream) {
      if (k < 0 || k > NUM_COLUMNS || dim <= 0 || dim > SobolSequence.MAXDIM || stream == null)
         throw new IllegalArgumentException("Require 0 <= k <= 30, 1 <= dim <= "
               + SobolSequence.MAXDIM + ", and a non-null stream");
      this.numPoints = 1 << k;
      this.dim = dim;
      this.stream = stream;
      long[] columns = new SobolSequence(NUM_COLUMNS, 32, dim).getGeneratorMatricesColumns();
      this.sobolByteTables = createByteTables(columns, dim);
      this.coordinateSeeds = new int[dim];
      this.randomization = new SsjRandomization();
   }

   private static int[][] createByteTables(long[] columns, int dimension) {
      int[][] tables = new int[dimension][];
      // Dimension zero is a radical inverse and needs no table.
      for (int j = 1; j < dimension; j++) {
         int[] table = new int[4 * 256];
         for (int c = 0; c < NUM_COLUMNS; c++) {
            int offset = (c >>> 3) * 256;
            int bit = 1 << (c & 7);
            int column = Integer.reverse((int) columns[j * NUM_COLUMNS + c]);
            for (int value = 0; value < bit; value++)
               table[offset + bit + value] = table[offset + value] ^ column;
         }
         // Only six bits of the last index byte are reachable after INDEX_MASK.
         tables[j] = table;
      }
      return tables;
   }

   /**
    * Draws one fresh 32-bit index seed and derives all coordinate seeds using
    * the global dimension index. Reuses storage and does not generate points.
    */
   public void randomize() {
      indexSeed = (int) stream.nextBitsLong(32);
      for (int j = 0; j < dim; j++)
         coordinateSeeds[j] = BurleyUtils.hashCombine(indexSeed, j);
      randomizationVersion++;
   }

   private int scrambledIndex(int i) {
      return BurleyUtils.nestedUniformScramble(i, indexSeed) & INDEX_MASK;
   }

   @Override
   public double getCoordinate(int i, int j) {
      if (i < 0 || i >= numPoints || j < 0 || j >= dim)
         throw new IndexOutOfBoundsException("Point or coordinate index out of range");
      return scrambledCoordinate(scrambledIndex(i), j);
   }

   private double scrambledCoordinate(int index, int j) {
      // reverse(sobol(index, 0)) = index for the first SSJ Sobol dimension.
      int reversedCoordinate = index;
      if (j != 0) {
         int[] table = sobolByteTables[j];
         reversedCoordinate = table[index & 255]
               ^ table[256 + ((index >>> 8) & 255)]
               ^ table[512 + ((index >>> 16) & 255)]
               ^ table[768 + (index >>> 24)];
      }
      int x = Integer.reverse(BurleyUtils.laineKarrasPermutation(reversedCoordinate, coordinateSeeds[j]));
      return Integer.toUnsignedLong(x) * NORM32 + EPSILON_HALF;
   }

   /**
    * Returns an iterator that shares one scrambled index across the coordinates
    * of its current point. Its cache sees later randomizations without a reset.
    *
    * @return an iterator over this point set
    */
   @Override
   public PointSetIterator iterator() {
      return new SsjIterator();
   }

   /** @return the stream used to draw index seeds */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream for future randomizations without changing current seeds.
    *
    * @param stream source of random 32-bit seeds
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   /**
    * Returns the RQMC adapter bound to this instance. Its stream accessors
    * delegate to this point set.
    *
    * @return the associated randomization
    */
   public PointSetRandomization getRandomization() {
      return randomization;
   }

   /**
    * Rejects random shifts; use {@link #randomize()} or {@link #getRandomization()}.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void addRandomShift(int d1, int d2, RandomStream stream) {
      throw new UnsupportedOperationException("Use the associated Burley SSJ randomization");
   }

   @Override
   public String toString() {
      return "Burley SSJ scrambling (30 columns, 32 output bits, shared index, dimension = "
            + dim + ", points = " + numPoints + ")";
   }

   private final class SsjIterator extends DefaultPointSetIterator {
      private int cachedPoint = -1;
      private int cachedIndex;
      private long cachedVersion;

      private int prepareIndex() {
         if (cachedPoint != curPointIndex || cachedVersion != randomizationVersion) {
            cachedIndex = scrambledIndex(curPointIndex);
            cachedPoint = curPointIndex;
            cachedVersion = randomizationVersion;
         }
         return cachedIndex;
      }

      @Override
      public double nextCoordinate() {
         if (curPointIndex >= numPoints || curCoordIndex >= dim)
            outOfBounds();
         if (curPointIndex < 0 || curCoordIndex < 0)
            throw new IndexOutOfBoundsException("Point or coordinate index out of range");
         double coordinate = scrambledCoordinate(prepareIndex(), curCoordIndex);
         curCoordIndex++;
         return coordinate;
      }

      @Override
      public void nextCoordinates(double[] p, int d) {
         if (d > dim - curCoordIndex || (d > 0 && curPointIndex >= numPoints))
            outOfBounds();
         if (d > 0) {
            if (curPointIndex < 0 || curCoordIndex < 0)
               throw new IndexOutOfBoundsException("Point or coordinate index out of range");
            int index = prepareIndex();
            for (int c = 0; c < d; c++) {
               p[c] = scrambledCoordinate(index, curCoordIndex);
               curCoordIndex++;
            }
         }
      }
   }

   private final class SsjRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != BurleySSJPointSet.this)
            throw new IllegalArgumentException("The Burley SSJ adapter can only randomize its associated point set");
         BurleySSJPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return BurleySSJPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         BurleySSJPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return BurleySSJPointSet.this.toString();
      }
   }
}
