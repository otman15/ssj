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
 * Suppose you have a collection of data files, each one containing a list of
 * observations from a given distribution, and you want to create a document
 * that shows a histogram of the empirical distribution, for each file. This
 * class contains tools to do that. It assumes that the data files are
 * classified with multiple parameters, and organize the histograms with a
 * multidimensional ordering based on these parameters.
 *
 * This class is actually more specific. It assumes that each data file contains
 * $m$ observations of a QMC or RQMC estimator for a given integrand (model), a
 * given number @f$s@f$ of dimensions, a given RQMC method, and a given number
 * of points @f$n = 2^k@f$. These four parameters classify the data sets and the
 * histograms in a four-dimensional array. The histograms will be regrouped by
 * model at the highest level, then by dimension @f$s@f$, by RQMC method, and by
 * value of @f$k@f$. For each model, the program constructs a LaTeX file to
 * produce a .pdf document that displays the histograms in that order, so we can
 * easily visualize and compare the distributions across different methods and
 * values of @f$k@f$ for the same function. All the histograms for a given value
 * of @f$s@f$ are put together in a large table, usually with one row for all
 * values of @f$k@f$ for each method. The rows of that table can cover several
 * letter-sized pages if many RQMC methods are considered.
 *
 * The data files in the input directory are assumed to be named as follows.
 * Each model is identified by a short string called the model tag. Each file
 * name starts with its model tag, then the value of @f$s@f$, then the
 * identifier of the method, then the value of @f$k@f$, then the number @f$m@f$
 * of observations in the file. All the fields are separated by the character
 * `-`, The file extension is `.dat`. For example,
 * `Gaussian-2-Lat-RS-8-10000.dat` will be an input file that contains 10000
 * observations (real numbers) and nothing else, for the model `Gaussian` in 2
 * dimensions, obtained with the `Lat-RS` RQMC method, with @f$n = 2^8@f$ RQMC
 * points.
 *
 * The class provides two main entry points. The method
 * {@link #writeCollection} generates complete LaTeX documents containing
 * collections of histograms. The method {@link #makeHistogramLatex} generates
 * only the LaTeX code for one histogram from a data file path. Its title, axis
 * dimensions, legend options, and extreme marks are specified explicitly.
 *
 * For histogram collections, some default parameters can be changed beforehand
 * via `set` methods. The number of bins is 100 by default, the two smallest and
 * two largest observations are marked by default, and the built-in statistical
 * legend can be shown or hidden. Its position can be selected automatically or
 * set explicitly. The {@code showLegend} and {@code legendPos} settings apply
 * only to the built-in statistical legend used by histogram collections; custom
 * legend options passed to {@link #makeHistogramLatex} must include their own
 * legend content, style, and position.
 *
 * The program {@link HistSamo25} gives an example of how to use this
 * class. Before calling any method, one must specify the input and output
 * folders that contain the data files and the generated histograms latex files,
 * respectively. Then the set of model tags, the set of method names, the set of
 * dimensions @f$s@f$, the set of values of @f$k@f$, and the number of observations
 * per histogram, must also be defined, to be passed as parameters. The method
 * {@code fileNameMaker} in the program will construct a file name as described
 * above for each combination of model, dimension, method, and value of @f$k@f$,
 * and search for that data file in the input folder. Output file names and page
 * titles are defined by {@code outputFileName} and {@code makePageTitle}.
 * Those helper methods can be adjusted in the source code if a different naming
 * or title convention is desired.
 *
 * This class has no public constructor; it is a static utility class, and
 * all the methods are static.
 */

public class HistCollectionLatex {

   /**
    * The numbers of smallest and largest observations marked in each plot. A small
    * red mark will indicate each of these extreme observations below the
    * histogram.
    */
   private static int[] extremeMarks = new int[] { 2, 2 };

   /**
    * Number of histogram bins.
    */
   private static int numBins = 100;

   /**
    * The position of the built-in statistical legend.
    */
   private static String legendPos = "auto";

   /**
    * Controls whether the built-in statistical legend is shown in histogram
    * collections.
    */
   private static String showLegend = "builtin";

   private HistCollectionLatex() {
      // Static utility class.
   }

   /**
    * Returns the configured numbers of marked extreme observations.
    *
    * @return two-element array containing the left and right mark values
    */
   public static int[] getExtremeMarks() {
      return new int[] { extremeMarks[0], extremeMarks[1] };
   }

   /**
    * Sets the numbers of smallest and largest observations marked in each plot.
    *
    * @param left  number of smallest observations to mark
    * @param right number of largest observations to mark
    */
   public static void setExtremeMarks(int left, int right) {
      if (left < 0 || right < 0)
         throw new IllegalArgumentException("Extreme mark counts must be nonnegative.");
      extremeMarks[0] = left;
      extremeMarks[1] = right;
   }

   /**
    * Returns the configured number of bins in the histogram.
    *
    * @return number of histogram bins
    */
   public static int getNumBins() {
      return numBins;
   }

   /**
    * Sets the number of bins in the histogram.
    *
    * @param bins number of histogram bins
    */
   public static void setNumBins(int bins) {
      if (bins <= 0)
         throw new IllegalArgumentException("Number of histogram bins must be positive.");
      numBins = bins;
   }

   /**
    * Sets the position of the built-in statistical legend. Valid values are
    * {@code auto} to choose the position from the histogram counts, and the
    * PGFPlots positions {@code north east}, {@code north west},
    * {@code south east}, {@code south west}, and {@code outer north east}.
    *
    * This setting applies only when {@code legendOptions} is {@code "builtin"}.
    * It does not modify custom legend options passed to
    * {@link #makeHistogramLatex}.
    *
    * @param pos position of the built-in statistical legend
    * @throws IllegalArgumentException if {@code pos} is not a valid legend position
    */
   public static void setLegendPos(String pos) {
      if (pos == null || (!pos.equals("auto") && !pos.equals("north east") && !pos.equals("north west")
            && !pos.equals("south east") && !pos.equals("south west")
            && !pos.equals("outer north east")))
         throw new IllegalArgumentException("Invalid legend position: " + pos);
      legendPos = pos;
   }

   /**
    * Sets whether the built-in statistical legend is shown in histogram
    * collections generated by {@link #writeCollection} or
    * {@link #writeModelFile}.
    *
    * @param option {@code "builtin"} to show the built-in statistical legend, or
    *               {@code null} to hide it
    * @throws IllegalArgumentException if {@code option} is neither
    *                                  {@code "builtin"} nor {@code null}
    */
   public static void setShowLegend(String option) {
      if (option != null && !option.equals("builtin"))
         throw new IllegalArgumentException("Show legend option must be \"builtin\" or null.");
      showLegend = option;
   }

   /**
    * Writes one complete LaTeX file for each configured model tag. The output
    * directory is created by writeModelFile if needed.
    *
    * @param inputFolder  directory containing the input {@code .dat} files
    * @param outputFolder directory in which LaTeX files are written
    * @param modelTags    model or function identifiers to process
    * @param methods      method names included in every comparison table
    * @param sDims        dimensions to process for every model
    * @param ks           exponents defining the column sample sizes
    *                     {@code n = 2^k}
    * @param m            observation count used in input names and page titles
    * @throws IOException if an output or input data file cannot be accessed
    */
   public static void writeCollection(String inputFolder, String outputFolder, String[] modelTags, String[] methods,
         int[] sDims, int[] ks, int m) throws IOException {
      for (String modelTag : modelTags) {
         writeModelFile(inputFolder, outputFolder, modelTag, methods, sDims, ks, m);
      }
   }

   /**
    * Writes the complete LaTeX document for one model across the supplied
    * dimensions, with one comparison table per dimension.
    *
    * @param inputFolder  directory containing the input {@code .dat} files
    * @param outputFolder directory in which the LaTeX file is written
    * @param modelTag     model or function identifier to process
    * @param methods      method names included as table rows
    * @param sDims        dimensions to include in the model document
    * @param ks           exponents defining the column sample sizes
    *                     {@code n = 2^k}
    * @param m            observation count used in input names and page titles
    * @throws IOException if the output file or an input data file cannot be
    *                     accessed
    */
   public static void writeModelFile(String inputFolder, String outputFolder, String modelTag, String[] methods,
         int[] sDims, int[] ks, int m) throws IOException {
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
            writeHistogramPageBody(out, inputDir, modelTag, methods, s, ks, m, pageTitle);
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
    * The title, axis dimensions, legend options, and numbers of marked extreme
    * observations are specified explicitly. A {@code null} title produces no
    * title. If {@code legendOptions} is {@code null}, no legend is generated. If
    * it is {@code "builtin"}, the built-in statistical legend is generated and
    * positioned using {@link #setLegendPos}. Any other nonempty string is
    * inserted as-is as custom PGFPlots legend options; in that case, the caller
    * is responsible for including legend entries, style, and position.
    *
    * @param filePath      path of the input {@code .dat} file
    * @param numBins       number of histogram bins
    * @param title         histogram title, or {@code null} for no title
    * @param width         PGFPlots axis width
    * @param height        PGFPlots axis height
    * @param legendOptions legend options, {@code null} for no legend,
    *                      {@code "builtin"} for the built-in statistical legend,
    *                      or custom PGFPlots legend options
    * @param extremeMarks  two-element array containing the numbers of smallest and
    *                      largest observations to mark
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read or has no observations
    */
   public static String makeHistogramLatex(String filePath, int numBins, String title,
      String width, String height, String legendOptions, int[] extremeMarks) throws IOException {
      if (numBins <= 0) throw new IllegalArgumentException("Number of histogram bins must be positive.");
      if (width == null || width.isBlank() || height == null || height.isBlank())
         throw new IllegalArgumentException("Histogram width and height must not be null or blank.");
      if (extremeMarks == null || extremeMarks.length != 2)
         throw new IllegalArgumentException("Extreme marks must contain exactly two values.");
      if (extremeMarks[0] < 0 || extremeMarks[1] < 0)
         throw new IllegalArgumentException("Extreme mark counts must be nonnegative.");

      TallyStore fileStats = new TallyStore();
      fileStats.fillFromFile(filePath);

      if (fileStats.numberObs() == 0)
         throw new IOException("No observations found in " + filePath);
      double xmin = fileStats.min();
      double xmax = fileStats.max();
      double range = xmax - xmin;
      double a = xmin;
      double b = xmax;

      if (range > 0.0) {
         b += range * 1.0e-12;
      } else {
         double fallbackRange = 1e-12 * Math.max(1.0, Math.abs(xmin));
         xmin -=  0.5 * fallbackRange;
         xmax +=  0.5 * fallbackRange;
         a = xmin;
         b = xmax;
      }
  
      TallyHistogram hist = new TallyHistogram(a, b, numBins);
      hist.fillFromTallyStore(fileStats);
      ScaledHistogram scHist = new ScaledHistogram(hist);

      String titleOptions = title == null ? "" : "title={" + escapeLatex(title) + "}, " + "title style={font=\\scriptsize}, ";

      // Width and height apply only to the axis rectangle, excluding labels. (scale only axis, )
      // If y-axis labels or other outer decorations are added, they can extend outside the cell and overlap nearby plots.
      scHist.setAxisOptions(titleOptions
            + "  width=" + width + ", height=" + height + ", scale only axis,\n"
            + "  xmin=" + (xmin - 0.01 * range) + ", xmax=" + (xmax + 0.01 * range) + ",\n"
            + "  ymin=0, ylabel={}, yticklabels={},\n"
            + "  scaled x ticks=true, minor x tick num=0, scaled y ticks=false,\n"
            + "  every x tick label/.append style={scale=0.6, transform shape},\n"
            + "  every x tick scale label/.style={"
            + "font={\\bfseries\\boldmath\\small}, " + "at={(axis description cs:1,0)}, \n"
            + "  anchor=north east, xshift=2pt, yshift=-9.2pt, inner sep=0pt},\n"
            + getLegendOptions(hist, fileStats, legendOptions));

      scHist.setAddPlotOptions("fill=blue!25, draw=blue!80!black, line width=0.03pt");
      String latex = scHist.toLatex(true, false);

      String extremeMarksLatex = addExtremeMarks(fileStats.getArray(), extremeMarks, fileStats.numberObs());
      if (!extremeMarksLatex.isEmpty()) {
         latex = latex.replace("\\end{axis}", extremeMarksLatex + "\n\\end{axis}");
      }

      return latex;
   }

   /**
    * Writes one breakable {@code longtable} of histograms.
    *
    * Rows correspond to the supplied methods and columns correspond to the
    * configured values of {@code k}, where {@code n = 2^k}. The first table header
    * contains the page title; continuation pages reserve the same title space with
    * a LaTeX phantom. Missing input files produce a labeled table cell and a
    * console message.
    *
    * @param out          output writer for the LaTeX file
    * @param inputFolder  directory containing the input data files
    * @param modelTag     current model used to construct input file names
    * @param s            current dimension used to construct input file names
    * @param ks           exponents defining the table columns
    * @param m            observation count used to construct input file names
    * @param pageTitle    title printed above the histogram grid
    * @param methods      method names to show as rows
    * @throws IOException if a data file cannot be read
    */
   private static void writeHistogramPageBody(PrintWriter out, File inputFolder, String modelTag,
         String[] methods, int s, int[] ks, int m, String pageTitle) throws IOException {
      out.println("\\sethistwidths{" + ks.length + "}");
      out.print("\\begin{longtable}{@{}>{\\centering\\arraybackslash}p{\\histmethodwidth}");
      for (int i = 0; i < ks.length; i++) {
         out.print("@{}>{\\centering\\arraybackslash}p{\\histcellwidth}");
      }
      out.println("@{}}");
      String titleLatex = "\\scriptsize\\textbf{" + pageTitle + "}";
      String phantomTitleLatex = "\\phantom{" + titleLatex + "}";
      out.println("\\multicolumn{" + (ks.length + 1) + "}{c}{" + titleLatex + "} \\\\[2mm]");
      writeKHeaderRow(out, ks);
      out.println("\\endfirsthead");
      out.println("\\multicolumn{" + (ks.length + 1) + "}{c}{" + phantomTitleLatex + "} \\\\[2mm]");

      writeKHeaderRow(out, ks);
      out.println("\\endhead");
      for (String method : methods) {
         out.print("\\raisebox{0.7cm}{\\rotatebox{90}{\\scriptsize " + escapeLatex(method) + "}}");
         for (int k : ks) {
            String fileName = fileNameMaker(modelTag, s, method, k, m);
            File file = new File(inputFolder, fileName);
            if (!file.exists()) {
               System.out.println("Missing file: " + fileName);
               out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{{\\tiny Missing}}");
               continue;
            }
            String title = cleanTitle(file.getName());
            out.print(" & \\makebox[" + "\\histcellwidth" + "][c]{");
            out.print(makeHistogramLatex(file.getAbsolutePath(), numBins, title, "\\histaxiswidth", "\\histaxisheight", showLegend, getExtremeMarks()));
            out.println("}");
         }
         out.println("\\\\[1.5mm]");
      }
      out.println("\\end{longtable}");
   }

   /**
    * Builds the title displayed above one dimension table. If m is a power of 10,
    * it is displayed as 10^n.
    *
    * @param model model tag
    * @param s     dimension
    * @param m     observation count displayed in the title
    * @return formatted table title
    */
   private static String makePageTitle(String model, int s, int m) {
      String mStr = Integer.toString(m);
      String samples = m > 0 && mStr.matches("10*") ? "$10^{" + (mStr.length() - 1) + "}$ samples" : m + " samples";
      return "RQMC comparison: " + escapeLatex(model) + " s = " + s + " (" + samples + ")";
   }

   /**
    * Places the legend on the side with fewer observations in the outer bins. It
    * returns north west when the right outer bins are much heavier by a factor of
    * 1.9; otherwise, it returns the default north east.
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
    * Builds the PGFPlots legend options for one histogram.
    *
    * If {@code legendOptions} is {@code null}, no legend is generated. If it is
    * {@code "builtin"}, the built-in statistical legend containing the variance,
    * skewness, and kurtosis is generated. Any other nonempty string is used as
    * custom PGFPlots legend options. Custom legend options are returned as-is,
    * except that they are trimmed and a trailing comma is added when missing.
    *
    * The position of the built-in legend is selected from the configured
    * {@code legendPos}. If the position is {@code "auto"}, it is selected from the
    * histogram counts. Custom legend options control their own position.
    *
    * @param hist          histogram used for the variance and automatic position
    * @param fileStats     observations used for skewness and kurtosis
    * @param legendOptions legend options, {@code null} for no legend,
    *                      {@code "builtin"} for the built-in statistical legend,
    *                      or custom PGFPlots legend options
    * @return PGFPlots legend options, or an empty string when the legend is hidden
    */
   private static String getLegendOptions(TallyHistogram hist, TallyStore fileStats,
         String legendOptions) {
      if (legendOptions == null)
         return "";

      if (legendOptions.isBlank())
         throw new IllegalArgumentException("Legend options must not be empty.");

      if (!legendOptions.equals("builtin")) {
         String options = legendOptions.trim();
         if (!options.endsWith(","))
            options += ",";
         return options + " ";
      }

      String pos = legendPos.equals("auto") ? getLegendPos(hist.getCounters()) : legendPos;

      String legend = "  \\parbox[c][0.35cm][c]{1.2cm}{\\centering\n"
            + "  \\scalebox{0.6}{\\bfseries\\boldmath\n"
            + "  \\begin{tabular}{@{}l@{}}\n"
            + "   $\\sigma^2$=" + sci(hist.variance()) + "\\\\[-1pt]\n"
            + "   $\\gamma$=" + sci(fileStats.skewness()) + "\\\\[-1pt]\n"
            + "   $\\kappa'$=" + sci(fileStats.kurtosis()) + "\n"
            + "  \\end{tabular}"
            + "}}";

      return " legend entries={{" + legend + "}},\n"
            + " legend image code/.code={},\n"
            + " legend style={\n"
            + " draw=none, " + "line width=0.1pt, " + "fill=none, " + "font=\\small, "
            + "cells={anchor=east}, " + "inner xsep=0pt, " + "inner ysep=3pt,"
            + " },\n"
            + " legend pos=" + pos + ",\n";
   }

   /**
    * Generates PGFPlots marks for selected extreme observations.
    *
    * The method sorts the observations and marks the requested number of smallest
    * and largest values with red vertical dashes at {@code y = 0}.
    *
    * @param values       observation array
    * @param extremeMarks two-element array containing the left and right mark
    *                     counts
    * @param n            number of valid observations in the array
    * @return LaTeX code for the extreme-value marks, or an empty string if
    *         unavailable
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
         coords.append("(").append(texNum(sorted[i])).append(",0) ");
      }
      for (int i = n - right; i < n; i++) {
         coords.append("(").append(texNum(sorted[i])).append(",0) ");
      }
      if (coords.length() == 0)
         return "";
      return "\n\\addplot+[only marks, mark=|, mark size=2.5pt, "
            + "mark options={red, line width=0.5pt}, forget plot] coordinates {" + coords + "};";
   }

   /**
    * Writes the LaTeX document header.
    *
    * The generated document uses letter paper and loads the packages required for
    * PGFPlots histograms, graphical transformations, and breakable tables.
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
      out.println("  \\ifdim\\histcellwidth>5.3cm");
      out.println("    \\setlength{\\histcellwidth}{5.3cm}%");
      out.println("  \\fi");
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
    * @param ks  exponents defining the table columns
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
    * The resulting title omits the {@code .dat} extension when present and removes
    * the trailing numeric suffix.
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
    * @param s        dimension
    * @param method   method identifier
    * @param k        exponent defining the sample size {@code n = 2^k}
    * @param m        observation count
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
      String s = String.format(Locale.US, "%2.2e", x);
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
