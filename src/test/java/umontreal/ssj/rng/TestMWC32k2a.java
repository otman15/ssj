package umontreal.ssj.rng;

import java.util.Arrays;

/**
 * Simple standalone tests for MWC32k2a.
 *
 * Run with assertions enabled or just run normally. This class uses its own
 * check() method, so it does not require JUnit.
 */
public class TestMWC32k2a {
   private static final long A1 = 1111111464L;
   private static final long A2 = 1111111464L;
   private static final long MASK32 = 0xFFFFFFFFL;
   private static final long TWO32 = 1L << 32;
   private static final double NORM = 0x1.0p-32;

   private static final long[] SEED = { 12345L, 67890L, 1L };

   public static void main(String[] args) {
      testSeedValidation();
      testNextDoubleMatchesRecurrence();
      testNextIntMatchesRecurrence();
      testResetStartStream();
      testResetStartSubstream();
      testResetNextSubstream();
      testArrayMethods();
      testClone();
      testPackageSeed();
      testAntitheticNextDouble();
      testIncreasedPrecisionNextDouble();
      testNextIntFrequency();

      System.out.println("All MWC32k2a tests passed.");
   }

   private static void testSeedValidation() {
      MWC32k2a s = new MWC32k2a();

      expectThrows(() -> s.setSeed(null));
      expectThrows(() -> s.setSeed(new long[] { 1L, 2L }));
      expectThrows(() -> s.setSeed(new long[] { 0L, 0L, 0L }));
      expectThrows(() -> s.setSeed(new long[] { -1L, 1L, 1L }));
      expectThrows(() -> s.setSeed(new long[] { 1L, -1L, 1L }));
      expectThrows(() -> s.setSeed(new long[] { 1L, 1L, A1 + A2 }));

      s.setSeed(SEED);
      checkArrayEquals(SEED, s.getState(), "setSeed/getState failed");

      System.out.println("testSeedValidation passed.");
   }

   private static void testNextDoubleMatchesRecurrence() {
      MWC32k2a s = new MWC32k2a();
      s.setSeed(SEED);

      Oracle oracle = new Oracle(SEED);

      for (int i = 0; i < 1000; i++) {
         double expected = oracle.nextDouble();
         double actual = s.nextDouble();

         checkClose(expected, actual, "nextDouble recurrence mismatch at i = " + i);
         check(actual > 0.0 && actual < 1.0, "nextDouble outside (0,1)");
      }

      checkArrayEquals(oracle.state(), s.getState(), "State mismatch after nextDouble calls");

      System.out.println("testNextDoubleMatchesRecurrence passed.");
   }

   private static void testNextIntMatchesRecurrence() {
      MWC32k2a s = new MWC32k2a();
      s.setSeed(SEED);

      Oracle oracle = new Oracle(SEED);

      for (int i = 0; i < 1000; i++) {
         int expected = oracle.nextInt(0, 9);
         int actual = s.nextInt(0, 9);

         check(expected == actual, "nextInt(0,9) mismatch at i = " + i);
         check(actual >= 0 && actual <= 9, "nextInt(0,9) outside range");
      }

      for (int i = 0; i < 1000; i++) {
         int expected = oracle.nextInt(-5, 5);
         int actual = s.nextInt(-5, 5);

         check(expected == actual, "nextInt(-5,5) mismatch at i = " + i);
         check(actual >= -5 && actual <= 5, "nextInt(-5,5) outside range");
      }

      int fixed = s.nextInt(123, 123);
      oracle.nextInt(123, 123);
      check(fixed == 123, "nextInt(i,i) should always return i");

      for (int i = 0; i < 1000; i++) {
         int expected = oracle.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
         int actual = s.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

         check(expected == actual, "Full int range nextInt mismatch at i = " + i);
      }

      expectThrows(() -> s.nextInt(10, 9));

      System.out.println("testNextIntMatchesRecurrence passed.");
   }

