package rqmcexperiments;

import java.io.IOException;
import java.nio.file.Path;
import java.awt.Color;

import umontreal.ssj.charts.XYLineChart;
import umontreal.ssj.charts.XYListSeriesCollection;

/**
 * Merges execution-time CSV files produced by several RQMC implementation
 * experiments and creates comparison plots from the merged data.
 *
 * The generated plots compare median execution time across methods while one
 * experiment parameter is varied and the remaining parameters are fixed. The
 * first family varies the number of replications @f$m@f$ for each selected
 * pair of @f$s@f$ and @f$k@f$ values. The second varies @f$k@f$ for each
 * selected pair of @f$s@f$ and @f$m@f$ values, with the horizontal axis
 * displayed as @f$n = 2^k@f$.
 */
public class AllMethodsExp {

   /**
    * Merges the experiment CSV files and exports median-time charts for all
    * configured parameter combinations as LaTeX/PGFPlots representations.
    *
    * @param args ignored
    * @throws IOException if a CSV or LaTeX output file cannot be read or written
    */
   public static void main(String[] args) throws IOException {
      // Directory containing one timing CSV file for each implementation.
      String inputDir = "/home/otman/Dropbox/Nus-comparisons/all-methods/";
      Path[] inputFiles = {
            Path.of(inputDir + "ssj_MC2_time_rep-11.csv"),
            Path.of(inputDir + "sciMl_MC2_time_by_rep-11.csv"),
            Path.of(inputDir + "qmcpy_MC2_time_rep-11.csv"),
            Path.of(inputDir + "owen_MC2_time_rep-11.csv"),
            Path.of(inputDir + "burley-quantlib_MC2_time_rep-11.csv"),
            Path.of(inputDir + "friedel-keller-libseq_MC2_time_rep-11.csv"),
            Path.of(inputDir + "helmer-stochastic-sobol_MC2_time_rep-11.csv"),
            Path.of(inputDir + "pbrt-fast-owen_MC2_time_rep-11.csv"),
            Path.of(inputDir + "pbrt-owen_MC2_time_rep-11.csv"),
            Path.of(inputDir + "adaptive-regular-tiles_MC2_time_rep-11.csv")
      };

      int runs = 11;

      String outputDir = "/home/otman/Documents/GitHub/rqmc-experiments/";

      Path outputFile = Path.of(outputDir + "data/allMetTime" + runs + ".csv");

      // Keep the header from the first file and append all method results.
      expUtil.mergeCsvFiles(inputFiles, outputFile, true);

      ///////////////////////////////////
      ///////////////////////////////////

      // Values used to create all m and k comparison-plot combinations.
      int[] sValues = {2, 16, 32};
      int[] mValues = {1, 5, 20, 50};
      int[] kValues = {8, 12, 16};
      boolean logYaxis = true;
      String yLogScaled = logYaxis ? "yLogScaled" : "";

      Color[] colors = {
            Color.RED, Color.BLUE, new Color(0, 160, 0), // green
            Color.MAGENTA, Color.ORANGE, Color.CYAN, Color.BLACK,
            new Color(128, 0, 255), // violet
            new Color(255, 20, 147), // deep pink
            new Color(128, 64, 0), // brown
            new Color(128, 255, 0) // bright lime
      };

      // Create  plots varying m, one for every selected (s, k) pair.
      for (int s : sValues) {
         for (int k : kValues) {
            XYLineChart chart = ExecutionTimePlots.createMedianTimePlot(outputFile, "s", s, "k", k,
                  "m", "Number of replications m", 50.0, x -> x, logYaxis);
            setSeriesColors(chart, colors);
            chart.view(800, 500);
            expUtil.writeTikzPictureFile(
                  chart, outputDir + "plots/median-time-r" + runs + "-s" + s + "-k" + k
                        + yLogScaled + ".tex", 13, 7, logYaxis);
         }
      }

      // Create  plots varying k, one for every selected (s, m) pair.
      for (int s : sValues) {
         for (int m : mValues) {
            XYLineChart chart = ExecutionTimePlots.createMedianTimePlot(outputFile, "s", s, "m", m,
                  "k", "Number of points n", 16.0, kValue -> Math.pow(2.0, kValue), logYaxis);
            setSeriesColors(chart, colors);
            expUtil.writeTikzPictureFile(
                  chart, outputDir + "plots/median-time-r" + runs + "-s" + s + "-m" + m
                        + yLogScaled + ".tex", 13, 7, logYaxis);
         }
      }
   }

   /** Assigns a stable color to each implementation series. */
   private static void setSeriesColors(XYLineChart chart, Color[] colors) {
      for (int i = 0; i < colors.length; i++)
         chart.getSeriesCollection().setColor(i, colors[i]);
   }
}
