package rqmcexperiments;

import java.io.*;
import umontreal.ssj.stat.*;

/**
 * This is the main program used to make experiments for the WSC 2026 paper on RQMC.
 * It makes RQMC replicates for selected functions, a fixed list of RQMC methods
 * specified in `WSC26RQMCSamples64`, for selected numbers of dimensions s
 * and numbers of points @f$2^k@f$. The output is a set of files, two for each function 
 * and dimension, that contain data sets with the log-variance and the mean for each RQMC estimator
 * (each column) and each value of `k` (each row). 
 */

public class HistLatex {

   public static void main(String[] args) throws IOException {

      // String directory = "C:/Users/Lecuyer/Dropbox/samo25/paperdat/";  // For testing
      String directory = "C:/Users/Lecuyer/Dropbox/wsc23/test/";      // For testing
      
      // double xmin = -5.2E-12;
      // double xmax =  5.2E-12;
      double xmin = - 1.1E-2;
      double xmax =  -xmin;
      int numBins = 40;

      // int numObs  = 1000000;
     
      TallyHistogram hist = new TallyHistogram(xmin, xmax, numBins);
      // hist.fillFromFile(directory + "SmoothPerB4-8-Lat-RS-16.dat");
      hist.fillFromFile(directory + "SumUeU-16-Lat-RvRS-10-10000.dat");
      // ScaledHistogram histScaled = new ScaledHistogram(hist, 1.0);
      // String histStr = histScaled.toLatex(false, true);
      ScaledHistogram scHist = new ScaledHistogram(hist, 1.0);
      scHist.setAxisOptions(
    		   "title={SumUeU-16-Lat-RvRS-10-10000}, " +
    		   "title style={font=\\fontsize{5}{5.5}\\selectfont}, " +
    		   "width=8cm, height=5cm, " +
    		   "xlabel={}, ylabel={}, " +
    		   "scaled x ticks=true, " +
    		   "scaled y ticks=false, " +
    		   "tick label style={font=\\fontsize{6}{6.5}\\selectfont}, " +
    		   "every x tick scale label/.append style={font=\\fontsize{5}{5.5}\\selectfont\\bfseries\\boldmath, yshift=5pt}, " +
    		   "legend entries={{$\\mathrm{Var}=" 
    		   + String.format(java.util.Locale.US, "%.2e", hist.variance()) 
    		   + "$}}, " +
    		   "legend image code/.code={}, " +
    		   "legend style={draw=none, fill=none, font=\\scriptsize, cells={anchor=west}, inner xsep=0pt, inner ysep=0pt}, " +
    		   "legend pos=north east"
    		);
      scHist.setAddPlotOptions("fill=red!30, draw=black");
      System.out.println(scHist.toLatex(true, false));
      
      xmax = 1.25E-2;
      xmin = -xmax;
      hist = new TallyHistogram(xmin, xmax, numBins);
      hist.fillFromFile(directory + "SumUeU-16-Lat-RvRS-10-10000.dat");
      // histScaled = new ScaledHistogram(hist, 1.0);
      // histStr = histScaled.toLatex(false, true);
      scHist = new ScaledHistogram(hist, 1.0);

      scHist.setAxisOptions(
    		   "title={SumUeU-16-Lat-RvRS-10-10000}, " +
    		   "title style={font=\\fontsize{5}{5.5}\\selectfont}, " +
    		   "width=8cm, height=5cm, " +
    		   "xlabel={}, ylabel={}, " +
    		   "scaled x ticks=true, " +
    		   "scaled y ticks=false, " +
    		   "tick label style={font=\\fontsize{6}{6.5}\\selectfont}, " +
    		   "every x tick scale label/.append style={font=\\fontsize{5}{5.5}\\selectfont\\bfseries\\boldmath, yshift=5pt}, " +
    		   "legend entries={{$\\mathrm{Var}=" 
    		   + String.format(java.util.Locale.US, "%.2e", hist.variance()) 
    		   + "$}}, " +
    		   "legend image code/.code={}, " +
    		   "legend style={draw=none, fill=none, font=\\scriptsize, cells={anchor=west}, inner xsep=0pt, inner ysep=0pt}, " +
    		   "legend pos=north east"
    		);
      scHist.setAddPlotOptions("fill=blue!30, thick");
      System.out.println(scHist.toLatex(false, true));
   }
}