   private static void testResetStartStream() {
      MWC32k2a s = new MWC32k2a();
      s.setSeed(SEED);

      double u1 = s.nextDouble();
      s.nextDouble();
      s.nextDouble();

      s.resetStartStream();
      double u2 = s.nextDouble();

      checkClose(u1, u2, "resetStartStream did not return to stream start");

      System.out.println("testResetStartStream passed.");
   }

   private static void testResetStartSubstream() {
      MWC32k2a s = new MWC32k2a();
      s.setSeed(SEED);

      double u1 = s.nextDouble();
      s.nextDouble();
      s.nextDouble();

      s.resetStartSubstream();
      double u2 = s.nextDouble();

      checkClose(u1, u2, "resetStartSubstream did not return to substream start");

      System.out.println("testResetStartSubstream passed.");
   }

   private static void testResetNextSubstream() {
      MWC32k2a s = new MWC32k2a();
      s.setSeed(SEED);

      double firstStreamValue = s.nextDouble();

      s.resetStartStream();
      s.resetNextSubstream();

      double firstSubstreamValue = s.nextDouble();

      check(firstStreamValue != firstSubstreamValue,
            "resetNextSubstream did not move to a different substream");

      s.nextDouble();
      s.nextDouble();

      s.resetStartSubstream();
      double repeatedFirstSubstreamValue = s.nextDouble();

      checkClose(firstSubstreamValue, repeatedFirstSubstreamValue,
            "resetStartSubstream failed after resetNextSubstream");

      System.out.println("testResetNextSubstream passed.");
   }

   private static void testArrayMethods() {
      MWC32k2a s1 = new MWC32k2a();
      MWC32k2a s2 = new MWC32k2a();

      s1.setSeed(SEED);
      s2.setSeed(SEED);

      double[] arr = new double[20];
      s1.nextArrayOfDouble(arr, 5, 10);

      for (int i = 0; i < 5; i++)
         check(arr[i] == 0.0, "nextArrayOfDouble modified values before start");

      for (int i = 5; i < 15; i++) {
         double expected = s2.nextDouble();
         checkClose(expected, arr[i], "nextArrayOfDouble mismatch at index " + i);
         check(arr[i] > 0.0 && arr[i] < 1.0, "Array double outside (0,1)");
      }

      for (int i = 15; i < 20; i++)
         check(arr[i] == 0.0, "nextArrayOfDouble modified values after end");

      s1.setSeed(SEED);
      s2.setSeed(SEED);

      int[] ints = new int[20];
      s1.nextArrayOfInt(-3, 3, ints, 5, 10);

      for (int i = 0; i < 5; i++)
         check(ints[i] == 0, "nextArrayOfInt modified values before start");

      for (int i = 5; i < 15; i++) {
         int expected = s2.nextInt(-3, 3);
         check(expected == ints[i], "nextArrayOfInt mismatch at index " + i);
         check(ints[i] >= -3 && ints[i] <= 3, "Array int outside range");
      }

      for (int i = 15; i < 20; i++)
         check(ints[i] == 0, "nextArrayOfInt modified values after end");

      System.out.println("testArrayMethods passed.");
   }

   private static void testClone() {
      MWC32k2a s1 = new MWC32k2a();
      s1.setSeed(SEED);

      s1.nextDouble();
      s1.nextDouble();

      MWC32k2a s2 = s1.clone();

      checkArrayEquals(s1.getState(), s2.getState(), "clone did not copy state");

      for (int i = 0; i < 100; i++) {
         double u1 = s1.nextDouble();
         double u2 = s2.nextDouble();

         checkClose(u1, u2, "clone sequence mismatch at i = " + i);
      }

      System.out.println("testClone passed.");
   }

   private static void testPackageSeed() {
      MWC32k2a.setPackageSeed(SEED);
      MWC32k2a s1 = new MWC32k2a();

      MWC32k2a.setPackageSeed(SEED);
      MWC32k2a s2 = new MWC32k2a();

      for (int i = 0; i < 100; i++) {
         double u1 = s1.nextDouble();
         double u2 = s2.nextDouble();

         checkClose(u1, u2, "setPackageSeed did not reproduce stream");
      }

      System.out.println("testPackageSeed passed.");
   }

