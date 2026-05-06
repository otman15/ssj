package rqmcexperiments;

import java.io.*;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment;
import umontreal.ssj.util.Chrono;

// Generate and store RQMC replicates for various models, for WSC 2023 paper

public class WSC23RepsRQMC extends RQMCExperiment {

   public static void main(String[] args) throws IOException {

      int m = 10000; // Number of RQMC randomizations.
      MonteCarloModelDouble model;
      Chrono timerTotal = new Chrono();
      System.out.println("WSC23RepsRQMC, old code, 32-bit hups \n");
      // Edit here to select the range for s and k.
      // for (int s = 4; s <= 32; s *= 2) {
      for (int s = 1; s <= 32; s *= 2) {
         for (int k = 8; k <= 16; k = k + 2) {
            System.out.println("WSC23RepsRQMC, run with s = " + s + ", k = " + k);
            // Uncomment the model you want below. ***
            // model = new SmoothPerBeta52(s, 1.0);
            model = new SmoothPerB4(s, 1.0);
            // model = new SumUeU(s);
            // model = new IndBox(s);
            // model = new PieceLinGauss(s);
            // model = new IndSumNormal(s);
            // model = new SmoothGauss(s);
            // model = new SumUniforms(s);
            // model = new MC2(s);
            // model = new RidgeJohnsonSU(s);

            WSC23RQMCSamples.simulRepsAllTypes(model, s, k, m);
            // RQMCSamples.simulRepsNUS (model, s, k, m);
            //WSC23RQMCSamples.simulRepsLMS(model, s, k, m);
            //WSC23RQMCSamples.simulRepsLMS(model, s, k, m);
            // WSC23RQMCSamples.simulRepsLMS(model, s, k, m);
            // RQMCSamples.simulRepsDS(model, s, k, m);
         }
      }

      // model = new MC2(8);
      // RQMCSamples.simulRepsAllTypes (model, 8, 6, m);

      System.out
            .println("Total time for everything: " + timerTotal.format() + "\n================================== \n");
   }
}
