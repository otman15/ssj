package rqmcexperiments;

import java.io.IOException;

/**
 * Example that uses {@link HistCollectionLatex} to generate histograms in LaTeX files.
 * The local variables in the `main` set the directories, list of models, list of methods,
 * dimensions, values of `k = log_2 n`, and number of observations.
 * All of these are passed as parameters to `HistCollectionLatex.writeCollection`,
 * which constructs one LaTeX file for each model in the list.
 */
public class HistSamo25 {

   /**
    * Sets the SAMO 2025 parameters and writes the histogram LaTeX files.
    */
   public static void main(String[] args) throws IOException {

      String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String outputFolder = "/home/otman/Documents/GitHub/Data/samo25-test/test-pdf/";
//"Gaussian", "Oscillatory",
      String[] modelTags = new String[] {
         "SmoothPerB4", "SumUeU", "MC2", "Polynomial", 
         "SmoothGauss", "PieceLinGauss", "IndSumNormal"
      };
      String[] methods = new String[] {
         "Lat-RS", "Lat-RSB", "Lat-Rv", "Lat-Rpv", "Lat-RvRS",
         "Lat-RvRSB", "Lat-RpvRS", "Lat-RpvRSB",
         "Sob-RDS", "Sob-RDSB", "Sob-LMS", "Sob-LMS-RDS",
         "Sob-LMS-RDS-IRB", "Sob-NUS"
      };
      int[] sDims = new int[] {2, 4, 8, 16, 32};  // Dimensions s.
      int[] ks = new int[] {10, 12, 14, 16};      // Values of k = log_2 n.
      int m = 10000;                    // Number of observations per file.

      HistCollectionLatex.setLegendPos("auto");
      HistCollectionLatex.setShowLegend("builtin");
      HistCollectionLatex.writeCollection(
         inputFolder, outputFolder, modelTags, methods, sDims, ks, m);
   }
}
