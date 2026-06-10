package rqmcexperiments;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import umontreal.ssj.mcqmctools.MonteCarloExperiment;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.rng.MWC64k2a2;


public class TestGenzFunctions {

   private static final int NUM_REPS = 1_000_000;
   private static final int[] DIMENSIONS = {2, 4, 8, 16, 32};
   private static double[]c;
   private static double[]w;
   private static double[]wOs;
   private static double w1 ;
   private static double[] wDisc = {0.2, 0.6};
// add exact mean method to the class before executing
   /**
   public static void main(String[] args)  throws IOException {
	   
	   try (PrintWriter out = new PrintWriter(new FileWriter("/home/otman/Documents/GitHub/Data/o-Genz-test/genz-test_results.res"))) {
	     
		   wOs = makeW(DIMENSIONS.length);
		   int ind = 0;
		   
	      out.println("-----Genz Functions simple Tests------m = " + NUM_REPS + "----------------");
	      for (int s : DIMENSIONS) {
	          c = makeC(s);
	          w = makeW(s);
	
	          w1 = wOs[ind];
	          
	         ind++;
	         
	         String line = String.format("\n --------- Dimension s = " + s);
	         System.out.println(line);
	         out.println(line);
	         out.print(" C_j = ");
	         for(int i =0; i <s;i++) {
	        	 out.printf("%.2f  " , c[i]); 
	        	 if(i==16) out.print("\n       ");
	        	 }
	         out.println("");
	         out.print(" W_j = ");
	         for(int i =0; i <s;i++) {out.printf("%.2f  " , w[i] ); if(i==16) out.print("\n       ");}
	         out.println(" \n");
	         out.printf(" w1Disc = %.2f  w2Disc = %.2f  %n", wDisc[0] , wDisc[1]);
	         out.printf(" w1_Osill = %.2f  ", w1);
	         out.println("\n");
	
	         long t0 = System.nanoTime();
	         GenzGaussian gaussian = new GenzGaussian(s, c, w);
	         long t1 = System.nanoTime();
	         run("GenzGaussian", gaussian,gaussian.getExactMean(), t1 - t0,  out);
	
	         t0 = System.nanoTime();
	         GenzContinuous continuous = new GenzContinuous(s, c, w);
	         t1 = System.nanoTime();
	         run("GenzContinuous", continuous,continuous.getExactMean(), t1 - t0, out);
	
	         t0 = System.nanoTime();
	         GenzProductPeak productPeak = new GenzProductPeak(s, c, w);
	         t1 = System.nanoTime();
	         run("GenzProductPeak", productPeak, productPeak.getExactMean(), t1 - t0, out);
	
	         if (s <= 16) {
	            t0 = System.nanoTime();
	            GenzCornerPeak cornerPeak = new GenzCornerPeak(s, c);
	            t1 = System.nanoTime();
	            run("GenzCornerPeak", cornerPeak,cornerPeak.getExactMean(), t1 - t0, out);
	         } else {
	            out.println("GenzCornerPeak      skipped for s = " + s + "    exact heavy");
	         }
	
	         t0 = System.nanoTime();
	         GenzOscillatory oscillatory = new GenzOscillatory(s, c, w1);
	         t1 = System.nanoTime();
	         run("GenzOscillatory", oscillatory, oscillatory.getExactMean(), - t0, out);
	
	         t0 = System.nanoTime();
	         GenzDiscontinuous discontinuous = new GenzDiscontinuous(s, c, wDisc);
	         t1 = System.nanoTime();
	         run("GenzDiscontinuous", discontinuous, discontinuous.getExactMean(), t1 - t0, out);
	      }
	   }
      
      
   }

   private static void run(String name, MonteCarloModelDouble model, double exactMean, long constructorTimeNs,PrintWriter out) {
//		RandomStream stream = new LFSR258();
        RandomStream stream = new MRG32k3a();
        // RandomStream stream = new MWC64k2a2();
         
		Tally stat = new Tally(name);
		
		long startSim = System.nanoTime();
		
		MonteCarloExperiment.simulateRuns(model, NUM_REPS, stream, stat);
		
		long endSim = System.nanoTime();
		
		double mcMinusTrue = stat.average();
		double relativeError = Math.abs(exactMean) > 1.0e-14
		? Math.abs(mcMinusTrue) / Math.abs(exactMean)
		: Double.NaN;
		
		double simulationTimeMs = (endSim - startSim) / 1.0e6;
		
		String line = String.format(
	              "%-20s exactmean = %+.2e  error = %+.2e  relativeErr = %+.2e  simTime = %.1f ms%n", name ,exactMean, mcMinusTrue, relativeError, simulationTimeMs);
		System.out.print(line);
		out.print(line);
	
   }

   private static double[] makeC(int s) {
	   double[] c = new double[s];

	   for (int j = 0; j < s; j++)
		   c[j] = 0.25 + 6.5 * ((j * 19 + 3) % s) / (double) s;
	   
	   return c;
	}

   private static double[] makeW(int s) {
	   double[] w = new double[s];

	   for (int j = 0; j < s; j++)
		   w[j] = 0.1 + 0.89 * ((j * 31 + 5) % s) / (double) s;

	   return w;
	}
	*/
}