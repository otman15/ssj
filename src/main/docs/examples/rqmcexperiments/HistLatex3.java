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


public class HistLatex3 {
	
	
	public static void main(String[] args) throws IOException {

	      String dataDir = "/home/otman/Dropbox/samo25/datapl/";// .dat files should be here
	      String latexDir = "/home/otman/Documents/GitHub/Data/samo25-test/tikz/";// output .tex file will be created here

		   // Important: Change these parameters as needed to match the .dat files you have.
		   String modelTags[] = {"SmoothPerB4","SumUeU","MC2","Polynomial","Oscillatory","Gaussian","SmoothGauss","PieceLinGauss","IndSumNormal"}; // The model tag used in the .dat file names, e.g. "SumUeU", "Polynomial", etc.";
		   int sDims[] = {2,4,8,16,32};
		   int m = 10000;
		   int mExp = (int) Math.log10(m);
		   int[] ks = {10, 12, 14, 16};
		   
		   File inputFolder = new File(dataDir);
		   File outputFolder = new File(latexDir);
		   outputFolder.mkdirs();
		   File[] files = inputFolder.listFiles((dir, name) -> name.endsWith(".dat"));
		   if (files == null || files.length == 0) {
		      System.out.println("No .dat files found in " + dataDir);
		      return;
		   }
		   Arrays.sort(files);
		   
		   String[][] pages = {
				   {"Rank-1 lattice", "rank1_nonbaker", "Lat-RS,Lat-RvRS,Lat-RpvRS"},
				   {"Rank-1 lattice with baker transform", "rank1_baker", "Lat-RSB,Lat-RvRSB,Lat-RpvRSB"},
				   {"Sobol", "sobol", "Sob-RDS,Sob-LMS,Sob-NUS"}
				};

				for (String model : modelTags) {
				   for (int s : sDims) {
				      String baseTag = model + "-" + s;

				      for (String[] page : pages) {
				         writeHistogramPage(
				            files,
				            outputFolder,
				            baseTag,
				            model + "-" + s + "_" + page[1] + ".tex",
				            "RQMC " + page[0] + " comparison: " + baseTag + " ($10^{" + mExp + "}$ samples)",
				            page[2].split(","),
				            ks,
				            m
				         );
				      }
				   }
				}
		   
		}
	
	
	
	private static void writeHistogramPage(
		      File[] files,
		      File outputFolder,
		      String baseTag,
		      String outputName,
		      String pageTitle,
		      String[] methods,
		      int[] ks,
		      int m) throws IOException {

		   File outFile = new File(outputFolder, outputName);

		   try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {

		      out.println("\\documentclass[border=3pt]{standalone}");
		      out.println("\\usepackage{amsmath}");
		      out.println("\\usepackage{graphicx}");
		      out.println("\\usepackage{pgfplots}");
		      out.println("\\pgfplotsset{compat=1.18}");
		      out.println("\\begin{document}");
		      out.println();

		      out.print("\\begin{tabular}{@{}r");
		      for (int i = 0; i < ks.length; i++) {
		         out.print("@{\\hspace{1mm}}c");
		      }
		      out.println("@{}}");

		      out.println("\\multicolumn{" + (ks.length + 1)
		            + "}{c}{\\fontsize{7}{8}\\selectfont\\textbf{"
		            + escapeLatex(pageTitle) + "}} \\\\[2mm]");

		      for (int r = 0; r < methods.length; r++) {

		         out.print("{\\fontsize{6}{7}\\selectfont "
		               + escapeLatex(methods[r]) + "}");

		         for (int c = 0; c < ks.length; c++) {
		            int k = ks[c];

		            File file = findFile(files, baseTag, methods[r], k, m);

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
		      out.println();
		      out.println("\\end{document}");
		   }

		   System.out.println("LaTeX file created:");
		   System.out.println(outFile.getAbsolutePath());
		}
	
	
	
	
	
	
	private static File findFile(File[] files, String baseTag, String method, int k, int m) {
		   String exactName = baseTag + "-" + method + "-" + k + "-" + m + ".dat";

		   for (File file : files) {
		      if (file.getName().equals(exactName))
		         return file;
		   }

		   return null;
		}
	
	
	
	

	   private static String makeHistogramLatex(File file) throws IOException {

		   TallyStore fileStats = getFilefileStats(file);

	      double xmin = fileStats.min();
	      double xmax = fileStats.max();

	      if (xmin == xmax) {
	         xmin -= 1.0;
	         xmax += 1.0;
	      } else {
	         double pad = 0.03 * (xmax - xmin);
	         xmin -= pad;
	         xmax += pad;
	      }

	      int numBins = Math.max(20, (int) Math.round(2*Math.cbrt(fileStats.numberObs())));

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

	         // Smaller title font.
	         "title style={font=\\fontsize{5}{5.5}\\selectfont}, " +

	         // Bigger plot box.
	         "width=5.15cm, height=4.01cm, " +

	         "xlabel={}, ylabel={}, " +
	         "scaled x ticks=true, " +
	         "scaled y ticks=false, " +

	         // Smaller tick labels.
	         "tick label style={font=\\fontsize{4.5}{5}\\selectfont}, " +

	         // Smaller  bold x-axis multiplier, e.g. 1e-4.
	         "every x tick scale label/.append style={font=\\fontsize{4}{4.5}\\selectfont\\bfseries\\boldmath, yshift=5pt}, "  +
	         "every y tick scale label/.append style={font=\\fontsize{4}{4.5}\\selectfont}, " +

	         // Red vertical line at 0.
//	         "extra x ticks={0}, " +
//	         "extra x tick labels={}, " +
//	         "extra x tick style={grid=major, major grid style={red, thick}}, " +

	         // Smaller legend rectangle.
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

	      scHist.setAddPlotOptions("fill=blue, draw=black");

	      return scHist.toLatex(true, false);
	   }
	   
	   
	   

	   private static String cleanTitle(String fileName) {
	      String title = fileName.substring(0, fileName.length() - 4);
	      title = title.replaceFirst("-\\d+$", "");
	      return title;
	   }
	   
	   
	   
	   
	   private static TallyStore getFilefileStats(File file) throws IOException {
	      
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
	   
		
}
