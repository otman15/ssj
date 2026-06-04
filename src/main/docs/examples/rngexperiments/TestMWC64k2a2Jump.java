package rngexperiments;

import java.math.BigInteger;
import java.util.Arrays;

import umontreal.ssj.rng.MWC64k2a2;

/**
 * Tests the jump implementation of MWC64k2a2.
 *
 * The test checks three things:
 *
 * 1. A generic jump by n steps gives the same state as calling nextRaw() n times.
 * 2. resetNextSubstream() gives the same state as a generic jump by 2^62.
 * 3. Creating new streams gives the same states as generic jumps by 2^113.
 *
 *testSubstreamJump() and testStreamJump() assume that MWC64k2a2 has:
 *
 * public void advanceStateByJump(BigInteger n) // it has to be changed in the generator to run the test
 *
 * This is needed because the fixed jump sizes 2^62 and 2^113 are too large
 * to fit in a Java long.
 */
public class TestMWC64k2a2Jump {

   /** Stream spacing for MWC64k2a2: 2^113 generated values. */
   private static final int STREAM_ADVANCE_EXPONENT = 113;

   /** Substream spacing for MWC64k2a2: 2^62 generated values. */
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 62;

   /** BigInteger value of the stream jump, equal to 2^62. */
   private static final BigInteger STREAM_JUMP =
         BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT);

   /** BigInteger value of the substream jump, equal to 2^113. */
   private static final BigInteger SUBSTREAM_JUMP =
         BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT);

   /**
    * Test seeds.
    *
    * Each seed has the form:
    *
    * {x2, x1, carry}
    */
   private static final long[][] SEEDS = {
         {1L, 3L, 4L},
         {12345L, 67890L, 13579L},
         {-1L, 1L, 2L},
         {Long.MIN_VALUE, Long.MAX_VALUE, 190000000000000000L},
         {-1L, -1L, 6459908096439L}
   };

   /**
    * Small jump sizes.
    *
    * These are small enough to compare against repeated calls to nextRaw().
    */
   private static final int[] SMALL_JUMPS = {
         0, 1, 2, 3, 4, 5, 10, 63, 64, 65, 100, 1000, 10000
   };

   /** Number of successful comparisons. */
   private static int good = 0;

   /** Number of failed comparisons. */
   private static int bad = 0;

   /**
    * Runs all jump tests and prints a final summary.
    */
   public static void main(String[] args) {
      testJumpAgainstGeneration();
      testSubstreamJump();
      testStreamJump();

      System.out.println();
      System.out.println("======================================");
      System.out.println("SUMMARY");
      System.out.println("GOOD tests = " + good);
      System.out.println("BAD tests  = " + bad);
      System.out.println("======================================");
   }

   /**
    * Tests that advanceStateByJump(n) gives the same state as generating n values.
    *
    * This test is only done for small n, because repeated generation is not possible
    * for large fixed jumps like 2^62 or 2^113.
    */
   private static void testJumpAgainstGeneration() {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 1: jump(n) vs n calls to nextRaw()");
      System.out.println("======================================");

      for (long[] seed : SEEDS) {
         for (int n : SMALL_JUMPS) {
            MWC64k2a2 byGeneration = new MWC64k2a2();
            byGeneration.setSeed(seed.clone());

            MWC64k2a2 byJump = new MWC64k2a2();
            byJump.setSeed(seed.clone());

            long[] start = seed.clone();

            // Reference path: advance the state by generating n raw values.
            for (int i = 0; i < n; i++)
               byGeneration.nextRaw();

            // Jump path: advance the state directly by n steps.
            byJump.advanceStateByJump(BigInteger.valueOf(n));

            long[] expected = byGeneration.getState();
            long[] actual = byJump.getState();

            boolean sameState = Arrays.equals(expected, actual);

            // Extra check: after both states match, the next output should match too.
            long nextExpected = byGeneration.nextRaw();
            long nextActual = byJump.nextRaw();

            boolean sameNext = (nextExpected == nextActual);
            boolean ok = sameState && sameNext;

            printResult(
                  "jump(" + n + ") vs " + n + " nextRaw()",
                  start,
                  expected,
                  actual,
                  ok
            );

            System.out.println("next after expected = " + Long.toUnsignedString(nextExpected));
            System.out.println("next after actual   = " + Long.toUnsignedString(nextActual));
            System.out.println("next output match   = " + sameNext);
            System.out.println();
         }
      }
   }

   /**
    * Tests that resetNextSubstream() gives the same result as a generic jump by 2^62.
    *
    * This checks that the hardcoded/precomputed substream jump constants are correct.
    */
   private static void testSubstreamJump() {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 2: resetNextSubstream() vs jump(2^" + SUBSTREAM_ADVANCE_EXPONENT + ")");
      System.out.println("======================================");

      for (long[] seed : SEEDS) {
         MWC64k2a2 fixed = new MWC64k2a2();
         fixed.setSeed(seed.clone());

         MWC64k2a2 generic = new MWC64k2a2();
         generic.setSeed(seed.clone());

         long[] start = seed.clone();

         // Fixed path: use the generator's precomputed substream jump.
         fixed.resetNextSubstream();

         // Reference path: use the generic BigInteger jump by 2^62.
         generic.advanceStateByJump(SUBSTREAM_JUMP);

         long[] fixedState = fixed.getState();
         long[] genericState = generic.getState();

         printResult(
               "first substream jump",
               start,
               genericState,
               fixedState,
               Arrays.equals(genericState, fixedState)
         );

         // Repeat once to check that consecutive substream jumps also match.
         fixed.resetNextSubstream();
         generic.advanceStateByJump(SUBSTREAM_JUMP);

         long[] fixedState2 = fixed.getState();
         long[] genericState2 = generic.getState();

         printResult(
               "second substream jump",
               fixedState,
               genericState2,
               fixedState2,
               Arrays.equals(genericState2, fixedState2)
         );
      }
   }

   /**
    * Tests that stream creation advances the package seed by 2^113 each time.
    *
    * This checks that the fixed stream jump used in the constructor is correct.
    */
   private static void testStreamJump() {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 3: stream creation vs jump(2^" + STREAM_ADVANCE_EXPONENT + ")");
      System.out.println("======================================");

      for (long[] seed : SEEDS) {
         // Set the package seed so the next created stream starts from this seed.
         MWC64k2a2.setPackageSeed(seed.clone());

         // The constructor should create streams separated by the stream jump.
         MWC64k2a2 stream1 = new MWC64k2a2();
         MWC64k2a2 stream2 = new MWC64k2a2();
         MWC64k2a2 stream3 = new MWC64k2a2();

         long[] stream1State = stream1.getState();
         long[] stream2State = stream2.getState();
         long[] stream3State = stream3.getState();

         boolean firstOk = Arrays.equals(seed, stream1State);

         printResult(
               "first stream starts at package seed",
               seed,
               seed,
               stream1State,
               firstOk
         );

         MWC64k2a2 expected2 = new MWC64k2a2();
         expected2.setSeed(seed.clone());

         // Expected second stream = initial seed advanced by one stream jump.
         expected2.advanceStateByJump(STREAM_JUMP);

         printResult(
               "second stream vs jump(2^" + STREAM_ADVANCE_EXPONENT + ")",
               seed,
               expected2.getState(),
               stream2State,
               Arrays.equals(expected2.getState(), stream2State)
         );

         MWC64k2a2 expected3 = new MWC64k2a2();
         expected3.setSeed(seed.clone());

         // Expected third stream = initial seed advanced by two stream jumps.
         expected3.advanceStateByJump(STREAM_JUMP.multiply(BigInteger.valueOf(2L)));

         printResult(
               "third stream vs jump(2 * 2^" + STREAM_ADVANCE_EXPONENT + ")",
               seed,
               expected3.getState(),
               stream3State,
               Arrays.equals(expected3.getState(), stream3State)
         );
      }
   }

   /**
    * Prints one comparison result and updates the GOOD/BAD counters.
    *
    * @param label description of the test
    * @param start starting state
    * @param expected reference state
    * @param actual tested state
    * @param ok true if expected and actual match
    */
   private static void printResult(String label, long[] start, long[] expected, long[] actual, boolean ok) {
      System.out.println("--------------------------------------");
      System.out.println(label);
      System.out.println("start    = " + stateToString(start));
      System.out.println("expected = " + stateToString(expected));
      System.out.println("actual   = " + stateToString(actual));
      System.out.println("result   = " + (ok ? "GOOD" : "BAD"));

      if (ok)
         good++;
      else
         bad++;
   }

   /**
    * Converts a state array to a readable unsigned string.
    *
    * @param state state array
    * @return string representation of the state
    */
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
