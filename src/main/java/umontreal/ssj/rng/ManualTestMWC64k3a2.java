package umontreal.ssj.rng;

import java.util.Arrays;

public class ManualTestMWC64k3a2 {
   public static void main(String[] args) {
      long[] seed = { 1L, 3L, 4L, 5L };

      MWC64k3a2 rng = new MWC64k3a2("manual-test");
      rng.setSeed(seed);

      System.out.println("Initial state:");
      System.out.println(rng.toStringFull());

      generateDoubles(rng, 10);
      generateInts(rng, -5, 5, 30);
      generateLongs(rng, -10L, 10L, 30);
      generateDoubleArray(rng);
      generateLongArray(rng);
      testReset(rng, seed);
      countInts(rng, 0, 9, 100000);

      System.out.println("Final state:");
      System.out.println(rng.toStringFull());
   }

   private static void generateDoubles(MWC64k3a2 rng, int n) {
      System.out.println("\nNext doubles:");

      for (int i = 0; i < n; i++)
         System.out.println(i + " : " + rng.nextDouble());
   }

   private static void generateInts(MWC64k3a2 rng, int a, int b, int n) {
      System.out.println("\nNext ints in [" + a + ", " + b + "]:");

      for (int i = 0; i < n; i++)
         System.out.print(rng.nextInt(a, b) + " ");

      System.out.println();
   }

   private static void generateLongs(MWC64k3a2 rng, long a, long b, int n) {
      System.out.println("\nNext longs in [" + a + ", " + b + "]:");

      for (int i = 0; i < n; i++)
         System.out.print(rng.nextLong(a, b) + " ");

      System.out.println();
   }

   private static void generateDoubleArray(MWC64k3a2 rng) {
      double[] u = new double[10];

      rng.nextArrayOfDouble(u, 0, u.length);

      System.out.println("\nArray of doubles:");
      System.out.println(Arrays.toString(u));
   }

   private static void generateLongArray(MWC64k3a2 rng) {
      long[] u = new long[10];

      rng.nextArrayOfLong(100L, 200L, u, 0, u.length);

      System.out.println("\nArray of longs in [100, 200]:");
      System.out.println(Arrays.toString(u));
   }

   private static void testReset(MWC64k3a2 rng, long[] seed) {
      rng.setSeed(seed);

      double u1 = rng.nextDouble();
      double u2 = rng.nextDouble();

      rng.resetStartStream();

      double v1 = rng.nextDouble();
      double v2 = rng.nextDouble();

      System.out.println("\nReset test:");
      System.out.println("Before reset: " + u1 + ", " + u2);
      System.out.println("After reset:  " + v1 + ", " + v2);
      System.out.println("Same? " + (u1 == v1 && u2 == v2));
   }

   private static void countInts(MWC64k3a2 rng, int a, int b, int n) {
      rng.setSeed(new long[] { 1L, 3L, 4L, 5L });

      int[] counts = new int[b - a + 1];

      for (int i = 0; i < n; i++) {
         int x = rng.nextInt(a, b);
         counts[x - a]++;
      }

      System.out.println("\nCounts for nextInt(" + a + ", " + b + "), n = " + n + ":");
      System.out.println(Arrays.toString(counts));
   }
}