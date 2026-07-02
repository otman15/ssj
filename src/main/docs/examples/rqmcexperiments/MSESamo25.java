package rqmcexperiments;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Chrono;

/**
 * Example that uses {@link MeanMedianMSE} to generate tables for the MSEs of
 * @f$A_r@f$, @f$M_r@f$, and their ratio. For each combination of model and
 * dimension @f$s@f$, three {@code .res} files are generated. These files contain
 * tables whose columns are the RQMC methods, whose rows are the values of
 * @f$k = \log_2 n@f$, where @f$n@f$ is the number of RQMC points, and whose
 * entries are MSE or ratio values. One {@code .csv} file is also generated for
 * each model and value of @f$r@f$; it contains the moments and MSE estimates,
 * with one row for each existing input file.
 */
public class MSESamo25 {

   /**
    * Configures and runs MSE experiments with the samo25 data.
    */
   public static void main(String[] args) {

      String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String outputFolder = "/home/otman/Documents/GitHub/Data/o-test/testNewC/";
      // String[] modelTags = {"MC2"};
      String[] modelTags = new String[] {
            "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
            "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
      };
      int[] dims = {2, 4, 8, 16, 32};
      String[] methods = {
            "Lat-RS", "Lat-RSB", "Lat-Rv", "Lat-Rpv",
            "Lat-RvRS", "Lat-RvRSB", "Lat-RpvRS", "Lat-RpvRSB",
            "Sob-RDS", "Sob-RDSB", "Sob-LMS", "Sob-LMS-RDS",
            "Sob-LMS-RDS-IRB", "Sob-NUS"
      };
      int[] ks = {8, 10, 12, 14, 16};
      int r = 11;           // Number of observations averaged or used in each bootstrap median sample.

      int numObs = 10000;   // Number of observations in the input data files.
      int numReps = 10000;  // Number of bootstrap subsamples to estimate the MSE[M_r].

      RandomStream stream = new LFSR258();
      Chrono timerTotal = new Chrono();
      for (String model : modelTags)
         MeanMedianMSE.estimateMSEOneModel(inputFolder, outputFolder, model, dims,
               methods, ks, numObs, numReps, r, stream);
      System.out.println("\nTotal time for everything: " + timerTotal.format() +
            "\n=========================================== \n");
   }
}
