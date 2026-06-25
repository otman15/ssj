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
 * Generates standalone LaTeX/PGFPlots histogram documents from estimator
 * observations stored in {@code .dat} files. The class writes one LaTeX file
 * for each model tag. Within each file, it creates one comparison table for every
 * configured dimension. All configured methods form the table rows, while
 * values of {@code k}, representing sample sizes {@code n = 2^k}, form the
 * columns. The tables use LaTeX {@code longtable} environments so method rows
 * can continue onto additional letter-sized pages.
 *
 * <p>The public static entry points write a complete collection via {@link #writeCollection},
 * one model file via {@link #writeModelFile}, or one histogram via {@link #makeHistogramLatex}.
 * Use {@link #setNumBins(int)} and {@link #setExtremeMarks(int, int)}
 * to configure histogram bins and extreme marks before calling an entry
 * point.</p>
 *
 * <p>Configure the following values before calling the public static entry points:</p>
 * <ul>
 *   <li>the input and output folders;</li>
 *   <li>the model tags, dimensions {@code s}, values of {@code k}, and
 *       observation count {@code m};</li>
 *   <li>the histogram bin count and extreme-mark counts, using
 *       {@link #setNumBins(int)} and {@link #setExtremeMarks(int, int)}, default
 *       values are bins = 100, left marks = right marks = 2;</li>
 *   <li>the method names included as rows in every comparison table.</li>
 * </ul>
 *
 * <p>Input {@code .dat} files are not scanned from the input folder; their names
 * are constructed with {@code fileNameMaker} and then checked in that folder.
 * Output file names and page titles are defined by {@code outputFileName} and
 * {@code makePageTitle}. Those helpers must be adjusted in the source code if a different
 * naming or title convention is needed.</p>
 */
public class HistCollectionLatex {

   /**
    * The numbers of smallest and largest observations marked in each plot.
    */
   private static int[] extremeMarks = new int[] {2, 2};

   /**
    * Number of histogram bins.
    */
   private static int numBins = 100;

   private HistCollectionLatex() {
      // Static utility class.
   }

   /**
    * Returns the configured numbers of marked extreme observations.
    *
    * @return two-element array containing the left and right mark values
    */
   public static int[] getExtremeMarks() {
      return new int[] {extremeMarks[0], extremeMarks[1]};
   }

   /**
    * Sets the numbers of smallest and largest observations marked in each plot.
    *
    * @param left number of smallest observations to mark
    * @param right number of largest observations to mark
    */
   public static void setExtremeMarks(int left, int right) {
      if (left < 0 || right < 0)
         throw new IllegalArgumentException("Extreme mark counts must be nonnegative.");

      extremeMarks[0] = left;
      extremeMarks[1] = right;
   }

   /**
    * Returns the configured histogram bin count.
    *
    * @return number of histogram bins
    */
   public static int getNumBins() {
      return numBins;
   }

   /**
    * Sets the histogram bin count.
    *
    * @param bins number of histogram bins
    */
   public static void setNumBins(int bins) {
      if (bins <= 0)
         throw new IllegalArgumentException("Number of histogram bins must be positive.");

      numBins = bins;
   }

   /**
    * Writes one complete LaTeX file for each configured model tag.
    * The output directory is created by writeModelFile if needed.
    *
    * @param inputFolder directory containing the input {@code .dat} files
    * @param outputFolder directory in which LaTeX files are written
    * @param modelTags model or function identifiers to process
    * @param sDims dimensions to process for every model
    * @param ks exponents defining the column sample sizes {@code n = 2^k}
    * @param m observation count used in input names and page titles
    * @param methods method names included in every comparison table
    * @throws IOException if an output or input data file cannot be accessed
    */
   public static void writeCollection(
         String inputFolder,
         String outputFolder,
         String[] modelTags,
         int[] sDims,
         int[] ks,
         int m,
         String[] methods) throws IOException {

      for (String modelTag : modelTags) {
         writeModelFile(inputFolder, outputFolder, modelTag, sDims, ks, m, methods);
      }
   }

   /**
    * Writes the complete LaTeX document for one model across the supplied
    * dimensions, with one comparison table per dimension.
    *
    * @param inputFolder directory containing the input {@code .dat} files
    * @param outputFolder directory in which the LaTeX file is written
    * @param modelTag model or function identifier to process
    * @param sDims dimensions to include in the model document
    * @param ks exponents defining the column sample sizes {@code n = 2^k}
    * @param m observation count used in input names and page titles
    * @param methods method names included as table rows
    * @throws IOException if the output file or an input data file cannot be accessed
    */
   public static void writeModelFile(
         String inputFolder,
         String outputFolder,
         String modelTag,
         int[] sDims,
         int[] ks,
         int m,
         String[] methods) throws IOException {

      if (ks.length == 0 || sDims.length == 0)
         throw new IllegalArgumentException("ks and sDims must not be empty.");

      File inputDir = new File(inputFolder);
      File outputDir = new File(outputFolder);
      outputDir.mkdirs();
      if (!outputDir.exists() && !outputDir.mkdirs())
         throw new IOException("Could not create output directory: " + outputDir);
      File outFile = new File(outputDir, outputFileName(modelTag));

      try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
         writeLatexHeader(out);

         for (int s : sDims) {
            String pageTitle = makePageTitle(modelTag, s, m);

            writeHistogramPageBody(out, inputDir, modelTag, s, ks, m, pageTitle, methods);

            out.println("\\clearpage");
            out.println();
         }

         writeLatexFooter(out);
      }

      System.out.println("LaTeX file created:");
      System.out.println(outFile.getAbsolutePath());
   }

   /**
    * Builds the PGFPlots LaTeX code for one histogram.
    *
    * The plot includes a title derived from the input file name, summary
    * statistics in a legend, and marks for selected extreme observations.
    * The extreme marks and number of bins are class fields; use the setters
    * to configure them.
    *
    * @param file input {@code .dat} file
    *
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read or has no observations
    */
   public static String makeHistogramLatex(File file) throws IOException {
      if (file == null || !file.isFile())
         throw new IOException("Invalid input file: " + file);
      TallyStore fileStats = new TallyStore();
      fileStats.fillFromFile(file.getAbsolutePath());

      if (fileStats.numberObs() == 0)
         throw new IOException("No observations found in " + file.getAbsolutePath());

      double[] bounds = getHistogramBounds(fileStats.min(), fileStats.max());
      double xmin = bounds[0];
      double xmax = bounds[1];

      TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins);
      hist.fillFromTallyStore(fileStats);

      String legendPos = getLegendPos(hist.getCounters());

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
            "width=" + "\\histaxiswidth" + ", height=" + "\\histaxisheight" + "," +
            "scale only axis, " +  // Width and height apply only to the axis rectangle, excluding labels.
                                   // If y-axis labels or other outer decorations are added,
                                   // they can extend outside the cell and overlap nearby plots.
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

      String extremeMarksLatex = addExtremeMarks(fileStats.getArray(),
            getExtremeMarks(), fileStats.numberObs());

      if (!extremeMarksLatex.isEmpty()) {
         latex = latex.replace("\\end{axis}", extremeMarksLatex + "\n\\end{axis}");
      }

      return latex;
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
    * @param inputFolder directory containing the input data files
    * @param modelTag current model used to construct input file names
    * @param s current dimension used to construct input file names
    * @param ks exponents defining the table columns
    * @param m observation count used to construct input file names
    * @param pageTitle title printed above the histogram grid
    * @param methods method names to show as rows
    * @throws IOException if a data file cannot be read
    */
   private static void writeHistogramPageBody(
         PrintWriter out,
         File inputFolder,
         String modelTag,
         int s,
         int[] ks,
         int m,
         String pageTitle,
         String[] methods) throws IOException {

      out.println("\\sethistwidths{" + ks.length + "}");

      out.print("\\begin{longtable}{@{}>{\\centering\\arraybackslash}p{\\histmethodwidth}");
      for (int i = 0; i < ks.length; i++) {
         out.print("@{}>{\\centering\\arraybackslash}p{\\histcellwidth}");
      }
      out.println("@{}}");

      String titleLatex =
         "\\scriptsize\\textbf{" + pageTitle + "}";
      String phantomTitleLatex = "\\phantom{" + titleLatex + "}";

      out.println("\\multicolumn{" + (ks.length + 1)
            + "}{c}{"
            + titleLatex
            + "} \\\\[2mm]");

      writeKHeaderRow(out, ks);
      out.println("\\endfirsthead");

      out.println("\\multicolumn{" + (ks.length + 1)
            + "}{c}{"
            + phantomTitleLatex
            + "} \\\\[2mm]");

      writeKHeaderRow(out, ks);
      out.println("\\endhead");

      for (String method : methods) {

         out.print("\\raisebox{0.7cm}{\\rotatebox{90}{\\scriptsize "
               + escapeLatex(method) + "}}");

         for (int k : ks) {
            String fileName = fileNameMaker(modelTag, s, method, k, m);
            File file = new File(inputFolder, fileName);

            if (!file.exists()) {
               System.out.println("Missing file: " + fileName);
               out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{{\\tiny Missing}}");
               continue;
            }

            out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{");
            out.print(makeHistogramLatex(file));
            out.println("}");
         }

         out.println("\\\\[1.5mm]");
      }

      out.println("\\end{longtable}");
   }

   /**
    * Builds the title displayed above one dimension table.
    * If m is a power of 10, it is displayed as 10^n.
    *
    * @param model model tag
    * @param s dimension
    * @param m observation count displayed in the title
    * @return formatted table title
    */
   private static String makePageTitle(String model, int s, int m) {
      String mStr = Integer.toString(m);
      String samples = m > 0 && mStr.matches("10*")
            ? "$10^{" + (mStr.length() - 1) + "}$ samples"
            : m + " samples";

      return "RQMC comparison: " + escapeLatex(model) + " s = " + s
            + " (" + samples + ")";
   }

   /**
    * Expands histogram bounds around the observed min and max.
    * Uses a tiny fallback range when all observations are equal or the range is invalid.
    * This works well for centered data; for large nearly equal data, the bounds may
    * need manual adjustment in the generated LaTeX code.
    *
    * @param xmin minimum value of the observations
    * @param xmax maximum value of the observations
    * @return two-element array containing the lower and upper histogram bounds
    */
   private static double[] getHistogramBounds(double xmin, double xmax) {

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

      return new double[] {xmin, xmax};
   }

   /**
    * Places the legend on the side with fewer observations in the outer bins.
    * It returns north west when the right outer bins
    * are much heavier by a factor of 1.9; otherwise, it returns the default
    * north east.
    *
    * @param counts histogram bin counts
    * @return PGFPlots legend position
    */
   private static String getLegendPos(int[] counts) {

      int leftSum = 0;
      int rightSum = 0;
      int q = counts.length / 4;

      for (int i = 0; i < q; i++) {
         leftSum += counts[i];
         rightSum += counts[counts.length - 1 - i];
      }

      double legendMoveRatio = 1.9;

      if (rightSum > legendMoveRatio * Math.max(1, leftSum))
         return "north west";

      return "north east";
   }

   /**
    * Generates PGFPlots marks for selected extreme observations.
    *
    * The method sorts the observations and marks the requested number of
    * smallest and largest values with red vertical dashes at {@code y = 0}.
    *
    * @param values observation array
    * @param extremeMarks two-element array containing the left and right mark counts
    * @param n number of valid observations in the array
    * @return LaTeX code for the extreme-value marks, or an empty string if unavailable
    */
   private static String addExtremeMarks(double[] values, int[] extremeMarks, int n) {
      if (n < 4)
         return "";
      int left = Math.min(extremeMarks[0], n);
      int right = Math.min(extremeMarks[1], n - left);

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
    * Writes the table header row containing the configured sample sizes.
    *
    * @param out output writer for the LaTeX file
    * @param ks exponents defining the table columns
    */
   private static void writeKHeaderRow(PrintWriter out, int[] ks) {
      out.print("{}");
      for (int k : ks) {
         out.print(" & \\makebox[\\histcellwidth][c]{{\\scriptsize $n=2^{" + k + "}$}}");
      }
      out.println(" \\\\[1.5mm]");
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
    * The resulting title omits the {@code .dat} extension when present and
    * removes the trailing numeric suffix.
    *
    * @param fileName name of the input data file
    * @return cleaned title string
    */
   private static String cleanTitle(String fileName) {
      String title = fileName;

      if (title.endsWith(".dat"))
         title = title.substring(0, title.length() - 4);

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
   private static String outputFileName(String modelTag) {
      return modelTag + "-hist.tex";
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
   private static String fileNameMaker(String modelTag, int s, String method, int k, int m) {
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
    * @return text with underscores escaped
    */
   private static String escapeLatex(String s) {
      return s.replace("_", "\\_");
   }
}