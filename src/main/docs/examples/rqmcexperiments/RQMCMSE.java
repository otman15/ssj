package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;

/**
 * Estimates the mean square error (MSE) of two estimators from stored RQMC
 * simulation results. For each configured function, dimension, method, and
 * value of @f$k@f$, the experiment draws @f$m@f$ bootstrap samples of size
 * @f$r@f$ with replacement. It collects the sample average @f$A_r@f$ and
 * sample median @f$M_r@f$, then writes their MSE values to result tables.
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
   //   return tally.sumSquares() / tally.numberObs() + bias * bias;
      return tally.variance() + bias * bias;
   }

   /**
    * Computes the bootstrap MSE of @f$A_r@f$ and @f$M_r@f$ from one data file.
    *
    * @param filename input data file
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream; its current substream is restarted before sampling
    * @return array containing the MSE of @f$A_r@f$ and @f$M_r@f$
    * @throws IllegalArgumentException if the file contains no observations
    */
   private static double[] computeMseFromFile(String filename, int m, int r, RandomStream stream) {
      // Restart the current substream before generating the bootstrap samples.
      stream.resetStartSubstream();
      TallyStore simulations = new TallyStore();
      simulations.fillFromFile(filename); //use fillFromFile(filename, skip) if file contains comments
      int numSim = simulations.numberObs();
      if (numSim == 0)
         throw new IllegalArgumentException("No simulation values found in " + filename);

      double[] values = simulations.getArray();
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
         Arrays.sort(sample); 
         if ((r & 1) == 0) 
            statMed.add((sample[r / 2 - 1] + sample[r / 2]) / 2.0);
         else
            statMed.add(sample[r / 2]);
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
    * Computes and writes the MSE tables for one function and dimension.
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
      for (int i = 0; i < ks.length; i++) {
         int k = ks[i];

         // New bootstrap sequence for this k. Each method will restart this substream.
         stream.resetNextSubstream();

         arRows.append(k).append("  ");
         mrRows.append(k).append("  ");

         for (String method : methods) {
            String fileName = functionName + "-" + s + "-" + method
                  + "-" + k + "-" + numObs + ".dat";
            File file = new File(dataDir, fileName);

            if (!file.isFile()) {
               arRows.append("Missing  ");
               mrRows.append("Missing  ");
               System.out.println("Missing file: " + file.getAbsolutePath());
               continue;
            }

            double[] result = computeMseFromFile(file.getAbsolutePath(), m, r, stream);
            arRows.append(result[0]).append("  ");
            mrRows.append(result[1]).append("  ");
         }
         arRows.append("\n");
         mrRows.append("\n");
      }

      String arTable = header.toString() + arRows.toString();
      String mrTable = header.toString() + mrRows.toString();

      System.out.println();
      System.out.println("A_r MSE - " + functionName + ", s=" + s);
      System.out.println(arTable);
      System.out.println();
      System.out.println("M_r MSE - " + functionName + ", s=" + s);
      System.out.println(mrTable);

      writeTable(new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Ar.res"), arTable);
      writeTable(new File(resultFolder,
            functionName + "-" + s + "-" + r + "-MSE-Mr.res"), mrTable);
   }

   /**
    * Configures and runs the MSE experiments.
    */
   public static void main(String[] args) {
      String dataDir = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String resultDir = "/home/otman/Documents/GitHub/Data/o-test/testNewC/";
      
      int m = 100000;
      int r = 11;
      int numObs = 10000;

      String[] functionNames = {"MC2"};
      int[] dimensions = {4};
      int[] ks = {8, 10, 12, 14, 16};
      String[] methods = { "Lat-RS", "Lat-RSB",  "Lat-Rv",  "Lat-Rpv",  
                           "Lat-RvRS",  "Lat-RvRSB",  "Lat-RpvRS",  "Lat-RpvRSB",     
                           "Sob-RDS",  "Sob-RDSB",  "Sob-LMS",  "Sob-LMS-RDS", "Sob-NUS"
      };
      

      RandomStream stream = new LFSR258();
      for (String functionName : functionNames)
         for (int s : dimensions)
            computeFolderMse(dataDir, resultDir, functionName, s,
                  methods, ks, numObs, m, r, stream);
   }
}
