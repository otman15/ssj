package rqmcexperiments;


import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.RandomStream;

public class TestGenzFunctions {

   private static final int NUM_REPS = 1000000;
   private static final int[] DIMENSIONS = {2, 4, 8, 16, 32};

   public static void main(String[] args) {
	   
      System.out.println("-----------m = " + NUM_REPS + "----------------");
      for (int s : DIMENSIONS) {
         double[] c = makeC(s);
         double[] w = makeW(s);
         double[] wDisc = {0.35, 0.65};
         
         

         System.out.println("\n --------- Dimension s = " + s);
         System.out.print(" C_j = ");
         for(int i =0; i <s;i++) System.out.printf("%.1f  " , c[i]);
         System.out.println("");
         System.out.print(" W_j = ");
         for(int i =0; i <s;i++) System.out.printf("%.1f  " , w[i] );
         System.out.println("\n");

         long t0 = System.nanoTime();
         MonteCarloModelDouble gaussian = new GenzGaussian(s, c, w);
         long t1 = System.nanoTime();
         run("GenzGaussian", gaussian, t1 - t0);

         t0 = System.nanoTime();
         MonteCarloModelDouble continuous = new GenzContinuous(s, c, w);
         t1 = System.nanoTime();
         run("GenzContinuous", continuous, t1 - t0);

         t0 = System.nanoTime();
         MonteCarloModelDouble productPeak = new GenzProductPeak(s, c, w);
         t1 = System.nanoTime();
         run("GenzProductPeak", productPeak, t1 - t0);

         if (s <= 16) {
            t0 = System.nanoTime();
            MonteCarloModelDouble cornerPeak = new GenzCornerPeak(s, c);
            t1 = System.nanoTime();
            run("GenzCornerPeak", cornerPeak, t1 - t0);
         } else {
            System.out.println("GenzCornerPeak      skipped for s = " + s);
         }

         t0 = System.nanoTime();
         MonteCarloModelDouble oscillatory = new GenzOscillatory(s, c, 0.37);
         t1 = System.nanoTime();
         run("GenzOscillatory", oscillatory, t1 - t0);

         t0 = System.nanoTime();
         MonteCarloModelDouble discontinuous = new GenzDiscontinuous(s, c, wDisc);
         t1 = System.nanoTime();
         run("GenzDiscontinuous", discontinuous, t1 - t0);
      }
   }

   private static void run(String name, MonteCarloModelDouble model, long constructTimeNs) {
      //RandomStream stream = new MRG32k3a();
      RandomStream stream = new MWC64k2a2();

      double sum = 0.0;

      long startSim = System.nanoTime();

      for (int i = 0; i < NUM_REPS; i++) {
         model.simulate(stream);
         sum += model.getPerformance();
      }

      long endSim = System.nanoTime();

      double mcMinusTrue = sum / NUM_REPS;
      double constructTimeMs = constructTimeNs / 1.0e6;
      double simTimeMs = (endSim - startSim) / 1.0e6;

      System.out.printf(
            "%-20s  MC - true = %+.6e    simulation = %.3f ms%n",
            name , mcMinusTrue, constructTimeMs, simTimeMs);
   }

   private static double[] makeC(int s) {
      double[] c = new double[s];

      for (int j = 0; j < s; j++)
         c[j] = 0.5 + (double) (j + 1) / (double) s;

      return c;
   }

   private static double[] makeW(int s) {
      double[] w = new double[s];

      for (int j = 0; j < s; j++)
         w[j] = 0.15 + 0.70 * (double) (j + 1) / (double) (s + 1);

      return w;
   }
}