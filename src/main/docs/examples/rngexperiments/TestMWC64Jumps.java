package rngexperiments;

import java.math.BigInteger;
import java.util.Arrays;

import umontreal.ssj.rng.BasicRandomStreamFactory;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.rng.RandomStreamFactory;

   /**
 * Tests the jump implementations of MWC64k2a2 and MWC64k3a2.
 *
 * The tests compare generic BigInteger jumps with repeated generation,
 * fixed substream jumps, and stream construction. They also check jump
 * composition, reset behavior, package seed reproducibility, invalid seed
 * rejection, boundary jump sizes, negative jump rejection, output sequence
 * consistency, and clone independence.
 */
public class TestMWC64Jumps {

   private static final int[] SMALL_JUMPS = {
         0, 1, 2, 3, 4, 5, 10, 63, 64, 65, 100, 1000, 10000
   };

   private static final int NEXT_OUTPUT_CHECK_COUNT = 8;

   /**
    * Test seeds for MWC64k2a2.
    *
    * Each seed has the form:
    *
    * {x2, x1, carry}
    */
   private static final long[][] SEEDS_K2 = {
         {1L, 3L, 4L},
         {12345L, 67890L, 13579L},
         {-1L, 1L, 2L},
         {Long.MIN_VALUE, Long.MAX_VALUE, 999999999999L},
         {-1L, -1L, 6459908096439L}
   };

   /**
    * Test seeds for MWC64k3a2.
    *
    * Each seed has the form:
    *
    * {x3, x2, x1, carry}
    */
   private static final long[][] SEEDS_K3 = {
         {1L, 3L, 4L, 5L},
         {12345L, 67890L, 13579L, 24680L},
         {-1L, 1L, 2L, 3L},
         {Long.MIN_VALUE, Long.MAX_VALUE, -123456789L, 999999999999L},
         {-1L, -1L, -1L, 184000000000000000L}
   };

   private static final Variant[] VARIANTS = {
         new Variant<MWC64k2a2>("MWC64k2a2", MWC64k2a2.class, 113, 62, SEEDS_K2) {
            void setPackageSeed(long[] seed) { MWC64k2a2.setPackageSeed(seed); }
            void setSeed(MWC64k2a2 stream, long[] seed) { stream.setSeed(seed); }
            long[] getState(MWC64k2a2 stream) { return stream.getState(); }
            void advanceStateByJump(MWC64k2a2 stream, BigInteger n) { stream.advanceStateByJump(n); }
            long nextRaw(MWC64k2a2 stream) { return stream.nextRaw(); }
            MWC64k2a2 copy(MWC64k2a2 stream) { return stream.clone(); }
         },

         new Variant<MWC64k3a2>("MWC64k3a2", MWC64k3a2.class, 169, 118, SEEDS_K3) {
            void setPackageSeed(long[] seed) { MWC64k3a2.setPackageSeed(seed); }
            void setSeed(MWC64k3a2 stream, long[] seed) { stream.setSeed(seed); }
            long[] getState(MWC64k3a2 stream) { return stream.getState(); }
            void advanceStateByJump(MWC64k3a2 stream, BigInteger n) { stream.advanceStateByJump(n); }
            long nextRaw(MWC64k3a2 stream) { return stream.nextRaw(); }
            MWC64k3a2 copy(MWC64k3a2 stream) { return stream.clone(); }
         }
   };

   /**
    * Runs all jump tests and prints a final summary.
    */
   public static void main(String[] args) {
      for (int i = 0; i < VARIANTS.length; i++)
         testVariant(VARIANTS[i]);

      printSummary();
   }

   private static void testVariant(Variant variant) {
      System.out.println();
      System.out.println("######################################");
      System.out.println("GENERATOR: " + variant.name);
      System.out.println("######################################");

      testJumpAgainstGeneration(variant);
      testSubstreamJump(variant);
      testStreamJump(variant);
      testZeroJumpIdentity(variant);
      testJumpComposition(variant);
      testFixedJumpComposition(variant);
      testResetBehaviorAfterJumps(variant);
      testPackageSeedReproducibility(variant);
      testInvalidSeeds(variant);
      testBoundaryJumpSizes(variant);
      testNegativeJumpRejection(variant);
      testNextOutputSequencesAfterFixedJumps(variant);
      testCloneBehavior(variant);
   }

