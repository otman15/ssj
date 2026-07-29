package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.TallyStore;
//import umontreal.ssj.util.Chrono;

/**
 * Generates standalone LaTeX documents that compare the distributions of the
 * average @f$A_r@f$ and median @f$M_r@f$ for SAMO 2025 experiments.
 *
 * <p>For each input data file, the program bootstraps samples of size @f$r@f$
 * and draws the histograms of @f$A_r@f$ and @f$M_r@f$ on the same PGFPlots
 * axis. Each output document contains one page per dimension, with RQMC
 * methods in rows and sample sizes @f$n=2^k@f$ in columns.
 */
public class HistSamo25ArMr {
   // Fixed parameters for this particular paper.
   static String inputFolder = "C:/Users/Lecuyer/Dropbox/samo25/datapl/";
   static String outputFolder = "C:/Users/Lecuyer/Dropbox/samo25/paperdat/";
   static int numBins = 100;

   /**
    * Sets the parameters and writes the histogram LaTeX files for the SAMO paper
    */
   public static void main(String[] args) throws IOException {

      String[] fileNames = new String[] {
         "SmoothPerB4-8-Lat-RS-16-10000", "SmoothPerB4-8-Lat-RvRS-16-10000", 
         "SmoothPerB4-8-Lat-RpvRS-16-10000", "SmoothPerB4-8-Sob-RDS-16-10000",
         "SmoothPerB4-8-Sob-LMS-RDS-16-10000", "SmoothPerB4-8-Sob-NUS-16-10000",
         "MC2-8-Sob-LMS-RDS-16-10000", "MC2-16-Sob-LMS-RDS-14-10000",
         "MC2-16-Sob-NUS-14-10000"
      };
      String[] titleNames = new String[] {
            "Lat-RS", "Lat-RvRS", "Lat-RpvRS","Sob-RDS", "Sob-LMS-RDS", "Sob-NUS",
            "Sob-LMS-RDS, $s=8$, $k=16$", "Sob-LMS-RDS, $s=16$, $k=14$",
            "Sob-NUS, $s=16$, $k=14$"
         };
      int r = 11;
      int numBins = 100;
      int numObs = 10000;   // Number of observations in the input data files.
      int[] marks = new int[] {0, 99, 499, numObs-1, numObs-100, numObs-500};   // This is for 10^4 obs.
      int numReps = 10000;  // Number of bootstrap subsamples of A_r and M_r.
      RandomStream stream = new LFSR258();
      // Chrono timerTotal = new Chrono();
      
      TallyStore tallyInput = new TallyStore();   // The values of X.
      TallyStore statAver = new TallyStore();     // The values of A_r
      TallyStore statMed = new TallyStore();      // The values of M_r
      for (int i = 0; i < fileNames.length; i++) {  // Draw histograms for each case.
         System.out.println("makeDoublestogramLatex: " + fileNames[i]);  // Optional
         tallyInput.fillFromFile(inputFolder + fileNames[i] + ".dat");
         MeanMedianMSE.bootstrapArMrValues(tallyInput, numReps, r, stream, statAver, statMed);        
         String latexCode = HistSamo25Paper.makeDoubleHistogramLatex(statAver, statMed, 
               titleNames[i], "pos=north east", numBins, marks);     
         File outFile = new File(outputFolder, fileNames[i] + "-ArMr-hist.tex");  
         try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
            out.print(latexCode);
            System.out.println("Hist printed to file: " + fileNames[i]);
         } catch (IOException e) {
            throw new RuntimeException("Could not write " + outFile.getAbsolutePath(), e);
         }
         
      }
      System.out.println("ALL DONE ");
   }
}
