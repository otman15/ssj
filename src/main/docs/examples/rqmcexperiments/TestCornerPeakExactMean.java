package rqmcexperiments;

import java.util.Random;

public class TestCornerPeakExactMean {

   public static void main(String[] args) {
      int[] dims = {2, 4, 8, 16, 32};

      for (int s : dims) {
         test("constant c = 0.5", makeConstant(s, 0.5));
         test("linear c[j] = (j+1)/s", makeLinear(s));
         test("random c", makeRandom(s, 12345 + s));
         test("mixed c", makeMixed(s));
      }
   }

   private static void test(String name, double[] c) {
      GenzCornerPeak f = new GenzCornerPeak(c.length, c);

      long t0 = System.nanoTime();
      double oldMean = f.computeExactMeanRec();
      long t1 = System.nanoTime();

      double newMean = f.computeExactMean();
      long t2 = System.nanoTime();

      double absDiff = Math.abs(oldMean - newMean);
      double relDiff = absDiff / Math.max(1.0, Math.abs(oldMean));

      System.out.println("===== " + name + " =====");
      System.out.println("s = " + c.length);
      System.out.println("old mean = " + oldMean);
      System.out.println("new mean = " + newMean);
      System.out.println("abs diff = " + absDiff);
      System.out.println("rel diff = " + relDiff);
      System.out.println("old time ms = " + (t1 - t0) / 1e6);
      System.out.println("new time ms = " + (t2 - t1) / 1e6);
      System.out.println();
   }

   private static double[] makeConstant(int s, double value) {
      double[] c = new double[s];

      for (int j = 0; j < s; j++)
         c[j] = value;

      return c;
   }

   private static double[] makeLinear(int s) {
      double[] c = new double[s];

      for (int j = 0; j < s; j++)
         c[j] = (j + 1.0) / s;

      return c;
   }

   private static double[] makeRandom(int s, long seed) {
      Random rand = new Random(seed);
      double[] c = new double[s];

      for (int j = 0; j < s; j++)
         c[j] = 0.1 + rand.nextDouble();

      return c;
   }

   private static double[] makeMixed(int s) {
      double[] base = {0.2, 1.3, 0.7, 2.1, 0.4, 1.8, 0.9, 0.6};
      double[] c = new double[s];

      for (int j = 0; j < s; j++)
         c[j] = base[j % base.length];

      return c;
   }
}