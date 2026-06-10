package rngexperiments;

import java.math.BigInteger;
import java.util.Arrays;

import umontreal.ssj.rng.BasicRandomStreamFactory;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.rng.RandomStreamFactory;

/**
 * Assertion-based version of TestMWC64Jumps.
 *
 * The tests compare generic BigInteger jumps with repeated generation,
 * fixed substream jumps, and stream construction. They also check jump
 * composition, reset behavior, package seed reproducibility, invalid seed
 * rejection, boundary jump sizes, negative jump rejection, output sequence
 * consistency, and clone independence.
 *
 * This class stays quiet unless an assertion fails, then prints a compact
 * summary of the number of successful assertion checks.
 */
public class TestJumpsAssert {

   private static final int[] SMALL_JUMPS = {
         0, 1, 2, 3, 4, 5, 10, 63, 64, 65, 100, 1000, 10000
   };

   private static final int NEXT_OUTPUT_CHECK_COUNT = 8;

   /**
    * Test seeds for MWC64k2a2. Each seed has the form {x2, x1, carry}.
    */
   private static final long[][] SEEDS_K2 = {
         {1L, 3L, 4L},
         {12345L, 67890L, 13579L},
         {-1L, 1L, 2L},
         {Long.MIN_VALUE, Long.MAX_VALUE, 999999999999L},
         {-1L, -1L, 184000000000000000L}
   };

