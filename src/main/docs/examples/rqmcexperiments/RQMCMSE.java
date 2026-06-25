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
 * @f$r@f$ with replacement. It collects the sample average @f$A_r@f$ and
 * sample median @f$M_r@f$, then writes result tables for their MSE values and
 * the ratio @f$\mathrm{MSE}[A_r] / \mathrm{MSE}[M_r]@f$.
 * Add comment about target =0
 */
public class RQMCMSE {

   private static final double TARGET = 0.0;

   /**
    * Computes the MSE from a tally of estimator values.
    *
    * @param tally tally containing the estimator values
    * @return estimated MSE relative to {@link #TARGET}
   */
   private static double mse(Tally tally) {
      double bias = tally.average() - TARGET;
      return tally.variance() + bias * bias;
   }

   /**
    * Reads simulation observations from a data file.
    *
    * @param filename input data file
    * @return array containing the simulation observations
    * @throws IllegalArgumentException if the file contains no observations
   */
   private static double[] readSimulationValues(String filename) {
      TallyStore simulations = new TallyStore();
      // Use fillFromFile(filename, skip) if the file contains comments.
      simulations.fillFromFile(filename);
      if (simulations.numberObs() == 0)
         throw new IllegalArgumentException("No simulation values found in " + filename);
      return simulations.getArray();
   }

   /**
    * Computes the bootstrap MSE of @f$A_r@f$ and @f$M_r@f$ from simulation
    * observations.
    *
    * @param values simulation observations
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream used for sampling
    * @return array containing the MSE of @f$A_r@f$ and @f$M_r@f$
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

      double arMse = mse(statAver);
      double mrMse = mse(statMed);
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
    * Computes and writes the three MSE tables for one function and dimension.
    * Missing input files are reported and skipped.
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

      StringBuilder arRows = new StringBuilder();
      StringBuilder mrRows = new StringBuilder();
      StringBuilder ratioRows = new StringBuilder();
      for (int i = 0; i < ks.length; i++) {
         int k = ks[i];

         // Use a new substream for this k.
         stream.resetNextSubstream();

         arRows.append(k).append("  ");
         mrRows.append(k).append("  ");
         ratioRows.append(k).append("  ");

         for (String method : methods) {
            File file = getSimulationFile(
                  dataDir, functionName, s, method, k, numObs);

            if (file == null) {
               arRows.append("Missing  ");
               mrRows.append("Missing  ");
               ratioRows.append("Missing  ");
               continue;
            }

            double[] values = readSimulationValues(file.getAbsolutePath());
            // Reuse the same substream for every method at this k.
            stream.resetStartSubstream();
            double[] result = computeMseArMr(values, m, r, stream);
            arRows.append(result[0]).append("  ");
            mrRows.append(result[1]).append("  ");
            ratioRows.append(result[0] / result[1]).append("  ");
         }
         arRows.append("\n");
         mrRows.append("\n");
         ratioRows.append("\n");
      }

      String arTable = header.toString() + arRows.toString();
      String mrTable = header.toString() + mrRows.toString();
      String ratioTable = header.toString() + ratioRows.toString();

      File arFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ar.res");
      File mrFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Mr.res");
      File ratioFile = new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ratio-ArOverMr.res");

      writeTable(arFile, arTable);
      writeTable(mrFile, mrTable);
      writeTable(ratioFile, ratioTable);

      System.out.println("MSE result tables written to:");
      System.out.println("  " + arFile.getAbsolutePath());
      System.out.println("  " + mrFile.getAbsolutePath());
      System.out.println("  " + ratioFile.getAbsolutePath());
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
