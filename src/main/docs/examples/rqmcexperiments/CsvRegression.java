package rqmcexperiments;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import umontreal.ssj.functionfit.LeastSquares;
import umontreal.ssj.util.Misc;

/**
 * Computes configurable linear regressions from CSV data.
 */
public class CsvRegression {

   private static class RegressionResult {
      String groupColumn;
      String groupValue;
      String filterColumn1;
      int filterValue1;
      String filterColumn2;
      int filterValue2;
      String xColumn;
      String xTransformName;
      String yColumn;
      boolean logY;
      Double maxX;
      int observations;
      double intercept;
      double slope;
      double rSquared;
   }

   private static class InteractionResult {
      String method;
      int s;
      int observations;
      double intercept;
      double coefficientM;
      double coefficientN;
      double coefficientMN;
      double rSquared;
   }

   private static class RBetaResult {
      String method;
      int s;
      int k;
      int m;
      double rBeta;
      double rBetaB0;
   }

   public static void main(String[] args) throws IOException {
      int runs = 11;
      int s = 8;
      int k = 10;
      int m = 20;
      Path inputFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/all-methods-"
                  + runs + ".csv");
      Path results1File = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "regression-x1-r" + runs + "-s" + s + "-k" + k + ".csv");
      Path bothResultsFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "regressions-r" + runs + "-s" + s + "-k" + k
                  + "-m" + m + ".csv");
      Path comparisonFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "regression-comparison-r" + runs + "-s" + s + "-k" + k
                  + "-m" + m + ".csv");
      Path interactionFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "interaction-regression-r" + runs + "-s" + s + ".csv");
      boolean printResults = true;
      boolean writeCsv = true;

      if (!printResults && !writeCsv)
         throw new IllegalArgumentException(
               "At least one of printResults or writeCsv must be true.");

      // Map<String, RegressionResult> results1 = regress(
      //       inputFile, "s", s, "k", k,
      //       "method", "m", "m", "median_time",
      //       50.0, xValue -> xValue, false);

      // Map<String, RegressionResult> results2 = regress(
      //       inputFile, "s", s, "m", m,
      //       "method", "k", "n=2^k", "median_time",
      //       16.0, kValue -> Math.pow(2.0, kValue), false);

      // // Example 1: write results1 alone.
      // writeRegressionResults(
      //       results1File, List.of(results1), writeCsv, printResults);

      // // Example 2: write results1 and results2 in the original row format.
      // writeRegressionResults(
      //       bothResultsFile, List.of(results1, results2),
      //       writeCsv, printResults);

      // // Example 3: match results1 and results2 and write r_beta.
      // writeComparison(
      //       comparisonFile, results1, results2, k, m,
      //       writeCsv, printResults);
      int[] sValues = {2,4,6,8,16,32};
      int[] kValues = {8,10,12,14,16};
      int[] mValues = {1,2,5,10,20,30,50};
      Map<String, InteractionResult> allInteractionResults =
            new LinkedHashMap<>();
      for(int sval: sValues){
            Map<String, InteractionResult> interactionResults =
                  fitInteractionModel(inputFile, sval, 50.0, 16.0);
            for (Map.Entry<String, InteractionResult> entry
                  : interactionResults.entrySet())
               allInteractionResults.put(
                     sval + "-" + entry.getKey(), entry.getValue());
      }
      Path outputFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "interaction-regression-r" + runs + ".csv");
      writeInteractionResults(
            outputFile, allInteractionResults, writeCsv, printResults);
      List<RBetaResult> rBetaResults = computeRBetaValues(
            allInteractionResults, kValues, mValues);
      Path rBetaFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "interaction-r-beta-r" + runs + ".csv");
      writeRBetaResults(
            rBetaFile, rBetaResults, writeCsv, printResults);
      Path summaryFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/"
                  + "interaction-summary-r" + runs + ".csv");
      summarizeInteractions(
            allInteractionResults, rBetaResults,
            summaryFile, writeCsv, printResults);
   }

   private static List<RBetaResult> computeRBetaValues(
         Map<String, InteractionResult> interactionResults,
         int[] kValues, int[] mValues) {
      List<RBetaResult> results = new ArrayList<>();
      for (InteractionResult interaction : interactionResults.values()) {
         for (int k : kValues) {
            double n = Math.scalb(1.0, k);
            for (int m : mValues) {
               double denominator = n
                     * (interaction.coefficientN
                           + interaction.coefficientMN * m);
               RBetaResult result = new RBetaResult();
               result.method = interaction.method;
               result.s = interaction.s;
               result.k = k;
               result.m = m;
               result.rBeta = denominator == 0.0 ? Double.NaN
                     : m * (interaction.coefficientM
                           + interaction.coefficientMN * n) / denominator;
               double denominatorB0 = interaction.coefficientN
                     + interaction.coefficientMN * m;
               result.rBetaB0 = denominatorB0 == 0.0 ? Double.NaN
                     : m * interaction.coefficientMN / denominatorB0;
               results.add(result);
            }
         }
      }
      return results;
   }

   private static void writeRBetaResults(Path outputFile,
         List<RBetaResult> results,
         boolean writeCsv, boolean printResults) throws IOException {
      try (BufferedWriter writer = openWriter(outputFile, writeCsv)) {
         outputLine(writer, printResults,
               "method,s,k,m,r_beta,r_beta_b0");
         for (RBetaResult result : results) {
            String rBeta = Double.isFinite(result.rBeta)
                  ? String.format(Locale.US, "%.12g", result.rBeta) : "NA";
            String rBetaB0 = Double.isFinite(result.rBetaB0)
                  ? String.format(Locale.US, "%.12g", result.rBetaB0) : "NA";
            outputLine(writer, printResults,
                  String.format(Locale.US, "%s,%d,%d,%d,%s,%s",
                        result.method, result.s, result.k, result.m,
                        rBeta, rBetaB0));
         }
      }
   }

   private static void summarizeInteractions(
         Map<String, InteractionResult> results,
         List<RBetaResult> rBetaResults, Path outputFile,
         boolean writeCsv, boolean printResults) throws IOException {
      Map<String, List<InteractionResult>> groupedResults =
            new LinkedHashMap<>();
      for (InteractionResult result : results.values())
         groupedResults.computeIfAbsent(result.method,
               key -> new ArrayList<>()).add(result);
      Map<String, List<Double>> groupedRBeta = new LinkedHashMap<>();
      for (RBetaResult result : rBetaResults) {
         if (Double.isFinite(result.rBeta))
            groupedRBeta.computeIfAbsent(result.method,
                  key -> new ArrayList<>()).add(result.rBeta);
      }

      try (BufferedWriter writer = openWriter(outputFile, writeCsv)) {
         outputLine(writer, printResults,
               "method,min_r_squared,max_r_squared,median_q,min_q,max_q,"
                     + "median_c_over_s,median_r_beta,min_r_beta,max_r_beta");
         for (Map.Entry<String, List<InteractionResult>> entry
               : groupedResults.entrySet()) {
            List<Double> qValues = new ArrayList<>();
            double[] rSquaredValues = new double[entry.getValue().size()];
            double[] cOverSValues = new double[entry.getValue().size()];

            for (int i = 0; i < entry.getValue().size(); i++) {
               InteractionResult result = entry.getValue().get(i);
               rSquaredValues[i] = result.rSquared;
               cOverSValues[i] = result.coefficientMN / result.s;
               if (result.coefficientMN != 0.0)
                  qValues.add(result.coefficientN / result.coefficientMN);
            }

            double minRSquared = rSquaredValues[0];
            double maxRSquared = rSquaredValues[0];
            for (double value : rSquaredValues) {
               minRSquared = Math.min(minRSquared, value);
               maxRSquared = Math.max(maxRSquared, value);
            }
            double medianCOverS =
                  Misc.getMedian(cOverSValues, cOverSValues.length);

            String medianQ = "NA";
            String minQ = "NA";
            String maxQ = "NA";
            if (!qValues.isEmpty()) {
               double[] qArray = new double[qValues.size()];
               for (int i = 0; i < qValues.size(); i++)
                  qArray[i] = qValues.get(i);
               double minQValue = qArray[0];
               double maxQValue = qArray[0];
               for (double value : qArray) {
                  minQValue = Math.min(minQValue, value);
                  maxQValue = Math.max(maxQValue, value);
               }
               medianQ = String.format(Locale.US, "%.12g",
                     Misc.getMedian(qArray, qArray.length));
               minQ = String.format(Locale.US, "%.12g", minQValue);
               maxQ = String.format(Locale.US, "%.12g", maxQValue);
            }

            String medianRBeta = "NA";
            String minRBeta = "NA";
            String maxRBeta = "NA";
            List<Double> methodRBeta = groupedRBeta.get(entry.getKey());
            if (methodRBeta != null && !methodRBeta.isEmpty()) {
               double[] rBetaArray = new double[methodRBeta.size()];
               for (int i = 0; i < methodRBeta.size(); i++)
                  rBetaArray[i] = methodRBeta.get(i);
               double minRBetaValue = rBetaArray[0];
               double maxRBetaValue = rBetaArray[0];
               for (double value : rBetaArray) {
                  minRBetaValue = Math.min(minRBetaValue, value);
                  maxRBetaValue = Math.max(maxRBetaValue, value);
               }
               medianRBeta = String.format(Locale.US, "%.12g",
                     Misc.getMedian(rBetaArray, rBetaArray.length));
               minRBeta =
                     String.format(Locale.US, "%.12g", minRBetaValue);
               maxRBeta =
                     String.format(Locale.US, "%.12g", maxRBetaValue);
            }

            String line = String.format(Locale.US,
                  "%s,%.12g,%.12g,%s,%s,%s,%.12g,%s,%s,%s",
                  entry.getKey(), minRSquared, maxRSquared,
                  medianQ, minQ, maxQ, medianCOverS,
                  medianRBeta, minRBeta, maxRBeta);
            outputLine(writer, printResults, line);
         }
      }
   }

   private static Map<String, InteractionResult> fitInteractionModel(
         Path inputFile, int s, Double maxM, Double maxK) throws IOException {
      int methodColumnIndex =
            expUtil.getCsvColumnIndex(inputFile, "method");
      int sColumnIndex = expUtil.getCsvColumnIndex(inputFile, "s");
      int kColumnIndex = expUtil.getCsvColumnIndex(inputFile, "k");
      int mColumnIndex = expUtil.getCsvColumnIndex(inputFile, "m");
      int timeColumnIndex =
            expUtil.getCsvColumnIndex(inputFile, "median_time");

      List<String[]> rows = expUtil.filterCsvRows(
            inputFile, Map.of("s", Integer.toString(s)));
      if (maxM != null)
         rows.removeIf(
               row -> Double.parseDouble(row[mColumnIndex]) > maxM);
      if (maxK != null)
         rows.removeIf(
               row -> Double.parseDouble(row[kColumnIndex]) > maxK);

      Map<String, List<String[]>> groupedRows =
            expUtil.groupAndSortCsvRows(
                  rows, methodColumnIndex, mColumnIndex);
      Map<String, InteractionResult> results = new LinkedHashMap<>();

      for (Map.Entry<String, List<String[]>> entry
            : groupedRows.entrySet()) {
         List<String[]> groupRows = entry.getValue();
         if (groupRows.size() < 5)
            throw new IllegalArgumentException(
                  "At least five observations are required for group "
                        + entry.getKey() + ".");

         double mScale = 0.0;
         double nScale = 0.0;
         for (String[] row : groupRows) {
            double mValue = Double.parseDouble(row[mColumnIndex]);
            int kValue = Integer.parseInt(row[kColumnIndex]);
            double nValue = Math.scalb(1.0, kValue);
            mScale = Math.max(mScale, Math.abs(mValue));
            nScale = Math.max(nScale, Math.abs(nValue));
         }
         if (!(mScale > 0.0) || !(nScale > 0.0)
               || !Double.isFinite(mScale) || !Double.isFinite(nScale))
            throw new IllegalArgumentException(
                  "Invalid scaling values for group " + entry.getKey() + ".");

         double[][] x = new double[groupRows.size()][3];
         double[] y = new double[groupRows.size()];
         for (int i = 0; i < groupRows.size(); i++) {
            String[] row = groupRows.get(i);
            double scaledM = Double.parseDouble(row[mColumnIndex]) / mScale;
            int kValue = Integer.parseInt(row[kColumnIndex]);
            double scaledN = Math.scalb(1.0, kValue) / nScale;
            x[i][0] = scaledM;
            x[i][1] = scaledN;
            x[i][2] = scaledM * scaledN;
            y[i] = Double.parseDouble(row[timeColumnIndex]);
         }

         double[] coefficients = LeastSquares.calcCoefficients0(x, y);
         InteractionResult result = new InteractionResult();
         result.method = entry.getKey();
         result.s = Integer.parseInt(groupRows.get(0)[sColumnIndex]);
         result.observations = groupRows.size();
         result.intercept = coefficients[0];
         result.coefficientM = coefficients[1] / mScale;
         result.coefficientN = coefficients[2] / nScale;
         result.coefficientMN = coefficients[3] / (mScale * nScale);
         result.rSquared =
               coefficientOfDetermination(x, y, coefficients);
         results.put(result.method, result);
      }
      return results;
   }

   private static Map<String, RegressionResult> regress(Path inputFile,
         String filterColumn1, int filterValue1,
         String filterColumn2, int filterValue2,
         String groupColumn, String xColumn, String xTransformName,
         String yColumn,
         Double maxX, DoubleUnaryOperator xTransform, boolean logY)
         throws IOException {
      int groupColumnIndex =
            expUtil.getCsvColumnIndex(inputFile, groupColumn);
      int xColumnIndex = expUtil.getCsvColumnIndex(inputFile, xColumn);
      int yColumnIndex = expUtil.getCsvColumnIndex(inputFile, yColumn);

      Map<String, String> conditions = Map.of(
            filterColumn1, Integer.toString(filterValue1),
            filterColumn2, Integer.toString(filterValue2));

      List<String[]> rows = expUtil.filterCsvRows(inputFile, conditions);
      if (maxX != null)
         rows.removeIf(
               row -> Double.parseDouble(row[xColumnIndex]) > maxX);

      Map<String, List<String[]>> groupedRows =
            expUtil.groupAndSortCsvRows(
                  rows, groupColumnIndex, xColumnIndex);
      Map<String, RegressionResult> results = new LinkedHashMap<>();

      for (Map.Entry<String, List<String[]>> entry
            : groupedRows.entrySet()) {
         List<String[]> groupRows = entry.getValue();
         if (groupRows.size() < 2)
            throw new IllegalArgumentException(
                  "At least two observations are required for group "
                        + entry.getKey() + ".");

         double[] x = new double[groupRows.size()];
         double[] y = new double[groupRows.size()];
         for (int i = 0; i < groupRows.size(); i++) {
            x[i] = xTransform.applyAsDouble(
                  Double.parseDouble(groupRows.get(i)[xColumnIndex]));
            double value =
                  Double.parseDouble(groupRows.get(i)[yColumnIndex]);
            if (logY && value <= 0.0)
               throw new IllegalArgumentException(
                     "Log regression requires positive y values.");
            y[i] = logY ? Math.log(value) / Math.log(2.0) : value;
         }

         if (x[0] == x[x.length - 1])
            throw new IllegalArgumentException(
                  "Regression requires at least two distinct x values for group "
                        + entry.getKey() + ".");

         double[] coefficients = LeastSquares.calcCoefficients(x, y);
         RegressionResult result = new RegressionResult();
         result.groupColumn = groupColumn;
         result.groupValue = entry.getKey();
         result.filterColumn1 = filterColumn1;
         result.filterValue1 = filterValue1;
         result.filterColumn2 = filterColumn2;
         result.filterValue2 = filterValue2;
         result.xColumn = xColumn;
         result.xTransformName = xTransformName;
         result.yColumn = yColumn;
         result.logY = logY;
         result.maxX = maxX;
         result.observations = x.length;
         result.intercept = coefficients[0];
         result.slope = coefficients[1];
         result.rSquared =
               coefficientOfDetermination(x, y, coefficients);
         results.put(result.groupValue, result);
      }
      return results;
   }

   private static double coefficientOfDetermination(double[] x, double[] y,
         double[] coefficients) {
      double mean = 0.0;
      for (double value : y)
         mean += value;
      mean /= y.length;

      double residualSumSquares = 0.0;
      double totalSumSquares = 0.0;
      for (int i = 0; i < y.length; i++) {
         double residual =
               y[i] - (coefficients[0] + coefficients[1] * x[i]);
         residualSumSquares += residual * residual;
         double centered = y[i] - mean;
         totalSumSquares += centered * centered;
      }
      return totalSumSquares == 0.0
            ? Double.NaN
            : 1.0 - residualSumSquares / totalSumSquares;
   }

   private static double coefficientOfDetermination(double[][] x, double[] y,
         double[] coefficients) {
      double mean = 0.0;
      for (double value : y)
         mean += value;
      mean /= y.length;

      double residualSumSquares = 0.0;
      double totalSumSquares = 0.0;
      for (int i = 0; i < y.length; i++) {
         double predicted = coefficients[0];
         for (int j = 0; j < x[i].length; j++)
            predicted += coefficients[j + 1] * x[i][j];
         double residual = y[i] - predicted;
         residualSumSquares += residual * residual;
         double centered = y[i] - mean;
         totalSumSquares += centered * centered;
      }
      return totalSumSquares == 0.0
            ? Double.NaN
            : 1.0 - residualSumSquares / totalSumSquares;
   }

   private static void writeRegressionResults(Path outputFile,
         List<Map<String, RegressionResult>> resultSets,
         boolean writeCsv, boolean printResults) throws IOException {
      try (BufferedWriter writer = openWriter(outputFile, writeCsv)) {
         outputLine(writer, printResults,
               "group_variable,group_value,filter_1,filter_value_1,filter_2,"
                     + "filter_value_2,x_variable,x_transform,y_variable,log_y,"
                     + "max_x_variable,observations,intercept,slope,r_squared");
         for (Map<String, RegressionResult> resultSet : resultSets) {
            for (RegressionResult result : resultSet.values())
               outputRegressionResult(writer, printResults, result);
         }
      }
   }

   private static void outputRegressionResult(BufferedWriter writer,
         boolean printResults, RegressionResult result) throws IOException {
      String line = String.format(Locale.US,
            "%s,%s,%s,%d,%s,%d,%s,%s,%s,%b,%s,%d,%.12g,%.12g,%.12g",
            result.groupColumn, result.groupValue,
            result.filterColumn1, result.filterValue1,
            result.filterColumn2, result.filterValue2,
            result.xColumn, result.xTransformName, result.yColumn, result.logY,
            result.maxX == null ? "" : Double.toString(result.maxX),
            result.observations, result.intercept, result.slope,
            result.rSquared);
      outputLine(writer, printResults, line);
   }

   private static void writeComparison(Path outputFile,
         Map<String, RegressionResult> results1,
         Map<String, RegressionResult> results2,
         int k, int m, boolean writeCsv, boolean printResults)
         throws IOException {
      if (!results1.keySet().equals(results2.keySet()))
         throw new IllegalArgumentException(
               "The regression result sets do not contain the same groups.");

      double n = Math.scalb(1.0, k);
      if (!Double.isFinite(n) || m == 0)
         throw new IllegalArgumentException("Invalid k or m for comparison.");

      for (String groupValue : results1.keySet())
         validateComparison(
               results1.get(groupValue), results2.get(groupValue), k, m);

      try (BufferedWriter writer = openWriter(outputFile, writeCsv)) {
         outputLine(writer, printResults,
               "group_value,filter_1,filter_value_1,"
                     + "x1_filter,x1_filter_value,x1_variable,x1_transform,"
                     + "y_variable,observations_x1,intercept_x1,slope_x1,"
                     + "r_squared_x1,x2_filter,x2_filter_value,x2_variable,"
                     + "x2_transform,observations_x2,intercept_x2,slope_x2,"
                     + "r_squared_x2,r_beta");

         for (String groupValue : results1.keySet()) {
            RegressionResult result1 = results1.get(groupValue);
            RegressionResult result2 = results2.get(groupValue);
            double rBeta = (result1.slope / n) / (result2.slope / m);
            String line = String.format(Locale.US,
                  "%s,%s,%d,%s,%d,%s,%s,%s,%d,%.12g,%.12g,%.12g,"
                        + "%s,%d,%s,%s,%d,%.12g,%.12g,%.12g,%.12g",
                  groupValue,
                  result1.filterColumn1, result1.filterValue1,
                  result1.filterColumn2, result1.filterValue2,
                  result1.xColumn, result1.xTransformName, result1.yColumn,
                  result1.observations, result1.intercept, result1.slope,
                  result1.rSquared,
                  result2.filterColumn2, result2.filterValue2,
                  result2.xColumn, result2.xTransformName,
                  result2.observations, result2.intercept, result2.slope,
                  result2.rSquared, rBeta);
            outputLine(writer, printResults, line);
         }
      }
   }

   private static void validateComparison(RegressionResult result1,
         RegressionResult result2, int k, int m) {
      if (!result1.groupColumn.equals(result2.groupColumn)
            || !result1.groupValue.equals(result2.groupValue)
            || !result1.filterColumn1.equals(result2.filterColumn1)
            || result1.filterValue1 != result2.filterValue1
            || !result1.yColumn.equals(result2.yColumn))
         throw new IllegalArgumentException(
               "The regressions are not a valid match for group "
                     + result1.groupValue + ".");
      if (!result1.filterColumn2.equals("k") || result1.filterValue2 != k
            || !result2.filterColumn2.equals("m")
            || result2.filterValue2 != m)
         throw new IllegalArgumentException(
               "The fixed k or m does not match for group "
                     + result1.groupValue + ".");
      if (!Double.isFinite(result1.slope)
            || !Double.isFinite(result2.slope) || result2.slope == 0.0)
         throw new IllegalArgumentException(
               "Invalid slope for group " + result1.groupValue + ".");
   }

   private static void writeInteractionResults(Path outputFile,
         Map<String, InteractionResult> results,
         boolean writeCsv, boolean printResults) throws IOException {
      try (BufferedWriter writer = openWriter(outputFile, writeCsv)) {
         outputLine(writer, printResults,
               "method,s,observations,intercept,coefficient_m,coefficient_n,"
                     + "coefficient_mn,r_squared");
         for (InteractionResult result : results.values()) {
            String line = String.format(Locale.US,
                  "%s,%d,%d,%.12g,%.12g,%.12g,%.12g,%.12g",
                  result.method, result.s, result.observations,
                  result.intercept, result.coefficientM, result.coefficientN,
                  result.coefficientMN, result.rSquared);
            outputLine(writer, printResults, line);
         }
      }
   }

   private static BufferedWriter openWriter(Path outputFile, boolean writeCsv)
         throws IOException {
      if (!writeCsv)
         return null;
      Path parent = outputFile.getParent();
      if (parent != null)
         Files.createDirectories(parent);
      return Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
   }

   private static void outputLine(BufferedWriter writer,
         boolean printResults, String line) throws IOException {
      if (printResults)
         System.out.println(line);
      if (writer != null) {
         writer.write(line);
         writer.newLine();
      }
   }
}
