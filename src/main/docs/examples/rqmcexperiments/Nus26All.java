package rqmcexperiments;

import java.io.IOException;

/**
 * Example that uses {@link HistCollectionLatex} to generate histograms in LaTeX files for NUS exp.
 * The local variables in the `main` set the directories, list of models, list of methods,
 * dimensions, values of `k = log_2 n`, and number of observations.
 * All of these are passed as parameters to `HistCollectionLatex.writeCollection`,
 * which constructs one LaTeX file for each model in the list.
 */
public class Nus26All {

   /**
    * Sets the SAMO 2025 parameters and writes the histogram LaTeX files.
    */
   public static void main(String[] args) throws IOException {

      String inputFolder = "/home/otman/Dropbox/Nus-comparisons/all-methods/dat/";
      String outputFolder = "/home/otman/Documents/GitHub/rqmc-experiments/plots/histograms/";

      String[] modelTags = new String[] {
        "MC2" , "SumUeU", "SmoothPerB4", "Polynomial"
      };
      
      String[] methods = new String[] {
         "NUS-SSJ","SSJ-PRESORTED","Friedel-Keller-libseq","Art-Owen","Burley-QuantLib",
         "Adaptive-Regular-Tiles","NUS-SciML","NUS-QMCPy","Helmer-Stochastic-Sobol",
         "PBRT-OwenScrambler","PBRT-FastOwenScrambler"
      };

      int[] sDims = new int[] {2, 4, 8, 16, 32};  // Dimensions s.
      int[] ks = new int[] {10, 12, 14, 16};      // Values of k = log_2 n.
      int m = 1000;                    // Number of observations per file.

      // modelTags = new String[] {"SmoothPerB4"};
      // int[] sDims = new int[] {2};  // Dimensions s.
      // int[] ks = new int[] {10};      // Values of k = log_2 n.
         
      HistCollectionLatex.writeCollection(
         inputFolder, outputFolder, modelTags, methods, sDims, ks, m);
   }
}