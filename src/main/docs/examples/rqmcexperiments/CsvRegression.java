package rqmcexperiments;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import umontreal.ssj.functionfit.LeastSquares;

/**
 * Computes configurable linear regressions from CSV data.
 */
public class CsvRegression {

   public static void main(String[] args) throws IOException {
      Path inputFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/all-methods22.csv");
      Path outputFile = Path.of(
            "/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/regressions.csv");
      boolean printResults = true;
      boolean writeCsv = true;

      if (!printResults && !writeCsv)
         throw new IllegalArgumentException(
               "At least one of printResults or writeCsv must be true.");

      try (BufferedWriter writer = writeCsv
            ? Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)
            : null) {
         outputHeader(writer, printResults);

         // Linear regression of median_time with respect to m.
         regress(inputFile, "s", 16, "k", 14,
               "method", "m", "median_time", 20.0, false,
               writer, printResults);

         // Linear regression of log2(median_time) with respect to k.
         regress(inputFile, "s", 16, "m", 1,
               "method", "k", "median_time", 15.0, true,
               writer, printResults);
      }
   }

   private static void regress(Path inputFile,
         String filterColumn1, int filterValue1,
         String filterColumn2, int filterValue2,
         String groupColumn, String xColumn, String yColumn,
         Double maxX, boolean logY,
         BufferedWriter writer, boolean printResults) throws IOException {
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

      for (Map.Entry<String, List<String[]>> entry
            : groupedRows.entrySet()) {
         List<String[]> methodRows = entry.getValue();
         if (methodRows.size() < 2)
            throw new IllegalArgumentException(
                  "At least two observations are required for group "
                        + entry.getKey() + ".");

         double[] x = new double[methodRows.size()];
         double[] y = new double[methodRows.size()];
         for (int i = 0; i < methodRows.size(); i++) {
            x[i] = Double.parseDouble(methodRows.get(i)[xColumnIndex]);
            double value =
                  Double.parseDouble(methodRows.get(i)[yColumnIndex]);
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
         double rSquared =
               coefficientOfDetermination(x, y, coefficients);

         outputResult(writer, printResults, entry.getKey(),
               filterColumn1, filterValue1, filterColumn2, filterValue2,
               groupColumn, xColumn, yColumn, logY, maxX, x.length,
               coefficients[0], coefficients[1], rSquared);
      }
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

   private static void outputHeader(BufferedWriter writer,
         boolean printResults) throws IOException {
      String header = "group_variable,group_value,filter_1,filter_value_1,filter_2,"
            + "filter_value_2,x_variable,y_variable,log_y,max_x,observations,"
            + "intercept,slope,r_squared";
      outputLine(writer, printResults, header);
   }

   private static void outputResult(BufferedWriter writer,
         boolean printResults, String groupValue,
         String filterColumn1, int filterValue1,
         String filterColumn2, int filterValue2,
         String groupColumn, String xColumn, String yColumn, boolean logY,
         Double maxX, int observations,
         double intercept, double slope, double rSquared) throws IOException {
      String result = String.format(Locale.US,
            "%s,%s,%s,%d,%s,%d,%s,%s,%b,%s,%d,%.12g,%.12g,%.12g",
            groupColumn, groupValue, filterColumn1, filterValue1,
            filterColumn2, filterValue2, xColumn, yColumn, logY,
            maxX == null ? "" : Double.toString(maxX),
            observations, intercept, slope, rSquared);
      outputLine(writer, printResults, result);
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
