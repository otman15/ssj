package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

import umontreal.ssj.stat.TallyHistogram;
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.stat.ScaledHistogram;


/**
 * Produces a LaTeX/PGFPlots document that contains a collection of histograms 
 * for the distribution of QMC or RQMC estimators for one or more functions,
 * various dimensions `s` and number of points \(n=2^k\). 
 * 
 * NOTES and QUESTIONS:
 * 
 * -- I think this program should be made general and more flexible, not only for the samo25 files.
 * Should the main function that is specific to samo25 be in a separate file?  
 * If we do that, the global variables in the class would have to be passed as parameters to the functions instead.
 * But then these functions would be more general.
 * -- It is not a good idea to scan all the .dat files in the directory, because we 
 * may want to do only one or a few functions, there may be other .dat files there that are
 * irrelevant, we may want to name the files in a different way, etc.  
 * I think all the file names that we want should be constructed in the program,
 * which should then check the directory to find them.
 * Currently there is a file hash map, but no explanation of what it does.
 * -- Currently, the main loops over a list of models. What is does for each model should perhaps
 * be put in a method, to simplify the main function ?
 * -- The directory names should be set by the user...
 * -- The option of shifting the data should probably be removed? 
 * They should already be shifted properly in the .dat files.  We can do that. 
 * -- Should the functions to make the header and footer of the file be defined elsewhere, 
 * e.g., in the stat package?
 * -- Does one `comparison page` means one value of s for one function?
 * 
 *
 * The class scans a directory of `.dat` files, groups them by model, dimension,
 * method, and sample size, then writes one stand-alone LaTeX file per model.
 * Each page compares several RQMC methods over several values of \(n=2^k\).
 *
 * The histograms are built from SSJ `TallyStore` and `TallyHistogram` objects,
 * with optional centering for models whose exact integral is known.
 */

public class HistCollectionLatex {

	private static final int NUM_BINS = 100; //Number of bins used for every histogram.
	private static final int RIGHT_EXT_MARK = 2; //Number of largest observations marked on the right side of each histogram.
	private static final int LEFT_EXT_MARK = 2; //Number of smallest observations marked on the left side of each histogram.
	private static final String HIST_CELL_WIDTH = "5.2cm"; // // Fixed width of each histogram column in the LaTeX table (controls alignment)
	private static final String AXIS_WIDTH = "6.5cm";  // latex plot option
	private static final String AXIS_HEIGHT = "5.5cm"; // latex plot option

