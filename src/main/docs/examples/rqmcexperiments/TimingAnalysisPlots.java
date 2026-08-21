package rqmcexperiments;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import umontreal.ssj.charts.XYLineChart;
import umontreal.ssj.probdist.EmpiricalDist;

/**
 * Builds CSV summaries and plots for the NUS all-methods timing analysis.
 *
 * <p>The class reads regression and timing outputs, summarizes values that are
 * useful for analysis, then creates PNG and LaTeX figures from those summaries.
 */
public class TimingAnalysisPlots {

   private static final int RUNS = 11;
   private static final Path INPUT_DIRECTORY = Path.of(
         "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp");
   private static final Path OUTPUT_DIRECTORY = Path.of(
         "/home/otman/Documents/GitHub/Data/o-test/nus/nus-report");
   private static final Color[] COLORS = {
         Color.RED, Color.BLUE, new Color(0, 190, 60),
         new Color(220, 190, 0), Color.MAGENTA, Color.CYAN.darker()
   };

   /**
    * Holds the descriptive statistics computed for one group of numeric
    * values.
    */
   private static class Statistics {
      int observations;
      double median;
      double minimum;
      double maximum;
      double firstQuartile;
      double thirdQuartile;
   }

   /**
    * Runs the full timing-analysis plotting workflow.
    *
    * <p>The workflow reads the interaction r_beta table, the interaction
    * coefficient table, and the original timing table. It writes summary CSV
    * files for r_beta trends, interaction coefficient ratios, and execution
    * times, then saves the corresponding plots.
    */
   public static void main(String[] args) throws IOException {
      // Inputs produced by the regression and timing experiments.
      Path rBetaInput = INPUT_DIRECTORY.resolve(
            "interaction-r-beta-r" + RUNS + ".csv");
      Path interactionInput = INPUT_DIRECTORY.resolve(
            "interaction-regression-r" + RUNS + ".csv");
      Path timingInput = INPUT_DIRECTORY.resolve(
            "all-methods-" + RUNS + ".csv");

      Files.createDirectories(OUTPUT_DIRECTORY);

      // Summary CSV files used as stable inputs for the plots below.
      Path rBetaByM = OUTPUT_DIRECTORY.resolve(
            "r-beta-vs-m-summary-r" + RUNS + ".csv");
      Path rBetaByK = OUTPUT_DIRECTORY.resolve(
            "r-beta-vs-k-summary-r" + RUNS + ".csv");
      Path repeatedCostPerPointReplicationDimension = OUTPUT_DIRECTORY.resolve(
            "repeated-cost-per-point-replication-dimension-summary-r"
                  + RUNS + ".csv");
      Path nDependentToRepeatedCostRatio = OUTPUT_DIRECTORY.resolve(
            "n-dependent-to-repeated-cost-ratio-summary-r" + RUNS + ".csv");
      Path timeByM = OUTPUT_DIRECTORY.resolve(
            "execution-time-vs-m-s32-k16-summary-r" + RUNS + ".csv");

      // Convert detailed experiment outputs into compact summaries that are
      // easier to inspect and plot.
      writeRBetaSummary(rBetaInput, rBetaByM, "m");
      writeRBetaSummary(rBetaInput, rBetaByK, "k");
      writeInteractionRatioSummary(
            interactionInput, repeatedCostPerPointReplicationDimension, true);
      writeInteractionRatioSummary(
            interactionInput, nDependentToRepeatedCostRatio, false);
      writeTimeSummary(timingInput, timeByM, 32, 16);

      // Create the figures used to inspect r_beta, interaction coefficients,
      // coefficient ratios, and execution time behavior.
      saveRBetaPlot(rBetaByM, "m", "Number of replications m",
            "R_beta versus m",
            OUTPUT_DIRECTORY.resolve("r-beta-vs-m-r" + RUNS + ".png"));
      saveRBetaPlot(rBetaByK, "k", "k (n = 2^k)",
            "R_beta versus k",
            OUTPUT_DIRECTORY.resolve("r-beta-vs-k-r" + RUNS + ".png"));
      saveHorizontalComparison(repeatedCostPerPointReplicationDimension, true,
            "Repeated cost per point, per replication, and per dimension (C_mn/s)",
            "coefficient_mn / s",
            OUTPUT_DIRECTORY.resolve(
                  "repeated-cost-per-point-replication-dimension-r"
                        + RUNS + ".png"));
      saveHorizontalComparison(nDependentToRepeatedCostRatio, false,
            "Ratio of n-dependent to repeated cost, D_n/C_mn",
            "n_dependent_to_repeated_cost_ratio = coefficient_n / coefficient_mn",
            OUTPUT_DIRECTORY.resolve(
                  "n-dependent-to-repeated-cost-ratio-r" + RUNS + ".png"));
      saveTimePlot(timeByM, 32, 16);
   }

