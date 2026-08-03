package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.TallyStore;
// import umontreal.ssj.util.Chrono;

/**
 * Generates standalone LaTeX documents that compare the distributions of the
 * average @f$A_r@f$ and median @f$M_r@f$ for SAMO 2025 experiments.
 *
 * <p>For each input data file, the program bootstraps samples of size @f$r@f$
 * and draws the histograms of @f$A_r@f$ and @f$M_r@f$ on the same PGFPlots
 * axis. Each output document contains one table per value of @f$s@f$; a table
 * may span several pages. RQMC methods appear in rows, while sample sizes
 * appear in columns, with @f$n=2^k@f$.
 */
public class HistSamo25ArMr {
   // Fixed parameters for this particular paper.
   static String inputFolder = "C:/Users/Lecuyer/Dropbox/samo25/datapl/";
   static String outputFolder = "C:/Users/Lecuyer/Dropbox/samo25/paperdat/";
   private static final int NUM_BINS = 100;
   private static final int R = 11;
   private static final int NUM_REPS = 10000;
   private static final int[] MARKS = {
      0, 99, 499, NUM_REPS - 1, NUM_REPS - 100, NUM_REPS - 500
   };

   /**
    * Builds the input data file name for one experiment configuration.
    */
   private static String fileNameMaker(
         String modelTag, int s, String method, int k, int m) {
      return modelTag + "-" + s + "-" + method + "-" + k + "-" + m + ".dat";
   }

   /**
    * Reads one data file and generates the LaTeX code for its overlaid
    * histograms of @f$A_r@f$ and @f$M_r@f$.
    */
   private static String makeArMrHistogramLatex(
         File inputFile, String title,
         RandomStream stream,
         boolean resetBeforeHistogram) throws IOException {

      TallyStore tallyInput = new TallyStore();
      tallyInput.fillFromFile(inputFile.getAbsolutePath());

      TallyStore statAver = new TallyStore();
      TallyStore statMed = new TallyStore();

      if (resetBeforeHistogram)
         stream.resetStartStream();

      MeanMedianMSE.bootstrapArMrValues(
            tallyInput, NUM_REPS, R, stream, statAver, statMed);

      return HistSamo25Paper.makeDoubleHistogramLatex(
            statAver,
            statMed,
            title,
            "pos=north east",
            NUM_BINS,
            MARKS);
   }

   /**
    * Writes a LaTeX table comparing @f$A_r@f$ and @f$M_r@f$ for one model
    * and dimension.
    *
    * <p>For each RQMC method and sample size, it reads the experiment
    * observations, bootstraps samples of size @f$r@f$, and plots the resulting
    * averages and medians as overlaid histograms.
    */
   private static void writeHistogramPageBody(
         PrintWriter out, File inputFolder,
         String modelTag, String[] methods, int s,
         int[] ks, int m, String pageTitle,
         RandomStream stream,
         boolean resetBeforeEachHistogram) throws IOException {

      out.println("\\sethistwidths{" + ks.length + "}");
      out.print(
            "\\begin{longtable}{@{}>{\\centering\\arraybackslash}p{\\histmethodwidth}");

      for (int i = 0; i < ks.length; i++)
         out.print("@{}>{\\centering\\arraybackslash}p{\\histcellwidth}");

      out.println("@{}}");

      String titleLatex = "\\scriptsize\\textbf{" + pageTitle + "}";
      String phantomTitleLatex = "\\phantom{" + titleLatex + "}";

      out.println("\\multicolumn{" + (ks.length + 1)
            + "}{c}{" + titleLatex + "} \\\\[2mm]");

      out.print("{}");
      for (int k : ks)
         out.print(" & \\makebox[\\histcellwidth][c]"
               + "{{\\scriptsize $n=2^{" + k + "}$}}");
      out.println(" \\\\[1.5mm]");

      out.println("\\endfirsthead");
      out.println("\\multicolumn{" + (ks.length + 1)
            + "}{c}{" + phantomTitleLatex + "} \\\\[2mm]");

      out.print("{}");
      for (int k : ks)
         out.print(" & \\makebox[\\histcellwidth][c]"
               + "{{\\scriptsize $n=2^{" + k + "}$}}");
      out.println(" \\\\[1.5mm]");

      out.println("\\endhead");

      for (String method : methods) {
         out.print("\\raisebox{0.7cm}{\\rotatebox{90}"
               + "{\\scriptsize " + method + "}}");

         for (int k : ks) {
            String fileName = fileNameMaker(
                  modelTag, s, method, k, m);

            File file = new File(inputFolder, fileName);

            if (!file.exists()) {
               System.out.println("Missing file: " + fileName);
               out.print(" & \\makebox[\\histcellwidth][c]"
                     + "{{\\tiny Missing}}");
               continue;
            }

            String title = method + ", $s=" + s + "$, $k=" + k + "$";
            String latexCode = makeArMrHistogramLatex(
                  file, title, stream, resetBeforeEachHistogram);

            out.print(" & \\makebox[\\histcellwidth][c]{");
            out.print(latexCode);
            out.println("}");
         }

         out.println("\\\\[1.5mm]");
      }

      out.println("\\end{longtable}");
   }

