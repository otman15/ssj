package rqmcexperiments;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;

/**
 * Example that uses {@link MeanMedianMSE} to generate tables for the MSEs of
 * @f$A_r@f$, @f$M_r@f$, and their ratio. For each combination of model and
 * dimension @f$s@f$, three .res files are generated. Each file contains a
 * data table whose columns are for the RQMC methods, the rows are for values of
 * @f$k = \log_2 n@f$ where @f$n@f$ is the number of RQMC points,
 * and the entries are MSE or ratio values.
 */
public class MSESamo25 {

   /**
    * Configures and runs MSE experiments with the samo25 data.
    */
   public static void main(String[] args) {
   
      String dataDir = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String resultDir = "/home/otman/Documents/GitHub/Data/o-test/testNewC/";
      String[] modelTags = {"MC2"};
      String[] methods = {
            "Lat-RS", "Lat-RSB", "Lat-Rv", "Lat-Rpv",
            "Lat-RvRS", "Lat-RvRSB", "Lat-RpvRS", "Lat-RpvRSB",
            "Sob-RDS", "Sob-RDSB", "Sob-LMS", "Sob-LMS-RDS",
            "Sob-LMS-RDS-IRB", "Sob-NUS"
      };
      int[] dimensions = {4};
      int[] ks = {8, 10, 12, 14, 16};

      int numObs = 10000;   // Number of observations in the input data files.
      int numReps = 100000; // Number of replications to estimate the MSE_Mr.
      int r = 11;           // Sample size for the mean or median estimator.

      RandomStream stream = new LFSR258();
      for (String model : modelTags)
         for (int s : dimensions)
            MeanMedianMSE.computeFolderMSE(dataDir, resultDir, model, s,
                  methods, ks, numObs, numReps, r, stream);
   }
}
