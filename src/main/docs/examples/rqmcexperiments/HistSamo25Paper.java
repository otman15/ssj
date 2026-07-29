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
   static String inputFolder = "C:/Users/Lecuyer/Dropbox/samo25/datapl/";
   static String outputFolder = "C:/Users/Lecuyer/Dropbox/samo25/paperdat/";
         
   /**
    * Builds the PGFPlots LaTeX code for one histogram.
    *
    * The plot includes a title derived from the input file name, and summary
    * statistics in a legend. The range of the histogram goes from the smallest 
    * observation on the left of the first bin to the largest observation plus a
    * tiny margin of `1.0e-12` times the range on the right of the last bin.
    * Extra space of one percent of the range is added in the display on each side
    * of the histogram.
    *
    * @param String fileName input file name without folder name and extension.
    *
    * @return LaTeX code for the histogram
    * @throws IOException if the data file cannot be read or has no observations
    */
   public static String makeSimpleHistogramLatex(String fileName, String titleName, 
         String legpos, int numBins, int[] marks) throws IOException {
      TallyStore data = new TallyStore();
      data.fillFromFile(inputFolder + fileName + ".dat");
      data.quickSort();
      // int n = data.numberObs();
      double a = data.min();
      double b = data.max();
      double range = b - a;
      TallyHistogram hist = new TallyHistogram(a, b + range * 1.0e-12, numBins);
      hist.fillFromTallyStore(data);     
      ScaledHistogram scHist = new ScaledHistogram(hist);
      System.out.println(hist.toString());
      scHist.setAxisOptions("title={{\\footnotesize " + fileName + "}}, width=4.4cm, height=3.0cm, scale only axis, \n" +
             "  ymin=0.0, xmin = " + (a - 0.01 * range) + ", xmax = " + (b + 0.01 * range) + 
             ",\n  ylabel={}, yticklabels={}, \n" +
             "  scaled x ticks=true, minor x tick num=0, scaled y ticks=false, \n" +
             "  every x tick label/.append style={scale=0.6, transform shape}, \n" +
             "  every x tick scale label/.style={at={(axis description cs:1, 0)}, \n" +
                "  anchor=north east, xshift=2pt, yshift=-6.2pt, inner sep=0pt}, \n" +
             "  legend entries={{\\parbox[c][0.13cm][c]{1.5cm}{\\centering\\scalebox{0.6}{\\tt \n" +
             "  \\begin{tabular}{@{}l@{}}\n     $\\sigma^2=$ " + sci(data.variance())
                 + "\\\\[-1pt]\n     $\\gamma=$ " + sci(data.skewness())
                 + "\\\\[-1pt]\n     $\\kappa'=$ " + sci(data.kurtosis()) + "\n  \\end{tabular}}}}},\n" +
             "  legend image code/.code={}, \n" +
             "  legend style={draw=none, fill=none, cells={anchor=west}, inner xsep=0pt, inner ysep=5pt}, \n" +
             "  legend pos=north east");
      scHist.setAddPlotOptions("mark=none,very thin,fill=blue!25");
      // Make the latex file.
      String latexCode = scHist.toLatex(true, false);
      // Add marks.
      StringBuilder coords = new StringBuilder();
      for(int i : marks)
         coords.append("(").append(String.format(Locale.US, "%.17g", data.getArray()[i])).append(",0) ");
      // The following is very specific to this Sob-RDS case; it adds purple markes in the middle.
      if (fileName == "SmoothPerB4-8-Sob-RDS-16-10000")
         coords.append(" (-4.760742119957395E-6,0) (4.485223280581408E-6,0)");
      String adds = "\\addplot+[only marks, mark=|, mark size=2.5pt, "
            + "mark options={green,thick}, forget plot] coordinates {" + coords + "};";
      latexCode = latexCode.replace("\\end{axis}", adds + "\n\\end{axis}");
      return latexCode;
   }

   public static String makeDoubleHistogramLatex(TallyStore data1, TallyStore data2, String titleName, 
         String legpos, int numBins, int[] marks) throws IOException {
      data1.quickSort();
      data2.quickSort();
      // int n1 = data1.numberObs();
      // int n2 = data2.numberObs();
      double a = Math.min(data1.min(), data2.min());
      double b = Math.max(data1.max(), data2.max());
      double range = b - a;
      System.out.println("makeSimpleHistogramLatex: a = " + a + ", b = " + b);

      TallyHistogram hist1 = new TallyHistogram(a, b + range * 1.0e-12, numBins);
      hist1.fillFromTallyStore(data1);     
      ScaledHistogram scHist1 = new ScaledHistogram(hist1);
      TallyHistogram hist2 = new TallyHistogram(a, b + range * 1.0e-12, numBins);
      hist2.fillFromTallyStore(data2);     
      ScaledHistogram scHist2 = new ScaledHistogram(hist2);
      // ScaledHistogram scHist = new ScaledHistogram();
      
      // System.out.println(hist.toString());
      scHist1.setAxisOptions("title={" + titleName + "}, width=4.4cm, height=3.0cm, scale only axis, \n" +
             "  ymin=0.0, xmin = " + (a - 0.01 * range) + ", xmax = " + (b + 0.01 * range) + 
             ",\n  ylabel={}, yticklabels={}, \n" +
             "  scaled x ticks=true, minor x tick num=0, scaled y ticks=false, \n" +
             "  every x tick label/.append style={scale=0.6, transform shape}, \n" +
             "  every x tick scale label/.style={at={(axis description cs:1, 0)}, \n" +
                "  anchor=north east, xshift=2pt, yshift=-6.2pt, inner sep=0pt}, \n");
      scHist1.setAddPlotOptions("mark=none,very thin,fill=green!25,opacity=0.6,fill opacity=0.6");
      scHist2.setAddPlotOptions("mark=none,very thin,fill=red!25,opacity=0.6,fill opacity=0.6");
      // Make the latex file.
      String latexCode = scHist1.toLatexTwoHist(scHist2);
      // Add the marks.
      StringBuilder coords = new StringBuilder();
      for(int i : marks)
         coords.append("(").append(String.format(Locale.US, "%.17g", data1.getArray()[i])).append(",0) ");
      String adds = "\\addplot+[only marks, mark=|, mark size=2.5pt, "
            + "mark options={green,thick}, forget plot] coordinates {" + coords + "};";
      latexCode = latexCode.replace("\\end{axis}", adds + "\n\\end{axis}");
      coords = new StringBuilder();
      for(int i : marks)
         coords.append("(").append(String.format(Locale.US, "%.17g", data2.getArray()[i])).append(",0) ");
      adds = "\\addplot+[only marks, mark=|, mark size=2.5pt, "
            + "mark options={red,thick}, forget plot] coordinates {" + coords + "};";
      latexCode = latexCode.replace("\\end{axis}", adds + "\n\\end{axis}");
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
      String[] titleNames = new String[] {
            "Lat-RS", "Lat-RvRS", "Lat-RpvRS","Sob-RDS", "Sob-LMS-RDS", "Sob-NUS",
            "Sob-LMS-RDS, $s=8$, $k=16$", "Sob-LMS-RDS, $s=16$, $k=14$",
            "Sob-NUS, $s=16$, $k=14$"
         };
      String[] legendAnchor = new String[] {
            "style={at={(0.5, 0.97)}, anchor=north}", 
            "pos=north west", "pos=north west", 
            "style={at={(0.5, 0.97)}, anchor=north}", 
            "pos=north east", "pos=north east", 
            "pos=north east", "pos=north east", "pos=north east",
         };
      int numBins = 100;
      int[] marks = new int[] {0, 1, 9999, 9998};

      for (int i = 0; i < fileNames.length; i++) {
         String latexCode = makeSimpleHistogramLatex(fileNames[i], titleNames[i], legendAnchor[i], numBins, marks);
         File outFile = new File(outputFolder, fileNames[i] + "-hist-paper.tex");    // Add "-paper" for final ones.
         try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
            out.print(latexCode);
            System.out.println("Hist printed to file: " + fileNames[i] + "-hist-paper.tex");
         } catch (IOException e) {
            throw new RuntimeException("Could not write " + outFile.getAbsolutePath(), e);
         }
      }
/*
      // The following gives almost the same output using 'HistCollectionLatex.makeHistogramLatex'
      int[] ExtremMarks = new int[] {2,2};
      String path;
      String latexHist;
      for(String fileName: fileNames){
         path = inputFolder + fileName + ".dat";
         latexHist = HistCollectionLatex.makeHistogramLatex(path, numBins, fileName,
            "4.4 cm", "3 cm", "builtin", ExtremMarks);

         File outFile = new File(outputFolder, fileName + "-hist25.tex");// hist25 is used to nor overwrite makeSimpleHistogramLatex files
         try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
            out.print(latexHist);
         } catch (IOException e) {
            throw new RuntimeException("Could not write " + outFile.getAbsolutePath(), e);
         }
      }

   }
   
}
