package rqmcexperiments;

import java.io.IOException;

/**
 * Example that uses {@link HistCollectionLatex} to generate the SAMO 2025
 * histogram LaTeX files. The local variables in {@link #main(String[])} set the
 * folders, models, dimensions, values of {@code k}, observation count, and
 * methods.
 */
public class HistSamo25 {

   /**
    * Sets the SAMO 2025 parameters and writes the histogram LaTeX files.
    */
   public static void main(String[] args) throws IOException {
      String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String outputFolder = "/home/otman/Documents/GitHub/Data/samo25-test/latexNewConfig/";

      int m = 10000;
      int[] sDims = new int[] {2, 4, 8, 16, 32};
      int[] ks = new int[] {10, 12, 14, 16};

      String[] modelTags = new String[] {
         "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
         "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
      };

      String[] methods = new String[] {
         "Lat-RS", "Lat-RSB", "Lat-Rv", "Lat-Rpv", "Lat-RvRS",
         "Lat-RvRSB", "Lat-RpvRS", "Lat-RpvRSB",
         "Sob-RDS", "Sob-RDSB", "Sob-LMS", "Sob-LMS-RDS",
         "Sob-LMS-RDS-IRB", "Sob-NUS"
      };

      HistCollectionLatex.writeCollection(
         inputFolder, outputFolder,
         modelTags, sDims, ks, m,
         methods
      );
   }
}
