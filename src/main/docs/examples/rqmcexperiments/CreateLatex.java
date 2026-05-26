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
	private static int numBins = 100;
	// Distance from Q1/Q3 needed to mark a point as an outlier.
	// Smaller value = outliers are marked closer to the main data.
	// Larger value = only very far points are marked.
	//private static final double 	BIG_OUTLIER_IQR = 7.0;
	// Minimum fullRange / IQR ratio required before showing any outlier marks.
	// Smaller value = outlier marks appear more often.
	// Larger value = marks appear only for very stretched distributions.
	//private static final double SHOW_OUTLIER_RANGE_IQR = 10.0;

   public static void main(String[] args) throws IOException {

      String dataDir = "/home/otman/Dropbox/samo25/datapl/";
      String latexDir = "/home/otman/Documents/GitHub/Data/samo25-test/latex-files/";

      String[] modelTags = {"SmoothPerB4","SumUeU","MC2","Polynomial","Oscillatory","Gaussian","SmoothGauss","PieceLinGauss","IndSumNormal"};// "MC2"IndSumNormal
      //String[] modelTags = {"SmoothPerB4"};
      
      int[] sDims = {2, 4, 8, 16, 32};
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
                     m,model,s //model and s added only for centering data
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
         int m,
         String model,
         int s) throws IOException {
	   
	   final String plotCellWidth = "5.2cm";/// plotCellWidth controls alignment/page width

      out.print("\\begin{tabular}{@{}c");
      for (int i = 0; i < ks.length; i++) {
         out.print("@{\\hspace{0mm}}c");
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
            	out.print(" & \\makebox[" + plotCellWidth + "][c]{{\\tiny Missing}}");
               continue;
            }

            out.print(" & \\makebox[" + plotCellWidth + "][c]{");
            out.print(makeHistogramLatex(file, model, s));
            out.println("}");
         }

         out.println("\\\\[1.5mm]");
      }

      out.print("{}");
      for (int k : ks) {
    	  out.print(" & \\makebox[" + plotCellWidth + "][c]{{\\fontsize{6}{7}\\selectfont $n=2^{" + k + "}$}}");//out.print(" & {\\fontsize{6}{7}\\selectfont $n=2^{" + k + "}$}");
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

   private static String makeHistogramLatex(File file, String model, int s) throws IOException {

      double shift = getCenteringShift(model, s);// for data centering
      TallyStore tallyStorefile = getFileStats(file, shift);

      double xmin = tallyStorefile.min();
      double xmax = tallyStorefile.max();
//
//      if (xmin == xmax) {
//         xmin -= 1.0;
//         xmax += 1.0;
//      } else {
//         double pad = 0.03 * (xmax - xmin);
//         xmin -= pad;
//         xmax += pad;
//      }

      double center = 0.5 * (xmin + xmax); ///////////////// working
      double range = xmax - xmin;

      if (!(range > 0.0) || Double.isNaN(range) || Double.isInfinite(range)) {
         double fallbackRange = 1e-12 * Math.max(1.0, Math.abs(center));
         xmin = center - 0.5 * fallbackRange;///////////////////////////////keep this/////////////////////////
         xmax = center + 0.5 * fallbackRange;
      } else {
         double finalRange = 1.06 * range;
         xmin = center - 0.5 * finalRange;
         xmax = center + 0.5 * finalRange;/////////////////////
      }
//      double center = 0.5 * (xmin + xmax);///////////////////not working
//      double range = xmax - xmin;
//      double scale = Math.max(1.0, Math.abs(center));
//
//      boolean flatData =
//            !(range > 0.0)
//            || Double.isNaN(range)
//            || Double.isInfinite(range)
//            || range < 1e-12 * scale;
//
//      if (flatData) {
//         double plotRange = 1e-6 * scale;
//         xmin = center - 0.5 * plotRange;
//         xmax = center + 0.5 * plotRange;
//      } else {
//         double finalRange = 1.06 * range;
//         xmin = center - 0.5 * finalRange;
//         xmax = center + 0.5 * finalRange;
//      }
      
//      int minBins = 50;
//      int maxBins = 200;

      int n = tallyStorefile.numberObs();
      double[] values = tallyStorefile.getArray();
      
      //numBins = getNumBins(n, values, xmin, xmax, minBins, maxBins);
      
      //System.out.println("File: " + file.getName() + ", numBins: " + numBins);
      //numBins = flatData ? 3 : getNumBins(n, values, xmin, xmax, minBins, maxBins);

      TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins);
      hist.fillFromTallyStore(tallyStorefile); 
      ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);
      
      String centered="";
      if (shift != 0) centered = " (centered)";

      String title = cleanTitle(file.getName()) + centered;

      String legend =
    	 "\\scalebox{0.58}{\\bfseries\\boldmath"
         + "\\begin{tabular}{@{}l@{}}"
         + "$\\sigma^2$=" + sci(hist.variance())
         + "\\\\[-1pt]$\\gamma$=" + sci(tallyStorefile.skewness())
         + "\\\\[-1pt]$\\kappa'$=" + sci(tallyStorefile.kurtosis())
         + "\\end{tabular}"
         + "}";
      scHist.setAxisOptions(
    		   "title={" + escapeLatex(title) + "}, " +
    		   "title style={font=\\tiny}, " + //"title style={font=\\fontsize{5}{5.5}\\selectfont}, " +
    		   "width=6.5cm, height=5.5cm, " +
    		   "xlabel={}, ylabel={}, " +
    		   "xmin=" + String.format(Locale.US, "%.17g", xmin) + ", " +
    		   "xmax=" + String.format(Locale.US, "%.17g", xmax) + ", " +
    		   "scaled x ticks=true, " +
    		   "minor x tick num=0, " +
    		   "scaled y ticks=false, " +
    		   "tick label style={font=\\small}, " + //"tick label style={font=\\fontsize{5.0}{5.0}\\selectfont}, " +
    		   "every x tick label/.append style={font=\\small, scale=0.6, transform shape}, " + //"every x tick label/.append style={font=\\fontsize{5.0}{5.0}\\selectfont, scale=0.6, transform shape}, " +
    		   "every x tick scale label/.style={font={\\bfseries\\boldmath\\small}, scale=1.0, transform shape, at={(axis description cs:1,0)}, anchor=north east, xshift=2pt, yshift=-9.2pt, inner sep=0pt}, " + //"every x tick scale label/.style={font={\\bfseries\\boldmath\\fontsize{5.0}{5.0}\\selectfont}, scale=1.0, transform shape, at={(axis description cs:1,0)}, anchor=north east, xshift=2pt, yshift=-9.2pt, inner sep=0pt}, " +
    		   "every y tick scale label/.append style={font=\\small}, " + //"every y tick scale label/.append style={font=\\fontsize{5}{5}\\selectfont}, " +
    		   "legend entries={{" + legend + "}}, " +
    		   "legend image code/.code={}, " +
    		   "legend style={"
    		      + "draw=none, "
    		      + "fill=none, "
    		      + "font=\\small, "
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

   private static TallyStore getFileStats(File file, double shift) throws IOException {

      TallyStore tallyStorefile = new TallyStore();

      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line;

         while ((line = reader.readLine()) != null) {
            line = cleanDataLine(line);

            if (line.isEmpty())
               continue;

            String[] values = line.split("\\s+");

            for (String value : values) {
               double x = Double.parseDouble(value);
               tallyStorefile.add(x-shift);
            }
         }
      }

      if (tallyStorefile.numberObs() == 0)
         throw new IOException("No observations found in " + file.getAbsolutePath());

      return tallyStorefile;
   }
   
   /*
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
	}*/

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
//
//	   double q1 = sorted[(int) Math.floor(0.25 * (n - 1))];
//	   double q3 = sorted[(int) Math.floor(0.75 * (n - 1))];
//	   double iqr = q3 - q1;
//
////	   if (iqr <= 0.0 || Double.isNaN(iqr) || Double.isInfinite(iqr))
////	      return "";
//
//	   double fullRange = sorted[n - 1] - sorted[0];
//	   double scale = Math.max(1.0,
//			      Math.max(Math.abs(sorted[0]), Math.abs(sorted[n - 1])));
//
//			// If all values are almost identical, do not show outlier marks.
////			if (fullRange <= 1e-12 * scale)
////			   return "";
//	   double rangeRatio = fullRange / iqr;
//
//	   // Normal-looking histograms: no red marks.
////	   if (rangeRatio < SHOW_OUTLIER_RANGE_IQR)
////	      return "";
//
//	   double lower = q1 - BIG_OUTLIER_IQR * iqr;
//	   double upper = q3 + BIG_OUTLIER_IQR * iqr;

