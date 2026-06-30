package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.util.Misc;

/**
 * Suppose we have a large number of independent observations from a given
 * distribution and we want to estimate the true mean of the distribution by
 * picking a small number @f$r@f$ of observations at random from the large set,
 * with replacement, and taking either their average @f$A_r@f$ or their median
 * @f$M_r@f$ as an estimator of the mean.
 * We want to estimate the MSE of @f$A_r@f$ and @f$M_r@f$ and
 * the ratio @f$\mathrm{MSE}[A_r] / \mathrm{MSE}[M_r]@f$ to compare them.
 * The true mean is assumed to be known and given by the variable {@code exactMean}.
 * It is zero by default, but can be changed via {@link #setExactMean(double)}.
 * To estimate the MSEs, @f$\mathrm{MSE}[A_r]@f$ is computed by
 * {@link #mseAr(Tally, int)} directly from the empirical variance of the
 * stored observations, using
 * @f$\mathrm{Var}_{\mathrm{emp}}(X)/r + \mathrm{bias}^2@f$.
 * For @f$M_r@f$, we pick @f$r@f$ observations at random from the set,
 * compute @f$M_r@f$, repeat that @f$m@f$ times, and estimate the MSE from
 * these values. The method
 * {@link #bootstrapMrValues(TallyStore, int, int, RandomStream, TallyStore)}
 * generates the @f$M_r@f$ values, and {@link #mse(Tally)} computes their
 * MSE.
 *
 * This class is organized to do this not only for a single distribution (data
 * set), but for a large collection of data sets that are stored in data files
 * in exactly the same way and with the same naming convention as for the class
 * {@link HistCollectionLatex}. The top-level entry method is
 * {@link #computeFolderMSE}. It takes the input and output directories, a
 * model tag (name), the number @f$s@f$ of dimensions, a set of RQMC method
 * names, a set of values of @f$k@f$, the number of observations per input
 * file, the number @f$m@f$ of replications to estimate the @f$\mathrm{MSE}[M_r]@f$, the value of
 * @f$r@f$, and a random stream.
 * The input file names to search will be constructed based on this information.
 * After estimating the two MSEs for each method and each @f$k@f$, three
 * {@code .res} files will be created for this model and value of @f$s@f$,
 * each one containing a table whose columns correspond to RQMC methods, the
 * rows are for the values of @f$k@f$, and the entries are the MSE or ratio
 * values.
 */
public class MeanMedianMSE {
   /**
    * Exact mean used to compute the bias in the MSE. Default is 0.0.
    */
   private static double exactMean = 0.0;

   private MeanMedianMSE() {
      // Static utility class.
   }

   /**
    * Sets the {@code exactMean} used as the target value in the MSE computations.
    * The default value is 0.0. When computing results for several models, this
    * value should be updated before processing each model if the exact mean is
    * different.
    *
    * @param target exact mean for the current model
    */
   public static void setExactMean(double target) {
      exactMean = target;
   }

   /**
    * Returns the current value of {@code exactMean}.
    * @return current value of {@code exactMean}
    */
   public static double getExactMean() {
      return exactMean;
   }

   /**
    * Computes the MSE from a tally of observations.
    *
    * @param tally tally containing the observations
    * @return estimated MSE relative to {@code exactMean}
    */
   public static double mse(Tally tally) {
      double bias = tally.average() - exactMean;
      return tally.variance() * (tally.numberObs() - 1.0)
         / tally.numberObs() + bias * bias;
   }

   /**
    * Computes the MSE of the average of @f$r@f$ observations sampled with
    * replacement from the empirical distribution defined by the values in the
    * tally using @f$\mathrm{Var}_{\mathrm{emp}}(X)/r + \mathrm{bias}^2@f$.
    * This avoids using bootstrap for the average and computes the MSE directly
    * from the stored observations.
    *
    * @param tally tally containing the observations
    * @param r number of observations sampled and averaged
    * @return estimated MSE relative to {@code exactMean}
    */
   public static double mseAr(Tally tally, int r) {
      double bias = tally.average() - exactMean;
      return tally.variance() * (tally.numberObs() - 1.0)
         / tally.numberObs() / r + bias * bias;
   }

   /**
    * Reads observations from an input data file and stores them in a TallyStore.
    *
    * @param filename input data file
    * @return TallyStore containing the observations
    * @throws IllegalArgumentException if the file contains no observations
    */
   private static TallyStore readDataValues(String filename) {
      TallyStore tally = new TallyStore();
      // Use fillFromFile(filename, skip) if the file contains comments.
      tally.fillFromFile(filename);
      if (tally.numberObs() == 0)
         throw new IllegalArgumentException("No data values found in " + filename);
      return tally;
   }

