package umontreal.ssj.rng;

import java.math.BigInteger;
import java.util.Arrays;

public class TestMWC64k3a2 {
   private static final long[] SEED = { 1L, 3L, 4L, 5L };

   private static final BigInteger A2 = new BigInteger("184698970548483715");
   private static final BigInteger A3 = new BigInteger("6028691832887");
   private static final BigInteger TWO64 = BigInteger.ONE.shiftLeft(64);
   private static final BigInteger MASK64 = TWO64.subtract(BigInteger.ONE);

   public static void main(String[] args) {
      testStateAgainstBigInteger();
      testNextDoubleRange();
      testNextLongRange();
      testNextIntRange();
      testResetStartStream();
      testResetStartSubstream();
      testResetNextSubstream();
      testArrayOfLong();
      testClone();
      testPackageSeed();

      System.out.println("All MWC64k3a2 tests passed.");
   }

   private static void testStateAgainstBigInteger() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      BigInteger[] ref = {
            unsigned(SEED[0]),
            unsigned(SEED[1]),
            unsigned(SEED[2]),
            unsigned(SEED[3])
      };

      for (int n = 0; n < 1000; n++) {
         s.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
         ref = nextRefState(ref);

         long[] state = s.getState();

         check(state[0] == ref[0].longValue(), "x0 mismatch at step " + n);
         check(state[1] == ref[1].longValue(), "x1 mismatch at step " + n);
         check(state[2] == ref[2].longValue(), "x2 mismatch at step " + n);
         check(state[3] == ref[3].longValue(), "carry mismatch at step " + n);
      }

      System.out.println("testStateAgainstBigInteger passed.");
   }

   private static BigInteger[] nextRefState(BigInteger[] state) {
      BigInteger x0 = state[0];
      BigInteger x1 = state[1];
      BigInteger x2 = state[2];
      BigInteger carry = state[3];

      BigInteger t = A2.multiply(x1).add(A3.multiply(x0)).add(carry);

      BigInteger xNew = t.and(MASK64);
      BigInteger cNew = t.shiftRight(64);

      return new BigInteger[] { x1, x2, xNew, cNew };
   }

   private static void testNextDoubleRange() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      for (int i = 0; i < 100000; i++) {
         double u = s.nextDouble();
         check(u > 0.0 && u < 1.0, "nextDouble outside (0,1)");
      }

      System.out.println("testNextDoubleRange passed.");
   }

   private static void testNextLongRange() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      for (int i = 0; i < 100000; i++) {
         long x = s.nextLong(-10L, 10L);
         check(x >= -10L && x <= 10L, "nextLong outside range");
      }

      check(s.nextLong(5L, 5L) == 5L, "nextLong(i,i) failed");
      expectThrows(() -> s.nextLong(10L, 5L));

      System.out.println("testNextLongRange passed.");
   }

   private static void testNextIntRange() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      for (int i = 0; i < 100000; i++) {
         int x = s.nextInt(-5, 5);
         check(x >= -5 && x <= 5, "nextInt outside range");
      }

      check(s.nextInt(7, 7) == 7, "nextInt(i,i) failed");
      expectThrows(() -> s.nextInt(10, 5));

      System.out.println("testNextIntRange passed.");
   }

   private static void testResetStartStream() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      double u1 = s.nextDouble();
      s.nextDouble();

      s.resetStartStream();
      double u2 = s.nextDouble();

      check(u1 == u2, "resetStartStream failed");

      System.out.println("testResetStartStream passed.");
   }

   private static void testResetStartSubstream() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      double u1 = s.nextDouble();
      s.nextDouble();

      s.resetStartSubstream();
      double u2 = s.nextDouble();

      check(u1 == u2, "resetStartSubstream failed");

      System.out.println("testResetStartSubstream passed.");
   }

   private static void testResetNextSubstream() {
      MWC64k3a2 s = new MWC64k3a2();
      s.setSeed(SEED);

      double u1 = s.nextDouble();

      s.resetStartStream();
      s.resetNextSubstream();

      double u2 = s.nextDouble();

      check(u1 != u2, "resetNextSubstream did not move");

      s.nextDouble();
      s.resetStartSubstream();

      double u3 = s.nextDouble();

      check(u2 == u3, "resetStartSubstream after resetNextSubstream failed");

      System.out.println("testResetNextSubstream passed.");
   }

   private static void testArrayOfLong() {
      MWC64k3a2 s1 = new MWC64k3a2();
      MWC64k3a2 s2 = new MWC64k3a2();

      s1.setSeed(SEED);
      s2.setSeed(SEED);

      long[] arr = new long[20];

      s1.nextArrayOfLong(-3L, 3L, arr, 5, 10);

      for (int i = 0; i < 5; i++)
         check(arr[i] == 0L, "array modified before start");

      for (int i = 5; i < 15; i++) {
         long expected = s2.nextLong(-3L, 3L);
         check(arr[i] == expected, "array mismatch at index " + i);
      }

      for (int i = 15; i < 20; i++)
         check(arr[i] == 0L, "array modified after end");

      System.out.println("testArrayOfLong passed.");
   }

   private static void testClone() {
      MWC64k3a2 s1 = new MWC64k3a2();
      s1.setSeed(SEED);

      s1.nextDouble();
      s1.nextDouble();

      MWC64k3a2 s2 = s1.clone();

      check(Arrays.equals(s1.getState(), s2.getState()), "clone state mismatch");

      for (int i = 0; i < 1000; i++)
         check(s1.nextDouble() == s2.nextDouble(), "clone sequence mismatch");

      System.out.println("testClone passed.");
   }

   private static void testPackageSeed() {
      MWC64k3a2.setPackageSeed(SEED);
      MWC64k3a2 s1 = new MWC64k3a2();

      MWC64k3a2.setPackageSeed(SEED);
      MWC64k3a2 s2 = new MWC64k3a2();

      for (int i = 0; i < 1000; i++)
         check(s1.nextDouble() == s2.nextDouble(), "package seed failed");

      System.out.println("testPackageSeed passed.");
   }

   private static BigInteger unsigned(long x) {
      return new BigInteger(Long.toUnsignedString(x));
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