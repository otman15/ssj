/*
 * Java adaptation of QuantLib 1.43's Burley2020SobolRsg, SobolRsg (Jaeckel),
 * and MersenneTwisterUniformRng.
 *
 * Copyright (C) 2023 Peter Caspers
 * Copyright (C) 2003, 2004 Ferdinando Ametrano
 * Copyright (C) 2006 Richard Gould
 * Copyright (C) 2007 Mark Joshi
 * Copyright (C) 2013 Cheng Li
 * Copyright (C) 2010 Kakhkhor Abdijalilov
 * Copyright (C) 1997 - 2002 Makoto Matsumoto and Takuji Nishimura
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the names of the copyright holders nor the names of the QuantLib
 * Group and its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package umontreal.ssj.hups64;

import java.util.Arrays;

import umontreal.ssj.rng.RandomStream;

/**
 * Ports QuantLib 1.43's {@code Burley2020SobolRsg}, using its default Jaeckel
 * direction integers, to SSJ's {@link PointSet} interface. For the same nonzero
 * Sobol and scramble seeds, point {@code i} matches QuantLib's
 * {@code skipTo(i)} in both its integer coordinates and double conversion.
 *
 * <p>QuantLib scrambles the index once using the first group seed, then uses
 * that index in every dimension. Every dimension has its own 32-column Sobol
 * matrix. Groups of four share only a seed used to derive coordinate seeds,
 * through QuantLib's chained 64-bit hash. This differs from the four-dimensional
 * padding of {@link BurleyPointSet}. The Sobol matrix product uses the scrambled
 * index plus one, with unsigned 32-bit wraparound, and no Gray coding.
 *
 * <p>The first 32 dimensions use QuantLib's tabulated Jaeckel initializers.
 * Higher dimensions use the same MT19937-based initialization as QuantLib,
 * controlled by the fixed Sobol seed. Primitive polynomials are enumerated in
 * the ascending order of QuantLib's table instead of embedding its 21200 entries.
 * All 32 columns are constructed regardless of the requested point count.
 *
 * <p>Call {@link #randomize()} or {@link #setScrambleSeed(long)} before accessing
 * coordinates. Randomization selects a nonzero 32-bit seed from the supplied
 * {@link RandomStream}; MT19937 expands that seed into the group seeds. As in
 * QuantLib, an explicit seed of zero requests automatic seeding. Here automatic
 * seeds come from the supplied SSJ stream, rather than QuantLib's clock-based
 * seed generator. {@link #getScrambleSeed()} exposes the selected seed for
 * reproducibility. For a nonzero Java {@code long}, its low 32 bits initialize
 * MT19937, matching a 64-bit C++ {@code unsigned long}, including when those
 * low bits are zero.
 *
 * <p>Conversion follows QuantLib exactly: {@code (unsigned(z) + 0.5) * 2^-32}.
 * Values are strictly between zero and one; no additional SSJ epsilon is added.
 * Coordinates are computed on demand. Each iterator computes the index once per
 * point and keeps only that point, with storage independent of the point count.
 * Use {@code new RQMCPointSet(points, points.getRandomization())} for experiments.
 *
 * <p>The supported dimension is 1 through 21200, as in a standard QuantLib 1.43
 * build. SSJ's signed-int point count restricts this point set to {@code 2^k}
 * points with {@code 0 <= k <= 30}. Only the Jaeckel direction-integer option
 * is implemented.
 */
public final class BurleyQuantLibPointSet extends PointSet {
   private static final int MAX_DIMENSION = 21200;
   private static final double NORM32 = 0x1.0p-32;

   // QuantLib sobolrsg.cpp's initializers[31], without the zero terminators.
   private static final int[][] JAECKEL_INITIALIZERS = {
      { 1 },
      { 1, 1 },
      { 1, 3, 7 },
      { 1, 1, 5 },
      { 1, 3, 1, 1 },
      { 1, 1, 3, 7 },
      { 1, 3, 3, 9, 9 },
      { 1, 3, 7, 7, 21 },
      { 1, 1, 5, 11, 27 },
      { 1, 1, 7, 3, 29 },
      { 1, 3, 7, 13, 3 },
      { 1, 3, 5, 1, 15 },
      { 1, 1, 1, 9, 23, 37 },
      { 1, 1, 3, 13, 11, 7 },
      { 1, 3, 3, 5, 19, 33 },
      { 1, 1, 7, 13, 25, 5 },
      { 1, 1, 1, 3, 13, 39 },
      { 1, 3, 5, 11, 7, 11 },
      { 1, 3, 1, 7, 3, 23, 79 },
      { 1, 3, 1, 15, 17, 63, 13 },
      { 1, 3, 3, 3, 25, 17, 115 },
      { 1, 3, 7, 9, 31, 29, 17 },
      { 1, 1, 3, 15, 29, 15, 41 },
      { 1, 3, 1, 9, 5, 21, 119 },
      { 1, 1, 5, 5, 1, 27, 33 },
      { 1, 1, 3, 1, 23, 13, 75 },
      { 1, 1, 7, 7, 19, 25, 105 },
      { 1, 3, 5, 5, 21, 9, 7 },
      { 1, 1, 1, 15, 5, 49, 59 },
      { 1, 3, 5, 15, 17, 19, 21 },
      { 1, 1, 7, 11, 13, 29, 3 }
   };

