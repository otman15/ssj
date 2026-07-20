package rqmcexperiments;

import umontreal.ssj.hups64.PointSet;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.rng.RandomStream;

/**
 * Burley shuffled-scrambled Sobol point set padded by groups of four
 * dimensions, using SSJ's Sobol direction numbers.
 *
 * <p>The first four SSJ Sobol generator matrices are reused for every group
 * of four coordinates. Each group receives a different seed. Burley's
 * permutation is applied to the 30 leading bits, while any remaining SSJ
 * output bits are preserved.
 */
public class BurleySSJPadded {
   private static final int NUM_BITS = 30;
   private static final int MASK = (1 << NUM_BITS) - 1;
   private static final int GROUP_SIZE = 4;

   private final int outputDigits;
   private final int tailBits;
   private final long tailMask;
   private final double normFactor;
   private final long[] directions;

   /**
    * Constructs the generator used by the padded point set.
    *
    * @param outputDigits number of output bits, between 30 and 62
    */
   public BurleySSJPadded(int outputDigits) {
      assert (outputDigits >= NUM_BITS && outputDigits <= 62);

      this.outputDigits = outputDigits;
      this.tailBits = outputDigits - NUM_BITS;
      this.tailMask = tailBits == 0 ? 0L : (1L << tailBits) - 1L;
      this.normFactor = Math.scalb(1.0, -outputDigits);
      SobolSequence sobol = new SobolSequence(NUM_BITS, outputDigits, GROUP_SIZE);
      this.directions = sobol.getGeneratorMatricesColumns();
   }

   private static int reverseBits30(int x) {
      return Integer.reverse(x) >>> (32 - NUM_BITS);
   }

   private static int nestedUniformScramble30(int x, int seed) {
      x &= MASK;
      x = reverseBits30(x);
      x = laineKarrasPermutation(x, seed) & MASK;
      return reverseBits30(x);
   }

   private long sobolBits(int index, int localDim) {
      assert (localDim >= 0 && localDim < GROUP_SIZE);

      long x = 0L;
      int offset = localDim * NUM_BITS;
      while (index != 0) {
         int bit = Integer.numberOfTrailingZeros(index);
         x ^= directions[offset + bit];
         index &= index - 1;
      }
      return x;
   }

   private long value(int index, int localDim, int groupSeed,
                      int coordinateSeed) {
      int shuffledIndex = nestedUniformScramble30(index, groupSeed);
      long bits = sobolBits(shuffledIndex, localDim);
      long tail = bits & tailMask;
      int prefix = (int) (bits >>> tailBits);
      long scrambledPrefix = nestedUniformScramble30(prefix, coordinateSeed);
      return (scrambledPrefix << tailBits) | tail;
   }

   /**
    * Point set whose coordinates are generated on demand.
    */
   public static class PaddedPointSet extends PointSet {
      private final int n;
      private final int s;
      private final BurleySSJPadded generator;
      private final int[] groupSeeds;
      private final int[] coordinateSeeds;
      private int hashedSeed;

      /**
       * Constructs a padded point set.
       *
       * @param k number of points is 2^k, with 0 <= k <= 30
       * @param s dimension of the point set
       * @param outputDigits number of output bits, between 30 and 62
       * @param seed initial randomization seed
       */
      public PaddedPointSet(int k, int s, int outputDigits, int seed) {
         assert (k >= 0 && k <= NUM_BITS);
         assert (s > 0);
         assert (outputDigits >= NUM_BITS && outputDigits <= 62);

         this.n = 1 << k;
         this.s = s;
         this.generator = new BurleySSJPadded(outputDigits);
         this.groupSeeds = new int[(s + GROUP_SIZE - 1) / GROUP_SIZE];
         this.coordinateSeeds = new int[s];
         this.numPoints = n;
         this.dim = s;
         setSeed(seed);
      }

      public void setSeed(int seed) {
         this.hashedSeed = hash(seed);
         for (int group = 0; group < groupSeeds.length; group++)
            groupSeeds[group] = group == 0
                  ? hashedSeed
                  : hash(hashCombine(hashedSeed, group));

         for (int coordinate = 0; coordinate < s; coordinate++) {
            int group = coordinate / GROUP_SIZE;
            int localDim = coordinate % GROUP_SIZE;
            coordinateSeeds[coordinate] =
                  hashCombine(groupSeeds[group], localDim);
         }
      }

      @Override
      public double getCoordinate(int i, int j) {
         assert (i >= 0 && i < n);
         assert (j >= 0 && j < s);

         int group = j / GROUP_SIZE;
         int localDim = j % GROUP_SIZE;
         long bits = generator.value(i, localDim, groupSeeds[group],
                                     coordinateSeeds[j]);
         return bits * generator.normFactor;
      }
   }

   /**
    * RQMC randomization using one seed for each replication.
    */
   public static class Randomization implements PointSetRandomization {
      private RandomStream stream;

      public Randomization(RandomStream stream) {
         this.stream = stream;
      }

      @Override
      public void randomize(PointSet p) {
         if (!(p instanceof PaddedPointSet))
            throw new IllegalArgumentException(
                  "BurleySSJPadded.Randomization requires a PaddedPointSet");

         PaddedPointSet pointSet = (PaddedPointSet) p;
         pointSet.setSeed((int) stream.nextBitsLong(32));
      }

      @Override
      public void setStream(RandomStream stream) {
         this.stream = stream;
      }

      @Override
      public RandomStream getStream() {
         return stream;
      }
   }

   /**
    * MurmurHash3 finalizer used by Burley's code.
    *
    * @param x the input integer
    * @return the hashed integer
    */
   public static int hash(int x) {
      x ^= x >>> 16;
      x *= 0x85ebca6b;
      x ^= x >>> 13;
      x *= 0xc2b2ae35;
      x ^= x >>> 16;
      return x;
   }

   public static int hashCombine(int seed, int v) {
      return seed ^ (v + (seed << 6) + (seed >>> 2));
   }

      /**
    * Applies the Laine-Karras 32-bit permutation used by Burley.
    *
    * @param x the input integer
    * @param seed the permutation seed
    * @return the permuted integer
    */
   public static int laineKarrasPermutation(int x, int seed) {
      x += seed;
      x ^= x * 0x6c50b47c;
      x ^= x * 0xb82f1e52;
      x ^= x * 0xc7afe638;
      x ^= x * 0x8d22f6e6;
      return x;
   }
}
