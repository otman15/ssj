package umontreal.ssj.rng;

import java.util.Arrays;

/**
 * Basic tests for MWC64k2a2.
 */
public class TestMWC64k2a2 {
   private static final long[] SEED = { 12345L, 67890L, 1L };

   public static void main(String[] args) {
      testNextDoubleRange();
      testNextLongRange();
      testNextIntRange();
      testResetStartStream();
      testResetStartSubstream();
      testResetNextSubstream();
      testArrayOfLong();
      testClone();
      testPackageSeed();
      testToString();

      System.out.println("All MWC64k2a2 tests passed.");
   }

   private static void testNextDoubleRange() {
      MWC64k2a2 s = new MWC64k2a2();
      s.setSeed(SEED);

      for (int i = 0; i < 1_000_000; i++) {
         double u = s.nextDouble();
         check(u > 0.0 && u < 1.0, "nextDouble outside (0,1): " + u);
      }

      System.out.println("testNextDoubleRange passed.");
   }

   private static void testNextLongRange() {
      MWC64k2a2 s = new MWC64k2a2();
      s.setSeed(SEED);

      for (int i = 0; i < 100_000; i++) {
         long x = s.nextLong(-10L, 10L);
         check(x >= -10L && x <= 10L, "nextLong outside [-10,10]: " + x);
      }

      check(s.nextLong(5L, 5L) == 5L, "nextLong(i,i) failed");

      expectThrows(() -> s.nextLong(10L, 5L));

      System.out.println("testNextLongRange passed.");
   }

   private static void testNextIntRange() {
      MWC64k2a2 s = new MWC64k2a2();
      s.setSeed(SEED);

      for (int i = 0; i < 100_000; i++) {
         int x = s.nextInt(-5, 5);
         check(x >= -5 && x <= 5, "nextInt outside [-5,5]: " + x);
      }

      check(s.nextInt(7, 7) == 7, "nextInt(i,i) failed");

      expectThrows(() -> s.nextInt(10, 5));

      System.out.println("testNextIntRange passed.");
   }

   private static void testResetStartStream() {
      MWC64k2a2 s = new MWC64k2a2();
      s.setSeed(SEED);

      double u1 = s.nextDouble();

      s.nextDouble();
      s.nextDouble();

      s.resetStartStream();

      double u2 = s.nextDouble();

      check(u1 == u2, "resetStartStream failed");

      System.out.println("testResetStartStream passed.");
   }

   private static void testResetStartSubstream() {
      MWC64k2a2 s = new MWC64k2a2();
      s.setSeed(SEED);

      double u1 = s.nextDouble();

      s.nextDouble();
      s.nextDouble();

      s.resetStartSubstream();

      double u2 = s.nextDouble();

      check(u1 == u2, "resetStartSubstream failed");

      System.out.println("testResetStartSubstream passed.");
   }

   private static void testResetNextSubstream() {
      MWC64k2a2 s = new MWC64k2a2();
      s.setSeed(SEED);

      double u1 = s.nextDouble();

      s.resetStartStream();
      s.resetNextSubstream();

      double u2 = s.nextDouble();

      check(u1 != u2, "resetNextSubstream did not move");

      s.nextDouble();
      s.nextDouble();

      s.resetStartSubstream();

      double u3 = s.nextDouble();

      check(u2 == u3, "resetStartSubstream after resetNextSubstream failed");

      System.out.println("testResetNextSubstream passed.");
   }

   private static void testArrayOfLong() {
      MWC64k2a2 s1 = new MWC64k2a2();
      MWC64k2a2 s2 = new MWC64k2a2();

      s1.setSeed(SEED);
      s2.setSeed(SEED);

      long[] arr = new long[20];

      s1.nextArrayOfLong(-3L, 3L, arr, 5, 10);

      for (int i = 0; i < 5; i++)
         check(arr[i] == 0L, "array modified before start");

      for (int i = 5; i < 15; i++) {
         long expected = s2.nextLong(-3L, 3L);
         check(arr[i] == expected, "array mismatch at index " + i);
         check(arr[i] >= -3L && arr[i] <= 3L, "array value outside range");
      }

      for (int i = 15; i < 20; i++)
         check(arr[i] == 0L, "array modified after end");

      System.out.println("testArrayOfLong passed.");
   }

   private static void testClone() {
      MWC64k2a2 s1 = new MWC64k2a2();
      s1.setSeed(SEED);

      s1.nextDouble();
      s1.nextDouble();

      MWC64k2a2 s2 = s1.clone();

      check(Arrays.equals(s1.getState(), s2.getState()), "clone state mismatch");

      for (int i = 0; i < 1000; i++)
         check(s1.nextDouble() == s2.nextDouble(), "clone sequence mismatch");

      System.out.println("testClone passed.");
   }

   private static void testPackageSeed() {
      MWC64k2a2.setPackageSeed(SEED);
      MWC64k2a2 s1 = new MWC64k2a2();

      MWC64k2a2.setPackageSeed(SEED);
      MWC64k2a2 s2 = new MWC64k2a2();

      for (int i = 0; i < 1000; i++)
         check(s1.nextDouble() == s2.nextDouble(), "package seed failed");

      System.out.println("testPackageSeed passed.");
   }

   private static void testToString() {
      MWC64k2a2 s = new MWC64k2a2("test");
      s.setSeed(SEED);

      check(s.toString().contains("MWC64k2a2"), "toString missing class name");
      check(s.toStringFull().contains("Ig"), "toStringFull missing Ig");
      check(s.toStringFull().contains("Bg"), "toStringFull missing Bg");
      check(s.toStringFull().contains("Cg"), "toStringFull missing Cg");

      System.out.println("testToString passed.");
   }

   private static void check(boolean condition, String message) {
      if (!condition)
         throw new AssertionError(message);
   }

   private static void expectThrows(Runnable r) {
      boolean thrown = false;

      try {
         r.run();
      } catch (RuntimeException e) {
         thrown = true;
      }

      if (!thrown)
         throw new AssertionError("Expected exception, but none was thrown.");
   }
}