//	   int leftTotal = 0;
//	   while (leftTotal < n && sorted[leftTotal] < lower)
//	      leftTotal++;
//
//	   int rightTotal = 0;
//	   while (rightTotal < n && sorted[n - 1 - rightTotal] > upper)
//	      rightTotal++;
	   
//	   double median = sorted[n / 2];
//	   double minDistFromCenter = 0.05 * fullRange;  // 5% of total data range
//
//	   int leftTotal = 0;
//	   while (leftTotal < n
//	         && sorted[leftTotal] < lower
//	         && Math.abs(sorted[leftTotal] - median) > minDistFromCenter)
//	      leftTotal++;
//
//	   int rightTotal = 0;
//	   while (rightTotal < n
//	         && sorted[n - 1 - rightTotal] > upper
//	         && Math.abs(sorted[n - 1 - rightTotal] - median) > minDistFromCenter)
//	      rightTotal++;
//
//	   int total = leftTotal + rightTotal;
//
//	   if (total == 0)
//	      return "";

//	   int maxMarks=2;

//	   if (rangeRatio >= 40.0)
//	      maxMarks = 8;
//	   else if (rangeRatio >= 25.0)
//	      maxMarks = 4;
//	   else
//	      maxMarks = 2;

//	   int take = Math.min(total, maxMarks);
//	   int leftTake = Math.min(leftTotal, take / 2);
//	   int rightTake = Math.min(rightTotal, take - leftTake);
//	   // If one side has fewer outliers, give the remaining marks to the other side.
//	   leftTake = Math.min(leftTotal, take - rightTake);
	   
	   int leftTake = 2;//Math.min(leftTotal, maxMarks/2);
	   int rightTake =2;// Math.min(rightTotal, maxMarks/2);

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
   
   private static double getCenteringShift(String model, int s) {
	   if (model.equals("Oscillatory"))
	      return exactOscillatoryGenz(s);

	   if (model.equals("Gaussian"))
		      return exactIntegralGaussian(s);

	   return 0.0;
	}
   
   public static double exactIntegralGaussian(int s) {
	   	return Math.pow(1.462651745907181, s);
   }

	private static double exactOscillatoryGenz(int s) {
	   double prod = 1.0;

	   for (int j = 1; j <= s; j++) {
	      double a = (double) j / s;
	      prod *= 2.0 * Math.sin(a / 2.0) / a;
	   } 

	   return prod * Math.cos((s + 1.0) / 4.0);
	}
}














