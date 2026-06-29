package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.util.Misc;

/**
 * Estimates the MSE of @f$A_r@f$, the average of @f$r@f$ observations sampled
 * with replacement from stored RQMC simulation values, and @f$M_r@f$, the
 * corresponding sample median, for each configured function, dimension,
 * method, and value of @f$k@f$.
 *
 * For @f$M_r@f$, the experiment draws @f$m@f$ bootstrap samples of size
 * @f$r@f$ with replacement from the stored simulation values. For each
 * bootstrap sample, it computes the sample median @f$M_r@f$, then estimates
 * @f$\mathrm{MSE}[M_r]@f$ with respect to the current target, which is 0.
 *
 * For @f$A_r@f$, the experiment uses the empirical variance of the stored
 * simulation values instead of bootstrap samples. Since @f$A_r@f$ is an
 * average, @f$\mathrm{MSE}[A_r]@f$ is computed as
 * @f$\mathrm{Var}_{\mathrm{emp}}(X)/r + \mathrm{bias}^2@f$, where the current
 * target is 0. This avoids the extra Monte Carlo noise from bootstrapping
 * @f$A_r@f$.
 *
 * Since {@code tally.variance()} uses the sample variance with denominator
 * @f$(n - 1)@f$, we convert it to the empirical variance with denominator
 * @f$n@f$ by multiplying by @f$(n - 1)/n@f$.
 *
 * The experiment writes three result tables: @f$\mathrm{MSE}[A_r]@f$,
 * @f$\mathrm{MSE}[M_r]@f$, and
 * @f$\mathrm{MSE}[A_r] / \mathrm{MSE}[M_r]@f$.
 */
public class RQMCMSE {

   /**
    * Computes the empirical MSE of all observations summarized by a tally.
    *
    * @param tally tally summarizing the observations
    * @param target exact target value
    * @return empirical MSE relative to @f$target@f$
    */
   private static double mse(Tally tally, double target) {
      double bias = tally.average() - target;
      return tally.variance() * (tally.numberObs() - 1.0)
          / tally.numberObs() + bias * bias;
   }

   /**
    * Computes the empirical MSE of the average of @f$r@f$ observations sampled
    * with replacement from the empirical distribution defined by the values
    * stored in the tally.
    *
    * @param tally tally containing the stored simulation values that define the
    *        empirical distribution
    * @param target exact target value
    * @param r number of observations sampled and averaged
    * @return empirical MSE of the average relative to @f$target@f$
    */
   private static double mse(Tally tally, double target, int r) {
      double bias = tally.average() - target;
      return tally.variance() * (tally.numberObs() - 1.0)
          / tally.numberObs() / r + bias * bias;
   }

   /**
    * Reads simulation observations from a data file.
    *
    * @param filename input data file
    * @return TallyStore containing the simulation observations
    * @throws IllegalArgumentException if the file contains no observations
    */
   private static TallyStore readSimulationValues(String filename) {
      TallyStore simulations = new TallyStore();
      // Use fillFromFile(filename, skip) if the file contains comments.
      simulations.fillFromFile(filename);
      if (simulations.numberObs() == 0)
         throw new IllegalArgumentException("No simulation values found in " + filename);
      return simulations;
   }

   /**
    * Computes bootstrap @f$\mathrm{MSE}[M_r]@f$. For each bootstrap sample,
    * @f$M_r@f$ is the sample median of @f$r@f$ observations sampled with
    * replacement from the stored simulation values.
    *
    * @param values simulation observations
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream used for sampling
    * @return bootstrap @f$\mathrm{MSE}[M_r]@f$
    */
   private static double computeMseMr(double[] values, int m, int r, RandomStream stream) {
      int numSim = values.length;
      double[] sample = new double[r];
      Tally statMed = new Tally("M_r");

      for (int i = 0; i < m; i++) {
         for (int j = 0; j < r; j++) {
            double value = values[stream.nextInt(0, numSim - 1)];
            sample[j] = value;
         }

         statMed.add(Misc.getMedian(sample, r));
      }

      return mse(statMed, 0);
   }

