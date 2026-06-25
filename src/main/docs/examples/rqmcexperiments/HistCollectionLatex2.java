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
 * Generates standalone LaTeX/PGFPlots documents containing collections of
 * histograms for QMC or RQMC estimators. One LaTeX file is written for each
 * configured model. For every model and dimension {@code s}, the document
 * contains comparison pages for configured groups of methods. Methods appear
 * as rows and values of {@code k}, representing sample sizes {@code n = 2^k},
 * appear as columns. A comparison page can be divided into several tabular
 * pages according to {@code maxRowsPerPage}.
 *
 * <p>Input {@code .dat} files are not discovered by scanning the input
 * directory. Their expected names are constructed with
 * {@code dataFileNameMaker}, and each resulting path is then checked in the
 * configured input directory. Observations are plotted as stored; any required
 * preparation, centering, or shifting must already have been applied to the
 * data files.</p>
 *
 * <p>The {@link #main(String[])} method is the configuration entry point. It
 * specifies the input and output folders, the models or functions to process,
 * dimensions {@code s}, values of {@code k}, the observation count {@code m},
 * comparison-page titles and method lists, and {@code maxRowsPerPage} for
 * splitting large method groups. It also configures {@code leftExtMark} and
 * {@code rightExtMark}, the {@code dataFileNameMaker} input naming rule, and
 * {@code outputFileNamePattern} for generated {@code .tex} file names.</p>
 */

public class HistCollectionLatex2 {

   private static final int NUM_BINS = 100; // Number of bins used for every histogram.
   private static final String HIST_CELL_WIDTH = "5.2cm"; // Fixed histogram-column width for table alignment.
   private static final String AXIS_WIDTH = "6.5cm"; // LaTeX plot option.
   private static final String AXIS_HEIGHT = "5.5cm"; // LaTeX plot option.

   /** Configures the histogram collection and writes its LaTeX documents. */
   public static void main(String[] args) throws IOException {
      String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String outputFolder = "/home/otman/Documents/GitHub/Data/samo25-test/latexNewConfig/";

     // String[] modelTags = new String[] {"Polynomial", "PieceLinGauss"};
      //    "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
      //    "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
      // };
      String[] modelTags = new String[] {"Gaussian"};

      int[] sDims = new int[] {2, 4};//, 8, 16, 32};
      int m = 10000;
      int[] ks = new int[] {10, 12, 14, 16};
      int leftExtMark = 2;
      int rightExtMark =2;

      String methodes1Title = "Rank-1 lattice";
      String methods1 = "Lat-RS,Lat-RSB,Lat-Rv,Lat-Rpv,Lat-RvRS,Lat-RvRSB,Lat-RpvRS,Lat-RpvRSB";
      int maxRowsPerPage = 4;

      String methodes2Title = "Sobol";
      String methods2 = "Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS";

      ComparisonPage[] pages = new ComparisonPage[] {
         new ComparisonPage( methodes1Title, methods1, maxRowsPerPage),
         new ComparisonPage(methodes2Title ,methods2, maxRowsPerPage )
      };

      DataFileNameMaker dataFileNameMaker = (modelTag, s, method, k, numObs) ->
            modelTag + "-" + s + "-" + method + "-" + k + "-" + numObs + ".dat";
      String outputFileNamePattern = "%s-hist.tex";

      HistConfig samo25Config = HistConfig.create(
         inputFolder, outputFolder,
         modelTags, sDims, ks, m,
         pages,
         leftExtMark, rightExtMark,
         outputFileNamePattern,
         dataFileNameMaker

      );

      writeCollection(samo25Config);
   }

   /**
    * Writes the configured histogram collection, producing one LaTeX file for
    * each model.
    *
    * @param config folders, naming rules, plot settings, and models to process
    * @throws IOException if an output or input data file cannot be accessed
    */
   public static void writeCollection(HistConfig config) throws IOException {
      config.outputFolder.mkdirs();

      for (ModelConfig modelConfig : config.models) {
         writeModelFile(config, modelConfig);
      }
   }

   /**
    * Writes the complete standalone LaTeX document for one model.
    *
    * @param config folders, naming rules, and plot settings
    * @param modelConfig model, dimensions, sample sizes, and comparison pages
    * @throws IOException if the output file or an input data file cannot be accessed
    */
   private static void writeModelFile(HistConfig config, ModelConfig modelConfig) throws IOException {
      File outFile = new File(config.outputFolder, String.format(config.outputFileNamePattern, modelConfig.modelTag));

      try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
         writeLatexHeader(out);

         for (int s : modelConfig.sDims) {

            for (ComparisonPage page : modelConfig.pages) {
               writeComparisonPage(out, config, modelConfig, s, page);
            }
         }

         writeLatexFooter(out);
      }

      System.out.println("LaTeX file created:");
      System.out.println(outFile.getAbsolutePath());
   }

   /**
    * Writes the tabular page or pages for one method group at one dimension.
    * The methods are split into chunks of at most {@code maxRowsPerPage}; a
    * nonpositive limit leaves the group on one page.
    *
    * @param out output writer for the model's LaTeX file
    * @param config input naming and plot settings
    * @param modelConfig current model configuration
    * @param s current dimension
    * @param page comparison-page title, methods, and row limit
    * @throws IOException if an input data file cannot be read
   */
   private static void writeComparisonPage(
         PrintWriter out,
         HistConfig config,
         ModelConfig modelConfig,
         int s,
         ComparisonPage page) throws IOException {

      String pageTitle = makePageTitle(
         modelConfig.modelTag,
         s,
         page.title,
         modelConfig.m
      );

      // A nonpositive limit means that the method group is not split.
      int rowsPerPage = page.maxRowsPerPage <= 0
            ? page.methods.length
            : page.maxRowsPerPage;

      for (int start = 0; start < page.methods.length; start += rowsPerPage) {
         int end = Math.min(start + rowsPerPage, page.methods.length);
         String[] methodsChunk = Arrays.copyOfRange(page.methods, start, end);

         // Later chunks hide the title with \phantom to preserve page height.
         boolean showTitle = (start == 0);

         writeHistogramPageBody(
            out,
            config,
            modelConfig,
            s,
            pageTitle,
            methodsChunk,
            rowsPerPage,
            showTitle
         );

         out.println();
      }
   }

   /**
    * Writes the body of one tabular histogram page.
    *
    * Rows correspond to the supplied methods and columns correspond to the
    * model's values of {@code k}, where {@code n = 2^k}. Each histogram cell
    * is placed in a fixed-width box to keep the page layout aligned across
    * method groups.
    * Invisible rows pad short chunks to {@code targetRows}, and a hidden title
    * uses {@code \phantom} so split pages retain consistent dimensions.
    *
    * @param out output writer for the LaTeX file
    * @param config input folder, naming rule, and histogram plot settings
    * @param modelConfig current model configuration
    * @param s current dimension used to construct input file names
    * @param pageTitle title printed above the histogram grid
    * @param methods method names to show as rows
    * @param targetRows row count used to keep split pages the same height
    * @param showT whether to display the title instead of a same-size phantom
    * @throws IOException if a data file cannot be read
    */
   private static void writeHistogramPageBody(
         PrintWriter out,
         HistConfig config,
         ModelConfig modelConfig,
         int s,
         String pageTitle,
         String[] methods,
         int targetRows,
         boolean showTitle) throws IOException {

      out.print("\\begin{tabular}{@{}c");
      for (int i = 0; i < modelConfig.ks.length; i++) {
    	  out.print("@{}c");
      }
      out.println("@{}}");

      String titleLatex =
      "\\scriptsize\\textbf{" + escapeLatex(pageTitle) + "}";

      if (!showTitle)
         titleLatex = "\\phantom{" + titleLatex + "}";

      out.println("\\multicolumn{" + (modelConfig.ks.length + 1)
            + "}{c}{"
            + titleLatex
            + "} \\\\[2mm]");
      
      out.print("{}");
      for (int k : modelConfig.ks) {
    	  out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{{\\scriptsize $n=2^{" + k + "}$}}");
      }
      out.println(" \\\\[1.5mm]");

      for (String method : methods) {

    	   out.print("\\raisebox{0.7cm}{\\rotatebox{90}{\\scriptsize "
    		      + escapeLatex(method) + "}}");
         for (int k : modelConfig.ks) {
            String fileName = config.dataFileNameMaker.make(modelConfig.modelTag, s, method, k, modelConfig.m);
            File file = new File(config.inputFolder, fileName);

            if (!file.exists()){
               System.out.println("Missing file: " + fileName);
               out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{{\\tiny Missing}}");
               continue;
            }

            out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{");
            out.print(makeHistogramLatex(file, config));
            out.println("}");
         }

         out.println("\\\\[1.5mm]");
      }

      // Invisible rows keep every chunk of a split comparison at the same height.
      for (int i = methods.length; i < targetRows; i++) {
         out.print("\\rule{0pt}{" + AXIS_HEIGHT + "}");

         for (int j = 0; j < modelConfig.ks.length; j++) {
            out.print(" & \\makebox[" + HIST_CELL_WIDTH + "][c]{\\rule{0pt}{" + AXIS_HEIGHT + "}}");
         }

         out.println("\\\\[1.5mm]");
      }


      out.println("\\end{tabular}");
   }

   private static ModelConfig[] makeModelConfigs(
         String[] modelTags,
         int[] sDims,
         int[] ks,
         int m,
         ComparisonPage[] pages) {

      ModelConfig[] models = new ModelConfig[modelTags.length];

      for (int i = 0; i < modelTags.length; i++) {
         ModelConfig modelConfig = new ModelConfig();

         modelConfig.modelTag = modelTags[i];
         modelConfig.sDims = sDims;
         modelConfig.ks = ks;
         modelConfig.m = m;
         modelConfig.pages = pages;

         models[i] = modelConfig;
      }

      return models;
   }

   private static String makePageTitle(String model, int s, String pageTitle, int m) {
      int mExp = (int) Math.log10(m);
      return "RQMC " + pageTitle + " comparison: "
            + model + " s = " + s + " ($10^{" + mExp + "}$ samples)";
   }
   
   /**
    * Builds the LaTeX code for a single histogram.
    *
    * The method builds the histogram data, converts the SSJ histogram to
    * PGFPlots LaTeX code, and adds red marks for the configured extreme
    * observations.
    *
    * @param file input {@code .dat} file
    * @param config plot settings, including the extreme-mark counts
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read
    */
   private static String makeHistogramLatex(File file, HistConfig config) throws IOException {

      HistogramData data = buildHistogramData(file);

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

      String extremeMarks = getExtremeMarks(values, n, config.leftExtMark, config.rightExtMark );

      if (!extremeMarks.isEmpty()) {
         latex = latex.replace("\\end{axis}", extremeMarks + "\n\\end{axis}");
      }

      return latex;
   }

   /**
    * Reads prepared observations and builds the statistics, histogram bounds,
    * histogram counts, and legend placement used to render one plot. The
    * observation values themselves are not centered or shifted.
    *
    * @param file input {@code .dat} file
    * @return data needed to render the histogram
    * @throws IOException if the file cannot be read or contains no observations
    */
   private static HistogramData buildHistogramData(File file) throws IOException {
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

      TallyHistogram hist = new TallyHistogram(xmin, xmax, NUM_BINS);
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
    * The generated document uses the {@code standalone} class with one cropped
    * page per tabular environment and loads the packages required for PGFPlots
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
   
   /** Defines the expected input file naming rule. */
   public interface DataFileNameMaker {
      String make(String modelTag, int s, String method, int k, int m);
   }

   public static class HistConfig {
      File inputFolder;
      File outputFolder;
      DataFileNameMaker dataFileNameMaker;
      ModelConfig[] models;
      int rightExtMark;
      int leftExtMark;
      String outputFileNamePattern;

      /** Creates a complete histogram configuration from the settings in {@code main}. */
      static HistConfig create(
            String inputFolder,
            String outputFolder,
            String[] modelTags,
            int[] sDims,
            int[] ks,
            int m,
            ComparisonPage[] pages,
            int leftExtMark,
            int rightExtMark,
            String outputFileNamePattern,
            DataFileNameMaker dataFileNameMaker) {

         HistConfig config = new HistConfig();
         config.inputFolder = new File(inputFolder);
         config.outputFolder = new File(outputFolder);
         config.dataFileNameMaker = dataFileNameMaker;
         config.models = makeModelConfigs(modelTags, sDims, ks, m, pages);
         config.rightExtMark = rightExtMark;
         config.leftExtMark = leftExtMark;
         config.outputFileNamePattern = outputFileNamePattern;
         return config;
      }
   }

   private static class ModelConfig {
      String modelTag;
      int[] sDims;
      int[] ks;
      int m;
      ComparisonPage[] pages;
   }

   public static class ComparisonPage {
      String title;
      int maxRowsPerPage;
      String[] methods;

      ComparisonPage(String title, String methods, int maxRowsPerPage) {
         this.title = title;
         this.methods = methods.split(",");
         this.maxRowsPerPage = maxRowsPerPage;
      }
   }

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