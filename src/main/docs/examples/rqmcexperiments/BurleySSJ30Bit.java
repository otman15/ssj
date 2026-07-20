package rqmcexperiments;

import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.hups64.BurleyUtils;

// SSJ-based 30-bit adaptation of Burley's hash-based Sobol-Owen method.
// It keeps the same shuffle -> Sobol -> scramble pipeline, but uses SSJ Sobol
// generator matrix columns instead of Burley's hardcoded 32-bit direction table.
// This is therefore not expected to match Burley's C++ code bit-for-bit.
public class BurleySSJ30Bit {
   private static final int NUM_BITS = 30;
   private static final int MASK = (1 << NUM_BITS) - 1;
   private static final double SCALE = 0x1.0p-30;

   private final int s;
   private final long[] directions;

   public BurleySSJ30Bit(int s) {
      assert (s > 0);

      this.s = s;
      // SSJ ties the number of Sobol columns to k, so this builds 30 columns.
      SobolSequence sobol = new SobolSequence(NUM_BITS, NUM_BITS, s);
      this.directions = sobol.getGeneratorMatricesColumns();
   }

   private static int reverseBits30(int x) {
      // Integer.reverse reverses all 32 bits. We then discard the two bits that
      // are outside this 30-bit adaptation, keeping the reversed 30-bit word.
      return Integer.reverse(x) >>> (32 - NUM_BITS);
   }

   static int nestedUniformScrambleBase2(int x, int seed) {
      // Same reverse -> Laine-Karras -> reverse idea as Burley, restricted to 30 bits.
      x &= MASK;
      x = reverseBits30(x);
      x = BurleyUtils.laineKarrasPermutation(x, seed) & MASK;
      x = reverseBits30(x);
      return x;
   }

   int sobol(int index, int dim) {
      if (dim < 0 || dim >= s)
         return 0;

      int x = 0;
      index &= MASK;
      // Use SSJ's generator matrix columns as Sobol direction numbers.
      for (int bit = 0; bit < NUM_BITS; bit++) {
         int mask = (index >>> bit) & 1;
         x ^= mask * (int) directions[dim * NUM_BITS + bit];
      }
      return x & MASK;
   }

   int sobol_owen(int i, int dim, int hashedSeed) {
      // Burley pipeline: shuffle the index, compute Sobol, then scramble the coordinate.
      int index = nestedUniformScrambleBase2(i, hashedSeed);
      return nestedUniformScrambleBase2(sobol(index, dim),
            BurleyUtils.hashCombine(hashedSeed, dim));
   }

   static double toDouble(int value) {
      // Convert the unsigned 30-bit integer to [0, 1).
      return (value & MASK) * SCALE;
   }
}
