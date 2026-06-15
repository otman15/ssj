package rqmcexperiments;

import java.util.Locale;

// Simple Java port of the C++ sobol_owen generator from Burley 2020.
public class SobolOwen {
   // Same direction numbers as directions[5][32] in sobol.cpp.
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
      },
      {
         0x80000000, 0x40000000, 0x20000000, 0xb0000000,
         0xf8000000, 0xdc000000, 0x7a000000, 0x9d000000,
         0x5a800000, 0x2fc00000, 0xa1600000, 0xf0b00000,
         0xda880000, 0x6fc40000, 0x81620000, 0x40bb0000,
         0x22878000, 0xb3c9c000, 0xfb65a000, 0xddb2d000,
         0x78022800, 0x9c0b3c00, 0x5a0fb600, 0x2d0ddb00,
         0xa2878080, 0xf3c9c040, 0xdb65a020, 0x6db2d0b0,
         0x800228f8, 0x400b3cdc, 0x200fb67a, 0xb00ddb9d
      }
   };
// The C++ code uses uint32_t, which is an unsigned 32-bit integer.
// Java's int is signed, but we can still match the bitwise behavior by treating it as unsigned when needed.
   /**
    * Hash the user seed to produce a more uniformly distributed internal seed.
    * @param x
    * @return the hashed seed
   */
   static int hash(int x) {
      // Same MurmurHash3 finalizer as genpoints.cpp.
      x ^= x >>> 16;
      x *= 0x85ebca6b;
      x ^= x >>> 13;
      x *= 0xc2b2ae35;
      x ^= x >>> 16;
      return x;
   }
/**
 * Derive a dimension-dependent seed.
 * @param seed
 * @param v 
 * @return the combined seed for the given dimension
 */
   static int hashCombine(int seed, int v) {
      // C++ uses uint32_t, so the right shift must be unsigned in Java.
      return seed ^ (v + (seed << 6) + (seed >>> 2));
   }
/**
 * Reverse the bits of an integer, matching the behavior of reverse_bits in sobol.h.
 * @param x
 * @return the integer with its bits reversed
 */
   static int reverseBits(int x) {
      // Integer.reverse is the direct Java equivalent of reverse_bits.
      return Integer.reverse(x);
   }
/**
 * Apply the Laine-Karras permutation to an integer with a given seed.
 * @param x
 * @param seed
 * @return
 */
   static int laineKarrasPermutation(int x, int seed) {
      // Java int overflow wraps modulo 2^32, matching uint32_t arithmetic.
      x += seed;
      x ^= x * 0x6c50b47c;
      x ^= x * 0xb82f1e52;
      x ^= x * 0xc7afe638;
      x ^= x * 0x8d22f6e6;
      return x;
   }
/**
 * Apply the nested uniform scramble to an integer with a given seed.
 * @param x
 * @param seed
 * @return
 */
   static int nestedUniformScrambleBase2(int x, int seed) {
      // Same pipeline as nested_uniform_scramble_base2 in sobol.h.
      x = reverseBits(x);
      x = laineKarrasPermutation(x, seed);
      x = reverseBits(x);
      return x;
   }
/**
 * Compute the Sobol sequence value for a given index and dimension.
 * @param index
 * @param dim
 * @return
 */
   static int sobol(int index, int dim) {
      // Match the C++ guard: unsupported dimensions return zero.
      if (dim > 4)
         return 0;

      int x = 0;
      for (int bit = 0; bit < 32; bit++) {
         // C++ shifts an unsigned index; Java needs >>> for the same bits.
         int mask = (index >>> bit) & 1;
         x ^= mask * DIRECTIONS[dim][bit];
      }
      return x;
   }

   static int sobol_rds(int i, int dim, int dimSeed) {
      // Same branch as genpoints.cpp after seed = hash_combine(seed, hash(dim)).
      return sobol(i, dim) ^ dimSeed;
   }
