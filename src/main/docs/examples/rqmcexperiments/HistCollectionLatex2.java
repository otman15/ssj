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
 * Generates LaTeX/PGFPlots histogram collections from estimator observations
 * stored in {@code .dat} files. The class writes one LaTeX article for each
 * model tag. Within each article, it creates a comparison table for every
 * configured dimension and comparison-page entry. Methods form the table rows,
 * while values of {@code k}, representing sample sizes {@code n = 2^k}, form
 * the columns. The tables use LaTeX {@code longtable} environments so method
 * rows can continue onto additional letter-sized pages.
 *
 * <p>Configure the following values in {@link #main(String[])}:</p>
 * <ul>
 *   <li>the input and output folders;</li>
 *   <li>the model tags, dimensions {@code s}, values of {@code k}, and
 *       observation count {@code m};</li>
 *   <li>the histogram bin count and the numbers of extreme observations to
 *       mark on the left and right;</li>
 *   <li>the comparison-page titles and comma-separated method names. Each row
 *       of {@code pages} contains one title followed by its method list.</li>
 * </ul>
 *
 * <p>The expected input name for each model, dimension, method, {@code k}, and
 * {@code m} is produced by {@link #fileNameMaker(String, int, String, int, int)}.
 * Customize that method when the data files follow another naming convention.
 * Customize {@link #outputFileName(String)} to change generated {@code .tex}
 * names, {@link #makePageTitle(String, int, String, int)} to change table
 * headings, and {@link #writeLatexHeader(PrintWriter)} or
 * {@link #makeHistogramLatex(File, int, int, int)} to adjust document layout
 * or histogram styling.</p>
 */

public class HistCollectionLatex2 {

   File inputFolder;
   File outputFolder;
   String[] modelTags;
   int[] sDims;
   int[] ks;
   int m;
   String[][] pages;
   int rightExtMark;
   int leftExtMark;
   int numBins;

   /**
    * Creates a histogram collection generator with settings shared by all
    * configured models.
    *
    * @param inputFolder directory containing the input {@code .dat} files
    * @param outputFolder directory in which LaTeX files are written
    * @param modelTags model or function identifiers to process
    * @param sDims dimensions to process for every model
    * @param ks exponents defining the column sample sizes {@code n = 2^k}
    * @param m observation count used in input names and page titles
    * @param pages comparison definitions; each row contains a title and a
    *        comma-separated method list
    * @param leftExtMark number of smallest observations marked in each plot
    * @param rightExtMark number of largest observations marked in each plot
    * @param numBins number of histogram bins
    */
   public HistCollectionLatex2(
      String inputFolder,
      String outputFolder,
      String[] modelTags,
      int[] sDims,
      int[] ks,
      int m,
      String[][] pages,
      int leftExtMark,
      int rightExtMark,
      int numBins) {

      this.inputFolder = new File(inputFolder);
      this.outputFolder = new File(outputFolder);
      this.modelTags = modelTags;
      this.sDims = sDims;
      this.ks = ks;
      this.m = m;
      this.pages = pages;
      this.rightExtMark = rightExtMark;
      this.leftExtMark = leftExtMark;
      this.numBins = numBins;
   }


   /**
    * Configures an example histogram collection and writes its LaTeX files.
    *
    * @param args command-line arguments; not used
    * @throws IOException if an input or output file cannot be accessed
    */
   public static void main(String[] args) throws IOException {
      String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String outputFolder = "/home/otman/Documents/GitHub/Data/samo25-test/latexNewConfig/";

      // String[] modelTags = new String[] {"Polynomial", "PieceLinGauss",
      //      "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
      //      "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
      //   };

      String[] modelTags = new String[] {"SmoothPerB4"};



      int[] sDims = new int[] {2, 8, 32};
      int m = 10000;
      int[] ks = new int[] {10, 12, 16};
      int leftExtMark = 2;
      int rightExtMark =2;
      int numBins = 100;

      String methodes1Title = "Rank-1 lattice - sobol";
      String methods1 = "Lat-RS,Lat-RSB,Lat-Rv,Lat-Rpv,Lat-RvRS,Lat-RvRSB,Lat-RpvRS,Lat-RpvRSB,Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS";

      // String methodes2Title = "Sobol";
      // String methods2 = "Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS";

      // Each row contains a page title and its comma-separated method names.
      String[][] pages = new String[][] {
         {methodes1Title, methods1}
         // {methodes2Title, methods2}
      };

      HistCollectionLatex2 samo25Config = new HistCollectionLatex2(
         inputFolder, outputFolder,
         modelTags, sDims, ks, m,
         pages,
         leftExtMark, rightExtMark, numBins

      );

      samo25Config.writeCollection();
   }

   /**
    * Creates the output directory and writes one complete LaTeX file for each
    * configured model tag.
    *
    * @throws IOException if an output or input data file cannot be accessed
    */
   public void writeCollection() throws IOException {
      this.outputFolder.mkdirs();

      for (String modelTag : this.modelTags) {
         this.writeModelFile(modelTag, this.sDims, this.pages);
      }
   }

   /**
    * Writes the complete LaTeX document for one model across the supplied
    * dimensions and comparison pages.
    *
    * @param modelTag model to process
    * @param sDims dimensions to include in the model document
    * @param pages comparison definitions; each row contains a title and a
    *        comma-separated method list
    * @throws IOException if the output file or an input data file cannot be accessed
    */
   private void writeModelFile(String modelTag, int[] sDims, String[][] pages) throws IOException {
      File outFile = new File(this.outputFolder, outputFileName(modelTag));

      try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
         writeLatexHeader(out);

         for (int s : sDims) {

            for (String[] page : pages) {
               this.writeComparisonPage(out, modelTag, s, page);
            }
         }

         writeLatexFooter(out);
      }

      System.out.println("LaTeX file created:");
      System.out.println(outFile.getAbsolutePath());
   }

   /**
    * Writes one comparison table for a model and dimension, followed by a
    * LaTeX page break. The page entry supplies the displayed title and the
    * methods used as table rows.
    *
    * @param out output writer for the model's LaTeX file
    * @param modelTag current model
    * @param s current dimension
    * @param page two-element array containing the page title and comma-separated methods
    * @throws IOException if an input data file cannot be read
    */
   private void writeComparisonPage(
         PrintWriter out,
         String modelTag,
         int s,
         String[] page) throws IOException {

      String pageTitle = makePageTitle(
         modelTag,
         s,
         page[0],
         this.m
      );

      this.writeHistogramPageBody(
         out,
         modelTag,
         s,
         pageTitle,
         page[1].split(",")
      );

      out.println("\\clearpage");
      out.println();
   }

   /**
    * Writes one breakable {@code longtable} of histograms.
    *
    * Rows correspond to the supplied methods and columns correspond to the
    * configured values of {@code k}, where {@code n = 2^k}. The first table
    * header contains the page title; continuation pages reserve the same title
    * space with a LaTeX phantom. Missing input files produce a labeled table
    * cell and a console message.
    *
    * @param out output writer for the LaTeX file
    * @param modelTag current model used to construct input file names
    * @param s current dimension used to construct input file names
    * @param pageTitle title printed above the histogram grid
    * @param methods method names to show as rows
    * @throws IOException if a data file cannot be read
    */
   private void writeHistogramPageBody(
         PrintWriter out,
         String modelTag,
         int s,
         String pageTitle,
         String[] methods) throws IOException {

      out.println("\\sethistwidths{" + this.ks.length + "}");

      out.print("\\begin{longtable}{@{}>{\\centering\\arraybackslash}p{\\histmethodwidth}");
      for (int i = 0; i < this.ks.length; i++) {
         out.print("@{}>{\\centering\\arraybackslash}p{\\histcellwidth}");
      }
      out.println("@{}}");

      String titleLatex =
         "\\scriptsize\\textbf{" + escapeLatex(pageTitle) + "}";
      String phantomTitleLatex = "\\phantom{" + titleLatex + "}";

      out.println("\\multicolumn{" + (this.ks.length + 1)
            + "}{c}{"
            + titleLatex
            + "} \\\\[2mm]");

      out.print("{}");
      for (int k : this.ks) {
         out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{{\\scriptsize $n=2^{" + k + "}$}}");
      }
      out.println(" \\\\[1.5mm]");
      out.println("\\endfirsthead");

      out.println("\\multicolumn{" + (this.ks.length + 1)
            + "}{c}{"
            + phantomTitleLatex
            + "} \\\\[2mm]");

      out.print("{}");
      for (int k : this.ks) {
         out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{{\\scriptsize $n=2^{" + k + "}$}}");
      }
      out.println(" \\\\[1.5mm]");
      out.println("\\endhead");

      for (String method : methods) {

         out.print("\\raisebox{0.7cm}{\\rotatebox{90}{\\scriptsize "
               + escapeLatex(method) + "}}");

         for (int k : this.ks) {
            String fileName = fileNameMaker(modelTag, s, method, k, this.m);
            File file = new File(this.inputFolder, fileName);

            if (!file.exists()){
               System.out.println("Missing file: " + fileName);
               out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{{\\tiny Missing}}");
               continue;
            }

            out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{");
            out.print(makeHistogramLatex(file, this.numBins, this.rightExtMark, this.leftExtMark));
            out.println("}");
         }

         out.println("\\\\[1.5mm]");
      }

      out.println("\\end{longtable}");
   }

   /**
    * Builds the title displayed above a comparison table.
    *
    * @param model model tag
    * @param s dimension
    * @param pageTitle configured comparison title
    * @param m observation count used to derive the displayed base-10 exponent
    * @return formatted comparison-page title
    */
   private static String makePageTitle(String model, int s, String pageTitle, int m) {
      int mExp = (int) Math.log10(m);
      return "RQMC " + pageTitle + " comparison: "
            + model + " s = " + s + " ($10^{" + mExp + "}$ samples)";
   }
   
   /**
    * Builds the PGFPlots LaTeX code for one histogram.
    *
    * The plot includes a title derived from the input file name, summary
    * statistics in a legend, and marks for selected extreme observations.
    *
    * @param file input {@code .dat} file
    * @param numBins number of histogram bins
    * @param rightExtMark number of largest observations to mark
    * @param leftExtMark number of smallest observations to mark
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read or has no observations
    */
   private static String makeHistogramLatex(File file, int numBins, int rightExtMark, int leftExtMark) throws IOException {

      HistogramData data = buildHistogramData(file, numBins);

      TallyStore fileStats = data.stats;
      TallyHistogram hist = data.hist;
      double[] values = data.values;
      int n = data.n;
      double xmin = data.xmin;
      double xmax = data.xmax;
      String legendPos = data.legendPos;

      ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);
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
    		   "width=" + "\\histaxiswidth" + ", height="+"\\histaxisheight"+ ","  +
            "scale only axis, " +  // Width and height apply only to the axis rectangle, excluding labels.
                                   // Remove this if y-axis labels or other outer decorations are added,
                                   // because they can extend outside the cell and overlap nearby plots.
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

      String extremeMarks = getExtremeMarks(values, n, leftExtMark, rightExtMark );

      if (!extremeMarks.isEmpty()) {
         latex = latex.replace("\\end{axis}", extremeMarks + "\n\\end{axis}");
      }

      return latex;
   }

   /**
    * Reads observations and builds the statistics, plot bounds, histogram, and
    * legend placement used to render one plot.
    *
    * @param file input {@code .dat} file
    * @param numBins number of histogram bins
    * @return data needed to render the histogram
    * @throws IOException if the file cannot be read or contains no observations
    */
   private static HistogramData buildHistogramData(File file, int numBins) throws IOException {
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

      TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins);
      hist.fillFromTallyStore(fileStats);

      int[] counts = hist.getCounters();

      int leftSum = 0;
      int rightSum = 0;
      int q = counts.length / 4;

      for (int i = 0; i < q; i++) {
         leftSum += counts[i];
         rightSum += counts[counts.length - 1 - i];
      }

      double legendMoveRatio = 1.9;
      String legendPos = "north east";

      if (rightSum > legendMoveRatio * Math.max(1, leftSum))
         legendPos = "north west";

      HistogramData data = new HistogramData();
      data.stats = fileStats;
      data.hist = hist;
      data.values = fileStats.getArray();
      data.n = fileStats.numberObs();
      data.xmin = xmin;
      data.xmax = xmax;
      data.legendPos = legendPos;

      return data;
   }

   /**
    * Generates PGFPlots marks for selected extreme observations.
    *
    * The method sorts the observations and marks the requested number of
    * smallest and largest values with red vertical dashes at {@code y = 0}.
    *
    * @param values observation array
    * @param n number of valid observations in the array
    * @param left number of smallest observations to mark
    * @param right number of largest observations to mark
    * @return LaTeX code for the extreme-value marks, or an empty string if unavailable
    */
   private static String getExtremeMarks(double[] values, int n, int left, int right) {
      if (n < 4)
         return "";

      double[] sorted = Arrays.copyOf(values, n);
      Arrays.sort(sorted);

      StringBuilder coords = new StringBuilder();

      for (int i = 0; i < left; i++) {
         coords.append("(")
               .append(texNum(sorted[i]))
               .append(",0) ");
      }

      for (int i = n - right; i < n; i++) {
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

   /**
    * Writes the LaTeX document header.
    *
    * The generated document uses letter paper and loads the packages required
    * for PGFPlots histograms, graphical transformations, and breakable tables.
    *
    * @param out output writer for the LaTeX file
    */
   private static void writeLatexHeader(PrintWriter out) {
      out.println("\\documentclass[letterpaper]{article}");
      out.println("\\usepackage[margin=0.2in]{geometry}");
      out.println("\\usepackage{amsmath}");
      out.println("\\usepackage{graphicx}");
      out.println("\\usepackage{array}");
      out.println("\\usepackage{longtable}");
      out.println("\\usepackage{pgfplots}");
      out.println("\\pgfplotsset{compat=1.18}");
      out.println("\\pagestyle{empty}");
      out.println();
      out.println("\\newlength{\\histmethodwidth}");
      out.println("\\newlength{\\histcellwidth}");
      out.println("\\newlength{\\histaxiswidth}");
      out.println("\\newlength{\\histaxisheight}");
      out.println("\\setlength{\\histmethodwidth}{0.2cm}");
      out.println("\\newcommand{\\sethistwidths}[1]{%");
      out.println("  \\setlength{\\histcellwidth}{\\dimexpr(\\textwidth-\\histmethodwidth)/#1\\relax}%");
      out.println("  \\setlength{\\histaxiswidth}{0.96\\histcellwidth}%");
      out.println("  \\setlength{\\histaxisheight}{0.86\\histaxiswidth}%");
      out.println("}");
      out.println();
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
    * Converts a data file name into a plot title.
    *
    * The resulting title omits the {@code .dat} extension and trailing
    * observation count.
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
    * Defines the output LaTeX file name for a model using the pattern
    * {@code modelTag-hist.tex}.
    *
    * @param modelTag model identifier
    * @return output file name relative to the configured output folder
    */
   private static String outputFileName(String modelTag){
      return modelTag +"-hist.tex";
   }

   /**
    * Defines the expected input data file name using the pattern
    * {@code modelTag-s-method-k-m.dat}.
    *
    * @param modelTag model identifier
    * @param s dimension
    * @param method method identifier
    * @param k exponent defining the sample size {@code n = 2^k}
    * @param m observation count
    * @return input file name relative to the configured input folder
    */
   private static String fileNameMaker(String modelTag, int s, String method, int k, int m){
      return modelTag + "-" + s + "-" + method + "-" + k + "-" + m + ".dat";
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
    * Escapes underscores for use in LaTeX text.
    *
    * @param s input string
    * @return LaTeX-safe string
    */
   private static String escapeLatex(String s) {
      return s.replace("_", "\\_");
   }

   /** Holds the statistics and layout values needed to render one histogram. */
   private static class HistogramData {
      TallyStore stats;
      TallyHistogram hist;
      double[] values;
      int n;
      double xmin;
      double xmax;
      String legendPos;
   }

}