   /**
    * Writes a formatted result table to a file.
    *
    * @param file output file
    * @param table formatted table
    * @throws RuntimeException if the file cannot be written
    */
   private static void writeTable(File file, String table) {
      try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
         out.print(table);
      } catch (IOException e) {
         throw new RuntimeException("Could not write " + file.getAbsolutePath(), e);
      }
   }

   /**
    * Returns the simulation file for one function, dimension, method, and
    * value of @f$k@f$.
    *
    * @param dataDir directory containing the simulation files
    * @param functionName function name used in the filename
    * @param s function dimension
    * @param method RQMC method name
    * @param k value of @f$k@f$
    * @param numObs number of observations identified in the filename
    * @return the simulation file, or `null` if it is missing
    */
   private static File getSimulationFile(String dataDir, String functionName,
         int s, String method, int k, int numObs) {
      String fileName = functionName + "-" + s + "-" + method
            + "-" + k + "-" + numObs + ".dat";
      File file = new File(dataDir, fileName);
      if (!file.isFile()) {
         System.out.println("Missing file: " + file.getAbsolutePath());
         return null;
      }
      return file;
   }

   /**
    * Computes and writes the three MSE result tables for one function and
    * dimension: @f$\mathrm{MSE}[A_r]@f$, @f$\mathrm{MSE}[M_r]@f$, and
    * @f$\mathrm{MSE}[A_r] / \mathrm{MSE}[M_r]@f$. Missing input files are
    * reported and skipped.
    *
    * @param dataDir directory containing the simulation files
    * @param resultDir directory in which result tables are written
    * @param functionName function name used in input filenames
    * @param s function dimension
    * @param methods RQMC method names
    * @param ks values of @f$k@f$
    * @param numObs number of observations identified in each filename
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream used for sampling
    * @throws IllegalArgumentException if the result directory cannot be created
    */
   private static void computeFolderMse(String dataDir, String resultDir,
         String functionName, int s,
         String[] methods, int[] ks, int numObs,
         int m, int r, RandomStream stream) {

      File resultFolder = new File(resultDir);
      if (!resultFolder.exists() && !resultFolder.mkdirs())
         throw new IllegalArgumentException(
               "Could not create result folder " + resultFolder.getAbsolutePath());

      StringBuilder header = new StringBuilder(" k ");
      for (String method : methods)
         header.append(" ").append(method).append(" ");
      header.append("\n");

      StringBuilder arMseRows = new StringBuilder();
      StringBuilder mrMseRows = new StringBuilder();
      StringBuilder ratioMseRows = new StringBuilder();
      for (int i = 0; i < ks.length; i++) {
         int k = ks[i];

         // Use a new substream for this k.
         stream.resetNextSubstream();

         arMseRows.append(k).append("  ");
         mrMseRows.append(k).append("  ");
         ratioMseRows.append(k).append("  ");

         for (String method : methods) {
            File file = getSimulationFile(
                  dataDir, functionName, s, method, k, numObs);

            if (file == null) {
               arMseRows.append("Missing  ");
               mrMseRows.append("Missing  ");
               ratioMseRows.append("Missing  ");
               continue;
            }

            TallyStore sim = readSimulationValues(file.getAbsolutePath());
            double[] values = sim.getArray();
            // Reuse the same substream for every method at this k.
            stream.resetStartSubstream();
            double mseAr = mse(sim, 0, r);
            double mseMr = computeMseMr(values, m, r, stream);
            arMseRows.append(mseAr).append("  ");
            mrMseRows.append(mseMr).append("  ");
            ratioMseRows.append(mseMr == 0.0 ? Double.NaN : mseAr / mseMr).append("  ");
         }

         arMseRows.append("\n");
         mrMseRows.append("\n");
         ratioMseRows.append("\n");
      }

      String arMseTable = header.toString() + arMseRows.toString();
      String mrMseTable = header.toString() + mrMseRows.toString();
      String ratioMseTable = header.toString() + ratioMseRows.toString();

      File arMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ar.res");
      File mrMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Mr.res");
      File ratioMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ratio.res");

      writeTable(arMseFile, arMseTable);
      writeTable(mrMseFile, mrMseTable);
      writeTable(ratioMseFile, ratioMseTable);

      System.out.println("MSE result tables written to:");
      System.out.println("  " + arMseFile.getAbsolutePath());
      System.out.println("  " + mrMseFile.getAbsolutePath());
      System.out.println("  " + ratioMseFile.getAbsolutePath());
   }

   /**
    * Configures and runs the MSE experiments.
    */
   public static void main(String[] args) {
      // Configure these paths for the local data and result directories.
      String dataDir = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String resultDir = "/home/otman/Documents/GitHub/Data/o-test/testNewC/";

      int m = 100000;
      int r = 11;
      int numObs = 10000;// Must match the number of observations in each stored RQMC file.

      String[] functionNames = {"MC2"};
      int[] dimensions = {4};
      int[] ks = {8, 10, 12, 14, 16};
      String[] methods = {
            "Lat-RS", "Lat-RSB", "Lat-Rv", "Lat-Rpv",
            "Lat-RvRS", "Lat-RvRSB", "Lat-RpvRS", "Lat-RpvRSB",
            "Sob-RDS", "Sob-RDSB", "Sob-LMS", "Sob-LMS-RDS",
            "Sob-LMS-RDS-IRB", "Sob-NUS"
      };

      RandomStream stream = new LFSR258();
      for (String functionName : functionNames)
         for (int s : dimensions)
            computeFolderMse(dataDir, resultDir, functionName, s,
                  methods, ks, numObs, m, r, stream);
   }
}
