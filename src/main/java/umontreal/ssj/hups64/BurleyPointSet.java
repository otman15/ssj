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
 * Computes Burley's shuffled, hash-scrambled digital points on demand, following
 * Listing 3 of Brent Burley's
 * <a href="https://jcgt.org/published/0009/04/01/paper.pdf">Practical Hash-based
 * Owen Scrambling</a> (2020) and its supplemental C++ implementation.
 * Groups of four coordinates reuse the same four generating matrices, with a
 * separate random seed for each group. Within each group, the point index is
 * scrambled, then each coordinate is scrambled with
 * {@code hash_combine(groupSeed, localDimension)}.
 *
 * <p>The constructors select either Burley's four reference matrices (the
 * default), or four hardcoded matrices derived from SSJ's built-in
 * {@link SobolSequence} direction data. Both sources provide 32 columns and
 * 32 output bits. The complete 32-bit scrambled index enters the matrix product,
 * even when the point count is only {@code 2^k} with {@code k < 32}. In
 * particular, its high bits retain their seed-dependent XOR-shift contribution
 * through columns beyond {@code k}. The number of points does not limit the
 * number of columns used. Matrix products use ordinary binary indices, without
 * a Gray-code transformation.
 *
 * <p>Construction consumes no random numbers. Call {@link #randomize()} before
 * accessing coordinates. Each randomization draws one 32-bit seed per group,
 * directly from {@link RandomStream#nextBitsLong(int)}, without an additional
 * hash of that seed. This corresponds to the seed argument of Listing 3, rather
 * than the user seed hashed by the supplemental {@code genpoints} command.
 * Coordinates are deterministic until the next randomization; coordinate access
 * consumes no randomness. Only small matrix lookup tables and seeds are stored,
 * using space independent of the number of points.
 *
 * <p>The integer coordinate {@code z} is converted using SSJ's convention
 * {@code unsigned(z) * 2^-32 + 2^-55}, giving a value strictly between zero and
 * one. The specialized iterator shares index scrambling within each group and
 * computes only the requested coordinates. Its cached index is refreshed after
 * a randomization, a point change, or a change of coordinate group.
 * For RQMC experiments, use this point set with {@link #getRandomization()}, for
 * example {@code new RQMCPointSet(points, points.getRandomization())}.
 */
public final class BurleyPointSet extends PointSet {
   private static final int GROUP_SIZE = 4;
   private static final double NORM32 = 0x1.0p-32;
   private static final double EPSILON_HALF = 0x1.0p-55;

   // First four rows of directions[5][32] in Burley's supplemental sobol.cpp.
   private static final int[][] REFERENCE_DIRECTIONS = {
      {
         0x80000000, 0x40000000, 0x20000000, 0x10000000,
         0x08000000, 0x04000000, 0x02000000, 0x01000000,
         0x00800000, 0x00400000, 0x00200000, 0x00100000,
         0x00080000, 0x00040000, 0x00020000, 0x00010000,
         0x00008000, 0x00004000, 0x00002000, 0x00001000,
         0x00000800, 0x00000400, 0x00000200, 0x00000100,
         0x00000080, 0x00000040, 0x00000020, 0x00000010,
         0x00000008, 0x00000004, 0x00000002, 0x00000001
      },
      {
         0x80000000, 0xc0000000, 0xa0000000, 0xf0000000,
         0x88000000, 0xcc000000, 0xaa000000, 0xff000000,
         0x80800000, 0xc0c00000, 0xa0a00000, 0xf0f00000,
         0x88880000, 0xcccc0000, 0xaaaa0000, 0xffff0000,
         0x80008000, 0xc000c000, 0xa000a000, 0xf000f000,
         0x88008800, 0xcc00cc00, 0xaa00aa00, 0xff00ff00,
         0x80808080, 0xc0c0c0c0, 0xa0a0a0a0, 0xf0f0f0f0,
         0x88888888, 0xcccccccc, 0xaaaaaaaa, 0xffffffff
      },
      {
         0x80000000, 0xc0000000, 0x60000000, 0x90000000,
         0xe8000000, 0x5c000000, 0x8e000000, 0xc5000000,
         0x68800000, 0x9cc00000, 0xee600000, 0x55900000,
         0x80680000, 0xc09c0000, 0x60ee0000, 0x90550000,
         0xe8808000, 0x5cc0c000, 0x8e606000, 0xc5909000,
         0x6868e800, 0x9c9c5c00, 0xeeee8e00, 0x5555c500,
         0x8000e880, 0xc0005cc0, 0x60008e60, 0x9000c590,
         0xe8006868, 0x5c009c9c, 0x8e00eeee, 0xc5005555
      },
      {
         0x80000000, 0xc0000000, 0x20000000, 0x50000000,
         0xf8000000, 0x74000000, 0xa2000000, 0x93000000,
         0xd8800000, 0x25400000, 0x59e00000, 0xe6d00000,
         0x78080000, 0xb40c0000, 0x82020000, 0xc3050000,
         0x208f8000, 0x51474000, 0xfbea2000, 0x75d93000,
         0xa0858800, 0x914e5400, 0xdbe79e00, 0x25db6d00,
         0x58800080, 0xe54000c0, 0x79e00020, 0xb6d00050,
         0x800800f8, 0xc00c0074, 0x200200a2, 0x50050093
      }
   };

   // SSJ SobolSequence.poly/minit and initGenMat(), with w = 32. Columns 1--30
   // match SobolSequence(30, 32, 4); columns 31--32 continue the same recurrence.
   private static final int[][] SSJ_DIRECTIONS_32 = {
      {
         0x80000000, 0x40000000, 0x20000000, 0x10000000,
         0x08000000, 0x04000000, 0x02000000, 0x01000000,
         0x00800000, 0x00400000, 0x00200000, 0x00100000,
         0x00080000, 0x00040000, 0x00020000, 0x00010000,
         0x00008000, 0x00004000, 0x00002000, 0x00001000,
         0x00000800, 0x00000400, 0x00000200, 0x00000100,
         0x00000080, 0x00000040, 0x00000020, 0x00000010,
         0x00000008, 0x00000004, 0x00000002, 0x00000001
      },
      {
         0x80000000, 0xc0000000, 0xa0000000, 0xf0000000,
         0x88000000, 0xcc000000, 0xaa000000, 0xff000000,
         0x80800000, 0xc0c00000, 0xa0a00000, 0xf0f00000,
         0x88880000, 0xcccc0000, 0xaaaa0000, 0xffff0000,
         0x80008000, 0xc000c000, 0xa000a000, 0xf000f000,
         0x88008800, 0xcc00cc00, 0xaa00aa00, 0xff00ff00,
         0x80808080, 0xc0c0c0c0, 0xa0a0a0a0, 0xf0f0f0f0,
         0x88888888, 0xcccccccc, 0xaaaaaaaa, 0xffffffff
      },
      {
         0x80000000, 0x40000000, 0xe0000000, 0xb0000000,
         0x68000000, 0xf4000000, 0x86000000, 0x4f000000,
         0xe8800000, 0xb4400000, 0x66e00000, 0xffb00000,
         0x80e80000, 0x40b40000, 0xe0660000, 0xb0ff0000,
         0x68808000, 0xf4404000, 0x86e0e000, 0x4fb0b000,
         0xe8e86800, 0xb4b4f400, 0x66668600, 0xffff4f00,
         0x80006880, 0x4000f440, 0xe00086e0, 0xb0004fb0,
         0x6800e8e8, 0xf400b4b4, 0x86006666, 0x4f00ffff
      },
      {
         0x80000000, 0xc0000000, 0xe0000000, 0x50000000,
         0x38000000, 0xac000000, 0x62000000, 0x93000000,
         0xdb800000, 0xfd400000, 0x5ae00000, 0x3db00000,
         0xb8080000, 0x6c0c0000, 0x820e0000, 0xc3050000,
         0xe3838000, 0x514ac000, 0x38e62000, 0xaeb93000,
         0x6385b800, 0x9143d400, 0xd8ebae00, 0xfeb6db00,
         0x5b800080, 0x3d4000c0, 0xbae000e0, 0x6db00050,
         0x80080038, 0xc00c00ac, 0xe00e0062, 0x50050093
      }
   };

   // Shared by all instances; the first dimension is a radical inverse in both sources.
   private static final int[][] REFERENCE_BYTE_TABLES = createByteTables(REFERENCE_DIRECTIONS);
   private static final int[][] SSJ_BYTE_TABLES = createByteTables(SSJ_DIRECTIONS_32);

   private RandomStream stream;
   private final int[][] sobolByteTables;
   private final int[] groupSeeds;
   private final int[] coordinateSeeds;
   private final PointSetRandomization randomization;
   private long randomizationVersion;

   /**
    * Prepares the full reference algorithm using Burley's four 32-column matrices.
    * The matrices are reused for each group of four dimensions.
    *
    * @param k number of points is {@code 2^k}, with {@code 0 <= k <= 30};
    *        the upper bound follows from SSJ's signed-int point count
    * @param dim positive dimension of the point set
    * @param stream source of random 32-bit group seeds
    * @throws IllegalArgumentException if an argument is null or a size is invalid
    */
   public BurleyPointSet(int k, int dim, RandomStream stream) {
      this(k, dim, stream, false);
   }

   /**
    * Prepares Burley's full 32-bit index and coordinate scrambling with either
    * the reference directions or hardcoded SSJ Sobol directions. The chosen
    * four base matrices are reused for every group of four coordinates.
    *
    * <p>The SSJ constants use its built-in primitive polynomials and initial
    * direction numbers. Their first 30 columns match
    * {@code new SobolSequence(30, 32, 4)}. Columns 31 and 32 were computed with
    * the same Sobol recurrence, beyond SSJ's signed-int point-count limit, and
    * are hardcoded here as well. No matrices are generated at construction.
    *
    * <p>Both sources use all 32 columns and the full scrambled index, including
    * for {@code k = 0}. The SSJ choice changes the generating matrices; it does
    * not restrict the index to {@code k} bits or change Burley's scrambling.
    *
    * @param k number of points is {@code 2^k}, with {@code 0 <= k <= 30}
    * @param dim positive dimension of this point set
    * @param stream source of random 32-bit group seeds
    * @param useSsjDirections true for SSJ's built-in Sobol directions;
    *        false for Burley's reference directions
    * @throws IllegalArgumentException if stream is null or a size is invalid
    */
   public BurleyPointSet(int k, int dim, RandomStream stream, boolean useSsjDirections) {
      checkSizes(k, dim, stream);
      this.numPoints = 1 << k;
      this.dim = dim;
      this.stream = stream;
      this.sobolByteTables = useSsjDirections ? SSJ_BYTE_TABLES : REFERENCE_BYTE_TABLES;
      this.groupSeeds = new int[(dim - 1) / GROUP_SIZE + 1];
      this.coordinateSeeds = new int[dim];
      this.randomization = new BurleyRandomization();
   }

   /**
    * Tabulates the reversed matrix product for each byte of the full index.
    * The radical-inverse dimension uses its direct formula instead.
    */
   private static int[][] createByteTables(int[][] directions) {
      int[][] tables = new int[directions.length][];
      for (int j = 1; j < directions.length; j++) {
         int[] table = new int[4 * 256];
         for (int c = 0; c < directions[j].length; c++) {
            int offset = (c >>> 3) * 256;
            int bit = 1 << (c & 7);
            int column = Integer.reverse(directions[j][c]);
            for (int value = 0; value < bit; value++)
               table[offset + bit + value] = table[offset + value] ^ column;
         }
         tables[j] = table;
      }
      return tables;
   }

   private static void checkSizes(int k, int dim, RandomStream stream) {
      if (k < 0 || k > 30 || dim <= 0 || stream == null)
         throw new IllegalArgumentException("Require 0 <= k <= 30, dim > 0, and a non-null stream");
   }

   /**
    * Draws one fresh 32-bit seed for each group of four dimensions and derives
    * its coordinate seeds using Burley's original {@code hash_combine}. Reuses
    * all allocated storage and does not generate or store coordinates.
    */
   public void randomize() {
      for (int group = 0; group < groupSeeds.length; group++)
         groupSeeds[group] = (int) stream.nextBitsLong(32);
      for (int j = 0; j < dim; j++)
         coordinateSeeds[j] = BurleyUtils.hashCombine(groupSeeds[j / GROUP_SIZE], j % GROUP_SIZE);
      randomizationVersion++;
   }

   @Override
   public double getCoordinate(int i, int j) {
      if (i < 0 || i >= numPoints || j < 0 || j >= dim)
         throw new IndexOutOfBoundsException("Point or coordinate index out of range");

      // Keep all 32 bits, including the contribution from columns beyond k.
      int index = BurleyUtils.nestedUniformScramble(i, groupSeeds[j / GROUP_SIZE]);
      return scrambledCoordinate(index, j % GROUP_SIZE, coordinateSeeds[j]);
   }

   private double scrambledCoordinate(int index, int localDimension, int seed) {
      // For the radical inverse, reverse(sobol(index, 0)) is simply index.
      int reversedCoordinate = index;
      if (localDimension != 0) {
         int[] table = sobolByteTables[localDimension];
         reversedCoordinate = table[index & 255]
               ^ table[256 + ((index >>> 8) & 255)]
               ^ table[512 + ((index >>> 16) & 255)]
               ^ table[768 + (index >>> 24)];
      }
      int x = Integer.reverse(BurleyUtils.laineKarrasPermutation(reversedCoordinate, seed));
      return Integer.toUnsignedLong(x) * NORM32 + EPSILON_HALF;
   }

   /**
    * Returns an iterator that computes coordinates in groups of four, sharing
    * one index scramble per group. Each iterator caches only that index and
    * sees subsequent randomizations, including without a reset.
    *
    * @return an iterator over this point set
    */
   @Override
   public PointSetIterator iterator() {
      return new BurleyIterator();
   }

   /**
    * Rejects random shifts; use {@link #randomize()} or {@link #getRandomization()}.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void addRandomShift(int d1, int d2, RandomStream stream) {
      throw new UnsupportedOperationException("Use the associated Burley randomization");
   }

   /** @return the stream used to draw group seeds */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream for future randomizations without changing current seeds.
    *
    * @param stream source of random 32-bit group seeds
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   /**
    * Returns the RQMC adapter, whose stream accessors delegate to this point set.
    * The adapter accepts only this instance.
    *
    * @return the associated Burley randomization
    */
   public PointSetRandomization getRandomization() {
      return randomization;
   }

   @Override
   public String toString() {
      return "Burley shuffled hash scrambling ("
            + (sobolByteTables == SSJ_BYTE_TABLES ? "SSJ" : "reference")
            + ", 32 columns"
            + ", dimension = " + dim + ", points = " + numPoints + ")";
   }

   private final class BurleyIterator extends DefaultPointSetIterator {
      private int cachedPoint = -1;
      private int cachedGroup = -1;
      private int cachedIndex;
      private long cachedVersion;

      private int prepareIndex(int group) {
         if (cachedPoint != curPointIndex || cachedGroup != group
               || cachedVersion != randomizationVersion) {
            cachedIndex = BurleyUtils.nestedUniformScramble(curPointIndex, groupSeeds[group]);
            cachedPoint = curPointIndex;
            cachedGroup = group;
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
         int j = curCoordIndex;
         double coordinate = scrambledCoordinate(prepareIndex(j / GROUP_SIZE), j % GROUP_SIZE, coordinateSeeds[j]);
         curCoordIndex++;
         return coordinate;
      }

      @Override
      public void nextCoordinates(double[] p, int d) {
         if (d > dim - curCoordIndex || (d > 0 && curPointIndex >= numPoints))
            outOfBounds();
         if (d > 0 && (curPointIndex < 0 || curCoordIndex < 0))
            throw new IndexOutOfBoundsException("Point or coordinate index out of range");
         for (int copied = 0; copied < d;) {
            int index = prepareIndex(curCoordIndex / GROUP_SIZE);
            int count = Math.min(d - copied, GROUP_SIZE - curCoordIndex % GROUP_SIZE);
            for (int c = 0; c < count; c++) {
               int j = curCoordIndex;
               p[copied++] = scrambledCoordinate(index, j % GROUP_SIZE, coordinateSeeds[j]);
               curCoordIndex++;
            }
         }
      }
   }

   private final class BurleyRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != BurleyPointSet.this)
            throw new IllegalArgumentException("The Burley adapter can only randomize its associated point set");
         BurleyPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return BurleyPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         BurleyPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return BurleyPointSet.this.toString();
      }
   }
}
