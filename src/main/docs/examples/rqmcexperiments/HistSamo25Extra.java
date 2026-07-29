package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Similar to `HistSamo25`, but this one produces only selected histograms,
 * usually for larger sample sizes.
 */
public class HistSamo25Extra {

   public static void main(String[] args) throws IOException {

      // inputFolder = "C:/Users/Lecuyer/Dropbox/samo25/datapl/";
      // outputFolder = "C:/Users/Lecuyer/Dropbox/samo25/paperdat/";

      String[] fileNames = new String[] { "SmoothPerB4-8-Lat-Rv-16-1000000", "SmoothPerB4-8-Lat-Rpv-16-1000000",
            "SmoothPerB4-8-Lat-RvRS-16-1000000", "SmoothPerB4-8-Sob-LMS-RDS-16-1000000", "MC2-8-Lat-Rv-16-1000000",
            "MC2-8-Lat-Rpv-16-1000000", "MC2-8-Sob-LMS-16-1000000", "MC2-8-Sob-LMS-RDS-16-1000000",
            "MC2-8-Sob-RDSB-16-1000000", "MC2-4-Sob-RDSB-14-1000000", };
      String[] titleNames = new String[] { "\\small SmoothPerB4-8-Lat-Rv-16-M", "\\small SmoothPerB4-8-Lat-Rpv-16-M",
            "\\small SmoothPerB4-8-Lat-RvRS-16-M", "\\small SmoothPerB4-8-Sob-LMS-RDS-16-M",
            "\\small MC2-8-Lat-Rv-16-M", "\\small MC2-8-Lat-Rpv-16-M", "\\small MC2-8-Sob-LMS-16-M",
            "\\small MC2-8-Sob-LMS-RDS-16-M", "\\small MC2-8-Sob-RDSB-16-M", "\\small MC2-4-Sob-RDSB-14-M", };
      String[] legendAnchor = new String[] { "pos=north east", "pos=north east", "pos=north west", "pos=north west",
            "pos=north east", "pos=north east", "pos=north east", "pos=north east",
            "style={at={(0.5, 0.95)}, anchor=north}", "style={at={(0.5, 0.95)}, anchor=north}", };
      int numBins = 200;
      int n = 1000000;
      int[] marks = new int[] { 0, 9, 99, 999, n - 1, n - 10, n - 100, n - 1000 }; // This is for 10^6 obs.

      for (int i = 0; i < fileNames.length; i++) {
         String latexCode = HistSamo25Paper.makeSimpleHistogramLatex(fileNames[i], titleNames[i], legendAnchor[i],
               numBins, marks);
         File outFile = new File(HistSamo25Paper.outputFolder, fileNames[i] + "-hist-paper.tex");
         try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
            out.print(latexCode);
            System.out.println("Hist printed to file: " + fileNames[i] + "-hist-paper.tex");
         } catch (IOException e) {
            throw new RuntimeException("Could not write " + outFile.getAbsolutePath(), e);
         }
      }
      System.out.println("ALL DONE ");
   }
}