   /**
    * Test seeds for MWC64k3a2. Each seed has the form {x3, x2, x1, carry}.
    */
   private static final long[][] SEEDS_K3 = {
         {1L, 3L, 4L, 5L},
         {12345L, 67890L, 13579L, 24680L},
         {-1L, 1L, 2L, 3L},
         {Long.MIN_VALUE, Long.MAX_VALUE, 999999999999L, 777777777777L},
         {-1L, -1L, 184000000000000000L, 5L}
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
    * Runs all assertion checks and prints a final summary if none fail.
    */
   public static void main(String[] args) {
      for (int i = 0; i < VARIANTS.length; i++)
         testVariant(VARIANTS[i]);

      printSummary();
      System.out.println("All jump assertion tests passed.");
   }

   private static void testVariant(Variant variant) {
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
      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];

         for (int j = 0; j < SMALL_JUMPS.length; j++) {
            int n = SMALL_JUMPS[j];
            RandomStream byGeneration = variant.newStream(seed);
            RandomStream byJump = variant.newStream(seed);

            // Reference path: move one step at a time by actually generating values.
            for (int k = 0; k < n; k++)
               variant.rawNext(byGeneration);

            // Jump path: move directly to the state that should follow n generated values.
            variant.jump(byJump, BigInteger.valueOf(n));

            long[] expected = variant.stateOf(byGeneration);
            long[] actual = variant.stateOf(byJump);
            long nextExpected = variant.rawNext(byGeneration);
            long nextActual = variant.rawNext(byJump);

            assertTrue(
                  variant,
                  variant.name + ": jump(" + n + ") vs " + n
                  + " calls to nextRaw(), seed=" + stateToString(seed),
                  Arrays.equals(expected, actual) && nextExpected == nextActual,
                  expected,
                  actual,
                  nextExpected,
                  nextActual);
         }
      }
   }

   /**
    * Starts two streams from the same seed. One stream uses resetNextSubstream(),
    * which relies on the generator's fixed jump constants. The other stream uses
    * the generic BigInteger jump by the same substream length. Both paths should
    * land on the same state, including after a second consecutive substream jump.
    */
   private static void testSubstreamJump(Variant variant) {
      BigInteger substreamJump = BigInteger.ONE.shiftLeft(variant.substreamAdvanceExponent);

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream fixed = variant.newStream(seed);
         RandomStream generic = variant.newStream(seed);

         // Fixed path: use the precomputed substream jump built into the generator.
         fixed.resetNextSubstream();
         // Generic path: compute and apply the same jump through advanceStateByJump().
         variant.jump(generic, substreamJump);

         assertStateEquals(
               variant,
               variant.name + ": resetNextSubstream() vs jump(2^"
               + variant.substreamAdvanceExponent + "), seed=" + stateToString(seed),
               variant.stateOf(generic),
               variant.stateOf(fixed));

         fixed.resetNextSubstream();
         variant.jump(generic, substreamJump);

         assertStateEquals(
               variant,
               variant.name + ": second resetNextSubstream() vs second jump(2^"
               + variant.substreamAdvanceExponent + "), seed=" + stateToString(seed),
               variant.stateOf(generic),
               variant.stateOf(fixed));
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

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         variant.setPackageSeed(seed.clone());

         RandomStream stream1 = variant.newInstance();
         RandomStream stream2 = variant.newInstance();
         RandomStream stream3 = variant.newInstance();

         assertStateEquals(
               variant,
               variant.name + ": first stream should start at package seed",
               seed,
               variant.stateOf(stream1));

         RandomStream expected2 = variant.newStream(seed);
         // Manual reference: original seed advanced by one stream spacing.
         variant.jump(expected2, streamJump);

         assertStateEquals(
               variant,
               variant.name + ": second stream vs jump(2^"
               + variant.streamAdvanceExponent + "), seed=" + stateToString(seed),
               variant.stateOf(expected2),
               variant.stateOf(stream2));

         RandomStream expected3 = variant.newStream(seed);
         // Manual reference: original seed advanced by two stream spacings.
         variant.jump(expected3, streamJump.multiply(BigInteger.valueOf(2L)));

         assertStateEquals(
               variant,
               variant.name + ": third stream vs jump(2 * 2^"
               + variant.streamAdvanceExponent + "), seed=" + stateToString(seed),
               variant.stateOf(expected3),
               variant.stateOf(stream3));
      }
   }

   /**
    * Applies a jump of size zero to a stream and verifies that every state
    * component is unchanged. This checks the identity case of the generic jump.
    */
   private static void testZeroJumpIdentity(Variant variant) {
      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream stream = variant.newStream(seed);
         long[] before = variant.stateOf(stream);

         variant.jump(stream, BigInteger.ZERO);

         assertStateEquals(
               variant,
               variant.name + ": jump(0) leaves state unchanged, seed=" + stateToString(seed),
               before,
               variant.stateOf(stream));
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

            assertStateEquals(
                  variant,
                  variant.name + ": jump(" + a + ") + jump(" + b
                  + ") vs jump(" + (a + b) + "), seed=" + stateToString(seed),
                  variant.stateOf(direct),
                  variant.stateOf(composed));
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

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream fixedSub = variant.newStream(seed);
         RandomStream directSub = variant.newStream(seed);

         // Fixed path: two public substream jumps.
         fixedSub.resetNextSubstream();
         fixedSub.resetNextSubstream();
         // Generic path: one direct jump of the same total distance.
         variant.jump(directSub, substreamJump.multiply(BigInteger.valueOf(2L)));

         assertStateEquals(
               variant,
               variant.name + ": two resetNextSubstream() calls vs jump(2 * 2^"
               + variant.substreamAdvanceExponent + "), seed=" + stateToString(seed),
               variant.stateOf(directSub),
               variant.stateOf(fixedSub));

         variant.setPackageSeed(seed.clone());
         RandomStream stream1 = variant.newInstance();
         RandomStream stream4 = variant.newInstance();
         stream4 = variant.newInstance();
         stream4 = variant.newInstance();

         RandomStream expected4 = variant.newStream(seed);
         // The fourth created stream is three stream spacings after the first.
         variant.jump(expected4, streamJump.multiply(BigInteger.valueOf(3L)));

         assertStateEquals(
               variant,
               variant.name + ": fourth created stream vs jump(3 * 2^"
               + variant.streamAdvanceExponent + "), first stream="
               + stateToString(variant.stateOf(stream1)),
               variant.stateOf(expected4),
               variant.stateOf(stream4));
      }
   }

   /**
    * Verifies that reset methods return to the correct saved states after the
    * current state has moved. resetStartStream() should return to the initial
    * stream seed. resetStartSubstream() should return to the beginning of the
    * current substream, not necessarily to the original stream seed.
    */
   private static void testResetBehaviorAfterJumps(Variant variant) {
      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream stream = variant.newStream(seed);

         // Move away from the initial stream state, then reset to it.
         variant.jump(stream, BigInteger.valueOf(1000L));
         stream.resetStartStream();

         assertStateEquals(
               variant,
               variant.name + ": resetStartStream() returns to the initial seed",
               seed,
               variant.stateOf(stream));

         RandomStream substream = variant.newStream(seed);
         substream.resetNextSubstream();
         long[] substreamStart = variant.stateOf(substream);

         // Move away from the substream start, then reset to that substream start.
         variant.jump(substream, BigInteger.valueOf(1000L));
         substream.resetStartSubstream();

         assertStateEquals(
               variant,
               variant.name + ": resetStartSubstream() returns to current substream start",
               substreamStart,
               variant.stateOf(substream));
      }
   }

   /**
    * Checks deterministic stream creation from the package seed. We set a package
    * seed, create the first three streams, then set the same package seed again
    * and recreate the first three streams. The two sequences of stream-start
    * states should match exactly.
    */
   private static void testPackageSeedReproducibility(Variant variant) {
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

         assertStateEquals(
               variant,
               variant.name + ": replayed first stream after setPackageSeed(), seed="
               + stateToString(seed),
               firstState1,
               variant.stateOf(replay1));

         assertStateEquals(
               variant,
               variant.name + ": replayed second stream after setPackageSeed(), seed="
               + stateToString(seed),
               firstState2,
               variant.stateOf(replay2));

         assertStateEquals(
               variant,
               variant.name + ": replayed third stream after setPackageSeed(), seed="
               + stateToString(seed),
               firstState3,
               variant.stateOf(replay3));
      }
   }

   /**
    * Tries seeds that should be rejected by the generator's seed validation.
    * These checks confirm that invalid seed shapes or invalid carry values do
    * not silently create unusable or forbidden states.
    */
   private static void testInvalidSeeds(final Variant variant) {
      assertException(
            variant,
            variant.name + ": null seed is rejected",
            new ThrowingRunnable() {
               public void run() {
                  variant.setSeedFromRandomStream(variant.newInstance(), null);
               }
            });

      assertException(
            variant,
            variant.name + ": wrong seed length is rejected",
            new ThrowingRunnable() {
               public void run() {
                  variant.setSeedFromRandomStream(variant.newInstance(), new long[] {1L});
               }
            });

      assertException(
            variant,
            variant.name + ": negative carry is rejected",
            new ThrowingRunnable() {
               public void run() {
                  long[] seed = variant.validSeed();
                  seed[seed.length - 1] = -1L;
                  variant.setSeedFromRandomStream(variant.newInstance(), seed);
               }
            });

      assertException(
            variant,
            variant.name + ": too-large carry is rejected",
            new ThrowingRunnable() {
               public void run() {
                  long[] seed = variant.validSeed();
                  seed[seed.length - 1] = Long.MAX_VALUE;
                  variant.setSeedFromRandomStream(variant.newInstance(), seed);
               }
            });

      assertException(
            variant,
            variant.name + ": all-zero state is rejected",
            new ThrowingRunnable() {
               public void run() {
                  variant.setSeedFromRandomStream(variant.newInstance(), new long[variant.seedLength()]);
               }
            });
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

            assertStateEquals(
                  variant,
                  variant.name + ": jump(" + jumpLabel(n)
                  + ") boundary composition, seed=" + stateToString(seed),
                  variant.stateOf(direct),
                  variant.stateOf(composed));
         }
      }
   }

   /**
    * Calls advanceStateByJump() with a negative distance and expects an exception.
    * A negative jump is not supported, so silently accepting it would hide a
    * caller error.
    */
   private static void testNegativeJumpRejection(final Variant variant) {
      assertException(
            variant,
            variant.name + ": jump(-1) is rejected",
            new ThrowingRunnable() {
               public void run() {
                  RandomStream stream = variant.newStream(variant.validSeed());
                  variant.jump(stream, BigInteger.valueOf(-1L));
               }
            });
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

      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream fixedSub = variant.newStream(seed);
         RandomStream genericSub = variant.newStream(seed);

         // Both objects start from seed. Move one with the fixed substream jump
         // and the other with the equivalent generic jump.
         fixedSub.resetNextSubstream();
         variant.jump(genericSub, substreamJump);

         assertTrue(
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

         assertTrue(
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
      for (int i = 0; i < variant.seeds.length; i++) {
         long[] seed = variant.seeds[i];
         RandomStream original = variant.newStream(seed);
         // Clone a non-initial state so the test covers the current state, not just construction.
         variant.jump(original, BigInteger.valueOf(17L));

         RandomStream copy = variant.copyOf(original);

         assertStateEquals(
               variant,
               variant.name + ": clone starts with the same state, seed=" + stateToString(seed),
               variant.stateOf(original),
               variant.stateOf(copy));

         long originalNext = variant.rawNext(original);
         long copyNext = variant.rawNext(copy);
         // Advance only the original; the clone should keep its own state.
         variant.rawNext(original);

         assertTrue(
               variant,
               variant.name + ": clone has matching next output and independent later state",
               originalNext == copyNext
               && !Arrays.equals(variant.stateOf(original), variant.stateOf(copy)));
      }
   }

   /**
    * Asserts one state comparison. The expected state is the reference path; the
    * actual state is the operation being tested.
    */
   private static void assertStateEquals(Variant variant, String label, long[] expected, long[] actual) {
      if (!Arrays.equals(expected, actual)) {
         throw new AssertionError(
               label
               + "\nexpected: " + stateToString(expected)
               + "\nactual:   " + stateToString(actual));
      }
      variant.good++;
   }

   /**
    * Asserts a boolean condition and counts it as one successful test if it passes.
    */
   private static void assertTrue(Variant variant, String label, boolean ok) {
      if (!ok)
         throw new AssertionError(label);
      variant.good++;
   }

   /**
    * Asserts a boolean condition and reports both state and next-output values if
    * the two paths disagree.
    */
   private static void assertTrue(Variant variant, String label, boolean ok,
                                  long[] expected, long[] actual,
                                  long nextExpected, long nextActual) {
      if (!ok) {
         throw new AssertionError(
               label
               + "\nexpected state: " + stateToString(expected)
               + "\nactual state:   " + stateToString(actual)
               + "\nexpected next:  " + Long.toUnsignedString(nextExpected)
               + "\nactual next:    " + Long.toUnsignedString(nextActual));
      }
      variant.good++;
   }

   /**
    * Runs code that should fail and counts the check if a RuntimeException is thrown.
    */
   private static void assertException(Variant variant, String label, ThrowingRunnable runnable) {
      try {
         runnable.run();
      } catch (RuntimeException e) {
         variant.good++;
         return;
      }

      throw new AssertionError(label + "\nexpected exception, but none was thrown");
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
    * Formats large boundary jumps such as 2^62 - 1, 2^62, and 2^62 + 1 so
    * assertion labels are readable.
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

   /**
    * Prints how many assertion checks passed for each generator.
    */
   private static void printSummary() {
      int totalGood = 0;

      System.out.println("======================================");
      System.out.println("SUMMARY");

      for (int i = 0; i < VARIANTS.length; i++) {
         Variant variant = VARIANTS[i];
         totalGood += variant.good;
         System.out.println(variant.name + ": assertion tests = " + variant.good);
      }

      System.out.println("TOTAL: assertion tests = " + totalGood);
      System.out.println("======================================");
   }

   /**
    * Converts a state array to a readable unsigned string.
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
