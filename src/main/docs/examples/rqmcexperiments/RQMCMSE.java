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
 * Estimates the mean square error (MSE) of two estimators from stored RQMC
 * simulation results. For each configured function, dimension, method, and
 * value of @f$k@f$, the experiment draws @f$m@f$ bootstrap samples of size
 * @f$r@f$ with replacement. For each bootstrap sample, it computes the sample
 * average @f$A_r@f$ and the sample median @f$M_r@f$, then estimates their MSE
 * values relative to the current target, which is 0.
 *
 * The MSE of @f$A_r@f$ is also computed directly from the stored simulation
 * values. Since @f$A_r@f$ is a sample average, its variance is
 * @f$\mathrm{Var}(X)/r@f$, so the direct computation uses
 * @f$\mathrm{Var}(X)/r + \mathrm{bias}^2@f$ and avoids the extra Monte Carlo
 * noise from bootstrap replications.
 *
 * The experiment writes four result tables: bootstrap
 * @f$\mathrm{MSE}[A_r]@f$, direct @f$\mathrm{MSE}[A_r]@f$, bootstrap
 * @f$\mathrm{MSE}[M_r]@f$, and the ratio of direct
 * @f$\mathrm{MSE}[A_r]@f$ to bootstrap @f$\mathrm{MSE}[M_r]@f$.
 */
public class RQMCMSE {

   /**
    * Computes the empirical MSE of the observations summarized by a tally.
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
    * Computes direct @f$\mathrm{MSE}[A_r]@f$ from the empirical distribution
    * defined by the stored simulation values. Here, "direct" means that instead
    * of bootstrapping @f$A_r@f$, the MSE is computed using
    * @f$\mathrm{Var}(tally) / r@f$. @f$A_r@f$ is the average of @f$r@f$
    * observations sampled with replacement from these stored values.
    *
    * @param tally tally containing the stored simulation values, usually with
    *        more observations than r
    * @param target exact target value
    * @param r number of observations averaged by @f$A_r@f$
    * @return direct MSE of @f$A_r@f$ relative to @f$target@f$
   */
   private static double directMse(Tally tally, double target, int r) {
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
    * Computes the bootstrap MSE of @f$A_r@f$ and @f$M_r@f$. For each
    * bootstrap sample, @f$A_r@f$ is the sample average and @f$M_r@f$ is the
    * sample median.
    *
    * @param values simulation observations
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream used for sampling
    * @return array containing bootstrap @f$\mathrm{MSE}[A_r]@f$ and
    *         bootstrap @f$\mathrm{MSE}[M_r]@f$
   */
   private static double[] computeMseArMr(double[] values, int m, int r, RandomStream stream) {
      int numSim = values.length;
      double[] sample = new double[r];
      Tally statAver = new Tally("A_r");
      Tally statMed = new Tally("M_r");

      for (int i = 0; i < m; i++) {
         double sum = 0.0;
         for (int j = 0; j < r; j++) {
            double value = values[stream.nextInt(0, numSim - 1)];
            sample[j] = value;
            sum += value;
         }

         statAver.add(sum / r);
         statMed.add(Misc.getMedian(sample, r));
      }

      double arMse = mse(statAver, 0);
      double mrMse = mse(statMed, 0);
      return new double[] {arMse, mrMse};
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
    * Computes and writes the four MSE result tables for one function and
    * dimension: bootstrap @f$\mathrm{MSE}[A_r]@f$, direct
    * @f$\mathrm{MSE}[A_r]@f$, bootstrap @f$\mathrm{MSE}[M_r]@f$, and the
    * ratio of direct @f$\mathrm{MSE}[A_r]@f$ to bootstrap
    * @f$\mathrm{MSE}[M_r]@f$. Missing input files are reported and skipped.
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

      StringBuilder directArMseRows = new StringBuilder();
      StringBuilder arMseRows = new StringBuilder();
      StringBuilder mrMseRows = new StringBuilder();
      StringBuilder ratioMseRows = new StringBuilder();
      for (int i = 0; i < ks.length; i++) {
         int k = ks[i];

         // Use a new substream for this k.
         stream.resetNextSubstream();

         directArMseRows.append(k).append("  ");
         arMseRows.append(k).append("  ");
         mrMseRows.append(k).append("  ");
         ratioMseRows.append(k).append("  ");

         for (String method : methods) {
            File file = getSimulationFile(
                  dataDir, functionName, s, method, k, numObs);

            if (file == null) {
               directArMseRows.append("Missing  ");
               arMseRows.append("Missing  ");
               mrMseRows.append("Missing  ");
               ratioMseRows.append("Missing  ");
               continue;
            }

            TallyStore sim = readSimulationValues(file.getAbsolutePath());
            double[] values = sim.getArray();
            // Reuse the same substream for every method at this k.
            stream.resetStartSubstream();
            double directArMse = directMse(sim, 0, r);
            double[] result = computeMseArMr(values, m, r, stream);
            directArMseRows.append(directArMse).append("  ");
            arMseRows.append(result[0]).append("  ");
            mrMseRows.append(result[1]).append("  ");
            ratioMseRows.append(directArMse / result[1]).append("  ");
         }
         directArMseRows.append("\n");
         arMseRows.append("\n");
         mrMseRows.append("\n");
         ratioMseRows.append("\n");
      }

      String directArMseTable = header.toString() + directArMseRows.toString();
      String arMseTable = header.toString() + arMseRows.toString();
      String mrMseTable = header.toString() + mrMseRows.toString();
      String ratioMseTable = header.toString() + ratioMseRows.toString();

      File directArMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ar-Direct.res");
      File arMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ar.res");
      File mrMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Mr.res");
      File ratioMseFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ratio-Ar-Mr.res");

      writeTable(directArMseFile, directArMseTable);
      writeTable(arMseFile, arMseTable);
      writeTable(mrMseFile, mrMseTable);
      writeTable(ratioMseFile, ratioMseTable);

      System.out.println("MSE result tables written to:");
      System.out.println("  " + directArMseFile.getAbsolutePath());
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
      int numObs = 10000;

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
