package umontreal.ssj.rng;

import java.util.Arrays;

public class TestMWC64k3a2Montgomery {
   private static final long[][] SEEDS = {
         { 1L, 3L, 4L, 5L },
         { -1L, -2L, Long.MIN_VALUE, 184704999240316600L },
         { Long.MIN_VALUE, 0xFEDCBA9876543210L, -123456789L, 184704999240316601L },
         { 0L, -1L, 0x8000000000000001L, 17L }
   };

   public static void main(String[] args) {
      testRepeatedSubstreamJumps();
      testRepeatedStreamCreation();
      testCloneDoesNotShareMontgomeryArrays();

      System.out.println("All MWC64k3a2Montgomery tests passed.");
   }

   private static void testRepeatedSubstreamJumps() {
      for (long[] seed : SEEDS) {
         MWC64k3a2 expected = originalStream(seed);
         MWC64k3a2Montgomery actual = montgomeryStream(seed);

         assertSameState(expected, actual, "initial substream seed=" + seedString(seed));
         assertSameRaw(expected, actual, "initial raw seed=" + seedString(seed));

         for (int jump = 1; jump <= 8; jump++) {
            expected.resetNextSubstream();
            actual.resetNextSubstream();

            String where = "substream jump " + jump + ", seed=" + seedString(seed);
            assertSameState(expected, actual, where);
            assertSameRaw(expected, actual, where);

            expected.resetStartSubstream();
            actual.resetStartSubstream();
            assertSameRaw(expected, actual, where + " after resetStartSubstream");
         }
      }

      System.out.println("testRepeatedSubstreamJumps passed.");
   }

   private static void testRepeatedStreamCreation() {
      for (long[] seed : SEEDS) {
         MWC64k3a2.setPackageSeed(seed.clone());
         MWC64k3a2Montgomery.setPackageSeed(seed.clone());

         for (int stream = 1; stream <= 6; stream++) {
            MWC64k3a2 expected = new MWC64k3a2();
            MWC64k3a2Montgomery actual = new MWC64k3a2Montgomery();

            String where = "stream " + stream + ", seed=" + seedString(seed);
            assertSameState(expected, actual, where);
            assertSameRaw(expected, actual, where);
         }
      }

      System.out.println("testRepeatedStreamCreation passed.");
   }

   private static void testCloneDoesNotShareMontgomeryArrays() {
      for (long[] seed : SEEDS) {
         MWC64k3a2Montgomery original = montgomeryStream(seed);
         original.resetNextSubstream();

         MWC64k3a2Montgomery clone = original.clone();

         original.resetNextSubstream();
         clone.resetNextSubstream();

         String where = "clone resetNextSubstream, seed=" + seedString(seed);
         check(Arrays.equals(original.getState(), clone.getState()), where + " state mismatch");

         for (int i = 0; i < 16; i++)
            check(original.nextRaw() == clone.nextRaw(), where + " raw mismatch at " + i);
      }

      System.out.println("testCloneDoesNotShareMontgomeryArrays passed.");
   }

   private static MWC64k3a2 originalStream(long[] seed) {
      MWC64k3a2 stream = new MWC64k3a2();
      stream.setSeed(seed.clone());
      return stream;
   }

   private static MWC64k3a2Montgomery montgomeryStream(long[] seed) {
      MWC64k3a2Montgomery stream = new MWC64k3a2Montgomery();
      stream.setSeed(seed.clone());
      return stream;
   }

   private static void assertSameState(MWC64k3a2 expected, MWC64k3a2Montgomery actual,
                                       String where) {
      check(Arrays.equals(expected.getState(), actual.getState()),
            where + " state expected=" + seedString(expected.getState())
                  + " actual=" + seedString(actual.getState()));
   }

   private static void assertSameRaw(MWC64k3a2 expected, MWC64k3a2Montgomery actual,
                                     String where) {
      for (int i = 0; i < 16; i++) {
         long e = expected.nextRaw();
         long a = actual.nextRaw();
         check(e == a, where + " raw mismatch at " + i
               + ": expected=" + Long.toUnsignedString(e)
               + " actual=" + Long.toUnsignedString(a));
      }
   }

   private static String seedString(long[] seed) {
      return "{ " + Long.toUnsignedString(seed[0]) + ", "
            + Long.toUnsignedString(seed[1]) + ", "
            + Long.toUnsignedString(seed[2]) + ", "
            + Long.toUnsignedString(seed[3]) + " }";
   }

   private static void check(boolean condition, String message) {
      if (!condition)
         throw new AssertionError(message);
   }
}
