package rngexperiments;

import java.math.BigInteger;
import java.util.Arrays;
import umontreal.ssj.rng.MWC64k3a2;

public class TestMWC64JumpsAssert {

   private static final int STREAM_ADVANCE_EXPONENT = 169;
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 118;

   private static final BigInteger STREAM_JUMP =
         BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT);

   private static final BigInteger SUBSTREAM_JUMP =
         BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT);

   private static final long[][] SEEDS = {
         {1L, 3L, 4L, 5L},
         {12345L, 67890L, 13579L, 24680L},
         {-1L, 1L, 2L, 3L},
         {Long.MIN_VALUE, Long.MAX_VALUE, -123456789L, 999999999999L},
         {-1L, -1L, -1L, 184000000000000000L}
   };

   private static final int[] SMALL_JUMPS = {
         0, 1, 2, 3, 4, 5, 10, 63, 64, 65, 100, 1000, 10000
   };

   public static void main(String[] args) {
//      testJumpAgainstGeneration();
      testSubstreamJump();
//      testStreamJump();

      System.out.println("All MWC64k3a2 jump tests passed.");
   }

   private static void testJumpAgainstGeneration() {
      for (long[] seed : SEEDS) {
         for (int n : SMALL_JUMPS) {
            MWC64k3a2 byGeneration = newStream(seed);
            MWC64k3a2 byJump = newStream(seed);

            for (int i = 0; i < n; i++)
               byGeneration.nextRaw();

            byJump.advanceStateByJump(BigInteger.valueOf(n));

            assertStateEquals(
                  "jump(" + n + ") vs " + n + " calls to nextRaw(), seed=" + stateToString(seed),
                  byGeneration.getState(),
                  byJump.getState()
            );

            long a = byGeneration.nextRaw();
            long b = byJump.nextRaw();

            if (a != b) {
               throw new AssertionError(
                     "Next output differs after jump(" + n + "), seed=" + stateToString(seed)
                     + "\nexpected: " + Long.toUnsignedString(a)
                     + "\nactual:   " + Long.toUnsignedString(b)
               );
            }
         }
      }

      System.out.println("jump(n) vs repeated generation: OK");
   }

   private static void testSubstreamJump() {
      for (long[] seed : SEEDS) {
         MWC64k3a2 fixed = newStream(seed);
         MWC64k3a2 generic = newStream(seed);

         fixed.resetNextSubstream();
         generic.advanceStateByJump(SUBSTREAM_JUMP);

         assertStateEquals(
               "resetNextSubstream() vs jump(2^" + SUBSTREAM_ADVANCE_EXPONENT + "), seed=" + stateToString(seed),
               generic.getState(),
               fixed.getState()
         );

         fixed.resetNextSubstream();
         generic.advanceStateByJump(SUBSTREAM_JUMP);

         assertStateEquals(
               "second resetNextSubstream() vs second jump(2^" + SUBSTREAM_ADVANCE_EXPONENT + "), seed=" + stateToString(seed),
               generic.getState(),
               fixed.getState()
         );
      }

      System.out.println("fixed substream jump: OK");
   }

   private static void testStreamJump() {
      for (long[] seed : SEEDS) {
         MWC64k3a2.setPackageSeed(seed.clone());

         MWC64k3a2 stream1 = new MWC64k3a2();
         MWC64k3a2 stream2 = new MWC64k3a2();
         MWC64k3a2 stream3 = new MWC64k3a2();

         assertStateEquals(
               "first stream should start at package seed",
               seed,
               stream1.getState()
         );

         MWC64k3a2 expected2 = newStream(seed);
         expected2.advanceStateByJump(STREAM_JUMP);

         assertStateEquals(
               "second stream vs jump(2^" + STREAM_ADVANCE_EXPONENT + "), seed=" + stateToString(seed),
               expected2.getState(),
               stream2.getState()
         );

         MWC64k3a2 expected3 = newStream(seed);
         expected3.advanceStateByJump(STREAM_JUMP.multiply(BigInteger.valueOf(2L)));

         assertStateEquals(
               "third stream vs jump(2 * 2^" + STREAM_ADVANCE_EXPONENT + "), seed=" + stateToString(seed),
               expected3.getState(),
               stream3.getState()
         );
      }

      System.out.println("fixed stream jump: OK");
   }

   private static MWC64k3a2 newStream(long[] seed) {
      MWC64k3a2 stream = new MWC64k3a2();
      stream.setSeed(seed.clone());
      return stream;
   }

   private static void assertStateEquals(String label, long[] expected, long[] actual) {
      if (!Arrays.equals(expected, actual)) {
         throw new AssertionError(
               label
               + "\nexpected: " + stateToString(expected)
               + "\nactual:   " + stateToString(actual)
         );
      }
   }

   private static String stateToString(long[] state) {
      StringBuilder sb = new StringBuilder("{ ");

      for (int i = 0; i < state.length; i++) {
         if (i > 0)
            sb.append(", ");
         sb.append(Long.toUnsignedString(state[i]));
      }

      sb.append(" }");
      return sb.toString();
   }
}