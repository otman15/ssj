package rqmcexperiments;

import java.io.*;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import umontreal.ssj.stat.*; 

/**
 * This program reads the .dat files created by `WSC23MoreReps.java`, which
 * contain RQMC replicates for various functions and methods, and creates a
 * LaTeX file with a 3x4 table of histograms of the RQMC errors, with one row
 * for each method type (Lat, Lat-Rv, Lat-Rp) and one column for each value of
 * `k` (number of points @f$n=2^k@f$). Each histogram has a title with the
 * function name and the method, and a legend with the variance, skewness and
 * excess kurtosis of the data. The output LaTeX file can be compiled to create
 * a PDF with the histograms.
 */


// Needs to be more organized and better documented, but it works for now.
//The code should be revised to be cleaner,
//but I wanted to get the results out first.
// If we could get stats from the TallyHistogram directly, that would be better than reading the file twice.
	
public class HistLat2 {

   public static void main(String[] args) throws IOException {

      String dataDir = "/home/otman/Documents/GitHub/Data/wsc23-test/";// .dat files should be here
      String latexDir = "/home/otman/Documents/GitHub/Data/tikz-latx/";// output .tex file will be created here

      //Important: Change these parameters as needed to match the .dat files you have.
      String modelTag = "SmoothGauss";
      int s = 8;
      int m = 10000;
      boolean baker = true;
      int[] ks = {10, 12, 14, 16};

      
      
      String baseTag = modelTag + "-" + s;

      String pageTitle;
      String[][] methodCandidates;

      if (!baker) {
         pageTitle = "RQMC Rank-1 lattice point set comparison: "
               + modelTag + "-" + s + " ($10^4$ samples)";

         methodCandidates = new String[][] {
            {"Lat-RS"},
            {"Lat-RvRS", "Lat-Rv-RS"},
            {"Lat-RpvRS", "Lat-RpRS", "Lat-Rp-RS", "Lat-Rpv-RS"}
         };
      } else {
         pageTitle = "RQMC Rank-1 lattice point set comparison: "
               + modelTag + "-baker-" + s + " ($10^4$ samples)";

         methodCandidates = new String[][] {
            {"Lat-RSB", "Lat-RST"},
            {"Lat-RvRSB", "Lat-RvRST", "Lat-Rv-RSB", "Lat-Rv-RST"},
            {"Lat-RpvRSB", "Lat-RpvRST", "Lat-Rp-RSB", "Lat-Rp-RST", "Lat-Rpv-RSB", "Lat-Rpv-RST"}
         };
      }

      String[] rowLabels = {"Lat", "Lat-Rv", "Lat-Rp"};

      File inputFolder = new File(dataDir);
      File outputFolder = new File(latexDir);
      outputFolder.mkdirs();

      File[] files = inputFolder.listFiles((dir, name) -> name.endsWith(".dat"));

      if (files == null || files.length == 0) {
         System.out.println("No .dat files found in " + dataDir);
         return;
      }

      Arrays.sort(files);

      String outputName = baker
            ? modelTag + "-baker-" + s + "_rank1_12plots.tex"
            : modelTag + "-" + s + "_rank1_12plots.tex";

      File outFile = new File(outputFolder, outputName);

      try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {

         out.println("\\documentclass[border=3pt]{standalone}");
         out.println("\\usepackage{amsmath}");
         out.println("\\usepackage{graphicx}");
         out.println("\\usepackage{pgfplots}");
         out.println("\\pgfplotsset{compat=1.18}");
         out.println("\\begin{document}");
         out.println();

         out.println("\\begin{tabular}{@{}r@{\\hspace{1mm}}c@{\\hspace{1mm}}c@{\\hspace{1mm}}c@{\\hspace{1mm}}c@{}}");
         out.println("\\multicolumn{5}{c}{\\fontsize{7}{8}\\selectfont\\textbf{" + pageTitle + "}} \\\\[2mm]");

         for (int r = 0; r < rowLabels.length; r++) {

            out.print("{\\fontsize{6}{7}\\selectfont " + rowLabels[r] + "}");

            for (int c = 0; c < ks.length; c++) {
               int k = ks[c];

               File file = findFile(files, baseTag, methodCandidates[r], k, m);

               if (file == null) {
                  out.print(" & {\\tiny Missing}");
                  continue;
               }

               out.print(" & ");
               out.println(makeHistogramLatex(file));
            }

            out.println("\\\\[1.5mm]");
         }

         out.print("{}");
         for (int k : ks) {
            out.print(" & {\\fontsize{6}{7}\\selectfont $n=2^{" + k + "}$}");
         }
         out.println(" \\\\");

         out.println("\\end{tabular}");
         out.println();
         out.println("\\end{document}");
      }

      System.out.println("LaTeX file created:");
      System.out.println(outFile.getAbsolutePath());
   }

   private static File findFile(File[] files, String baseTag, String[] methodCandidates, int k, int m) {

      for (String method : methodCandidates) {
         String exactName = baseTag + "-" + method + "-" + k + "-" + m + ".dat";

         for (File file : files) {
            if (file.getName().equals(exactName))
               return file;
         }
      }

      for (String method : methodCandidates) {
         String prefix = baseTag + "-" + method + "-" + k + "-";

         for (File file : files) {
            if (file.getName().startsWith(prefix) && file.getName().endsWith(".dat"))
               return file;
         }
      }

      return null;
   }

