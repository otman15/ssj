package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import umontreal.ssj.stat.ScaledHistogram;
import umontreal.ssj.stat.TallyHistogram;
import umontreal.ssj.stat.TallyStore;

/**
 * Example that uses {@link HistCollectionLatex} to generate histograms in LaTeX files.
 * The local variables in the `main` set the directories, list of models, list of methods,
 * dimensions, values of `k = log_2 n`, and number of observations.
 * All of these are passed as parameters to `HistCollectionLatex.writeCollection`,
 * which constructs one LaTeX file for each model in the list.
 */
public class HistSamo25Paper {

   // Fixed parameters for this particular paper.
   static String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
   static String outputFolder = "/home/otman/Documents/GitHub/Data/o-test/histSamoPaper/";

   static int numBins = 100;
         
   /**
    * Builds the PGFPlots LaTeX code for one histogram.
    *
    * The plot includes a title derived from the input file name, summary
    * statistics in a legend, and marks for selected extreme observations. The
    * extreme marks and number of bins are class fields; use the setters to
    * configure them.
    *
    * @param String fileName input file name without folder name and extension.
    *
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read or has no observations
    */
   public static String makeSimpleHistogramLatex(String fileName, int numBins) throws IOException {
      TallyStore data = new TallyStore();
      data.fillFromFile(inputFolder + fileName + ".dat");
      double xmin = data.min();
      double xmax = data.max();
      double range = xmax - xmin;
      TallyHistogram hist = new TallyHistogram(xmin, xmax * 1.000000001, numBins);
      hist.fillFromTallyStore(data);     
      ScaledHistogram scHist = new ScaledHistogram(hist);
      scHist.setAxisOptions("title={" + fileName + "}, width=4.4cm, height=3.0cm, scale only axis, \n" +
             "  ymin=0.0, xmin = " + (xmin - 0.01 * range) + ", xmax = " + (xmax + 0.01 * range) + 
             ",\n  ylabel={}, yticklabels={}, \n" +
             "  scaled x ticks=true, minor x tick num=0, scaled y ticks=false, \n" +
             "  every x tick label/.append style={scale=0.6, transform shape}, \n" +
             "  every x tick scale label/.style={at={(axis description cs:1, 0)}, \n" +
                "  anchor=north east, xshift=2pt, yshift=-6.2pt, inner sep=0pt}, \n" +
             "  legend entries={{\\parbox[c][0.1cm][c]{1.2cm}{\\centering\\scalebox{0.6}{\\tt \n" +
             "  \\begin{tabular}{@{}l@{}}\n     $\\sigma^2=$ " + sci(data.variance())
                 + "\\\\[-1pt]\n     $\\gamma=$ " + sci(data.skewness())
                 + "\\\\[-1pt]\n     $\\kappa'=$ " + sci(data.kurtosis()) + " \\end{tabular}}}}},\n" +
             "  legend image code/.code={}, \n" +
             "  legend style={draw=none, fill=none, cells={anchor=west}, inner xsep=0pt, inner ysep=5pt}, \n" +
             "  legend pos=north east");
      scHist.setAddPlotOptions("mark=none,very thin,fill=blue!25");
      // Make the latex file.
      String latexCode = scHist.toLatex(true, false);
      File outFile = new File(outputFolder, fileName + "-hist.tex");
      try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
         out.print(latexCode);
      } catch (IOException e) {
         throw new RuntimeException("Could not write " + outFile.getAbsolutePath(), e);
      }
      return latexCode;
   }

   private static String sci(double x) {
      String s = String.format(Locale.US, "%2.2e", x);
      s = s.replace("e-0", "e-");
      s = s.replace("e+0", "e");
      s = s.replace("e+", "e");
      return s;
   }
      
   /**
    * Sets the parameters and writes the histogram LaTeX files for the SAMO paper
    */
   public static void main(String[] args) throws IOException {

      /*
      String[] modelTags = new String[] {"SmoothPerB4"};
      String[] methods = new String[] {
         "Lat-RS", "Lat-RvRS", "Lat-RpvRS", 
         "Sob-RDS", "Sob-LMS-RDS", "Sob-NUS"
      };
      int[] sDims = new int[] {8};  // Dimensions s.
      int[] ks = new int[] {16};      // Values of k = log_2 n.
      int m = 10000;                    // Number of observations per file.
      HistCollectionLatex.writeCollection(
         inputFolder, outputFolder, modelTags, methods, sDims, ks, m);
      
      modelTags = new String[] {"MC2"};
      methods = new String[] {"Sob-LMS-RDS"};
      sDims = new int[] {8}; 
      ks = new int[] {16}; 
      HistCollectionLatex.writeCollection(
            inputFolder, outputFolder, modelTags, methods, sDims, ks, m);
      
      methods = new String[] {"Sob-LMS-RDS", "Sob-NUS"};
      sDims = new int[] {16};
      ks = new int[] {14};
      HistCollectionLatex.writeCollection(
            inputFolder, outputFolder, modelTags, methods, sDims, ks, m);
       */      
   
      System.out.println(makeSimpleHistogramLatex("SmoothPerB4-8-Lat-RS-16-10000", numBins));
      System.out.println(makeSimpleHistogramLatex("SmoothPerB4-8-Lat-RvRS-16-10000", numBins));
      System.out.println(makeSimpleHistogramLatex("SmoothPerB4-8-Lat-RpvRS-16-10000", numBins));

   }
   
}