   /**
    * Creates a standalone LaTeX histogram document for one model.
    *
    * <p>The caller chooses and initializes the random stream. To reproduce the
    * same histograms, use the same stream implementation and initial seed. If
    * {@code resetBeforeEachHistogram} is {@code true}, every histogram starts
    * from that same initial stream state, independently of traversal order.
    *
    * @param inputFolder folder containing the experiment data files
    * @param outputFolder folder in which to write the LaTeX file
    * @param modelTag model identifier used in file names and titles
    * @param methods RQMC methods to include
    * @param sDims dimensions to include
    * @param ks exponents defining sample sizes @f$n=2^k@f$
    * @param m number of observations recorded in each input file
    * @param stream caller-owned random stream used for bootstrapping
    * @param resetBeforeEachHistogram if {@code true}, reset {@code stream} to
    *        the start of the stream before generating each histogram
    * @throws IOException if an input or output file cannot be accessed
    */
   public static void writeModelFile(
         String inputFolder, String outputFolder,
         String modelTag, String[] methods,
         int[] sDims, int[] ks, int m,
         RandomStream stream,
         boolean resetBeforeEachHistogram) throws IOException {

      if (ks.length == 0 || sDims.length == 0)
         throw new IllegalArgumentException(
               "ks and sDims must not be empty.");
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null.");

      File inputDir = new File(inputFolder);
      File outputDir = new File(outputFolder);

      if (!outputDir.exists() && !outputDir.mkdirs())
         throw new IOException(
               "Could not create output directory: " + outputDir);

      File outFile = new File(
            outputDir, modelTag + "-ArMr-hist.tex");

      try (PrintWriter out =
            new PrintWriter(new FileWriter(outFile))) {

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
         out.println("\\setlength{\\histmethodwidth}{0.2cm}");
         out.println("\\newcommand{\\sethistwidths}[1]{%");
         out.println("  \\setlength{\\histcellwidth}"
               + "{\\dimexpr(\\textwidth-\\histmethodwidth)/#1\\relax}%");
         out.println("  \\ifdim\\histcellwidth>5.3cm");
         out.println("    \\setlength{\\histcellwidth}{5.3cm}%");
         out.println("  \\fi");
         out.println("}");
         out.println();

         out.println("\\begin{document}");
         out.println();

         for (int s : sDims) {
            String mStr = Integer.toString(m);
            String samples =
                  m > 0 && mStr.matches("10*")
                        ? "$10^{" + (mStr.length() - 1)
                              + "}$ samples"
                        : m + " samples";

            String pageTitle =
                  "RQMC comparison of $A_r$ and $M_r$: "
                        + modelTag.replace("_", "\\_")
                        + " s = " + s
                        + " (" + samples + ")";

            writeHistogramPageBody(
                  out, inputDir,
                  modelTag, methods,
                  s, ks, m,
                  pageTitle,
                  stream,
                  resetBeforeEachHistogram);

            out.println("\\clearpage");
            out.println();
         }

         out.println("\\end{document}");
      }

      System.out.println("LaTeX file created:");
      System.out.println(outFile.getAbsolutePath());
   }