   /**
    * Summarizes r_beta values by method and by one x variable.
    *
    * <p>The method reads the r_beta CSV file, groups rows by method, then
    * groups finite r_beta values by the selected x column. For each method and
    * x value, it writes the number of observations, median, first quartile, and
    * third quartile.
    */
   static void writeRBetaSummary(Path inputFile, Path outputFile,
         String xColumn) throws IOException {
      int methodIndex = expUtil.getCsvColumnIndex(inputFile, "method");
      int xIndex = expUtil.getCsvColumnIndex(inputFile, xColumn);
      int rBetaIndex = expUtil.getCsvColumnIndex(inputFile, "r_beta");
      List<String[]> rows = expUtil.filterCsvRows(inputFile, Map.of());
      Map<String, List<String[]>> byMethod =
            expUtil.groupAndSortCsvRows(rows, methodIndex, xIndex);

      try (BufferedWriter writer = newWriter(outputFile)) {
         writeLine(writer, "method," + xColumn
               + ",observations,median_r_beta,q1_r_beta,q3_r_beta");
         for (Map.Entry<String, List<String[]>> methodEntry
               : sortedEntries(byMethod)) {
            Map<Double, List<Double>> valuesByX = new TreeMap<>();
            for (String[] row : methodEntry.getValue()) {
               Double value = parseFinite(row[rBetaIndex]);
               // Ignore NA and non-finite r_beta values so the summary
               // describes only usable fitted quantities.
               if (value != null)
                  valuesByX.computeIfAbsent(
                        Double.parseDouble(row[xIndex]), key -> new ArrayList<>())
                        .add(value);
            }
            for (Map.Entry<Double, List<Double>> xEntry
                  : valuesByX.entrySet()) {
               Statistics statistics = statistics(xEntry.getValue());
               writeLine(writer, String.format(Locale.US,
                     "%s,%.12g,%d,%.12g,%.12g,%.12g",
                     methodEntry.getKey(), xEntry.getKey(),
                     statistics.observations, statistics.median,
                     statistics.firstQuartile, statistics.thirdQuartile));
            }
         }
      }
   }

   /**
    * Summarizes interaction-coefficient quantities by method.
    *
    * <p>The method reads the fitted interaction coefficients and groups them by
    * method. Depending on the selected mode, it summarizes either
    * coefficient_mn / s, the repeated cost per point per replication per
    * dimension, or coefficient_n / coefficient_mn, the ratio of n-dependent
    * cost to repeated cost.
    */
   static void writeInteractionRatioSummary(Path inputFile,
         Path outputFile, boolean repeatedCost) throws IOException {
      int methodIndex = expUtil.getCsvColumnIndex(inputFile, "method");
      int sIndex = expUtil.getCsvColumnIndex(inputFile, "s");
      int coefficientNIndex =
            expUtil.getCsvColumnIndex(inputFile, "coefficient_n");
      int coefficientMNIndex =
            expUtil.getCsvColumnIndex(inputFile, "coefficient_mn");
      List<String[]> rows = expUtil.filterCsvRows(inputFile, Map.of());
      Map<String, List<String[]>> byMethod =
            expUtil.groupAndSortCsvRows(rows, methodIndex, sIndex);
      String valueName = repeatedCost
            ? "repeated_cost_per_point_replication_dimension"
            : "n_dependent_to_repeated_cost_ratio";

      try (BufferedWriter writer = newWriter(outputFile)) {
         writeLine(writer, "method,observations,median_" + valueName
               + ",min_" + valueName + ",max_" + valueName
               + ",q1_" + valueName + ",q3_" + valueName);
         for (Map.Entry<String, List<String[]>> entry
               : sortedEntries(byMethod)) {
            List<Double> values = new ArrayList<>();
            for (String[] row : entry.getValue()) {
               double coefficientN =
                     Double.parseDouble(row[coefficientNIndex]);
               double coefficientMN =
                     Double.parseDouble(row[coefficientMNIndex]);
               double value = repeatedCost
                     ? coefficientMN / Double.parseDouble(row[sIndex])
                     : (coefficientMN == 0.0 ? Double.NaN
                           : coefficientN / coefficientMN);
               // Skip ratios that cannot be plotted or summarized as finite
               // numbers.
               if (Double.isFinite(value))
                  values.add(value);
            }
            Statistics statistics = statistics(values);
            writeLine(writer, String.format(Locale.US,
                  "%s,%d,%.12g,%.12g,%.12g,%.12g,%.12g",
                  entry.getKey(), statistics.observations, statistics.median,
                  statistics.minimum, statistics.maximum,
                  statistics.firstQuartile, statistics.thirdQuartile));
         }
      }
   }

