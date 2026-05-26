
      package rqmcexperiments;

      import java.io.*;
      import java.util.Arrays;
      import java.util.Formatter;
      import java.util.Locale;
      import umontreal.ssj.stat.*;

      /*On linux run:
      for f in folder_latex/*.tex; do
       echo "Compiling $f"; 
       pdflatex -output-directory=pdfFoler "$f"; 
       rm pdfFoler/*.aux pdfFoler/*.log; 
       done
      */

      public class CreateLatex {
      	private static int numBins = 200;
      	private static final double BIG_OUTLIER_IQR = 8.0;
      	private static final double SHOW_OUTLIER_RANGE_IQR = 15.0;

         public static void main(String[] args) throws IOException {

             String dataDir = "C:/Users/cherrato/Dropbox/samo25/datapl/";
             String latexDir = "C:/Users/cherrato/Documents/GitHub/Data/samo25-test/latex-files/";

            String[] modelTags = {"SmoothPerB4","SumUeU","MC2","Polynomial","Oscillatory","Gaussian","SmoothGauss","PieceLinGauss","IndSumNormal"};// "MC2"IndSumNormal
            //String[] modelTags = {"MC2"};
            int[] sDims = {2, 4, 8, 16,32};
            int m = 10000;
            int mExp = (int) Math.log10(m);
            int[] ks = {10, 12, 14, 16};

            String[][] pages = {
               {"Rank-1 lattice", "Lat-RS,Lat-RSB,Lat-Rv,Lat-Rpv,Lat-RvRS,Lat-RvRSB,Lat-RpvRS,Lat-RpvRSB"},
               //{"Rank-1 lattice with baker transform", "Lat-RSB,Lat-RvRSB,Lat-RpvRSB"},
               {"Sobol", "Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS"}
            };

            File inputFolder = new File(dataDir);
            File outputFolder = new File(latexDir);
            outputFolder.mkdirs();

            File[] files = inputFolder.listFiles((dir, name) -> name.endsWith(".dat"));

            if (files == null || files.length == 0) {
               System.out.println("No .dat files found in " + dataDir);
               return;
            }

            Arrays.sort(files);

            for (String model : modelTags) {

               File outFile = new File(outputFolder, model + "-hist.tex");

               try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {

                  writeLatexHeader(out);

                  for (int s : sDims) {
                     String baseTag = model + "-" + s;
                     String titleTag = model + " s = " + s;

                     for (String[] page : pages) {
                        String pageTitle =
                           "RQMC " + page[0] + " comparison: "
                           + titleTag + " ($10^{" + mExp + "}$ samples)";

                        writeHistogramPageBody(
                           out,
                           files,
                           baseTag,
                           pageTitle,
                           page[1].split(","),
                           ks,
                           m
                        );

                        out.println();
                     }
                  }

                  writeLatexFooter(out);
               }

               System.out.println("LaTeX file created:");
               System.out.println(outFile.getAbsolutePath());
            }
         }

         private static void writeLatexHeader(PrintWriter out) {
            out.println("\\documentclass[border=3pt,multi=tabular]{standalone}");
            out.println("\\usepackage{amsmath}");
            out.println("\\usepackage{graphicx}");
            out.println("\\usepackage{pgfplots}");
            out.println("\\pgfplotsset{compat=1.18}");
            out.println("\\begin{document}");
            out.println();
         }

         private static void writeLatexFooter(PrintWriter out) {
            out.println("\\end{document}");
         }

         private static void writeHistogramPageBody(
               PrintWriter out,
               File[] files,
               String baseTag,
               String pageTitle,
               String[] methods,
               int[] ks,
               int m) throws IOException {

            out.print("\\begin{tabular}{@{}c");
            for (int i = 0; i < ks.length; i++) {
               out.print("@{\\hspace{1mm}}c");
            }
            out.println("@{}}");

            out.println("\\multicolumn{" + (ks.length + 1)
                  + "}{c}{\\fontsize{7}{8}\\selectfont\\textbf{"
                  + escapeLatex(pageTitle) + "}} \\\\[2mm]");

            for (String method : methods) {

          	  out.print("\\raisebox{0.7cm}{\\rotatebox{90}{\\fontsize{6}{7}\\selectfont "
          		      + escapeLatex(method) + "}}");
               for (int k : ks) {
                  File file = findFile(files, baseTag, method, k, m);

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
         }

         private static File findFile(File[] files, String baseTag, String method, int k, int m) {
            String exactName = baseTag + "-" + method + "-" + k + "-" + m + ".dat";

            for (File file : files) {
               if (file.getName().equals(exactName))
                  return file;
            }
            
            System.out.println("Missing file: " + exactName);

            return null;
         }

         private static String makeHistogramLatex(File file) throws IOException {

            TallyStore fileStats = getFileStats(file);

//            double xmin = fileStats.min();
//            double xmax = fileStats.max();
      //
//            if (xmin == xmax) {
//               xmin -= 1.0;
//               xmax += 1.0;
//            } else {
//               double pad = 0.03 * (xmax - xmin);
//               xmin -= pad;
//               xmax += pad;
//            }
            double xmin = fileStats.min();
            double xmax = fileStats.max();

            double center = 0.5 * (xmin + xmax);
            double range = xmax - xmin;
            double minRange = 0.02 * Math.max(1.0, Math.abs(center));

            if (!(range > 0.0) || Double.isNaN(range) || Double.isInfinite(range)) {
               xmin = center - 0.5 * minRange;
               xmax = center + 0.5 * minRange;
            } else {
               double finalRange = Math.max(1.06 * range, minRange);
               xmin = center - 0.5 * finalRange;
               xmax = center + 0.5 * finalRange;
            }
            
            int minBins = 50;
            int maxBins = 200;

            int n = fileStats.numberObs();
            double[] values = fileStats.getArray();
            
            numBins = getNumBins(n, values, xmin, xmax, minBins, maxBins);
            
            //System.out.println("File: " + file.getName() + ", numBins: " + numBins);

            TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins);
            hist.fillFromFile(file.getAbsolutePath());

            ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);

            String title = cleanTitle(file.getName());

            String legend =
               "\\scalebox{0.62}{"
               + "\\begin{tabular}{@{}l@{}}"
               + "$\\sigma^2$=" + sci(hist.variance())
               + "\\\\[-1pt]$\\gamma$=" + sci(fileStats.skewness())
               + "\\\\[-1pt]$\\kappa'$=" + sci(fileStats.kurtosis())
               + "\\end{tabular}"
               + "}";
            scHist.setAxisOptions(
          		   "title={" + escapeLatex(title) + "}, " +
          		   "title style={font=\\fontsize{5}{5.5}\\selectfont}, " +
          		   "width=5.15cm, height=4.01cm, " +
          		   "xlabel={}, ylabel={}, " +
          		   "xmin=" + String.format(Locale.US, "%.17g", xmin) + ", " +
          		   "xmax=" + String.format(Locale.US, "%.17g", xmax) + ", " +
          		   "scaled x ticks=true, " +
          		   "minor x tick num=0, " +
          		   "scaled y ticks=false, " +
          		   "tick label style={font=\\fontsize{2.5}{3.0}\\selectfont}, " +
          		   "every x tick label/.append style={font=\\fontsize{1.0}{1.2}\\selectfont, scale=0.6, transform shape}, " +
          		   "every x tick scale label/.style={font={\\bfseries\\boldmath\\fontsize{3.0}{4.0}\\selectfont}, scale=1.0, transform shape, at={(axis description cs:1,0)}, anchor=north east, xshift=2pt, yshift=-9.2pt, inner sep=0pt}, " +
          		   "every y tick scale label/.append style={font=\\fontsize{4}{4.5}\\selectfont}, " +
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


            scHist.setAddPlotOptions("fill=blue, draw=blue!80!black, line width=0.03pt");

            String latex = scHist.toLatex(true, false);

            String outlierMarks = getOutlierMarks(values, n);

            if (!outlierMarks.isEmpty()) {
               latex = latex.replace("\\end{axis}", outlierMarks + "\n\\end{axis}");
            }

            return latex;
         }

         private static String cleanTitle(String fileName) {
            String title = fileName.substring(0, fileName.length() - 4);
            title = title.replaceFirst("-\\d+$", "");
            return title;
         }

         private static TallyStore getFileStats(File file) throws IOException {

            TallyStore fileStats = new TallyStore();

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
               String line;

               while ((line = reader.readLine()) != null) {
                  line = cleanDataLine(line);

                  if (line.isEmpty())
                     continue;

                  String[] values = line.split("\\s+");

                  for (String value : values) {
                     double x = Double.parseDouble(value);
                     fileStats.add(x);
                  }
               }
            }

            if (fileStats.numberObs() == 0)
               throw new IOException("No observations found in " + file.getAbsolutePath());

            return fileStats;
         }
         private static int getNumBins(int n, double[] values, double xmin, double xmax, int minBins, int maxBins) {

      	   if (n < 2 || xmin == xmax)
      	      return minBins;

      	   Arrays.sort(values, 0, n);

      	   double q1 = values[(int) Math.floor(0.25 * (n - 1))];
      	   double q3 = values[(int) Math.floor(0.75 * (n - 1))];

      	   double iqr = q3 - q1;
      	   double binWidth = 2.0 * iqr / Math.cbrt(n);

      	   if (binWidth <= 0.0 || Double.isNaN(binWidth) || Double.isInfinite(binWidth))
      	      return minBins;

      	   double rawBins = Math.ceil((xmax - xmin) / binWidth);

      	   return (int) Math.max(minBins, Math.min(maxBins, rawBins));
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
         
         private static String getOutlierMarks(double[] values, int n) {
      	   if (n < 4)
      	      return "";

      	   double[] sorted = Arrays.copyOf(values, n);
      	   Arrays.sort(sorted);

      	   double q1 = sorted[(int) Math.floor(0.25 * (n - 1))];
      	   double q3 = sorted[(int) Math.floor(0.75 * (n - 1))];
      	   double iqr = q3 - q1;

      	   if (iqr <= 0.0 || Double.isNaN(iqr) || Double.isInfinite(iqr))
      	      return "";

      	   double fullRange = sorted[n - 1] - sorted[0];
      	   double rangeRatio = fullRange / iqr;

      	   // Normal-looking histograms: no red marks.
      	   if (rangeRatio < SHOW_OUTLIER_RANGE_IQR)
      	      return "";

      	   double lower = q1 - BIG_OUTLIER_IQR * iqr;
      	   double upper = q3 + BIG_OUTLIER_IQR * iqr;

      	   int leftTotal = 0;
      	   while (leftTotal < n && sorted[leftTotal] < lower)
      	      leftTotal++;

      	   int rightTotal = 0;
      	   while (rightTotal < n && sorted[n - 1 - rightTotal] > upper)
      	      rightTotal++;

      	   int total = leftTotal + rightTotal;

      	   if (total == 0)
      	      return "";

      	   int maxMarks;

      	   if (rangeRatio >= 40.0)
      	      maxMarks = 8;
      	   else if (rangeRatio >= 25.0)
      	      maxMarks = 4;
      	   else
      	      maxMarks = 2;

      	   int take = Math.min(total, maxMarks);

      	   int leftTake = Math.min(leftTotal, take / 2);
      	   int rightTake = Math.min(rightTotal, take - leftTake);

      	   // If one side has fewer outliers, give the remaining marks to the other side.
      	   leftTake = Math.min(leftTotal, take - rightTake);

      	   StringBuilder coords = new StringBuilder();

      	   for (int i = 0; i < leftTake; i++) {
      	      coords.append("(")
      	            .append(String.format(Locale.US, "%.17g", sorted[i]))
      	            .append(",0) ");
      	   }

      	   for (int i = n - rightTake; i < n; i++) {
      	      coords.append("(")
      	            .append(String.format(Locale.US, "%.17g", sorted[i]))
      	            .append(",0) ");
      	   }

      	   if (coords.length() == 0)
      	      return "";

      	   return "\n\\addplot+[only marks, mark=|, mark size=2.5pt, red, "
      	         + "mark options={red, line width=0.5pt}, forget plot] coordinates {"
      	         + coords
      	         + "};";
      	}
      }