   /**
    * Sets the parameters and writes the histogram LaTeX files for the SAMO paper
    */
   public static void main(String[] args) throws IOException {

      // String[] fileNames = new String[] {
      //    "SmoothPerB4-8-Lat-RS-16-10000", "SmoothPerB4-8-Lat-RvRS-16-10000", 
      //    "SmoothPerB4-8-Lat-RpvRS-16-10000", "SmoothPerB4-8-Sob-RDS-16-10000",
      //    "SmoothPerB4-8-Sob-LMS-RDS-16-10000", "SmoothPerB4-8-Sob-NUS-16-10000",
      //    "MC2-8-Sob-LMS-RDS-16-10000", "MC2-16-Sob-LMS-RDS-14-10000",
      //    "MC2-16-Sob-NUS-14-10000"
      // };
      // String[] titleNames = new String[] {
      //       "Lat-RS", "Lat-RvRS", "Lat-RpvRS","Sob-RDS", "Sob-LMS-RDS", "Sob-NUS",
      //       "Sob-LMS-RDS, $s=8$, $k=16$", "Sob-LMS-RDS, $s=16$, $k=14$",
      //       "Sob-NUS, $s=16$, $k=14$"
      //    };
      // int r = 11;
      // int numBins = 100;
      // int numObs = 10000;   // Number of observations in the input data files.
      // int[] marks = new int[] {0, 99, 499, numObs-1, numObs-100, numObs-500};   // This is for 10^4 obs.
      // int numReps = 10000;  // Number of bootstrap subsamples of A_r and M_r.
      RandomStream stream = new LFSR258();
      // // Chrono timerTotal = new Chrono();
      
      // TallyStore tallyInput = new TallyStore();   // The values of X.
      // TallyStore statAver = new TallyStore();     // The values of A_r
      // TallyStore statMed = new TallyStore();      // The values of M_r
      // for (int i = 0; i < fileNames.length; i++) {  // Draw histograms for each case.
      //    System.out.println("makeDoublestogramLatex: " + fileNames[i]);  // Optional
      //    tallyInput.fillFromFile(inputFolder + fileNames[i] + ".dat");
      //    // TEMPORARY: Reset before each histogram so this loop uses
      //    // the same initial stream state as the reproducible standalone mode.
      //    stream.resetStartStream();
      //    MeanMedianMSE.bootstrapArMrValues(tallyInput, numReps, r, stream, statAver, statMed);        
      //    String latexCode = HistSamo25Paper.makeDoubleHistogramLatex(statAver, statMed, 
      //          titleNames[i], "pos=north east", numBins, marks);     
      //    File outFile = new File(outputFolder, fileNames[i] + "-ArMr-hist.tex");  
      //    try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
      //       out.print(latexCode);
      //       System.out.println("Hist printed to file: " + fileNames[i]);
      //    } catch (IOException e) {
      //       throw new RuntimeException("Could not write " + outFile.getAbsolutePath(), e);
      //    }
         
      // }
      // System.out.println("ALL DONE ");

   ///////////////Generates one LaTeX histogram document for each model listed below./////////////
      String[] modelTags = {
         "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
         "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
      };

      String[] methods = {
         "Lat-RS", "Lat-RSB", "Lat-Rv", "Lat-Rpv", "Lat-RvRS", "Lat-RvRSB",
         "Lat-RpvRS", "Lat-RpvRSB", "Sob-RDS", "Sob-RDSB", "Sob-LMS", "Sob-LMS-RDS",
         "Sob-LMS-RDS-IRB", "Sob-NUS"
      };

      int[] sDims = {2, 4, 8, 16, 32};
      int[] ks = {10, 12, 14, 16};
      int m = 10000;

      // Choose and initialize the random stream here. Reproducing a run
      // requires the same generator and initial seed.
      // RandomStream stream = new LFSR258();
      boolean resetBeforeEachHistogram = true;

      for (String modelTag : modelTags) {
         writeModelFile(
               inputFolder, outputFolder,
               modelTag, methods,
               sDims, ks, m,
               stream,
               resetBeforeEachHistogram);
      }

      System.out.println("ALL DONE");
   }
}
