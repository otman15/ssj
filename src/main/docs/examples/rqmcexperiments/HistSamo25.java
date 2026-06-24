package rqmcexperiments;

import java.io.IOException;

public class HistSamo25 {
   public static void main(String[] args) throws IOException {
      String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String outputFolder = "/home/otman/Documents/GitHub/Data/samo25-test/latexNewConfig/";

      String[] modelTags = new String[] {"Polynomial", "PieceLinGauss",
           "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
           "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
        };

      int[] sDims = new int[] {2, 8, 16};
      int m = 10000;
      int[] ks = new int[] {10, 12, 14, 16};
      int leftExtMark = 2;
      int rightExtMark =2;
      int numBins = 80;

      String methodesTitle = "Rank-1 lattice + sobol";
      String methods = "Lat-RS,Lat-RSB,Lat-Rv,Lat-Rpv,Lat-RvRS,Lat-RvRSB,Lat-RpvRS,Lat-RpvRSB,"+
      "Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS";

      // Each row contains a page title and its comma-separated method names.
      String[][] pages = new String[][] {
         {methodesTitle, methods}
      };

      HistCollectionLatex2 samo25Config = new HistCollectionLatex2(
         inputFolder, outputFolder,
         modelTags, sDims, ks, m,
         pages,
         leftExtMark, rightExtMark, numBins

      );

      samo25Config.writeCollection();
   }
}
