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
public class Samo25SamplesMain extends RQMCExperiment64 {

   public static void main(String[] args) throws IOException {
      // WSC26RQMCSamples64.directory = "C:/Users/Lecuyer/Dropbox/wsc26/data64/"; // Retained for 64 bits
      // WSC23MoreSamples.directory = "C:/Users/Lecuyer/Dropbox/wsc23/test/";        // For testing
      // WSC23MoreSamples.directory = "C:/Users/Lecuyer/Dropbox/samo25/test/";        // For Samo paper
      Samo25Samples.directory = "C:/Users/Lecuyer/Dropbox/samo25/datapl/";        // For Samo paper

      int m = 10000; // Number of RQMC randomizations.
      int million = 1000000; // Number of RQMC randomizations.
      // int m = 10; // Number of RQMC randomizations.
      int mink = 8;
      int maxk = 16;
      
      System.out.println("Generating vector for lattices: " + Arrays.toString(Samo25Samples.a18));
      Chrono timerTotal = new Chrono();
      for (int s = 1; s <= 32; s *= 2) {
      // for (int s = 2; s <= 32; s *= 2) {
         // System.out.println("WSC23MoreReps, run with s = " + s);      
         // Uncomment the models you want to run. ***
         //WSC23MoreSamples.simulRepsAllSizes(new SmoothPerBeta52(s, 1.0), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new SmoothPerB4(s, 1.0), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new SumUeU(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new MC2(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new Polynomial(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new Oscillatory(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new Gaussian(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new SmoothGauss(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new RidgeJohnsonSU(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new PieceLinGauss(s), s, mink, maxk, m);
         Samo25Samples.simulRepsAllSizes(new IndSumNormal(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new IndBox(s), s, mink, maxk, m);
         // WSC23MoreSamples.simulRepsLatRv(new SumUeU(s), s, mink, m);
         // WSC23MoreSamples.simulRepsSelectedTypes(new SumUeU(s), s, 10, m);
         
         //  RQMCSamples23.redirectToFile((model.getTag() + "-" + s));
         // WSC26RQMCSamples64.simulAllSizes(model, s, 8, 18, 1000);
         
         int m = 1000; // Number of RQMC randomizations.
         // int m = 10; // Number of RQMC randomizations.
         int mink = 8;
         int maxk = 12;
       //  WSC23MoreSamples.simulRepsAllSizes(new SmoothPerB4(s, 1.0), s, mink, maxk, m);
      //   WSC23MoreSamples.simulRepsAllSizes(new SumUeU(s), s, mink, maxk, m);
           WSC23MoreSamples.simulRepsAllSizes(new MC2(s), s, mink, maxk, m);
      //   WSC23MoreSamples.simulRepsAllSizes(new Polynomial(s), s, mink, maxk, m);
      //  WSC23MoreSamples.simulRepsAllSizes(new Oscillatory(s), s, mink, maxk, m);
//         WSC23MoreSamples.simulRepsAllSizes(new GenzGaussian(s), s, mink, maxk, m);
//         WSC23MoreSamples.simulRepsAllSizes(new SmoothGauss(s), s, mink, maxk, m);
//         WSC23MoreSamples.simulRepsAllSizes(new PieceLinGauss(s), s, mink, maxk, m);
         //WSC23MoreSamples.simulRepsAllSizes(new IndSumNormal(s), s, mink, maxk, m);
              
      }
      System.out.println("Total time for everything: " + timerTotal.format() +
            "\n=========================================== \n");
   }
}
