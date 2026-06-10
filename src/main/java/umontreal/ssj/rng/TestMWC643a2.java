package umontreal.ssj.rng;

import java.util.Arrays;
import java.math.BigInteger;
/*
 * This class test the jumps for mwc
 */

public class TestMWC643a2 {
   public static void main(String[] args) {
      long[][] seeds = {
         { 12345L, 12345L, 12345L, 12345L },
         { 1L, 3L, 4L, 5L },
         { 0L, 1L, 2L, 1L },
         { -1L, 12345L, 777L, 9L }
      };

      long[] jumps = {
         0L, 1L, 2L, 3L, 10L, 100L, 1000L, 10000L, 1L << 20
      };

      for (long[] seed : seeds) {
         for (long n : jumps) {
            MWC64k3a2 repeated = new MWC64k3a2();
            MWC64k3a2 jumped = new MWC64k3a2();

            repeated.setSeed(seed);
            jumped.setSeed(seed);

            for (long i = 0; i < n; i++)
               repeated.nextRaw();

            jumped.advanceStateByJump(BigInteger.valueOf(n));

            long[] stateRepeated = repeated.getState();
            long[] stateJumped = jumped.getState();

            if (!Arrays.equals(stateRepeated, stateJumped)) {
               System.out.println("FAILED");
               System.out.println("seed     = " + unsigned(seed));
               System.out.println("n        = " + n);
               System.out.println("repeated = " + unsigned(stateRepeated));
               System.out.println("jumped   = " + unsigned(stateJumped));
               throw new AssertionError("Jump mismatch.");
            }

            System.out.println("OK seed = " + unsigned(seed) + ", n = " + n);
         }
      }

      System.out.println("All MWC64k3a2 jump tests passed.");
   }

   private static String unsigned(long[] state) {
      StringBuilder sb = new StringBuilder();

      sb.append("{ ");
      for (int i = 0; i < state.length; i++) {
         if (i > 0)
            sb.append(", ");

         sb.append(Long.toUnsignedString(state[i]));
      }
      sb.append(" }");

      return sb.toString();
   }
}