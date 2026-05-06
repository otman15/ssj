package rqmcexperiments;

import java.io.*;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment64;
import umontreal.ssj.randvar.NormalGen;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Chrono;

/**
 * New version of the main program to generate and store large RQMC samples
 * for various functions and various methods, that was used for WSC 2023 paper.
 * This program uses the class `WSC23MoreSamples.java`, which uses `hups64`.
 */
public class WSC23MoreReps extends RQMCExperiment64 {

   public static void main(String[] args) throws IOException {
      // WSC26RQMCSamples64.directory = "C:/Users/Lecuyer/Dropbox/wsc26/data64/"; // Retained for 64 bits
      WSC23MoreSamples.directory = "C:/Users/Lecuyer/Dropbox/wsc23/test/";        // For testing

      MonteCarloModelDouble model;
      Chrono timerTotal = new Chrono();
      RandomStream noise = new LFSR258();

      for (int s = 8; s <= 8; s *= 2) {
      // for (int s = 4; s <= 32; s *= 2) {
         System.out.println("WSC23MoreReps, run with s = " + s);
         // Uncomment the model you want below. ***
         // model = new SmoothPerBeta52(s, 1.0);
         // model = new SmoothPerB4(s, 1.0);
         // model = new SumUeU(s);
         // model = new MC2(s);
         // model = new Polynomial(s);
         // model = new Oscillatory(s);
         // model = new Gaussian(s);
         // model = new SmoothGauss(s);
         // model = new PieceLinGauss(s);
         // model = new IndSumNormal(s);
         // model = new SumUniforms(s);
         // model = new RidgeJohnsonSU(s);
         // model = new IndBox(s);
         
         //  RQMCSamples23.redirectToFile((model.getTag() + "-" + s));
         // WSC26RQMCSamples64.simulAllSizes(model, s, 8, 18, 1000);
         
         int m = 10000; // Number of RQMC randomizations.
         int mink = 16;
         int maxk = 16;
         WSC23MoreSamples.simulRepsAllSizes(new SmoothPerB4(s, 1.0), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new SumUeU(s), s, mink, maxk, m);
         WSC23MoreSamples.simulRepsAllSizes(new MC2(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new Polynomial(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new Oscillatory(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new Gaussian(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new SmoothGauss(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new PieceLinGauss(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new IndSumNormal(s), s, mink, maxk, m);
              
      }
      System.out.println("Total time for everything: " + timerTotal.format() +
            "\n=========================================== \n");
   }
}