   /**
    * Summarizes median execution time by method and m for one fixed s and k.
    *
    * <p>The method filters the original timing CSV to the selected dimension
    * and k value, groups the remaining rows by method and m, and writes the
    * median finite execution time for each group.
    */
   static void writeTimeSummary(Path inputFile, Path outputFile,
         int s, int k) throws IOException {
      int methodIndex = expUtil.getCsvColumnIndex(inputFile, "method");
      int mIndex = expUtil.getCsvColumnIndex(inputFile, "m");
      int timeIndex = expUtil.getCsvColumnIndex(inputFile, "median_time");
      List<String[]> rows = expUtil.filterCsvRows(inputFile, Map.of(
            "s", Integer.toString(s), "k", Integer.toString(k)));
      Map<String, List<String[]>> byMethod =
            expUtil.groupAndSortCsvRows(rows, methodIndex, mIndex);

      try (BufferedWriter writer = newWriter(outputFile)) {
         writeLine(writer, "method,s,k,m,median_time");
         for (Map.Entry<String, List<String[]>> methodEntry
               : sortedEntries(byMethod)) {
            Map<Double, List<Double>> valuesByM = new TreeMap<>();
            for (String[] row : methodEntry.getValue()) {
               Double time = parseFinite(row[timeIndex]);
               // Only finite timing values are included in the median.
               if (time != null)
                  valuesByM.computeIfAbsent(
                        Double.parseDouble(row[mIndex]), key -> new ArrayList<>())
                        .add(time);
            }
            for (Map.Entry<Double, List<Double>> mEntry
                  : valuesByM.entrySet()) {
               Statistics statistics = statistics(mEntry.getValue());
               writeLine(writer, String.format(Locale.US,
                     "%s,%d,%d,%.12g,%.12g",
                     methodEntry.getKey(), s, k, mEntry.getKey(),
                     statistics.median));
            }
         }
      }
   }