   private RandomStream stream;
   private final long sobolSeed;
   // Column-major, as in QuantLib: directions[c][j].
   private final int[][] directions;
   private final int[] groupSeeds;
   private final int[] coordinateSeeds;
   private final MersenneTwister mt = new MersenneTwister();
   private final PointSetRandomization randomization = new QuantLibRandomization();
   private long scrambleSeed;
   private long randomizationVersion;
   private boolean randomized;

   /**
    * Constructs the point set with QuantLib's default Sobol seed of 42.
    * Construction consumes no stream values and does not randomize coordinates.
    *
    * @param k number of points is {@code 2^k}, with {@code 0 <= k <= 30}
    * @param dim dimension, from 1 to 21200
    * @param stream source of seeds for future randomizations
    * @throws IllegalArgumentException if a size is unsupported or stream is null
    */
   public BurleyQuantLibPointSet(int k, int dim, RandomStream stream) {
      this(k, dim, 42L, stream);
   }

   /**
    * Constructs the point set with an explicit Sobol seed. This seed determines
    * the generating matrices above dimension 32 and remains fixed across
    * randomizations. A zero Sobol seed selects a nonzero seed from the stream
    * only when those higher dimensions are needed. Other construction consumes
    * no stream values and does not randomize coordinates.
    *
    * @param k number of points is {@code 2^k}, with {@code 0 <= k <= 30}
    * @param dim dimension, from 1 to 21200
    * @param sobolSeed QuantLib Sobol seed, with zero requesting automatic seeding
    * @param stream source of automatic and randomization seeds
    * @throws IllegalArgumentException if a size is unsupported or stream is null
    */
   public BurleyQuantLibPointSet(int k, int dim, long sobolSeed, RandomStream stream) {
      if (k < 0 || k > 30 || dim < 1 || dim > MAX_DIMENSION || stream == null)
         throw new IllegalArgumentException("Require 0 <= k <= 30, 1 <= dim <= 21200, and a non-null stream");
      this.numPoints = 1 << k;
      this.dim = dim;
      this.stream = stream;
      this.sobolSeed = sobolSeed == 0L && dim > 32 ? nextNonzeroSeed() : sobolSeed;
      this.directions = new int[32][dim];
      this.groupSeeds = new int[(dim - 1) / 4 + 1];
      this.coordinateSeeds = new int[dim];
      initializeDirections();
   }

   private long nextNonzeroSeed() {
      long seed;
      do {
         seed = stream.nextBitsLong(32);
      } while (seed == 0L);
      return seed;
   }

   private void initializeDirections() {
      for (int c = 0; c < 32; c++)
         directions[c][0] = 1 << (31 - c);
      if (dim > 32)
         mt.seed(sobolSeed);

      int j = 1;
      for (int degree = 1; j < dim; degree++) {
         int order = (1 << degree) - 1;
         int[] factors = primeFactors(order);
         for (int middle = 0; middle < (1 << (degree - 1)) && j < dim; middle++) {
            int polynomial = (1 << degree) | (middle << 1) | 1;
            if (!isPrimitive(polynomial, degree, order, factors))
               continue;
            for (int c = 0; c < degree; c++) {
               int initial;
               if (j <= JAECKEL_INITIALIZERS.length) {
                  initial = JAECKEL_INITIALIZERS[j - 1][c];
               } else {
                  // floor(((uint32 + 0.5) / 2^32) * 2^(c+1)), as in
                  // QuantLib, is exactly this extraction of the leading bits.
                  do {
                     initial = mt.nextInt() >>> (31 - c);
                  } while ((initial & 1) == 0);
               }
               directions[c][j] = initial << (31 - c);
            }
            for (int c = degree; c < 32; c++) {
               int value = directions[c - degree][j] >>> degree;
               for (int t = 1; t < degree; t++)
                  if ((polynomial & (1 << (degree - t))) != 0)
                     value ^= directions[c - t][j];
               directions[c][j] = value ^ directions[c - degree][j];
            }
            j++;
         }
      }
   }