/**
 * Compute the Owen-scrambled Sobol sequence value for a given index, dimension, and seed.
 * @param i
 * @param dim
 * @param hashedSeed
 * @return
 */
   static int sobol_owen(int i, int dim, int hashedSeed) {
      // This is the exact sobol_owen pipeline from genpoints.cpp.
      int index = nestedUniformScrambleBase2(i, hashedSeed);
      return nestedUniformScrambleBase2(sobol(index, dim), hashCombine(hashedSeed, dim));
   }

   static int sobolOwenScrambleOnly(int i, int dim, int hashedSeed) {
      return nestedUniformScrambleBase2(
         sobol(i, dim),
         hashCombine(hashedSeed, dim)
      );
   }

   static int laine_karras(int i, int dim, int dimSeed) {
      // Same branch as genpoints.cpp after seed = hash_combine(seed, hash(dim)).
      return laineKarrasPermutation(reverseBits(i), dimSeed);
   }

   static void help() {
      // Same argument order and defaults as main.cpp.
      //System.err.println("Usage: java rqmcexperiments.SobolOwen [seq] [N=16] [dim=0] [seed=1]");
      System.err.println("seq is one of:");
      System.err.println("   sobol");
      System.err.println("   sobol_rds");
      System.err.println("   sobol_owen");
      System.err.println("   sobol_owen_scramble_only");
      System.err.println("   laine_karras");
   }

   public static void main(String[] args) {
      // Edit these values manually, then press Run in VS Code or Eclipse.
      String seq = "sobol_owen";//sobol sobol_owen sobol_rds sobol_owen_scramble_only laine_karras
      int n = 4;
      int dim = 0;
      int seed = 1;

      // Match main.cpp: negative n becomes 0, and invalid dim becomes 0.
      if (n < 0)
         n = 0;
      if (dim < 0 || dim > 4)
         dim = 0;

      // Match genpoints.cpp: hash the user seed exactly once before generation.
      int hashedSeed = hash(seed);

      System.out.println("method = " + seq);

      if (seq.equals("sobol")) {
         for (int i = 0; i < n; i++) {
            int value = sobol(i, dim);
            // Print the raw 32-bit value, then convert it directly to [0, 1).
            System.out.printf(Locale.ROOT, "%08x    %.17g%n",
                              value, Integer.toUnsignedLong(value) * 0x1.0p-32);
         }
      }
      else if (seq.equals("sobol_rds")) {
         int dimSeed = hashCombine(hashedSeed, hash(dim));
         for (int i = 0; i < n; i++) {
            int value = sobol_rds(i, dim, dimSeed);
            // Print the raw 32-bit value, then convert it directly to [0, 1).
            System.out.printf(Locale.ROOT, "%08x    %.17g%n",
                              value, Integer.toUnsignedLong(value) * 0x1.0p-32);
         }
      }
      else if (seq.equals("sobol_owen")) {
         for (int i = 0; i < n; i++) {
            int value = sobol_owen(i, dim, hashedSeed);
            // Print the raw 32-bit value, then convert it directly to [0, 1).
            System.out.printf(Locale.ROOT, "%08x    %.17g%n",
                              value, Integer.toUnsignedLong(value) * 0x1.0p-32);
         }
      }
      else if (seq.equals("sobol_owen_scramble_only")) {
         for (int i = 0; i < n; i++) {
            int value = sobolOwenScrambleOnly(i, dim, hashedSeed);
            // Print the raw 32-bit value, then convert it directly to [0, 1).
            System.out.printf(Locale.ROOT, "%08x    %.17g%n",
                              value, Integer.toUnsignedLong(value) * 0x1.0p-32);
         }
      }
      else if (seq.equals("laine_karras")) {
         int dimSeed = hashCombine(hashedSeed, hash(dim));
         for (int i = 0; i < n; i++) {
            int value = laine_karras(i, dim, dimSeed);
            // Print the raw 32-bit value, then convert it directly to [0, 1).
            System.out.printf(Locale.ROOT, "%08x    %.17g%n",
                              value, Integer.toUnsignedLong(value) * 0x1.0p-32);
         }
      }
      else {
         System.err.println("unknown sequence: " + seq);
         return;
      }
   }
}