   private static String makeHistogramLatex(File file) throws IOException {

      FileStats stats = getFileStats(file);

      double xmin = stats.min;
      double xmax = stats.max;

      if (xmin == xmax) {
         xmin -= 1.0;
         xmax += 1.0;
      } else {
         double pad = 0.03 * (xmax - xmin);
         xmin -= pad;
         xmax += pad;
      }

      int numBins = Math.max(20, (int) Math.round(2*Math.cbrt(stats.numObs)));

      TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins); 
      hist.fillFromFile(file.getAbsolutePath());

      ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);

      String title = cleanTitle(file.getName());

      String legend =
    	      "\\scalebox{0.62}{"
    	      + "\\begin{tabular}{@{}l@{}}"
    	      + "$\\sigma^2$=" + sci(hist.variance())
    	      + "\\\\[-1pt]$\\gamma$=" + sci(stats.skewness)
    	      + "\\\\[-1pt]$\\kappa'$=" + sci(stats.excessKurtosis)
    	      + "\\end{tabular}"
    	      + "}";

      scHist.setAxisOptions(
         "title={" + escapeLatex(title) + "}, " +

         // Smaller title font.
         "title style={font=\\fontsize{5}{5.5}\\selectfont}, " +

         // Bigger plot box.
         "width=5.15cm, height=4.01cm, " +

         "xlabel={}, ylabel={}, " +
         "scaled x ticks=true, " +
         "scaled y ticks=false, " +

         // Smaller tick labels.
         "tick label style={font=\\fontsize{4.5}{5}\\selectfont}, " +

         // Smaller  bold x-axis multiplier, e.g. 1e-4.
         "every x tick scale label/.append style={font=\\fontsize{4}{4.5}\\selectfont\\bfseries\\boldmath, yshift=5pt}, "  +
         "every y tick scale label/.append style={font=\\fontsize{4}{4.5}\\selectfont}, " +

         // Red vertical line at 0.
//         "extra x ticks={0}, " +
//         "extra x tick labels={}, " +
//         "extra x tick style={grid=major, major grid style={red, thick}}, " +

         // Smaller legend rectangle.
         "legend entries={{" + legend + "}}, " +
         "legend image code/.code={}, " +
         "legend style={"
            + "draw=none, "
            + "fill=none, "
            + "font=\\scriptsize, "
            + "cells={anchor=west}, "
            + "inner xsep=0pt, "
            + "inner ysep=0pt"
         + "}, " +
         "legend pos=north east"
      );

      scHist.setAddPlotOptions("fill=blue, draw=black");

      return scHist.toLatex(true, false);
   }

   private static String cleanTitle(String fileName) {
      String title = fileName.substring(0, fileName.length() - 4);
      title = title.replaceFirst("-\\d+$", "");
      return title;
   }

   private static FileStats getFileStats(File file) throws IOException {
      FileStats stats = new FileStats();

      double sum = 0.0;

      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line;

         while ((line = reader.readLine()) != null) {
            line = cleanDataLine(line);

            if (line.isEmpty())
               continue;

            String[] values = line.split("\\s+");

            for (String value : values) {
               double x = Double.parseDouble(value);

               if (x < stats.min)
                  stats.min = x;
               if (x > stats.max)
                  stats.max = x;

               sum += x;
               stats.numObs++;
            }
         }
      }

      if (stats.numObs == 0)
         throw new IOException("No observations found in " + file.getAbsolutePath());

      stats.mean = sum / stats.numObs;

      double m2 = 0.0;
      double m3 = 0.0;
      double m4 = 0.0;

      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line;

         while ((line = reader.readLine()) != null) {
            line = cleanDataLine(line);

            if (line.isEmpty())
               continue;

            String[] values = line.split("\\s+");

            for (String value : values) {
               double x = Double.parseDouble(value);
               double d = x - stats.mean;
               double d2 = d * d;

               m2 += d2;
               m3 += d2 * d;
               m4 += d2 * d2;
            }
         }
      }


      double m2n = m2 / stats.numObs;
      double m3n = m3 / stats.numObs;
      double m4n = m4 / stats.numObs;

      if (m2n > 0.0) {
         stats.skewness = m3n / Math.pow(m2n, 1.5);
         stats.excessKurtosis = m4n / (m2n * m2n) - 3.0;
      } else {
         stats.skewness = 0.0;
         stats.excessKurtosis = 0.0;
      }

      return stats;
   }

   private static String cleanDataLine(String line) {
      line = line.trim();

      if (line.isEmpty())
         return "";

      int commentIndex = line.indexOf('#');

      if (commentIndex >= 0)
         line = line.substring(0, commentIndex).trim();

      return line;
   }

   private static String sci(double x) {
      Formatter formatter = new Formatter(Locale.US);
      formatter.format("%.1e", x);
      String s = formatter.toString();
      formatter.close();

      s = s.replace("e-0", "e-");
      s = s.replace("e+0", "e");
      s = s.replace("e+", "e");

      return s;
   }

   private static String escapeLatex(String s) {
      return s.replace("_", "\\_");
   }

   private static class FileStats {
      int numObs = 0;
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      double mean = 0.0;
      double skewness = 0.0;
      double excessKurtosis = 0.0;
   }
}