   private static void testAntitheticNextDouble() {
      MWC32k2a normal = new MWC32k2a();
      MWC32k2a anti = new MWC32k2a();

      normal.setSeed(SEED);
      anti.setSeed(SEED);

		      normal.setAntithetic(false);
		      anti.setAntithetic(true);

      for (int i = 0; i < 100; i++) {
         double u = normal.nextDouble();
         double v = anti.nextDouble();

         checkClose(1.0 - u, v, "Antithetic nextDouble mismatch at i = " + i);
      }

      System.out.println("testAntitheticNextDouble passed.");
   }

   private static void testIncreasedPrecisionNextDouble() {
      MWC32k2a s1 = new MWC32k2a();
      MWC32k2a s2 = new MWC32k2a();

      s1.setSeed(SEED);
      s2.setSeed(SEED);

      s1.increasedPrecision(true);
      s2.increasedPrecision(true);

      for (int i = 0; i < 100; i++) {
         double u1 = s1.nextDouble();
         double u2 = s2.nextDouble();

         checkClose(u1, u2, "increasedPrecision reproducibility failed at i = " + i);
         check(u1 > 0.0 && u1 < 1.0, "increasedPrecision value outside (0,1)");
      }

      System.out.println("testIncreasedPrecisionNextDouble passed.");
   }

   private static void testNextIntFrequency() {
      MWC32k2a s = new MWC32k2a();
      s.setSeed(SEED);

      int n = 200000;
      int[] counts = new int[10];

      for (int i = 0; i < n; i++) {
         int x = s.nextInt(0, 9);
         counts[x]++;
      }

      double expected = n / 10.0;
      double tolerance = 0.05 * expected;

      for (int i = 0; i < counts.length; i++) {
         double diff = Math.abs(counts[i] - expected);
         check(diff < tolerance,
               "nextInt frequency too far from expected for value " + i
               + ". counts = " + Arrays.toString(counts));
      }

      System.out.println("nextInt counts = " + Arrays.toString(counts));
      System.out.println("testNextIntFrequency passed.");
   }

   private static final class Oracle {
      private long x0;
      private long x1;
      private long carry;

      Oracle(long[] seed) {
         this.x0 = seed[0];
         this.x1 = seed[1];
         this.carry = seed[2];
      }

      long nextNumber() {
         long t = A1 * x1 + A2 * x0 + carry;

         long xNew = t & MASK32;
         long cNew = t >>> 32;

         x0 = x1;
         x1 = xNew;
         carry = cNew;

         return xNew;
      }

      double nextDouble() {
         return (nextNumber() + 0.5) * NORM;
      }

      int nextInt(int i, int j) {
         if (i > j)
            throw new IllegalArgumentException();

         long bound = (long) j - (long) i + 1L;
         long limit = TWO32 - (TWO32 % bound);

         long r;
         do {
            r = nextNumber();
         } while (r >= limit);

         return (int) ((long) i + (r % bound));
      }

      long[] state() {
         return new long[] { x0, x1, carry };
      }
   }

   private static void check(boolean condition, String message) {
      if (!condition)
         throw new AssertionError(message);
   }

   private static void checkClose(double expected, double actual, String message) {
      if (Double.compare(expected, actual) != 0)
         throw new AssertionError(message + ": expected " + expected + ", got " + actual);
   }

   private static void checkArrayEquals(long[] expected, long[] actual, String message) {
      if (!Arrays.equals(expected, actual))
         throw new AssertionError(
               message + ": expected " + Arrays.toString(expected)
               + ", got " + Arrays.toString(actual));
   }

   private static void expectThrows(Runnable r) {
      boolean thrown = false;

      try {
         r.run();
      } catch (RuntimeException e) {
         thrown = true;
      }

      if (!thrown)
         throw new AssertionError("Expected an exception, but none was thrown.");
   }
}