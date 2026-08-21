package rqmcexperiments;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the NUS all-methods regression examples from one experiment script and
 * writes a separate CSV file for each kind of result.
 */
public class AllmethodsNusRegCsv {

   /**
    * Configures the NUS all-methods CSV inputs, runs all regression examples,
    * and writes the requested result tables.
    */
   public static void main(String[] args) throws IOException {
      // General experiment settings used by the examples below. The run count
      // selects the all-methods input file, while s, k, and m are the fixed
      // values used by the simple-regression examples.
      int runs = 11;
      int s = 8;
      int k = 10;
      int m = 20;

      String resultDir = "/home/otman/Documents/GitHub/rqmc-experiments/data/";

      // This input file is expected to contain all methods and all experiment
      // settings in one CSV table, including method, s, k, m, and median_time
      // columns.   
      Path inputFile = Path.of(resultDir + "allMetTime" + runs + ".csv");

      // The simple-regression outputs are kept separate from the interaction
      // outputs because they answer different questions about the timing data.
      Path simpleMRegressionFile = Path.of(
            resultDir + "simple-m-regression-r" + runs + "-s" + s
                  + "-k" + k + ".csv");
      Path simpleNRegressionFile = Path.of(
            resultDir + "simple-n-regression-r" + runs + "-s" + s
                  + "-m" + m + ".csv");
      Path simpleBothRegressionsFile = Path.of(
            resultDir + "simple-m-and-n-regressions-r" + runs + "-s" + s
                  + "-k" + k
                  + "-m" + m + ".csv");
      Path simpleRegressionRBetaComparisonFile = Path.of(
            resultDir + "simple-regression-r-beta-comparison-r" + runs
                  + "-s" + s + "-k" + k
                  + "-m" + m + ".csv");

      // These output files store the active interaction-model workflow:
      // fitted coefficients, r_beta values evaluated on a grid, and a compact
      // method-level summary.
      Path interactionRegressionCoefficientsFile = Path.of(
            resultDir + "interaction-regression-coefficients-r" + runs + ".csv");
      Path interactionRBetaGridFile = Path.of(
            resultDir + "interaction-r-beta-grid-r" + runs + ".csv");
      Path interactionRegressionSummaryFile = Path.of(
            resultDir + "interaction-regression-summary-r" + runs + ".csv");

      // Results can be printed, written to CSV files, or both. At least one
      // destination must be active so running the example produces something.
      boolean printResults = true;
      boolean writeCsv = true;
      if (!printResults && !writeCsv)
         throw new IllegalArgumentException(
               "At least one of printResults or writeCsv must be true.");

      // Example 1: simple regression in m.
      //
      // This fixes the dimension s and sample-size exponent k, then fits one
      // model per method:
      //
      // median_time = intercept + slope * m
      //
      // The slope estimates how much the median running time changes when m
      // increases while s and k stay fixed. Rows with m greater than 50 are
      // ignored so the fit focuses on the selected range of m values.
      Map<String, CsvRegression.RegressionResult> simpleMRegressionResults =
            CsvRegression.regress(
                  inputFile, "s", s, "k", k,
                  "method", "m", "m", "median_time",
                  50.0, xValue -> xValue, false);

      // Example 2: simple regression in n = 2^k.
      //
      // This fixes the dimension s and the m value, transforms each k into the
      // corresponding point count n = 2^k, then fits one model per method:
      //
      // median_time = intercept + slope * n
      //
      // The slope estimates how much the median running time changes as the
      // number of points grows while s and m stay fixed. Rows with k greater
      // than 16 are ignored so the fit uses the selected range of n values.
      Map<String, CsvRegression.RegressionResult> simpleNRegressionResults =
            CsvRegression.regress(
                  inputFile, "s", s, "m", m,
                  "method", "k", "n=2^k", "median_time",
                  14.0, kValue -> Math.pow(2.0, kValue), false);

      // Example 3: write the m-based simple regression alone.
      //
      // This output is useful when the goal is only to compare how sensitive
      // each method is to m at the chosen s and k values.
      CsvRegression.writeRegressionResults(
            simpleMRegressionFile, List.of(simpleMRegressionResults),
            writeCsv, printResults);

      // Example 4: write the n-based simple regression alone.
      //
      // This output is useful when the goal is only to compare how sensitive
      // each method is to n = 2^k at the chosen s and m values.
      CsvRegression.writeRegressionResults(
            simpleNRegressionFile, List.of(simpleNRegressionResults),
            writeCsv, printResults);

      // Example 5: write both simple regressions in one table.
      //
      // This keeps the m-based and n-based fits in the same output format so
      // their intercepts, slopes, and r-squared values can be inspected side by
      // side by method.
      CsvRegression.writeRegressionResults(
            simpleBothRegressionsFile,
            List.of(simpleMRegressionResults, simpleNRegressionResults),
            writeCsv, printResults);

      // Example 6: compare the two simple regressions with r_beta.
      //
      // This matches the m-based and n-based regressions for each method,
      // checks that the fixed settings agree, and computes r_beta from the two
      // slopes. The comparison estimates how the fitted cost of increasing m
      // relates to the fitted cost of increasing n at the selected k and m.
      CsvRegression.writeComparison(
            simpleRegressionRBetaComparisonFile,
            simpleMRegressionResults, simpleNRegressionResults,
            k, m, writeCsv, printResults);

      // Example 7: interaction regression in m, n, and m*n.
      //
      // Instead of fitting separate m and n regressions, this model fits one
      // timing equation per method and dimension:
      //
      // median_time = intercept + coefficient_m * m
      //             + coefficient_n * n + coefficient_mn * m * n
      //
      // The interaction term lets the fitted effect of m depend on n, and the
      // fitted effect of n depend on m. The workflow repeats this over several
      // dimensions, then uses the fitted coefficients to compute r_beta values
      // and method-level summaries.
      int[] sValues = {2, 4, 6, 8, 16, 32};
      int[] kValues = {8, 10, 12, 14, 16};
      int[] mValues = {1, 2, 5, 10, 20, 30, 50};
      Map<String, CsvRegression.InteractionResult> allInteractionResults =
            new LinkedHashMap<>();

      // Fit one interaction model per method for each selected dimension. The
      // map key combines s and method because the same method appears once for
      // each dimension.
      for (int sValue : sValues) {
         Map<String, CsvRegression.InteractionResult> interactionResults =
               CsvRegression.fitInteractionModel(
                     inputFile, sValue, 50.0, 16.0);
         for (Map.Entry<String, CsvRegression.InteractionResult> entry
               : interactionResults.entrySet())
            allInteractionResults.put(
                  sValue + "-" + entry.getKey(), entry.getValue());
      }

      // Write the fitted coefficients so each method and dimension can be
      // inspected directly.
      CsvRegression.writeInteractionResults(
            interactionRegressionCoefficientsFile, allInteractionResults,
            writeCsv, printResults);

      // Compute r_beta over the selected k and m grid. Because this comes from
      // the interaction model, r_beta can change with both n = 2^k and m.
      List<CsvRegression.RBetaResult> rBetaResults =
            CsvRegression.computeRBetaValues(
                  allInteractionResults, kValues, mValues);
      CsvRegression.writeRBetaResults(
            interactionRBetaGridFile, rBetaResults, writeCsv, printResults);

      // Summarize the interaction fits and r_beta values by method. This gives
      // a compact table of fit quality, q values, coefficient_mn / s, and the
      // range of r_beta values without reading every detailed row.
      CsvRegression.summarizeInteractions(
            allInteractionResults, rBetaResults,
            interactionRegressionSummaryFile, writeCsv, printResults);
   }
}