   // A degree-d polynomial is primitive precisely when x modulo that polynomial
   // has order 2^d - 1. Testing its prime divisors gives QuantLib's table order
   // without a large table of constants. These operations run only at construction.
   private static boolean isPrimitive(int polynomial, int degree, int order, int[] factors) {
      if (powerOfX(order, polynomial, degree) != 1)
         return false;
      for (int factor : factors)
         if (powerOfX(order / factor, polynomial, degree) == 1)
            return false;
      return true;
   }

   private static int[] primeFactors(int value) {
      int[] factors = new int[18];
      int count = 0;
      for (int p = 2; p * p <= value; p++) {
         if (value % p == 0) {
            factors[count++] = p;
            do {
               value /= p;
            } while (value % p == 0);
         }
      }
      if (value > 1)
         factors[count++] = value;
      return Arrays.copyOf(factors, count);
   }

   private static int powerOfX(int exponent, int polynomial, int degree) {
      int result = 1;
      int base = degree == 1 ? 1 : 2;
      while (exponent != 0) {
         if ((exponent & 1) != 0)
            result = multiplyModulo(result, base, polynomial, degree);
         base = multiplyModulo(base, base, polynomial, degree);
         exponent >>>= 1;
      }
      return result;
   }

   private static int multiplyModulo(int x, int y, int polynomial, int degree) {
      int result = 0;
      while (y != 0) {
         if ((y & 1) != 0)
            result ^= x;
         y >>>= 1;
         x <<= 1;
         if ((x & (1 << degree)) != 0)
            x ^= polynomial;
      }
      return result;
   }

   private static long hashMix(long x) {
      x ^= x >>> 32;
      x *= 0xe9846af9b1a615dL;
      x ^= x >>> 32;
      x *= 0xe9846af9b1a615dL;
      return x ^ (x >>> 28);
   }

   private static long hash(long value) {
      return (value & 0xffffffffL) + hashMix(value >>> 32);
   }

   private static long hashCombine(long seed, long value) {
      return hashMix(seed + 0x9e3779b9L + hash(value));
   }

   /**
    * Draws a fresh nonzero scramble seed from the current stream and expands it
    * using QuantLib's MT19937 and 64-bit coordinate-seed hash. Reuses all storage
    * and leaves the Sobol generating matrices fixed.
    */
   public void randomize() {
      setScrambleSeed(0L);
   }

   /**
    * Selects a scrambling with the same seed as a QuantLib C++ instance. A
    * nonzero argument consumes no stream values; zero selects a nonzero 32-bit
    * seed from the current stream, retrying if that stream returns zero.
    *
    * @param seed QuantLib scramble seed; zero requests automatic seeding
    */
   public void setScrambleSeed(long seed) {
      long selectedSeed = seed == 0L ? nextNonzeroSeed() : seed;
      mt.seed(selectedSeed);
      for (int group = 0; group < groupSeeds.length; group++) {
         groupSeeds[group] = mt.nextInt();
         long chainedSeed = Integer.toUnsignedLong(groupSeeds[group]);
         int first = group * 4;
         int count = Math.min(4, dim - first);
         for (int local = 0; local < count; local++) {
            chainedSeed = hashCombine(chainedSeed, local);
            coordinateSeeds[first + local] = (int) chainedSeed;
         }
      }
      scrambleSeed = selectedSeed;
      randomized = true;
      randomizationVersion++;
   }

   /** @return the selected scramble seed, or zero before the first randomization */
   public long getScrambleSeed() {
      return scrambleSeed;
   }

   /** @return the fixed Sobol seed, including an automatically selected seed if used */
   public long getSobolSeed() {
      return sobolSeed;
   }

   private void requireRandomized() {
      if (!randomized)
         throw new IllegalStateException("Call randomize() or setScrambleSeed() before accessing coordinates");
   }

   private int sobolIndex(int i) {
      // SobolRsg::skipTo(skip), with useGrayCode=false, uses N = skip + 1.
      return BurleyUtils.nestedUniformScramble(i, groupSeeds[0]) + 1;
   }

   private static double toDouble(int value) {
      return (Integer.toUnsignedLong(value) + 0.5) * NORM32;
   }

   @Override
   public double getCoordinate(int i, int j) {
      if (i < 0 || i >= numPoints || j < 0 || j >= dim)
         throw new IndexOutOfBoundsException("Point or coordinate index out of range");
      requireRandomized();
      int index = sobolIndex(i);
      if (j == 0)
         return toDouble(Integer.reverse(BurleyUtils.laineKarrasPermutation(index, coordinateSeeds[0])));
      int value = 0;
      for (int c = 0; index != 0; c++, index >>>= 1)
         value ^= directions[c][j] & -(index & 1);
      return toDouble(BurleyUtils.nestedUniformScramble(value, coordinateSeeds[j]));
   }

   /** @return an iterator retaining only its current point */
   @Override
   public PointSetIterator iterator() {
      return new QuantLibIterator();
   }

