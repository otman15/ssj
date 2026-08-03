package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.util.Misc;

/**
 * Suppose we have a large number of independent observations from a given
 * distribution and we want to estimate the true mean of the distribution by
 * picking a small number @f$r@f$ of observations at random from the large set,
 * with replacement, and taking either their average @f$A_r@f$ or their
 * median @f$M_r@f$ as an estimator of the mean.
 *
 * We want to estimate the MSE of @f$A_r@f$ and @f$M_r@f$ and the
 * ratio @f$\mathrm{MSE}[A_r] / \mathrm{MSE}[M_r]@f$ to compare them. The true
 * mean is assumed to be known and given by the variable {@code exactMean}. It
 * is zero by default, but can be changed via {@link #setExactMean(double)}.
 *
 * To estimate the MSEs, @f$\mathrm{MSE}[A_r]@f$ is computed by
 * {@link #mseAr(Tally, int)} directly from the empirical variance of the stored
 * observations, using @f$\mathrm{Var}_{\mathrm{emp}}(X)/r + \mathrm{bias}^2@f$.
 * For @f$M_r@f$, we pick @f$r@f$ observations at random from the set, 
 * compute @f$M_r@f$, repeat that @f$m@f$ times, and estimate the MSE from these
 * values. The method
 * {@link #bootstrapMrValues(TallyStore, int, int, RandomStream, TallyStore)}
 * generates the @f$M_r@f$ values, and {@link #mse(Tally)} computes their MSE.
 *
 * This class is organized to do this not only for a single distribution
 * (dataset), but for a large collection of data sets that are stored in data
 * files in exactly the same way and with the same naming convention as for the
 * class {@link HistCollectionLatex} and also for several values of @f$r@f$ by
 * calling {@link #estimateMSEOneModel} once for each value of @f$r@f$.
 *
 * The top-level entry method is {@link #estimateMSEOneModel}. It takes the
 * input and output directories, a model tag (name), a set of dimensions, a set
 * of RQMC method names, a set of values of @f$k@f$, the number of observations
 * per input file, the number @f$m@f$ of replications to estimate
 * the @f$\mathrm{MSE}[M_r]@f$, the value of @f$r@f$, and a random stream. The
 * input file names to search will be constructed based on this information.
 * After estimating the two MSEs for each method and each @f$k@f$, three
 * {@code .res} files will be created for this model and value of @f$s@f$, each
 * one containing a table whose columns correspond to RQMC methods, the rows are
 * for the values of @f$k@f$, and the entries are the MSE or ratio values. The
 * method also produces a {@code .csv} file that gives the moments and MSE
 * estimates for each input file, one case per row.
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
    *
    * @return current value of {@code exactMean}
    */
   public static double getExactMean() {
      return exactMean;
   }

   /**
    * Computes the MSE of the average of @f$r@f$ observations sampled with
    * replacement from the empirical distribution defined by the values in the
    * tally using @f$\mathrm{Var}_{\mathrm{emp}}(X)/r + \mathrm{bias}^2@f$. This
    * avoids using bootstrap for the average and computes the MSE directly from the
    * stored observations.
    *
    * @param tally tally containing the observations
    * @param r     number of observations sampled and averaged
    * @return estimated MSE relative to {@code exactMean}
    */
   public static double mseAr(Tally tally, int r) {
      double bias = tally.average() - exactMean;
      // double mse = (tally.variance() * (tally.numberObs() - 1.0) / tally.numberObs()) / r + bias * bias;
      double mse = (tally.variance() * (tally.numberObs() - 1.0) / tally.numberObs()) / r;
      // System.out.println("mseAr, exactMean =" + exactMean + ", bias = " + bias + ", MSE = " + mse);
      return mse;
   }

   /**
    * Reads observations from an input data file and stores them in a TallyStore.
    *
    * @param filename input data file
    * @return TallyStore containing the observations
    * @throws IllegalArgumentException if the file contains no observations
    */
   public static TallyStore readDataValues(String filename) {
      TallyStore tally = new TallyStore();
      // Use fillFromFile(filename, skip) if the file contains comments.
      tally.fillFromFile(filename);
      if (tally.numberObs() == 0)
         throw new IllegalArgumentException("No data values found in " + filename);
      return tally;
   }

   /**
    * Performs bootstrap simulations to obtain realizations of @f$M_r@f$, and
    * stores them in {@code statMed}.
    *
    * @param tally   input data observations
    * @param m       number of bootstrap samples
    * @param r       size of each bootstrap sample
    * @param stream  random stream used for sampling
    * @param statMed TallyStore in which the @f$M_r@f$ values are stored
    */
   public static void bootstrapMrValues(TallyStore tally, int m, int r, RandomStream stream, 
         TallyStore statMed) {
      int numSim = tally.numberObs();
      double[] values = tally.getArray();
      double[] sample = new double[r];
      statMed.init(); // Clear previous observations.
      for (int i = 0; i < m; i++) {
         for (int j = 0; j < r; j++) {
            sample[j] = values[stream.nextInt(0, numSim - 1)];
         }
         statMed.add(Misc.getMedian(sample, r));
      }
   }


   /**
    * Performs bootstrap simulations to obtain realizations of both @f$A_r@f$ and @f$M_r@f$, and
    * stores them in {@code statAver} and  {@code statMed}.
    *
    * @param tally   input data observations
    * @param m       number of bootstrap samples
    * @param r       size of each bootstrap sample
    * @param stream  random stream used for sampling
    * @param statAver TallyStore in which the @f$A_r@f$ values are stored
    * @param statMed TallyStore in which the @f$M_r@f$ values are stored
    */
   public static void bootstrapArMrValues(TallyStore tally, int m, int r, RandomStream stream, 
         TallyStore statAver, TallyStore statMed) {
      int numSim = tally.numberObs();
      double[] values = tally.getArray();
      double[] sample = new double[r];
      statAver.init();  statMed.init(); // Clear previous observations.
      for (int i = 0; i < m; i++) {
         for (int j = 0; j < r; j++) {
            sample[j] = values[stream.nextInt(0, numSim - 1)];
         }
         statAver.add(Misc.getAverage(sample, r));
         statMed.add(Misc.getMedian(sample, r));
      }
   }

   /**
    * Writes a formatted result table to a file.
    *
    * @param file  output file
    * @param table formatted table
    * @throws RuntimeException if the file cannot be written
    */
   public static void writeTable(File file, String table) {
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
    * @param model       model name used in the filename
    * @param s           model dimension
    * @param method      RQMC method name
    * @param k           value of @f$k@f$
    * @param numObs      number of observations contained in the filename
    * @return the data file, or {@code null} if it is missing
    */
   public static File getDataFile(String inputFolder, String model, int s, String method, int k, int numObs) {
      String fileName = model + "-" + s + "-" + method + "-" + k + "-" + numObs + ".dat";
      File file = new File(inputFolder, fileName);
      if (!file.isFile()) {
         System.out.println("Missing file: " + file.getAbsolutePath());
         return null;
      }
      return file;
   }
   

   /**
    * Computes the moments and the MSE estimates for one model and one value
    * of @f$r@f$. For each dimension @f$s@f$, writes three {@code .res} tables with
    * one row for each value of @f$k@f$ and one column for each RQMC method. It
    * also writes one {@code .csv} file containing the moments and MSE estimates,
    * with one row for each existing input file. Missing input files are reported
    * and marked as {@code Missing} in the {@code .res} tables.
    *
    * @param inputFolder  directory containing the input data files
    * @param outputFolder directory in which result files are written
    * @param model        model name used in input filenames
    * @param dims         set of model dimensions
    * @param methods      RQMC method names
    * @param ks           values of @f$k@f$
    * @param numObs       number of observations identified in each filename
    * @param m            number of bootstrap samples
    * @param r            sample size used for @f$A_r@f$ and for each bootstrap
    *                     sample of @f$M_r@f$
    * @param stream       random stream used for sampling
    * @throws IllegalArgumentException if the result directory cannot be created
    */
   public static void estimateMSEOneModel(String inputFolder, String outputFolder, String model, int[] dims,
         String[] methods, int[] ks, int numObs, int m, int r, RandomStream stream) {
      System.out.println("Running estimateMSEOneModel.... ");
      File resultFolder = new File(outputFolder);
      if (!resultFolder.exists() && !resultFolder.mkdirs())
         throw new IllegalArgumentException("Could not create result folder " + resultFolder.getAbsolutePath());

      // Builds the first row of the .csv file and of the .res files.
      StringBuilder csvHead = new StringBuilder("model,s,method,k,mean,variance,skewness,ekurtosis");
      csvHead.append(",MSEAr,MSEMr,MSEratio\n");
      StringBuilder resHeader = new StringBuilder(" k ");
      for (String method : methods)
         resHeader.append(" ").append(method).append(" ");
      resHeader.append("\n");

      StringBuilder csvRows = new StringBuilder();
      StringBuilder arRows = new StringBuilder();
      StringBuilder mrRows = new StringBuilder();
      StringBuilder ratioRows = new StringBuilder();
      TallyStore statMed = new TallyStore("M_r");

      for (int s : dims) {
         statMed.init();
         for (int k : ks) {
            // Use a new substream for this k.
            // stream.resetNextSubstream();
            arRows.append(k).append("  ");
            mrRows.append(k).append("  ");
            ratioRows.append(k).append("  ");

            for (String method : methods) {
               File file = getDataFile(inputFolder, model, s, method, k, numObs);
               if (file == null) {
                  arRows.append("Missing  ");
                  mrRows.append("Missing  ");
                  ratioRows.append("Missing  ");
                  continue;
               }
               TallyStore tally = readDataValues(file.getAbsolutePath());

               // We could use CRNs across the methods, but not sure if it makes sense,
               // because the input data for different methods are different and independent.
               // stream.resetStartSubstream();
               bootstrapMrValues(tally, m, r, stream, statMed);
               double arMse = mseAr(tally, r);
               double mrMse = statMed.mseKnownMean(exactMean);
               double ratio = mrMse == 0.0 ? Double.NaN : arMse / mrMse;

               arRows.append(arMse).append("  ");
               mrRows.append(mrMse).append("  ");
               ratioRows.append(ratio).append("  ");

               csvRows.append(model).append(",").append(s).append(",").append(method).append(",").append(k).append(",")
                     .append(tally.average()).append(",").append(tally.variance()).append(",").append(tally.skewness())
                     .append(",").append(tally.kurtosis()).append(",").append(arMse).append(",").append(mrMse)
                     .append(",").append(ratio).append("\n");
            }
            arRows.append("\n");
            mrRows.append("\n");
            ratioRows.append("\n");
         }
         String arTable = resHeader.toString() + arRows.toString();
         String mrTable = resHeader.toString() + mrRows.toString();
         String ratioTable = resHeader.toString() + ratioRows.toString();
         File arFile = new File(resultFolder, model + "-" + s + "-r" + r + "-MSE-Ar.res");
         File mrFile = new File(resultFolder, model + "-" + s + "-r" + r + "-MSE-Mr.res");
         File ratioFile = new File(resultFolder, model + "-" + s + "-r" + r + "-MSE-Ratio-ArOverMr.res");
         writeTable(arFile, arTable);
         writeTable(mrFile, mrTable);
         writeTable(ratioFile, ratioTable);

         System.out.println("MSE tables written to:");
         System.out.println("  " + arFile.getAbsolutePath());
         System.out.println("  " + mrFile.getAbsolutePath());
         System.out.println("  " + ratioFile.getAbsolutePath());

         arRows.setLength(0);
         mrRows.setLength(0);
         ratioRows.setLength(0);
      }
      String csvTable = csvHead.toString() + csvRows.toString();
      File csvFile = new File(resultFolder, model + "-MSE-" + r + ".csv");
      writeTable(csvFile, csvTable);
      System.out.println("csv file written to:");
      System.out.println("  " + csvFile.getAbsolutePath());
   }

   /**
    * Computes the moments and the MSE estimates for one model and several values
    * of @f$r@f$. For each dimension @f$s@f$ and each value of @f$k@f$, writes
    * three {@code .res} tables with one row for each value of @f$r@f$ and one
    * column for each RQMC method.
    *
    * @param inputFolder  directory containing the input data files
    * @param outputFolder directory in which result files are written
    * @param model        model name used in input filenames
    * @param dims         set of model dimensions
    * @param methods      RQMC method names
    * @param ks           values of @f$k@f$
    * @param numObs       number of observations identified in each filename
    * @param m            number of bootstrap samples
    * @param rs           the set of values of @f$r@f$
    * @param stream       random stream used for sampling
    * @throws IllegalArgumentException if the result directory cannot be created
    */
   public static void estimateMSEManyr(String inputFolder, String outputFolder, String model, int[] dims,
         String[] methods, int[] ks, int numObs, int m, int[] rs, RandomStream stream) {

      System.out.println("Running estimateMSEManyr.... ");
      File resultFolder = new File(outputFolder);
      if (!resultFolder.exists() && !resultFolder.mkdirs())
         throw new IllegalArgumentException("Could not create result folder " + resultFolder.getAbsolutePath());
      // Builds the first row of the .res files.
      StringBuilder resHeader = new StringBuilder(" r ");
      for (String method : methods)
         resHeader.append(" ").append(method).append(" ");
      resHeader.append("\n");

      StringBuilder arRows = new StringBuilder();
      StringBuilder mrRows = new StringBuilder();
      StringBuilder ratioRows = new StringBuilder();
      TallyStore statMed = new TallyStore("M_r");

      for (int s : dims) {
         statMed.init();
         for (int k : ks) {
            for (int r : rs) {
               // Use a new substream for this r.
               // stream.resetNextSubstream();
               arRows.append(r).append("  ");
               mrRows.append(r).append("  ");
               ratioRows.append(r).append("  ");

               for (String method : methods) {
                  File file = getDataFile(inputFolder, model, s, method, k, numObs);
                  if (file == null) {
                     arRows.append("Missing  ");
                     mrRows.append("Missing  ");
                     ratioRows.append("Missing  ");
                     continue;
                  }
                  TallyStore tally = readDataValues(file.getAbsolutePath());

                  // stream.resetStartSubstream();
                  bootstrapMrValues(tally, m, r, stream, statMed);
                  double arMse = mseAr(tally, r);
                  double mrMse = statMed.mseKnownMean(exactMean);
                  double ratio = mrMse == 0.0 ? Double.NaN : arMse / mrMse;

                  arRows.append(arMse).append("  ");
                  mrRows.append(mrMse).append("  ");
                  ratioRows.append(ratio).append("  ");
               }
               arRows.append("\n");
               mrRows.append("\n");
               ratioRows.append("\n");
            }
            String arTable = resHeader.toString() + arRows.toString();
            String mrTable = resHeader.toString() + mrRows.toString();
            String ratioTable = resHeader.toString() + ratioRows.toString();
            File arFile = new File(resultFolder, model + "-" + s + "-k" + k + "-MSE-Ar.res");
            File mrFile = new File(resultFolder, model + "-" + s + "-k" + k + "-MSE-Mr.res");
            File ratioFile = new File(resultFolder, model + "-" + s + "-k" + k + "-MSE-Ratio-ArOverMr.res");
            writeTable(arFile, arTable);
            writeTable(mrFile, mrTable);
            writeTable(ratioFile, ratioTable);

            System.out.println("MSE tables written to:");
            System.out.println("  " + arFile.getAbsolutePath());
            System.out.println("  " + mrFile.getAbsolutePath());
            System.out.println("  " + ratioFile.getAbsolutePath());

            arRows.setLength(0);
            mrRows.setLength(0);
            ratioRows.setLength(0);
         }
      }
      System.out.println("estimateMSEManyr done");
   }

   /**
    * For one category of methods, this function computes the moments and the MSE
    * estimates for one value of @f$r@f$, for all methods in the category, all
    * models, values of @f$s@f$, and values of @f$k@f$ in the sets. It constructs a
    * single .csv file that has one row for each case. The columns give the mean,
    * variance, absolute skewness, kurtosis (not excess), the MSE estimates, and
    * their ratio.
    *
    * @param inputFolder  directory containing the input data files
    * @param outputFolder directory in which result files are written
    * @param category     name of category
    * @param methods      method names in that category
    * @param models       set of model names in that category, used in input
    *                     filenames
    * @param dims         set of dimensions
    * @param ks           values of @f$k@f$
    * @param numObs       number of observations identified in each filename
    * @param m            number of bootstrap samples
    * @param r            sample size used for @f$A_r@f$ and for each bootstrap
    *                     sample of @f$M_r@f$
    * @param stream       random stream used for sampling
    * @throws IllegalArgumentException if the result directory cannot be created
    */
   public static void estimateMSEOneCategory(String inputFolder, String outputFolder, String category, String[] methods,
         String[] models, int[] dims, int[] ks, int numObs, int m, int r, RandomStream stream) {

      File resultFolder = new File(outputFolder);
      if (!resultFolder.exists() && !resultFolder.mkdirs())
         throw new IllegalArgumentException("Could not create result folder " + resultFolder.getAbsolutePath());

      // Builds the first row of the .csv file.
      StringBuilder csvHead = new StringBuilder("model,s,method,k,mean,variance,abs-skewness,kurtosis");
      csvHead.append(",MSEAr,MSEMr,MSEratio,log-ratio\n");
      StringBuilder csvRows = new StringBuilder();
      TallyStore statMed = new TallyStore("M_r");

      for (String model : models) {
         for (int s : dims) {
            statMed.init();
            for (int k : ks) {
               // Use a new substream for this k.
               // stream.resetNextSubstream();
               for (String method : methods) {
                  File file = getDataFile(inputFolder, model, s, method, k, numObs);
                  TallyStore tally = readDataValues(file.getAbsolutePath());
                  bootstrapMrValues(tally, m, r, stream, statMed);
                  double arMse = mseAr(tally, r);
                  double mrMse = statMed.mseKnownMean(exactMean);
                  double ratio = mrMse == 0.0 ? Double.NaN : arMse / mrMse;
                  if ((model!="SumUeU") | (method!="Lat-Rv")) 
                      csvRows.append(model).append(",").append(s).append(",").append(method).append(",").append(k)
                        .append(",").append(tally.average()).append(",").append(tally.variance()).append(",")
                        .append(Math.abs(tally.skewness())).append(",").append(tally.kurtosis(false, false)).append(",")
                        .append(arMse).append(",").append(mrMse).append(",").append(ratio).append(",")
                        .append(Math.log10(ratio)).append("\n");
               }
            }
         }
      }
      String csvTable = csvHead.toString() + csvRows.toString();
      File csvFile = new File(resultFolder, "Categ-" + category + "-MSE-r" + r + ".csv");
      writeTable(csvFile, csvTable);
      System.out.println("csv file written to:");
      System.out.println("  " + csvFile.getAbsolutePath());
   }

}