   /**
    * Performs bootstrap simulations to obtain realizations of @f$M_r@f$,
    * and store them in {@code statMed}.
    *
    * **********  Better to re-use the same collector and return it, so we can also
    *             look at the average, kurtosis, etc. if desired.***********************
    *             
    *
    * @param tally input data observations
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream used for sampling
    * @param statMed TallyStore in which the @f$M_r@f$ values are stored
   */
   public static void bootstrapMrValues(TallyStore tally, int m, int r,
         RandomStream stream, TallyStore statMed) {
      int numSim = tally.numberObs();
      double[] values = tally.getArray();
      double[] sample = new double[r];
      statMed.init(); // Clear previous observations.
      for (int i = 0; i < m; i++) {
         for (int j = 0; j < r; j++) {
            double value = values[stream.nextInt(0, numSim - 1)];
            sample[j] = value;
         }
         statMed.add(Misc.getMedian(sample, r));
      }
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
    * Constructs the input data file name for one model, dimension, method, and
    * value of @f$k@f$.
    *
    * @param inputFolder directory containing the input data files
    * @param model model name used in the filename
    * @param s model dimension
    * @param method RQMC method name
    * @param k value of @f$k@f$
    * @param numObs number of observations contained in the filename
    * @return the data file, or {@code null} if it is missing
    */
   private static File getDataFile(String inputFolder, String model,
         int s, String method, int k, int numObs) {
      String fileName = model + "-" + s + "-" + method
            + "-" + k + "-" + numObs + ".dat";
      File file = new File(inputFolder, fileName);
      if (!file.isFile()) {
         System.out.println("Missing file: " + file.getAbsolutePath());
         return null;
      }
      return file;
   }

   /**
    * Computes and writes the three MSE tables for one model and dimension.
    * Missing input files are reported and skipped.
    *
    * @param inputFolder directory containing the input data files
    * @param outputFolder directory in which result tables are written
    * @param model model name used in input filenames
    * @param s model dimension
    * @param methods RQMC method names
    * @param ks values of @f$k@f$
    * @param numObs number of observations identified in each filename
    * @param m number of bootstrap samples
    * @param r size of each bootstrap sample
    * @param stream random stream used for sampling
    * @throws IllegalArgumentException if the result directory cannot be created
    */
   public static void computeFolderMSE(String inputFolder, String outputFolder,
         String model, int s,
         String[] methods, int[] ks, int numObs,
         int m, int r, RandomStream stream) {

      File resultFolder = new File(outputFolder);
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

      TallyStore statMed = new TallyStore("M_r");
      for (int i = 0; i < ks.length; i++) {
         int k = ks[i];
         // Use a new substream for this k.
         stream.resetNextSubstream();
         arRows.append(k).append("  ");
         mrRows.append(k).append("  ");
         ratioRows.append(k).append("  ");

         for (String method : methods) {
            File file = getDataFile(
                  inputFolder, model, s, method, k, numObs);
            if (file == null) {
               arRows.append("Missing  ");
               mrRows.append("Missing  ");
               ratioRows.append("Missing  ");
               continue;
            }
            TallyStore tally = readDataValues(file.getAbsolutePath());
            // Reuse the same substream for every method at this k.
            stream.resetStartSubstream();
            bootstrapMrValues(tally, m, r, stream, statMed);
            double arMse = mseAr(tally, r);
            double mrMse = mse(statMed);
            arRows.append(arMse).append("  ");
            mrRows.append(mrMse).append("  ");
            ratioRows.append(mrMse == 0.0 ? Double.NaN : arMse / mrMse).append("  ");
         }
         arRows.append("\n");
         mrRows.append("\n");
         ratioRows.append("\n");
      }
      String arTable = header.toString() + arRows.toString();
      String mrTable = header.toString() + mrRows.toString();
      String ratioTable = header.toString() + ratioRows.toString();
      File arFile = new File(resultFolder,
            model + "-" + s + "-" + r + "-MSE-Ar.res");
      File mrFile = new File(resultFolder,
            model + "-" + s + "-" + r + "-MSE-Mr.res");
      File ratioFile = new File(resultFolder,
            model + "-" + s + "-" + r + "-MSE-Ratio-ArOverMr.res");
      writeTable(arFile, arTable);
      writeTable(mrFile, mrTable);
      writeTable(ratioFile, ratioTable);

      System.out.println("MSE result tables written to:");
      System.out.println("  " + arFile.getAbsolutePath());
      System.out.println("  " + mrFile.getAbsolutePath());
      System.out.println("  " + ratioFile.getAbsolutePath());
   }

}
