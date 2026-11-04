package umontreal.ssj.hups64;

/**
 * Utility methods for Burley's 32-bit hash-based Owen scrambling.
 */
public final class BurleyUtils {
   private BurleyUtils() {}

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

   /**
    * Applies Burley's 32-bit nested uniform scramble.
    *
    * @param x the input integer
    * @param seed the scramble seed
    * @return the scrambled integer
    */
   public static int nestedUniformScramble(int x, int seed) {
      x = Integer.reverse(x);
      x = laineKarrasPermutation(x, seed);
      x = Integer.reverse(x);
      return x;
   }

}