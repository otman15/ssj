package rqmcexperiments;

import java.util.Arrays;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import java.io.PrintStream;
import java.io.FileNotFoundException;

public class TestCornerPeakExactMean {

   private static volatile double sink;

   public static void main(String[] args) throws FileNotFoundException {
	   PrintStream out = new PrintStream("/home/otman/Documents/GitHub/Data/o-Genz-test/ExMeanCornerPeTest.res");
	   System.setOut(out);
	   RandomStream stream = new LFSR258();
      warmup(stream);

      int[] dims = {2, 4, 8, 16, 32};
      
      System.out.println("Gray vs Recursion for computing Corner peak exact mean for dim: 2,4,8,16,32" + "\n" + "Comparison of  exact means and times (median is used for time) "+
      "\n" + "For s=16 only 500 rep are made;  s=32 only 5 rep are made because it's already very slow" + "\n" + "Negative 'time relative diffrence' means gray is better");

      for (int s : dims) {
         test("random c", makeRandomC(s,stream));
      }

      System.out.println("sink = " + sink);
   }

   private static void warmup(RandomStream stream ) {
      double[] c = makeRandomC(16, stream);
      GenzCornerPeak f = new GenzCornerPeak(c.length, c, false);

      for (int i = 0; i < 5; i++) {
         sink += f.computeExactMeanRec();
         sink += f.computeExactMean();
      }
   }

   private static void test(String name, double[] c) {
      GenzCornerPeak f = new GenzCornerPeak(c.length, c, false);

      int reps = (c.length == 32) ? 5 : (c.length == 16) ? 100 : 10000;

      double[] recuTimes = new double[reps];
      double[] grayTimes = new double[reps];

      double recuMean = 0.0;
      double grayMean = 0.0;

      for (int r = 0; r < reps; r++) {
         if ((r & 1) == 0) {
            long t0 = System.nanoTime();
            recuMean = f.computeExactMeanRec();
            long t1 = System.nanoTime();

            grayMean = f.computeExactMean();
            long t2 = System.nanoTime();

            recuTimes[r] = (t1 - t0) / 1e6;
            grayTimes[r] = (t2 - t1) / 1e6;
         } else {
            long t0 = System.nanoTime();
            grayMean = f.computeExactMean();
            long t1 = System.nanoTime();

            recuMean = f.computeExactMeanRec();
            long t2 = System.nanoTime();

            grayTimes[r] = (t1 - t0) / 1e6;
            recuTimes[r] = (t2 - t1) / 1e6;
         }

         sink += recuMean + grayMean;
      }

      double meanRelDiff = Math.abs(grayMean - recuMean)
    	      / Math.max(1e-300, Math.abs(recuMean));

    	double recuMedianTime = median(recuTimes);
    	double grayMedianTime = median(grayTimes);

    	double timeRelDiff = (grayMedianTime - recuMedianTime)
    	      / Math.max(1e-300, recuMedianTime);

    	System.out.println("===== " + name + " =====");
    	System.out.println("s = " + c.length);
    	System.out.println("reps = " + reps);

    	System.out.println("recur mean = " + recuMean);
    	System.out.println("gray mean = " + grayMean);
    	System.out.println("mean rel diff = " + meanRelDiff);

    	System.out.println("recur median time ms = " + recuMedianTime);
    	System.out.println("gray median time ms = " + grayMedianTime);
    	System.out.println("time rel diff (gray-rec)/rec = " + timeRelDiff + " (" + (100.0 * timeRelDiff) + "%)");
    	System.out.println();
   }

   private static double median(double[] x) {
      double[] copy = x.clone();
      Arrays.sort(copy);
      int n = copy.length;

      if ((n & 1) == 1)
         return copy[n / 2];

      return 0.5 * (copy[n / 2 - 1] + copy[n / 2]);
   }
   
   private static double mean(double[] x) {
	   if (x.length == 0)
	      throw new IllegalArgumentException("array must not be empty");

	   double sum = 0.0;

	   for (int i = 0; i < x.length; i++)
	      sum += x[i];

	   return sum / x.length;
	}


   private static double[] makeRandomC(int s,RandomStream stream) {
      double[] c = new double[s];

      for (int j = 0; j < s; j++)
         c[j] = 0.1 + stream.nextDouble();

      return c;
   }

}