   /**
    * Starts two streams from the same seed. One stream is advanced by calling
    * nextRaw() n times, while the other stream is advanced directly with
    * advanceStateByJump(n). Both streams should then have the same internal
    * state and should produce the same next raw value.
    */
   private static void testJumpAgainstGeneration(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 1: jump(n) vs n calls to nextRaw()");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         for (int j = 0; j < SMALL_JUMPS.length; j++) {
            int n = SMALL_JUMPS[j];
            RandomStream byGeneration = variant.newStream(seed);
            RandomStream byJump = variant.newStream(seed);
            long[] start = seed.clone();

            // Reference path: move one step at a time by actually generating values.
            for (int k = 0; k < n; k++)
               variant.rawNext(byGeneration);

            // Jump path: move directly to the state that should follow n generated values.
            variant.jump(byJump, BigInteger.valueOf(n));

            long[] expected = variant.stateOf(byGeneration);
            long[] actual = variant.stateOf(byJump);
            boolean sameState = Arrays.equals(expected, actual);

            long nextExpected = variant.rawNext(byGeneration);
            long nextActual = variant.rawNext(byJump);

            boolean sameNext = (nextExpected == nextActual);
            boolean ok = sameState && sameNext;

            printResult(
                  variant,
                  variant.name + ": jump(" + n + ") vs " + n + " nextRaw()",
                  start,
                  expected,
                  actual,
                  ok);

            System.out.println("next after expected = " + Long.toUnsignedString(nextExpected));
            System.out.println("next after actual   = " + Long.toUnsignedString(nextActual));
            System.out.println("next output match   = " + sameNext);
            System.out.println();
         }
      }
   }

   /**
    * Starts two streams from the same seed. The first stream uses the generator's
    * fixed substream operation resetNextSubstream(); the second stream uses the
    * generic BigInteger jump by the documented substream length. If the fixed
    * jump constants are correct, both paths must land on the same state. The
    * test repeats this once more to check consecutive substream jumps.
    */
   private static void testSubstreamJump(Variant variant) {
      BigInteger substreamJump = BigInteger.ONE.shiftLeft(variant.substreamAdvanceExponent);

      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 2: resetNextSubstream() vs jump(2^"
            + variant.substreamAdvanceExponent + ")");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream fixed = variant.newStream(seed);
         RandomStream generic = variant.newStream(seed);
         long[] start = seed.clone();

         // Fixed path: use the precomputed substream jump built into the generator.
         fixed.resetNextSubstream();
         // Generic path: compute and apply the same jump through advanceStateByJump().
         variant.jump(generic, substreamJump);

         long[] fixedState = variant.stateOf(fixed);
         long[] genericState = variant.stateOf(generic);

         printResult(
               variant,
               variant.name + ": first substream jump",
               start,
               genericState,
               fixedState,
               Arrays.equals(genericState, fixedState));

         fixed.resetNextSubstream();
         variant.jump(generic, substreamJump);

         long[] fixedState2 = variant.stateOf(fixed);
         long[] genericState2 = variant.stateOf(generic);

         printResult(
               variant,
               variant.name + ": second substream jump",
               fixedState,
               genericState2,
               fixedState2,
               Arrays.equals(genericState2, fixedState2));
      }
   }

   /**
    * Sets the package seed and creates several streams. stream2 should start at
    * stream1's initial state advanced by one stream jump, and stream3 should
    * start at stream1's initial state advanced by two stream jumps. We compare
    * those constructor-created states with manually jumped streams.
    */
   private static void testStreamJump(Variant variant) {
      BigInteger streamJump = BigInteger.ONE.shiftLeft(variant.streamAdvanceExponent);

      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 3: stream creation vs jump(2^"
            + variant.streamAdvanceExponent + ")");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         variant.setPackageSeed(seed.clone());

         RandomStream stream1 = variant.newInstance();
         RandomStream stream2 = variant.newInstance();
         RandomStream stream3 = variant.newInstance();

         long[] stream1State = variant.stateOf(stream1);
         long[] stream2State = variant.stateOf(stream2);
         long[] stream3State = variant.stateOf(stream3);

         boolean firstOk = Arrays.equals(seed, stream1State);

         printResult(
               variant,
               variant.name + ": first stream starts at package seed",
               seed,
               seed,
               stream1State,
               firstOk);

         RandomStream expected2 = variant.newStream(seed);
         // Manual reference: original seed advanced by one stream spacing.
         variant.jump(expected2, streamJump);

         printResult(
               variant,
               variant.name + ": second stream vs jump(2^"
               + variant.streamAdvanceExponent + ")",
               seed,
               variant.stateOf(expected2),
               stream2State,
               Arrays.equals(variant.stateOf(expected2), stream2State));

         RandomStream expected3 = variant.newStream(seed);
         // Manual reference: original seed advanced by two stream spacings.
         variant.jump(expected3, streamJump.multiply(BigInteger.valueOf(2L)));

         printResult(
               variant,
               variant.name + ": third stream vs jump(2 * 2^"
               + variant.streamAdvanceExponent + ")",
               seed,
               variant.stateOf(expected3),
               stream3State,
               Arrays.equals(variant.stateOf(expected3), stream3State));
      }
   }

   /**
    * Applies a jump of size zero to a stream and verifies that every state
    * component is unchanged. This checks the identity case of the generic jump.
    */
   private static void testZeroJumpIdentity(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 4: zero jump is identity");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream stream = variant.newStream(seed);
         long[] before = variant.stateOf(stream);

         variant.jump(stream, BigInteger.ZERO);

         long[] after = variant.stateOf(stream);
         printResult(
               variant,
               variant.name + ": jump(0) leaves state unchanged",
               seed,
               before,
               after,
               Arrays.equals(before, after));
      }
   }

   /**
    * Starts two streams from the same seed. The first stream receives two jumps,
    * jump(a) and then jump(b). The second stream receives one jump, jump(a + b).
    * Because jumps represent state advances in the same recurrence, both streams
    * should finish in the same state.
    */
   private static void testJumpComposition(Variant variant) {
      int[][] jumps = {
            {0, 0},
            {0, 1},
            {1, 0},
            {1, 2},
            {63, 64},
            {100, 1000},
            {4096, 8192}
      };

      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 5: jump(a) then jump(b) vs jump(a + b)");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         for (int j = 0; j < jumps.length; j++) {
            int a = jumps[j][0];
            int b = jumps[j][1];
            RandomStream composed = variant.newStream(seed);
            RandomStream direct = variant.newStream(seed);

            // Composed path: advance in two pieces.
            variant.jump(composed, BigInteger.valueOf(a));
            variant.jump(composed, BigInteger.valueOf(b));
            // Direct path: advance by the same total distance in one operation.
            variant.jump(direct, BigInteger.valueOf(a + b));

            printResult(
                  variant,
                  variant.name + ": jump(" + a + ") + jump(" + b
                  + ") vs jump(" + (a + b) + ")",
                  seed,
                  variant.stateOf(direct),
                  variant.stateOf(composed),
                  Arrays.equals(variant.stateOf(direct), variant.stateOf(composed)));
         }
      }
   }

   /**
    * Checks composition for the fixed jumps used by the public stream API.
    * Two calls to resetNextSubstream() should equal one generic jump by twice
    * the substream spacing. Similarly, the fourth stream created from a package
    * seed should equal the original seed advanced by three stream spacings.
    */
   private static void testFixedJumpComposition(Variant variant) {
      BigInteger substreamJump = BigInteger.ONE.shiftLeft(variant.substreamAdvanceExponent);
      BigInteger streamJump = BigInteger.ONE.shiftLeft(variant.streamAdvanceExponent);

      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 6: fixed jump composition");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         RandomStream fixedSub = variant.newStream(seed);
         RandomStream directSub = variant.newStream(seed);

         // Fixed path: two public substream jumps.
         fixedSub.resetNextSubstream();
         fixedSub.resetNextSubstream();
         // Generic path: one direct jump of the same total distance.
         variant.jump(directSub, substreamJump.multiply(BigInteger.valueOf(2L)));

         printResult(
               variant,
               variant.name + ": two resetNextSubstream() calls vs jump(2 * 2^"
               + variant.substreamAdvanceExponent + ")",
               seed,
               variant.stateOf(directSub),
               variant.stateOf(fixedSub),
               Arrays.equals(variant.stateOf(directSub), variant.stateOf(fixedSub)));

         variant.setPackageSeed(seed.clone());
         RandomStream stream1 = variant.newInstance();
         RandomStream stream4 = variant.newInstance();
         stream4 = variant.newInstance();
         stream4 = variant.newInstance();

         RandomStream expected4 = variant.newStream(seed);
         // The fourth created stream is three stream spacings after the first.
         variant.jump(expected4, streamJump.multiply(BigInteger.valueOf(3L)));

         printResult(
               variant,
               variant.name + ": fourth created stream vs jump(3 * 2^"
               + variant.streamAdvanceExponent + ")",
               variant.stateOf(stream1),
               variant.stateOf(expected4),
               variant.stateOf(stream4),
               Arrays.equals(variant.stateOf(expected4), variant.stateOf(stream4)));
      }
   }

   /**
    * Verifies that reset methods return to the correct saved states after the
    * current state has moved. resetStartStream() should return to the initial
    * stream seed. resetStartSubstream() should return to the beginning of the
    * current substream, not necessarily to the original stream seed.
    */
   private static void testResetBehaviorAfterJumps(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 7: reset behavior after jumps");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         RandomStream stream = variant.newStream(seed);
         // Move away from the initial stream state, then reset to it.
         variant.jump(stream, BigInteger.valueOf(1000L));
         stream.resetStartStream();

         printResult(
               variant,
               variant.name + ": resetStartStream() returns to the initial seed",
               seed,
               seed,
               variant.stateOf(stream),
               Arrays.equals(seed, variant.stateOf(stream)));

         RandomStream substream = variant.newStream(seed);
         substream.resetNextSubstream();
         long[] substreamStart = variant.stateOf(substream);

         // Move away from the substream start, then reset to that substream start.
         variant.jump(substream, BigInteger.valueOf(1000L));
         substream.resetStartSubstream();

         printResult(
               variant,
               variant.name + ": resetStartSubstream() returns to current substream start",
               seed,
               substreamStart,
               variant.stateOf(substream),
               Arrays.equals(substreamStart, variant.stateOf(substream)));
      }
   }

   /**
    * Checks deterministic stream creation from the package seed. We set a package
    * seed, create the first three streams, then set the same package seed again
    * and recreate the first three streams. The two sequences of stream-start
    * states should match exactly.
    */
   private static void testPackageSeedReproducibility(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 8: package seed reproducibility");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         // First pass: record the stream-start states produced from this package seed.
         variant.setPackageSeed(seed.clone());
         RandomStream first1 = variant.newInstance();
         RandomStream first2 = variant.newInstance();
         RandomStream first3 = variant.newInstance();

         long[] firstState1 = variant.stateOf(first1);
         long[] firstState2 = variant.stateOf(first2);
         long[] firstState3 = variant.stateOf(first3);

         // Replay pass: resetting the same package seed should reproduce the same states.
         variant.setPackageSeed(seed.clone());
         RandomStream replay1 = variant.newInstance();
         RandomStream replay2 = variant.newInstance();
         RandomStream replay3 = variant.newInstance();

         printResult(
               variant,
               variant.name + ": replayed first stream after setPackageSeed(), seed="
               + stateToString(seed),
               firstState1,
               firstState1,
               variant.stateOf(replay1),
               Arrays.equals(firstState1, variant.stateOf(replay1)));

         printResult(
               variant,
               variant.name + ": replayed second stream after setPackageSeed(), seed="
               + stateToString(seed),
               firstState1,
               firstState2,
               variant.stateOf(replay2),
               Arrays.equals(firstState2, variant.stateOf(replay2)));

         printResult(
               variant,
               variant.name + ": replayed third stream after setPackageSeed(), seed="
               + stateToString(seed),
               firstState1,
               firstState3,
               variant.stateOf(replay3),
               Arrays.equals(firstState3, variant.stateOf(replay3)));
      }
   }

   /**
    * Tries seeds that should be rejected by the generator's seed validation.
    * These checks confirm that invalid seed shapes or invalid carry values do
    * not silently create unusable or forbidden states.
    */
   private static void testInvalidSeeds(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 9: invalid seed rejection");
      System.out.println("======================================");

      printBooleanResult(
            variant,
            variant.name + ": null seed is rejected",
            expectException(new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newInstance();
                  variant.setSeedFromRandomStream(stream, null);
               }
            }));

      printBooleanResult(
            variant,
            variant.name + ": wrong seed length is rejected",
            expectException(new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newInstance();
                  variant.setSeedFromRandomStream(stream, new long[] {1L});
               }
            }));

      printBooleanResult(
            variant,
            variant.name + ": negative carry is rejected",
            expectException(new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newInstance();
                  long[] seed = variant.validSeed();
                  seed[seed.length - 1] = -1L;
                  variant.setSeedFromRandomStream(stream, seed);
               }
            }));

      printBooleanResult(
            variant,
            variant.name + ": too-large carry is rejected",
            expectException(new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newInstance();
                  long[] seed = variant.validSeed();
                  seed[seed.length - 1] = Long.MAX_VALUE;
                  variant.setSeedFromRandomStream(stream, seed);
               }
            }));

      printBooleanResult(
            variant,
            variant.name + ": all-zero state is rejected",
            expectException(new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newInstance();
                  variant.setSeedFromRandomStream(stream, new long[variant.seedLength()]);
               }
            }));
   }

   /**
    * Exercises very large jump sizes around the important fixed powers. For each
    * boundary value n, one stream jumps directly by n, while another stream jumps
    * by n - 1 and then by 1. The comparison checks that the generic jump works
    * correctly around substream and stream spacing boundaries.
    */
   private static void testBoundaryJumpSizes(Variant variant) {
      BigInteger substreamJump = BigInteger.ONE.shiftLeft(variant.substreamAdvanceExponent);
      BigInteger streamJump = BigInteger.ONE.shiftLeft(variant.streamAdvanceExponent);
      BigInteger[] boundaries = {
            substreamJump.subtract(BigInteger.ONE),
            substreamJump,
            substreamJump.add(BigInteger.ONE),
            streamJump.subtract(BigInteger.ONE),
            streamJump,
            streamJump.add(BigInteger.ONE)
      };

      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 10: boundary jump sizes around fixed powers");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         for (int j = 0; j < boundaries.length; j++) {
            BigInteger n = boundaries[j];
            RandomStream direct = variant.newStream(seed);
            RandomStream composed = variant.newStream(seed);

            // Direct path: jump by the exact boundary value.
            variant.jump(direct, n);
            // Composed path: arrive at the same boundary as two consecutive jumps.
            variant.jump(composed, n.subtract(BigInteger.ONE));
            variant.jump(composed, BigInteger.ONE);

            printResult(
                  variant,
                  variant.name + ": jump(" + jumpLabel(n) + ") boundary composition",
                  seed,
                  variant.stateOf(direct),
                  variant.stateOf(composed),
                  Arrays.equals(variant.stateOf(direct), variant.stateOf(composed)));
         }
      }
   }

   /**
    * Calls advanceStateByJump() with a negative distance and expects an exception.
    * A negative jump is not supported, so silently accepting it would hide a
    * caller error.
    */
   private static void testNegativeJumpRejection(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 11: negative jump rejection");
      System.out.println("======================================");

      printBooleanResult(
            variant,
            variant.name + ": jump(-1) is rejected",
            expectException(new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newStream(variant.validSeed());
                  variant.jump(stream, BigInteger.valueOf(-1L));
               }
            }));
   }

   /**
    * Starts two streams from the same seed and moves them to the same fixed-jump
    * target by different paths. One path uses the public fixed jump operation
    * (resetNextSubstream() or stream construction), and the other uses the generic
    * BigInteger jump. Once both objects should be in the same state, they must
    * produce the same sequence of nextRaw() values, not just the same state array.
    */
   private static void testNextOutputSequencesAfterFixedJumps(Variant variant) {
      BigInteger substreamJump = BigInteger.ONE.shiftLeft(variant.substreamAdvanceExponent);
      BigInteger streamJump = BigInteger.ONE.shiftLeft(variant.streamAdvanceExponent);

      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 12: next output sequences after fixed jumps");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         RandomStream fixedSub = variant.newStream(seed);
         RandomStream genericSub = variant.newStream(seed);
         // Both objects start from seed. Move one with the fixed substream jump
         // and the other with the equivalent generic jump.
         fixedSub.resetNextSubstream();
         variant.jump(genericSub, substreamJump);

         printBooleanResult(
               variant,
               variant.name + ": next " + NEXT_OUTPUT_CHECK_COUNT
               + " outputs match after substream jump, seed=" + stateToString(seed),
               rawSequencesEqual(variant, fixedSub, genericSub, NEXT_OUTPUT_CHECK_COUNT));

         variant.setPackageSeed(seed.clone());
         variant.newInstance();
         RandomStream fixedStream = variant.newInstance();
         RandomStream genericStream = variant.newStream(seed);
         // fixedStream is the second created stream, so it should be one stream
         // spacing after seed. genericStream reaches that same state by jump().
         variant.jump(genericStream, streamJump);

         printBooleanResult(
               variant,
               variant.name + ": next " + NEXT_OUTPUT_CHECK_COUNT
               + " outputs match after stream jump, seed=" + stateToString(seed),
               rawSequencesEqual(variant, fixedStream, genericStream, NEXT_OUTPUT_CHECK_COUNT));
      }
   }

   /**
    * Moves a stream away from its seed, clones it, and checks two things. First,
    * the clone should start with exactly the same current state and next output.
    * Second, advancing the original after cloning should not also advance the
    * clone, proving that the mutable state arrays were copied independently.
    */
   private static void testCloneBehavior(Variant variant) {
      System.out.println();
      System.out.println("======================================");
      System.out.println("TEST 13: clone behavior");
      System.out.println("======================================");

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream original = variant.newStream(seed);
         // Clone a non-initial state so the test covers the current state, not just construction.
         variant.jump(original, BigInteger.valueOf(17L));

         RandomStream copy = variant.copyOf(original);

         printResult(
               variant,
               variant.name + ": clone starts with the same state",
               seed,
               variant.stateOf(original),
               variant.stateOf(copy),
               Arrays.equals(variant.stateOf(original), variant.stateOf(copy)));

         long originalNext = variant.rawNext(original);
         long copyNext = variant.rawNext(copy);
         boolean sameNext = (originalNext == copyNext);

         // Advance only the original; the clone should keep its own state.
         variant.rawNext(original);
         boolean independent = !Arrays.equals(variant.stateOf(original), variant.stateOf(copy));

         printBooleanResult(
               variant,
               variant.name + ": clone has matching next output and independent later state",
               sameNext && independent);
      }
   }

   /**
    * Prints one state comparison and updates the GOOD/BAD counters. The expected
    * state is the reference path; the actual state is the operation being tested.
    *
    * @param variant generator variant being tested
    * @param label description of the test
    * @param start starting state
    * @param expected reference state
    * @param actual tested state
    * @param ok true if expected and actual match
    */
   private static void printResult(Variant variant, String label, long[] start, long[] expected,
                                   long[] actual, boolean ok) {
      System.out.println("--------------------------------------");
      System.out.println(label);
      System.out.println("start    = " + stateToString(start));
      System.out.println("expected = " + stateToString(expected));
      System.out.println("actual   = " + stateToString(actual));
      System.out.println("result   = " + (ok ? "GOOD" : "BAD"));

      if (ok)
         variant.good++;
      else
         variant.bad++;
   }

   /**
    * Prints a boolean-only result and updates the GOOD/BAD counters.
    *
    * @param variant generator variant being tested
    * @param label description of the test
    * @param ok true if the check passed
    */
   private static void printBooleanResult(Variant variant, String label, boolean ok) {
      System.out.println("--------------------------------------");
      System.out.println(label);
      System.out.println("result   = " + (ok ? "GOOD" : "BAD"));

      if (ok)
         variant.good++;
      else
         variant.bad++;
   }

   /**
    * Compares two streams output by output. This is used after two different
    * jump paths have already landed on what should be the same state.
    */
   private static boolean rawSequencesEqual(Variant variant, RandomStream a, RandomStream b, int count) {
      for (int i = 0; i < count; i++) {
         if (variant.rawNext(a) != variant.rawNext(b))
            return false;
      }

      return true;
   }

   /**
    * Runs code that should fail. Returning true means the expected exception was
    * thrown; returning false means the invalid operation was incorrectly accepted.
    */
   private static boolean expectException(ThrowingRunnable runnable) {
      try {
         runnable.run();
         return false;
      } catch (RuntimeException e) {
         return true;
      }
   }

   /**
    * Formats large boundary jumps such as 2^62 - 1, 2^62, and 2^62 + 1 so the
    * printed test labels are readable.
    */
   private static String jumpLabel(BigInteger n) {
      int bitLength = n.bitLength();

      if (n.signum() > 0 && n.bitCount() == 1)
         return "2^" + (bitLength - 1);

      BigInteger lowerPower = BigInteger.ONE.shiftLeft(bitLength - 1);
      if (n.equals(lowerPower.subtract(BigInteger.ONE)))
         return "2^" + (bitLength - 1) + " - 1";
      if (n.equals(lowerPower.add(BigInteger.ONE)))
         return "2^" + (bitLength - 1) + " + 1";

      return n.toString();
   }

   private static void printSummary() {
      int totalGood = 0;
      int totalBad = 0;

      System.out.println();
      System.out.println("======================================");
      System.out.println("SUMMARY");

      for (int i = 0; i < VARIANTS.length; i++) {
         Variant variant = VARIANTS[i];
         totalGood += variant.good;
         totalBad += variant.bad;
         System.out.println(variant.name + ": GOOD tests = " + variant.good
               + ", BAD tests = " + variant.bad);
      }

      System.out.println("TOTAL: GOOD tests = " + totalGood + ", BAD tests = " + totalBad);
      System.out.println("======================================");
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

   private interface ThrowingRunnable {
      void run();
   }

   /**
    * Holds the generator-specific operations needed by the generic tests.
    * RandomStreamFactory creates new streams, while the abstract methods expose
    * operations that are not part of the RandomStream interface, such as
    * setSeed(), getState(), advanceStateByJump(), nextRaw(), and clone().
    */
   private static abstract class Variant<T extends RandomStream> {
      final String name;
      final int streamAdvanceExponent;
      final int substreamAdvanceExponent;
      final long[][] seeds;
      int good = 0;
      int bad = 0;

      private final Class<T> streamClass;
      private final RandomStreamFactory factory;

      Variant(String name, Class<T> streamClass, int streamAdvanceExponent,
              int substreamAdvanceExponent, long[][] seeds) {
         this.name = name;
         this.streamClass = streamClass;
         this.streamAdvanceExponent = streamAdvanceExponent;
         this.substreamAdvanceExponent = substreamAdvanceExponent;
         this.seeds = seeds;
         this.factory = new BasicRandomStreamFactory(streamClass);
      }

      RandomStream newInstance() {
         return streamClass.cast(factory.newInstance());
      }

      RandomStream newStream(long[] seed) {
         T stream = streamClass.cast(factory.newInstance());
         setSeed(stream, seed.clone());
         return stream;
      }

      abstract void setPackageSeed(long[] seed);
      abstract void setSeed(T stream, long[] seed);
      abstract long[] getState(T stream);
      abstract void advanceStateByJump(T stream, BigInteger n);
      abstract long nextRaw(T stream);
      abstract T copy(T stream);

      int seedLength() {
         return seeds[0].length;
      }

      long[] validSeed() {
         return seeds[0].clone();
      }

      void setSeedFromRandomStream(RandomStream stream, long[] seed) {
         setSeed(streamClass.cast(stream), seed);
      }

      void jump(RandomStream stream, BigInteger n) {
         advanceStateByJump(streamClass.cast(stream), n);
      }

      long[] stateOf(RandomStream stream) {
         return getState(streamClass.cast(stream));
      }

      long rawNext(RandomStream stream) {
         return nextRaw(streamClass.cast(stream));
      }

      RandomStream copyOf(RandomStream stream) {
         return copy(streamClass.cast(stream));
      }
   }
}