   /**
    * Saves a line plot showing median r_beta as a function of one x variable.
    *
    * <p>The method reads one r_beta summary file, creates one line series per
    * method, colors each series, adds a horizontal reference line at r_beta =
    * 1, and writes the chart as a PNG image.
    */
   static void saveRBetaPlot(Path summaryFile, String xColumn,
         String xLabel, String title, Path outputFile) throws IOException {
      int methodIndex = expUtil.getCsvColumnIndex(summaryFile, "method");
      int xIndex = expUtil.getCsvColumnIndex(summaryFile, xColumn);
      int medianIndex =
            expUtil.getCsvColumnIndex(summaryFile, "median_r_beta");
      List<String[]> rows = expUtil.filterCsvRows(summaryFile, Map.of());
      Map<String, List<String[]>> byMethod =
            expUtil.groupAndSortCsvRows(rows, methodIndex, xIndex);

      XYSeriesCollection dataset = new XYSeriesCollection();
      for (Map.Entry<String, List<String[]>> entry : sortedEntries(byMethod)) {
         XYSeries series = new XYSeries(entry.getKey());
         for (String[] row : entry.getValue())
            series.add(Double.parseDouble(row[xIndex]),
                  Double.parseDouble(row[medianIndex]));
         dataset.addSeries(series);
      }

      // Use a shared style across methods so the r_beta plots can be compared
      // visually.
      NumberAxis xAxis = new NumberAxis(xLabel);
      xAxis.setAutoRangeIncludesZero(false);
      NumberAxis yAxis = new NumberAxis("R_beta");
      XYLineAndShapeRenderer renderer =
            new XYLineAndShapeRenderer(true, true);
      for (int i = 0; i < dataset.getSeriesCount(); i++) {
         renderer.setSeriesPaint(i, COLORS[i % COLORS.length]);
         renderer.setSeriesStroke(i, new BasicStroke(1.6f));
      }
      XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
      plot.setBackgroundPaint(Color.WHITE);
      plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
      plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
      ValueMarker reference = new ValueMarker(1.0, Color.DARK_GRAY,
            new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                  BasicStroke.JOIN_BEVEL, 0.0f, new float[] {6.0f, 4.0f}, 0.0f));
      plot.addRangeMarker(reference);
      savePng(new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, true),
            outputFile);
   }

   /**
    * Saves a horizontal comparison plot with min-max ranges, quartile bands,
    * and medians for each method.
    *
    * <p>The method reads one interaction-ratio summary file, sorts methods by
    * name, builds three datasets for the full range, interquartile range, and
    * median marker, then writes the combined chart as a PNG image. The value
    * axis is logarithmic for repeated-cost summaries and linear for ratio
    * summaries.
    */
   static void saveHorizontalComparison(Path summaryFile,
         boolean logarithmic, String title, String valueLabel,
         Path outputFile) throws IOException {
      int methodIndex = expUtil.getCsvColumnIndex(summaryFile, "method");
      String suffix = logarithmic
            ? "repeated_cost_per_point_replication_dimension"
            : "n_dependent_to_repeated_cost_ratio";
      int medianIndex =
            expUtil.getCsvColumnIndex(summaryFile, "median_" + suffix);
      int minIndex = expUtil.getCsvColumnIndex(summaryFile, "min_" + suffix);
      int maxIndex = expUtil.getCsvColumnIndex(summaryFile, "max_" + suffix);
      int q1Index = expUtil.getCsvColumnIndex(summaryFile, "q1_" + suffix);
      int q3Index = expUtil.getCsvColumnIndex(summaryFile, "q3_" + suffix);
      List<String[]> rows = expUtil.filterCsvRows(summaryFile, Map.of());
      rows.sort(Comparator.comparing(row -> row[methodIndex]));

      String[] methods = new String[rows.size()];
      XYSeriesCollection ranges = new XYSeriesCollection();
      XYSeriesCollection intervals = new XYSeriesCollection();
      XYSeries medians = new XYSeries("Median");
      for (int i = 0; i < rows.size(); i++) {
         String[] row = rows.get(i);
         methods[i] = row[methodIndex];
         double minimum = Double.parseDouble(row[minIndex]);
         double maximum = Double.parseDouble(row[maxIndex]);
         double q1 = Double.parseDouble(row[q1Index]);
         double q3 = Double.parseDouble(row[q3Index]);
         if (logarithmic && (minimum <= 0.0 || q1 <= 0.0))
            throw new IllegalArgumentException(
                  "Logarithmic comparison values must be positive.");
         // Each method contributes a thin min-max line, a thicker quartile
         // interval, and one point for the median.
         XYSeries range = new XYSeries(methods[i]);
         range.add(minimum, i);
         range.add(maximum, i);
         ranges.addSeries(range);
         XYSeries interval = new XYSeries(methods[i]);
         interval.add(q1, i);
         interval.add(q3, i);
         intervals.addSeries(interval);
         medians.add(Double.parseDouble(row[medianIndex]), i);
      }
      XYSeriesCollection medianDataset = new XYSeriesCollection(medians);

      // The method names live on the vertical axis; the compared value is on
      // the horizontal axis so long method lists remain readable.
      ValueAxis valueAxis = logarithmic
            ? new LogAxis(valueLabel + " (logarithmic scale)")
            : new NumberAxis(valueLabel);
      SymbolAxis methodAxis = new SymbolAxis("Method", methods);
      methodAxis.setGridBandsVisible(false);
      methodAxis.setRange(-0.6, methods.length - 0.4);
      XYLineAndShapeRenderer rangeRenderer =
            new XYLineAndShapeRenderer(true, false);
      rangeRenderer.setDefaultStroke(new BasicStroke(1.0f));
      rangeRenderer.setDefaultPaint(Color.GRAY);
      XYLineAndShapeRenderer intervalRenderer =
            new XYLineAndShapeRenderer(true, false);
      intervalRenderer.setDefaultStroke(new BasicStroke(5.0f));
      intervalRenderer.setDefaultPaint(new Color(80, 120, 190));
      XYLineAndShapeRenderer medianRenderer =
            new XYLineAndShapeRenderer(false, true);
      medianRenderer.setDefaultPaint(Color.BLACK);
      medianRenderer.setDefaultShape(new Ellipse2D.Double(-4, -4, 8, 8));

      XYPlot plot = new XYPlot(ranges, valueAxis, methodAxis, rangeRenderer);
      plot.setDataset(1, intervals);
      plot.setRenderer(1, intervalRenderer);
      plot.setDataset(2, medianDataset);
      plot.setRenderer(2, medianRenderer);
      plot.setBackgroundPaint(Color.WHITE);
      plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
      plot.setRangeGridlinesVisible(false);
      JFreeChart chart =
            new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
      chart.setPadding(new RectangleInsets(5.0, 110.0, 5.0, 10.0));
      savePng(chart, outputFile);
   }

   /**
    * Saves the execution-time plot for one fixed s and k.
    *
    * <p>The method delegates the median-time chart construction to
    * {@link ExecutionTimePlots}, then writes both a PNG image and a LaTeX
    * PGFPlots version of the same chart.
    */
   private static void saveTimePlot(Path summaryFile, int s, int k)
         throws IOException {
      saveTimePlot(summaryFile, s, k,
            OUTPUT_DIRECTORY.resolve(
                  "execution-time-vs-m-s32-k16-r" + RUNS + ".png"),
            OUTPUT_DIRECTORY.resolve(
                  "execution-time-vs-m-s32-k16-r" + RUNS + ".tex"));
   }

   /**
    * Saves the execution-time plot for one fixed s and k using explicit output
    * file names.
    *
    * <p>The method delegates the median-time chart construction to
    * {@link ExecutionTimePlots}, then writes both a PNG image and a LaTeX
    * PGFPlots version of the same chart.
    */
   static void saveTimePlot(Path summaryFile, int s, int k, Path pngFile,
         Path latexFile) throws IOException {
      XYLineChart chart = ExecutionTimePlots.createMedianTimePlot(
            summaryFile, "s", s, "k", k, "m",
            "Number of replications m", null, value -> value, true);
      savePng(chart.getJFreeChart(), pngFile);
      expUtil.writeLatexFile(chart,
            latexFile.toString(), 13.0, 7.0, true);
   }

   /**
    * Computes descriptive statistics for a nonempty list of finite values.
    *
    * <p>The method copies and sorts the values, records the observation count,
    * minimum, and maximum, and computes the median and quartiles. A single
    * observation is used for all three quantiles.
    */
   private static Statistics statistics(List<Double> values) {
      if (values.isEmpty())
         throw new IllegalArgumentException("No finite values to summarize.");
      double[] sorted = new double[values.size()];
      for (int i = 0; i < values.size(); i++)
         sorted[i] = values.get(i);
      Arrays.sort(sorted);

      Statistics result = new Statistics();
      result.observations = sorted.length;
      result.minimum = sorted[0];
      result.maximum = sorted[sorted.length - 1];
      if (sorted.length == 1) {
         result.median = sorted[0];
         result.firstQuartile = sorted[0];
         result.thirdQuartile = sorted[0];
      } else {
         EmpiricalDist distribution = new EmpiricalDist(sorted);
         result.median = distribution.getMedian();
         result.firstQuartile = distribution.inverseF(0.25);
         result.thirdQuartile = distribution.inverseF(0.75);
      }
      return result;
   }

   /**
    * Returns map entries sorted by their string key.
    */
   private static List<Map.Entry<String, List<String[]>>> sortedEntries(
         Map<String, List<String[]>> groups) {
      List<Map.Entry<String, List<String[]>>> entries =
            new ArrayList<>(groups.entrySet());
      entries.sort(Map.Entry.comparingByKey());
      return entries;
   }

   /**
    * Parses a CSV numeric field and returns null for NA or non-finite values.
    */
   private static Double parseFinite(String text) {
      if (text.equalsIgnoreCase("NA"))
         return null;
      double value = Double.parseDouble(text);
      return Double.isFinite(value) ? value : null;
   }

   /**
    * Opens a UTF-8 writer for a generated summary CSV file.
    */
   private static BufferedWriter newWriter(Path outputFile)
         throws IOException {
      return Files.newBufferedWriter(
            outputFile, StandardCharsets.UTF_8);
   }

   /**
    * Writes one CSV line and terminates it.
    */
   private static void writeLine(BufferedWriter writer, String line)
         throws IOException {
      writer.write(line);
      writer.newLine();
   }

   /**
    * Saves a JFreeChart as a 1200 by 700 PNG image.
    */
   private static void savePng(JFreeChart chart, Path outputFile)
         throws IOException {
      ChartUtils.saveChartAsPNG(outputFile.toFile(), chart, 1200, 700);
   }
}
