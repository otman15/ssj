package rqmcexperiments;

import java.io.*;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import umontreal.ssj.stat.*; 


public class HistLatex3 {
	
	
	public static void main(String[] args) throws IOException {

	      String dataDir = "C:/Users/cherrato/Documents/GitHub/Data/datapl/";// .dat files should be here
	      String latexDir = "C:/Users/cherrato/Documents/GitHub/Data/fn-test/tikz/";// output .tex file will be created here
	      
	      //"PieceLinGauss", "Polynomial", "SmoothGauss", "Oscillatory",  "MC2"
		   String[] modelTags = {"PieceLinGauss", "Polynomial", "SmoothGauss"};
		   int[] ss = {2,4};
		   int m = 10000;
		   int[] ks = {8, 10, 12, 14, 16};
		   
		   
		   for(String modelTag : modelTags) {
			   for(int s:ss) {
	      
				   	makeLat(dataDir, latexDir , modelTag,s, m, ks);
			   }
		   }
	      
	}
	
	public static void makeLat(String dataDir, String latexDir, String modelTag, int s, int m, int[] ks) throws IOException {

		   // Important: Change these parameters as needed to match the .dat files you have.

		   // Choose what to generate.
		   boolean makeLattice = true;
		   boolean makeSobol = true;

		   String baseTag = modelTag + "-" + s;

		   File inputFolder = new File(dataDir);
		   File outputFolder = new File(latexDir);
		   outputFolder.mkdirs();

		   File[] files = inputFolder.listFiles((dir, name) -> name.endsWith(".dat"));

		   if (files == null || files.length == 0) {
		      System.out.println("No .dat files found in " + dataDir);
		      return;
		   }

		   Arrays.sort(files);

		   // ============================================================
		   // LATTICE PAGES
		   // ============================================================
		   if (makeLattice) {

		      // --------------------------
		      // Non-baker lattice page
		      // 4 methods x 4 k values = 16 plots
		      // --------------------------
		      writeHistogramPage(
		         files,
		         outputFolder,
		         baseTag,
		         modelTag + "-" + s + "_rank1_nonbaker_plots.tex",
		         "RQMC Rank-1 lattice point set comparison: "
		               + modelTag + "-" + s + " ($10^4$ samples)",
		         new String[][] {
		            {"Lat-RS"},
		            {"Lat-Rv"},
		            {"Lat-RvRS"},
		            {"Lat-RpvRS"}
		         },
		         new String[] {
		            "Lat-RS",
		            "Lat-Rv",
		            "Lat-RvRS",
		            "Lat-RpvRS"
		         },
		         ks,
		         m
		      );

		      // --------------------------
		      // Baker lattice page
		      // 4 methods x 4 k values = 16 plots
		      // --------------------------
		      writeHistogramPage(
		         files,
		         outputFolder,
		         baseTag,
		         modelTag + "-baker-" + s + "_rank1_baker_plots.tex",
		         "RQMC Rank-1 lattice point set comparison with baker transform: "
		               + modelTag + "-baker-" + s + " ($10^4$ samples)",
		         new String[][] {
		            {"Lat-RSB"},
		            {"Lat-RpvB"},
		            {"Lat-RvRSB"},
		            {"Lat-RpvRSB"}
		         },
		         new String[] {
		            "Lat-RSB",
		            "Lat-RpvB",
		            "Lat-RvRSB",
		            "Lat-RpvRSB"
		         },
		         ks,
		         m
		      );
		   }

		   // ============================================================
		   // SOBOL PAGE
		   // ============================================================
		   if (makeSobol) {

		      // 6 methods x 4 k values = 24 plots
		      writeHistogramPage(
		         files,
		         outputFolder,
		         baseTag,
		         modelTag + "-" + s + "_sobol_plots.tex",
		         "RQMC Sobol point set comparison: "
		               + modelTag + "-" + s + " ($10^4$ samples)",
		         new String[][] {
		            {"Sob-RDS"},
		            {"Sob-RDST"},
		            {"Sob-LMS"},
		            {"Sob-LMS-RDS"},
		            {"Sob-LMS-RDS-IRB"},
		            {"Sob-NUS"}
		         },
		         new String[] {
		            "Sob-RDS",
		            "Sob-RDST",
		            "Sob-LMS",
		            "Sob-LMS-RDS",
		            "Sob-LMS-RDS-IRB",
		            "Sob-NUS"
		         },
		         ks,
		         m
		      );
		   }
		}
	
	
	
	private static void writeHistogramPage(
		      File[] files,
		      File outputFolder,
		      String baseTag,
		      String outputName,
		      String pageTitle,
		      String[][] methodCandidates,
		      String[] rowLabels,
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

		      for (int r = 0; r < rowLabels.length; r++) {

		         out.print("{\\fontsize{6}{7}\\selectfont "
		               + escapeLatex(rowLabels[r]) + "}");

		         for (int c = 0; c < ks.length; c++) {
		            int k = ks[c];

		            File file = findFile(files, baseTag, methodCandidates[r], k, m);

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
	
	
	
	
	
	
	private static File findFile(File[] files, String baseTag, String[] methodCandidates, int k, int m) {

	      for (String method : methodCandidates) {
	         String exactName = baseTag + "-" + method + "-" + k + "-" + m + ".dat";

	         for (File file : files) {
	            if (file.getName().equals(exactName))
	               return file;
	         }
	      }

	      return null;
	   }
	
	
	
	

	   private static String makeHistogramLatex(File file) throws IOException {

		   TallyStore stats = getFileStats(file);

	      double xmin = stats.min();
	      double xmax = stats.max();

	      if (xmin == xmax) {
	         xmin -= 1.0;
	         xmax += 1.0;
	      } else {
	         double pad = 0.03 * (xmax - xmin);
	         xmin -= pad;
	         xmax += pad;
	      }

	      int numBins = Math.max(20, (int) Math.round(2*Math.cbrt(stats.numberObs())));

	      TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins); 
	      hist.fillFromFile(file.getAbsolutePath());

	      ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);

	      String title = cleanTitle(file.getName());

	      String legend =
	    	      "\\scalebox{0.62}{"
	    	      + "\\begin{tabular}{@{}l@{}}"
	    	      + "$\\sigma^2$=" + sci(hist.variance())
	    	      + "\\\\[-1pt]$\\gamma$=" + sci(stats.skewness())
	    	      + "\\\\[-1pt]$\\kappa'$=" + sci(stats.kurtosis())
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
	   
	   
	   
	   
	   private static TallyStore getFileStats(File file) throws IOException {
	      
	      TallyStore stats = new TallyStore();


	      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
	         String line;

	         while ((line = reader.readLine()) != null) {
	            line = cleanDataLine(line);

	            if (line.isEmpty())
	               continue;

	            String[] values = line.split("\\s+");

	            for (String value : values) {
	               double x = Double.parseDouble(value);
	               stats.add(x);
	            }
	         }
	      }

	      if (stats.numberObs() == 0)
	         throw new IOException("No observations found in " + file.getAbsolutePath());

	      return stats;
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