   // Very long main function.  Say what it does.
	public static void main(String[] args) throws IOException {
	    
      String dataDir = "/home/otman/Dropbox/samo25/datapl/"; // dat files source directory 
      String latexDir = "/home/otman/Documents/GitHub/Data/samo25-test/latex-files/"; // latex output dir

      String[] modelTags = {"SmoothPerB4","SumUeU","MC2","Polynomial","Oscillatory","Gaussian","SmoothGauss","PieceLinGauss","IndSumNormal"};
      
      int[] sDims = {2, 4, 8, 16, 32};
      int m = 10000;
      int mExp = (int) Math.log10(m);
      int[] ks = {10, 12, 14, 16};

      String[][] pages = {
         {"Rank-1 lattice", "Lat-RS,Lat-RSB,Lat-Rv,Lat-Rpv,Lat-RvRS,Lat-RvRSB,Lat-RpvRS,Lat-RpvRSB"},
         {"Sobol", "Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS"}
      };

      File inputFolder = new File(dataDir);
      File outputFolder = new File(latexDir);
      outputFolder.mkdirs();

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
                     inputFolder,
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
      
   /**
    * Writes the LaTeX document header.
    *
    * The generated document uses the `standalone` class with one cropped page
    * per tabular environment and loads the packages required for PGFPlots
    * histograms and basic graphical transformations.
    *
    * @param out output writer for the LaTeX file
    */
   private static void writeLatexHeader(PrintWriter out) {
      out.println("\\documentclass[border=3pt,multi=tabular]{standalone}");
      out.println("\\usepackage{amsmath}");
      out.println("\\usepackage{graphicx}");
      out.println("\\usepackage{pgfplots}");
      out.println("\\pgfplotsset{compat=1.18}");
      out.println("\\begin{document}");
      out.println();
   }
   
   /**
    * Writes the LaTeX document footer.
    *
    * @param out output writer for the LaTeX file
    */
   private static void writeLatexFooter(PrintWriter out) {
      out.println("\\end{document}");
   }
   
   /**
    * Writes one comparison page containing a grid of histograms.
    *
    * Rows correspond to RQMC methods and columns correspond to the selected
    * values of \(k\), where \(n=2^k\). Each histogram cell is placed in a
    * fixed-width box to keep the page layout aligned across method groups.
    *
    * @param out output writer for the LaTeX file
    * @param inputFolder dir containing the files
    * @param baseTag common file-name prefix for the current model and dimension
    * @param pageTitle title printed above the histogram grid
    * @param methods method names to show as rows
    * @param ks exponents defining the sample sizes \(n=2^k\)
    * @param m number of independent replications or observations in each file
    * @param shift centering value subtracted from each observation
    * @throws IOException if a data file cannot be read
    */
   private static void writeHistogramPageBody(
         PrintWriter out,
         File inputFolder,
         String baseTag,
         String pageTitle,
         String[] methods,
         int[] ks,
         int m
         ) throws IOException {

      out.print("\\begin{tabular}{@{}c");
      for (int i = 0; i < ks.length; i++) {
    	  out.print("@{}c");
      }
      out.println("@{}}");

      out.println("\\multicolumn{" + (ks.length + 1)
            + "}{c}{\\scriptsize\\textbf{"
            + escapeLatex(pageTitle) + "}} \\\\[2mm]");
      
      out.print("{}");
      for (int k : ks) {
    	  out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{{\\scriptsize $n=2^{" + k + "}$}}");
      }
      out.println(" \\\\[1.5mm]");

      for (String method : methods) {

    	   out.print("\\raisebox{0.7cm}{\\rotatebox{90}{\\scriptsize "
    		      + escapeLatex(method) + "}}");
         for (int k : ks) {
            File file = findFile(inputFolder, baseTag, method, k, m);

            if (file == null) {
            	out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{{\\tiny Missing}}");
               continue;
            }

            out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{");
            out.print(makeHistogramLatex(file));
            out.println("}");
         }

         out.println("\\\\[1.5mm]");
      }


      out.println("\\end{tabular}");
   }

   /**
    * Finds the data file matching a model, method, sample size, and replication count.
    *
    * The expected file name has the form
    * `baseTag-method-k-m.dat`.
    *
    * @param dataDir directory containing the files
    * @param baseTag model and dimension tag
    * @param method RQMC method tag
    * @param k exponent defining \(n=2^k\)
    * @param m number of observations in the file
    * @return matching file, or `null` if no file is found
    */
   private static File findFile(File dataDir, String baseTag, String method, int k, int m) {
	   String exactName = baseTag + "-" + method + "-" + k + "-" + m + ".dat";

	   File file = new File(dataDir, exactName);

	   if (!file.exists())
	      System.out.println("Missing file: " + exactName);

	   return file;
	}
   
   /**
    * Builds the LaTeX code for a single histogram.
    *
    * The method reads the data file, applies the centering shift, computes
    * summary statistics, creates an SSJ histogram, converts it to PGFPlots
    * LaTeX code, and adds red marks for selected extreme observations.
    *
    * @param file input `.dat` file
    * @param shift value subtracted from each observation before plotting if data has to be centered
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read
    */
   private static String makeHistogramLatex(File file) throws IOException {

      TallyStore fileStats = new TallyStore();
      fileStats.fillFromFile(file.getAbsolutePath());
      if (fileStats.numberObs() == 0)
         throw new IOException("No observations found in " + file.getAbsolutePath());

      double xmin = fileStats.min();
      double xmax = fileStats.max();

      double center = 0.5 * (xmin + xmax); 
      double range = xmax - xmin;

      if (!(range > 0.0) || Double.isNaN(range) || Double.isInfinite(range)) {
         double fallbackRange = 1e-12 * Math.max(1.0, Math.abs(center));
         xmin = center - 0.5 * fallbackRange;
         xmax = center + 0.5 * fallbackRange;
      } else {
         double finalRange = 1.06 * range;
         xmin = center - 0.5 * finalRange;
         xmax = center + 0.5 * finalRange;
      }

      int n = fileStats.numberObs();
      double[] values = fileStats.getArray();

      TallyHistogram hist = new TallyHistogram(xmin, xmax, NUM_BINS);
      hist.fillFromTallyStore(fileStats); 
      ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);
      
      int[] counts = hist.getCounters(); 

      int leftSum = 0;
      int rightSum = 0;
      int q = counts.length / 4;

      for (int i = 0; i < q; i++) {
         leftSum += counts[i];
         rightSum += counts[counts.length - 1 - i];
      }

      double legendMoveRatio = 1.9;  // move legend only if right side has at least 90% more observations
      String legendPos = "north east";
      
      if (rightSum > legendMoveRatio * Math.max(1, leftSum))
         legendPos = "north west";
      
      String title = cleanTitle(file.getName());
      
      String legend =
    		   "\\parbox[c][0.35cm][c]{1.1cm}{\\centering"
    		   + "\\scalebox{0.6}{\\bfseries\\boldmath"
    		   + "\\begin{tabular}{@{}l@{}}"
    		   + "$\\sigma^2$=" + sci(hist.variance())
    		   + "\\\\[-1pt]$\\gamma$=" + sci(fileStats.skewness())
    		   + "\\\\[-1pt]$\\kappa'$=" + sci(fileStats.kurtosis())
    		   + "\\end{tabular}"
    		   + "}}";
      scHist.setAxisOptions(
    		   "title={" + escapeLatex(title) + "}, " + 
    		   "title style={font=\\scriptsize}, " + 
    		   "width=" + AXIS_WIDTH + ", height="+AXIS_HEIGHT+ ","  +
    		   "xmin=" + texNum(xmin) + ", " +
    		   "xmax=" + texNum(xmax) + ", " +
    		   "scaled x ticks=true, " +
    		   "minor x tick num=0, " +
    		   "scaled y ticks=false, " +
    		   "tick label style={font=\\small}, " + 
    		   "every x tick label/.append style={scale=0.6, transform shape}, " + 
    		   "every x tick scale label/.style={font={\\bfseries\\boldmath\\small}, at={(axis description cs:1,0)}, anchor=north east, xshift=2pt, yshift=-9.2pt, inner sep=0pt}, " +
    		   "legend entries={{" + legend + "}}, " +
    		   "legend image code/.code={}, " +
    		   "legend style={"
    		   	  + "draw=gray, "
    		      + "line width=0.1pt, "
    		      + "fill=none, "
    		      + "font=\\small, "
    		      + "cells={anchor=east}, "
    		      + "inner xsep=0pt, "
    		      + "inner ysep=3pt,"
    		   + "}, " +
    		   "legend pos=" + legendPos
    		);


      scHist.setAddPlotOptions("fill=blue, draw=blue!80!black, line width=0.03pt");

      String latex = scHist.toLatex(true, false);

      String extremeMarks = getExtremeMarks(values, n);

      if (!extremeMarks.isEmpty()) {
         latex = latex.replace("\\end{axis}", extremeMarks + "\n\\end{axis}");
      }

      return latex;
   }
   
   /**
    * Converts a data file name into a plot title.
    *
    * The `.dat` extension and trailing replication count are removed.
    *
    * @param fileName name of the input data file
    * @return cleaned title string
    */
   private static String cleanTitle(String fileName) {
      String title = fileName.substring(0, fileName.length() - 4);
      title = title.replaceFirst("-\\d+$", "");
      return title;
   }

   /**
    * Formats a number in compact scientific notation.
    *
    * The exponent is simplified by removing unnecessary zeros and plus signs.
    *
    * @param x value to format
    * @return compact scientific-notation string
    */
   private static String sci(double x) {
      String s = String.format(Locale.US, "%.1e", x);

      s = s.replace("e-0", "e-");
      s = s.replace("e+0", "e");
      s = s.replace("e+", "e");

      return s;
   }
   
   /**
    * Formats a floating-point value for LaTeX/PGFPlots coordinates.
    *
    * The value is written with up to 17 significant digits using the US locale,
    * which ensures that the decimal separator is `.` instead of `,`.
    *
    * @param x value to format
    * @return formatted numeric string
    */
   private static String texNum(double x) {
	   return String.format(Locale.US, "%.17g", x);
	}
   
   /**
    * Escapes characters that have special meaning in LaTeX.
    *
    * @param s input string
    * @return LaTeX-safe string
    */
   private static String escapeLatex(String s) {
      return s.replace("_", "\\_");
   }
   
   /**
    * Generates PGFPlots marks for selected extreme observations.
    *
    * The method sorts the observations and marks the smallest
    * `LEFT_EXT_MARK` values and the largest `RIGHT_EXT_MARK` values
    * with red vertical dashes at \(y=0\).
    *
    * @param values observation array
    * @param n number of valid observations in the array
    * @return LaTeX code for the extreme-value marks, or an empty string if unavailable
    */
   private static String getExtremeMarks(double[] values, int n) {
	   if (n < 4)
	      return "";

	   double[] sorted = Arrays.copyOf(values, n);
	   Arrays.sort(sorted);

	   StringBuilder coords = new StringBuilder();

	   for (int i = 0; i < LEFT_EXT_MARK; i++) {
	      coords.append("(")
	            .append(texNum(sorted[i]))
	            .append(",0) ");
	   }

	   for (int i = n - RIGHT_EXT_MARK; i < n; i++) {
	      coords.append("(")
	            .append(texNum(sorted[i]))
	            .append(",0) ");
	   }

	   if (coords.length() == 0)
	      return "";

	   return "\n\\addplot+[only marks, mark=|, mark size=2.5pt, "
	         + "mark options={red, line width=0.5pt}, forget plot] coordinates {"
	         + coords
	         + "};";
	}
   
}