   /** @return the source of automatic and randomization seeds */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Sets the stream for future seed selections without changing coordinates.
    *
    * @param stream source of random seeds
    * @throws IllegalArgumentException if stream is null
    */
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   /** @return the randomization adapter bound to this instance */
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
      throw new UnsupportedOperationException("Use the associated QuantLib Burley randomization");
   }

   @Override
   public String toString() {
      return "QuantLib Burley scrambling (Jaeckel, 32 output bits, dimension = "
            + dim + ", points = " + numPoints + ")";
   }

   private final class QuantLibIterator extends DefaultPointSetIterator {
      private final int[] bits = new int[dim];
      private final double[] point = new double[dim];
      private int bufferedPoint = -1;
      private long bufferedVersion;

      private void preparePoint() {
         requireRandomized();
         if (bufferedPoint == curPointIndex && bufferedVersion == randomizationVersion)
            return;
         Arrays.fill(bits, 0);
         int index = sobolIndex(curPointIndex);
         point[0] = toDouble(Integer.reverse(
               BurleyUtils.laineKarrasPermutation(index, coordinateSeeds[0])));
         if (dim > 1) {
            // QuantLib's skipTo multiplies the same index into all dimensions.
            for (int c = 0; index != 0; c++, index >>>= 1) {
               if ((index & 1) != 0) {
                  int[] column = directions[c];
                  for (int j = 1; j < dim; j++)
                     bits[j] ^= column[j];
               }
            }
            for (int j = 1; j < dim; j++)
               point[j] = toDouble(BurleyUtils.nestedUniformScramble(bits[j], coordinateSeeds[j]));
         }
         bufferedPoint = curPointIndex;
         bufferedVersion = randomizationVersion;
      }

      @Override
      public double nextCoordinate() {
         if (curPointIndex >= numPoints || curCoordIndex >= dim)
            outOfBounds();
         if (curPointIndex < 0 || curCoordIndex < 0)
            throw new IndexOutOfBoundsException("Point or coordinate index out of range");
         preparePoint();
         return point[curCoordIndex++];
      }

      @Override
      public void nextCoordinates(double[] p, int d) {
         if (d > dim - curCoordIndex || (d > 0 && curPointIndex >= numPoints))
            outOfBounds();
         if (d > 0) {
            if (curPointIndex < 0 || curCoordIndex < 0)
               throw new IndexOutOfBoundsException("Point or coordinate index out of range");
            preparePoint();
            System.arraycopy(point, curCoordIndex, p, 0, d);
            curCoordIndex += d;
         }
      }
   }

   private final class QuantLibRandomization implements PointSetRandomization {
      @Override
      public void randomize(PointSet p) {
         if (p != BurleyQuantLibPointSet.this)
            throw new IllegalArgumentException("The QuantLib Burley adapter only accepts its associated point set");
         BurleyQuantLibPointSet.this.randomize();
      }

      @Override
      public RandomStream getStream() {
         return BurleyQuantLibPointSet.this.getStream();
      }

      @Override
      public void setStream(RandomStream stream) {
         BurleyQuantLibPointSet.this.setStream(stream);
      }

      @Override
      public String toString() {
         return BurleyQuantLibPointSet.this.toString();
      }
   }

   /** QuantLib's 2002-initialization MT19937; int overflow implements uint32_t. */
   private static final class MersenneTwister {
      private final int[] state = new int[624];
      private int position;

      void seed(long seed) {
         state[0] = (int) seed;
         for (int i = 1; i < state.length; i++)
            state[i] = 1812433253 * (state[i - 1] ^ (state[i - 1] >>> 30)) + i;
         position = state.length;
      }

      int nextInt() {
         if (position == state.length)
            twist();
         int value = state[position++];
         value ^= value >>> 11;
         value ^= (value << 7) & 0x9d2c5680;
         value ^= (value << 15) & 0xefc60000;
         return value ^ (value >>> 18);
      }

      private void twist() {
         int i = 0;
         for (; i < 624 - 397; i++) {
            int value = (state[i] & 0x80000000) | (state[i + 1] & 0x7fffffff);
            state[i] = state[i + 397] ^ (value >>> 1) ^ ((value & 1) * 0x9908b0df);
         }
         for (; i < 623; i++) {
            int value = (state[i] & 0x80000000) | (state[i + 1] & 0x7fffffff);
            state[i] = state[i - (624 - 397)] ^ (value >>> 1) ^ ((value & 1) * 0x9908b0df);
         }
         int value = (state[623] & 0x80000000) | (state[0] & 0x7fffffff);
         state[623] = state[396] ^ (value >>> 1) ^ ((value & 1) * 0x9908b0df);
         position = 0;
      }
   }
}
