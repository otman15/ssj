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
 * Direct reference implementation of Burley's shuffled, scrambled Sobol points.
 * Follows Listings 1--3 of Brent Burley's
 * <a href="https://jcgt.org/published/0009/04/01/paper.pdf">Practical Hash-based
 * Owen Scrambling</a> (2020), using the first four direction matrices from its
 * supplemental {@code sobol.cpp} and the functions in {@code sobol.h}.
 *
 * <p>Each coordinate is computed by scrambling the point index, XORing the
 * direction numbers selected by its 32 bits, then scrambling the Sobol value
 * with {@code hash_combine(groupSeed, localDimension)}. All 32 index bits and
 * matrix columns are retained regardless of the number of points. Groups of
 * four dimensions reuse the same matrices with a separate seed per group.
 * Ordinary binary indices are used, without a Gray-code transformation.
 *
 * <p>This implementation evaluates the 32-column loop for every coordinate,
 * including the radical-inverse dimension, and performs the bit reversals
 * explicitly. It uses the default {@link PointSet} iterator. It has no lookup
 * tables or coordinate cache and is independent of {@link BurleyUtils} and
 * {@link BurleyPointSet}, so it can check their optimized arithmetic.
 *
 * <p>Construction consumes no random numbers. Call {@link #randomize()} before
 * accessing coordinates. Each randomization draws one 32-bit seed per group
 * from {@link RandomStream#nextBitsLong(int)} and reuses the allocated seed
 * array. Coordinate access consumes no randomness and is deterministic until
 * the next randomization.
 *
 * <p>As in {@link BurleyPointSet}, the unsigned integer result {@code z} is
 * converted to {@code unsigned(z) * 2^-32 + 2^-55}. This SSJ conversion gives
 * doubles strictly between zero and one; the article's core returns integers.
 * For RQMC experiments, use {@link #getRandomization()}, for example
 * {@code new RQMCPointSet(points, points.getRandomization())}.
 */
public final class BurleyReferencePointSet extends PointSet {
   private static final int GROUP_SIZE = 4;
   private static final double NORM32 = 0x1.0p-32;
   private static final double EPSILON_HALF = 0x1.0p-55;

   // First four matrices of directions[5][32] in Burley's supplemental sobol.cpp.
   private static final int[][] DIRECTIONS = {
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

   private RandomStream stream;
   private final int[] groupSeeds;
   private final PointSetRandomization randomization;

   /**
    * Prepares the reference algorithm with Burley's four 32-column matrices.
    *
    * @param k number of points is {@code 2^k}, with {@code 0 <= k <= 30}
    * @param dim positive dimension of this point set
    * @param stream source of random 32-bit group seeds
    * @throws IllegalArgumentException if stream is null or a size is invalid
    */
   public BurleyReferencePointSet(int k, int dim, RandomStream stream) {
      if (k < 0 || k > 30 || dim <= 0 || stream == null)
         throw new IllegalArgumentException("Require 0 <= k <= 30, dim > 0, and a non-null stream");
      this.numPoints = 1 << k;
      this.dim = dim;
      this.stream = stream;
      this.groupSeeds = new int[(dim - 1) / GROUP_SIZE + 1];
      this.randomization = new ReferenceRandomization();
   }

   /**
    * Draws a fresh 32-bit seed for each group of four coordinates. Reuses the
    * seed array and does not generate or store coordinates.
    */
   public void randomize() {
      for (int group = 0; group < groupSeeds.length; group++)
         groupSeeds[group] = (int) stream.nextBitsLong(32);
   }

   @Override
   public double getCoordinate(int i, int j) {
      if (i < 0 || i >= numPoints || j < 0 || j >= dim)
         throw new IndexOutOfBoundsException("Point or coordinate index out of range");

      int seed = groupSeeds[j / GROUP_SIZE];
      int localDimension = j % GROUP_SIZE;
      int index = nestedUniformScramble(i, seed);
      int x = sobol(index, localDimension);
      x = nestedUniformScramble(x, hashCombine(seed, localDimension));
      return Integer.toUnsignedLong(x) * NORM32 + EPSILON_HALF;
   }

   // Direct translations of the supplemental sobol.cpp and sobol.h functions.
   // Java int arithmetic wraps modulo 2^32; >>> implements unsigned right shift.
   private static int sobol(int index, int dimension) {
      int x = 0;
      for (int bit = 0; bit < 32; bit++) {
         int mask = (index >>> bit) & 1;
         x ^= mask * DIRECTIONS[dimension][bit];
      }
      return x;
   }

   private static int hashCombine(int seed, int value) {
      return seed ^ (value + (seed << 6) + (seed >>> 2));
   }

   private static int reverseBits(int x) {
      x = ((x & 0xaaaaaaaa) >>> 1) | ((x & 0x55555555) << 1);
      x = ((x & 0xcccccccc) >>> 2) | ((x & 0x33333333) << 2);
      x = ((x & 0xf0f0f0f0) >>> 4) | ((x & 0x0f0f0f0f) << 4);
      x = ((x & 0xff00ff00) >>> 8) | ((x & 0x00ff00ff) << 8);
      return (x >>> 16) | (x << 16);
   }

   private static int laineKarrasPermutation(int x, int seed) {
      x += seed;
      x ^= x * 0x6c50b47c;
      x ^= x * 0xb82f1e52;
      x ^= x * 0xc7afe638;
      x ^= x * 0x8d22f6e6;
      return x;
   }

   private static int nestedUniformScramble(int x, int seed) {
      x = reverseBits(x);
      x = laineKarrasPermutation(x, seed);
      x = reverseBits(x);
      return x;
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
    * Returns the RQMC adapter bound to this point set. Its stream accessors
    * delegate to this instance.
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
      throw new UnsupportedOperationException("Use the associated Burley reference randomization");
   }

   @Override
   public String toString() {
      return "Burley reference scrambling (32 columns, 32 output bits, dimension = "
            + dim + ", points = " + numPoints + ")";
   }

   private final class ReferenceRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != BurleyReferencePointSet.this)
            throw new IllegalArgumentException("The Burley reference adapter can only randomize its associated point set");
         BurleyReferencePointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return BurleyReferencePointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         BurleyReferencePointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return BurleyReferencePointSet.this.toString();
      }
   }
}
