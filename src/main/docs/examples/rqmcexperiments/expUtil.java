package rqmcexperiments;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jfree.data.xy.XYDataset;

import umontreal.ssj.charts.XYLineChart;

/**
 * Contains utility methods that help with some experiments.
 */
public final class expUtil {

   private expUtil() {
   }

   /**
    * Writes an SSJ line chart to LaTeX using PGFPlots.
    */
   public static void writeLatexFile(XYLineChart chart, String outputFile,
         double width, double height, boolean logYAxis) throws IOException {
      XYDataset dataset =
            chart.getSeriesCollection().getSeriesCollection();
      if (logYAxis) {
         for (int series = 0; series < dataset.getSeriesCount(); series++) {
            for (int item = 0; item < dataset.getItemCount(series); item++) {
               if (dataset.getYValue(series, item) <= 0.0)
                  throw new IllegalArgumentException(
                        "Logarithmic plots require positive y values.");
            }
         }
      }

      String axisEnvironment = logYAxis ? "semilogyaxis" : "axis";
      double xmin = chart.getJFreeChart().getXYPlot()
            .getDomainAxis().getLowerBound();
      double xmax = chart.getJFreeChart().getXYPlot()
            .getDomainAxis().getUpperBound();
      double ymin = chart.getJFreeChart().getXYPlot()
            .getRangeAxis().getLowerBound();
      double ymax = chart.getJFreeChart().getXYPlot()
            .getRangeAxis().getUpperBound();
      String xTickOptions = powerOfTwoTickOptions(dataset);
      if (xTickOptions == null)
         xTickOptions = "scaled x ticks=false,"
               + "xticklabel style={/pgf/number format/1000 sep={,}},";

      Path path = Path.of(outputFile);
      Path parent = path.getParent();
      if (parent != null)
         Files.createDirectories(parent);

      try (BufferedWriter writer =
            Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
         writer.write("\\documentclass[12pt]{article}");
         writer.newLine();
         writer.write("\\usepackage{pgfplots}");
         writer.newLine();
         writer.write("\\pgfplotsset{compat=1.18}");
         writer.newLine();
         writer.write("\\begin{document}");
         writer.newLine();
         writer.write("\\begin{figure}");
         writer.newLine();
         writer.write("\\centering");
         writer.newLine();
         writer.write("\\begin{tikzpicture}");
         writer.newLine();
         writer.write(String.format(Locale.US,
               "\\begin{%s}[width=%.3fcm,height=%.3fcm,"
                     + "title={%s},xlabel={%s},ylabel={%s},"
                     + "xmin=%.15g,xmax=%.15g,ymin=%.15g,ymax=%.15g,"
                     + "%s"
                     + "tick label style={font=\\small},"
                     + "title style={at={(axis description cs:0.5,1.18)},"
                     + "anchor=south},"
                     + "grid=major,legend style={at={(0.5,1.02)},"
                     + "anchor=south,legend columns=3,"
                     + "font=\\scriptsize,draw=none}]",
               axisEnvironment, width, height,
               latexText(chart.getTitle()),
               latexText(chart.getJFreeChart().getXYPlot()
                     .getDomainAxis().getLabel()),
               latexText(chart.getJFreeChart().getXYPlot()
                     .getRangeAxis().getLabel()),
               xmin, xmax, ymin, ymax, xTickOptions));
         writer.newLine();

         for (int series = 0; series < dataset.getSeriesCount(); series++) {
            Color color = chart.getSeriesCollection().getColor(series);
            writer.write(String.format(Locale.US,
                  "\\definecolor{series%d}{RGB}{%d,%d,%d}",
                  series, color.getRed(), color.getGreen(), color.getBlue()));
            writer.newLine();
            writer.write(String.format(
                  "\\addplot[color=series%d,mark=*] coordinates {", series));
            writer.newLine();
            for (int item = 0; item < dataset.getItemCount(series); item++) {
               writer.write(String.format(Locale.US, "(%.15g,%.15g)",
                     dataset.getXValue(series, item),
                     dataset.getYValue(series, item)));
               writer.newLine();
            }
            writer.write("};");
            writer.newLine();
            writer.write("\\addlegendentry{"
                  + latexText(dataset.getSeriesKey(series).toString()) + "}");
            writer.newLine();
         }

         writer.write("\\end{" + axisEnvironment + "}");
         writer.newLine();
         writer.write("\\end{tikzpicture}");
         writer.newLine();
         writer.write("\\end{figure}");
         writer.newLine();
         writer.write("\\end{document}");
         writer.newLine();
      }
   }

   private static String powerOfTwoTickOptions(XYDataset dataset) {
      SortedSet<Double> xValues = new TreeSet<>();
      for (int series = 0; series < dataset.getSeriesCount(); series++) {
         for (int item = 0; item < dataset.getItemCount(series); item++) {
            double x = dataset.getXValue(series, item);
            if (!(x > 0.0))
               return null;
            int exponent = (int) Math.rint(Math.log(x) / Math.log(2.0));
            double powerOfTwo = Math.scalb(1.0, exponent);
            if (Math.abs(x - powerOfTwo) > Math.abs(x) * 1.0e-10)
               return null;
            xValues.add(x);
         }
      }

      if (xValues.isEmpty())
         return null;

      List<Double> ticks = new ArrayList<>(xValues);

      StringBuilder positions = new StringBuilder("xtick={");
      StringBuilder labels = new StringBuilder("xticklabels={");
      for (int i = 0; i < ticks.size(); i++) {
         if (i > 0) {
            positions.append(',');
            labels.append(',');
         }
         double x = ticks.get(i);
         int exponent = (int) Math.rint(Math.log(x) / Math.log(2.0));
         positions.append(String.format(Locale.US, "%.15g", x));
         labels.append("$2^{").append(exponent).append("}$");
      }
      positions.append("},");
      labels.append("},");
      return "scaled x ticks=false," + positions + labels;
   }

   private static String latexText(String text) {
      if (text == null)
         return "";
      return text.replace("\\^", "^")
            .replace("\\", "\\textbackslash{}")
            .replace("_", "\\_")
            .replace("%", "\\%")
            .replace("&", "\\&")
            .replace("#", "\\#")
            .replace("^", "\\^{}");
   }

   /**
    * Merges multiple CSV files into one output file while preserving the order
    * of the input files and the order of the rows inside each file.
    *
    * <p>If {@code hasHeader} is {@code true}, all input files must have the
    * same header. The header is written only once in the output file.
    *
    * <p>If {@code hasHeader} is {@code false}, all rows in all input files
    * must have the same number of columns.
    *
    * @param inputFiles the CSV files to merge, in the desired order
    * @param outputFile the merged CSV file
    * @param hasHeader  {@code true} if the first row of each file is a header
    * @throws IOException if a file cannot be read or written
    * @throws IllegalArgumentException if no input file is provided, an input
    *         file is empty, the headers differ, or rows do not have the same
    *         number of columns
    */
   public static void mergeCsvFiles(
         Path[] inputFiles,
         Path outputFile,
         boolean hasHeader) throws IOException {

      if (inputFiles == null || inputFiles.length == 0)
         throw new IllegalArgumentException("No input CSV file was provided.");

      Path normalizedOutput = outputFile.toAbsolutePath().normalize();

      for (Path inputFile : inputFiles) {
         if (inputFile.toAbsolutePath().normalize().equals(normalizedOutput))
            throw new IllegalArgumentException(
                  "The output file cannot also be an input file: " + outputFile);
      }

      Path parent = outputFile.getParent();
      if (parent != null)
         Files.createDirectories(parent);

      String expectedHeader = null;
      int expectedColumns = -1;

      try (BufferedWriter writer = Files.newBufferedWriter(
            outputFile,
            StandardCharsets.UTF_8)) {

         for (Path inputFile : inputFiles) {
            try (BufferedReader reader = Files.newBufferedReader(
                  inputFile,
                  StandardCharsets.UTF_8)) {

               String line = reader.readLine();

               if (line == null)
                  throw new IllegalArgumentException(
                        "The input file is empty: " + inputFile);

               if (hasHeader) {
                  if (expectedHeader == null) {
                     expectedHeader = line;
                     expectedColumns = countColumns(line);
                     writer.write(line);
                     writer.newLine();
                  } else if (!expectedHeader.equals(line)) {
                     throw new IllegalArgumentException(
                           "The CSV header differs in file: " + inputFile);
                  }

                  line = reader.readLine();
               } else if (expectedColumns < 0) {
                  expectedColumns = countColumns(line);
               }

               while (line != null) {
                  if (countColumns(line) != expectedColumns)
                     throw new IllegalArgumentException(
                           "A row has an unexpected number of columns in file: "
                                 + inputFile);

                  writer.write(line);
                  writer.newLine();
                  line = reader.readLine();
               }
            }
         }
      }
   }

   private static int countColumns(String line) {
      int columns = 1;
      boolean insideQuotes = false;

      for (int i = 0; i < line.length(); i++) {
         char character = line.charAt(i);

         if (character == '"') {
            if (insideQuotes
                  && i + 1 < line.length()
                  && line.charAt(i + 1) == '"') {
               i++;
            } else {
               insideQuotes = !insideQuotes;
            }
         } else if (character == ',' && !insideQuotes) {
            columns++;
         }
      }

      if (insideQuotes)
         throw new IllegalArgumentException(
               "The CSV row contains an unclosed quoted field: " + line);

      return columns;
   }

   /**
 * Reads a CSV file and returns the rows whose values match all the specified
 * filtering conditions.
 *
 * <p>The CSV file must contain a header. Each condition associates a column
 * name with the exact value required in that column. The returned rows
 * preserve their original order and do not include the header.
 *
 * @param inputFile the CSV file to filter
 * @param conditions the column names and values that rows must match
 * @return the matching rows, with each row represented as an array of values
 * @throws IOException if the file cannot be read
 * @throws IllegalArgumentException if the file is empty, a requested column
 *         does not exist, or a row has an invalid number of columns
 */
    public static List<String[]> filterCsvRows(
        Path inputFile,
        Map<String, String> conditions) throws IOException {

    if (conditions == null)
        throw new IllegalArgumentException("The filtering conditions cannot be null.");

    List<String[]> filteredRows = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(
            inputFile,
            StandardCharsets.UTF_8)) {

        String headerLine = reader.readLine();

        if (headerLine == null)
            throw new IllegalArgumentException(
                "The input file is empty: " + inputFile);

        String[] header = parseCsvRow(headerLine);
        int[] conditionColumns = new int[conditions.size()];
        String[] conditionValues = new String[conditions.size()];

        int conditionIndex = 0;

        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            int columnIndex = -1;

            for (int i = 0; i < header.length; i++) {
                if (header[i].equals(condition.getKey())) {
                columnIndex = i;
                break;
                }
            }

            if (columnIndex < 0)
                throw new IllegalArgumentException(
                    "Column not found: " + condition.getKey());

            conditionColumns[conditionIndex] = columnIndex;
            conditionValues[conditionIndex] = condition.getValue();
            conditionIndex++;
        }

        String line;

        while ((line = reader.readLine()) != null) {
            String[] row = parseCsvRow(line);

            if (row.length != header.length)
                throw new IllegalArgumentException(
                    "A row has an unexpected number of columns in file: "
                            + inputFile);

            boolean matches = true;

            for (int i = 0; i < conditionColumns.length; i++) {
                if (!row[conditionColumns[i]].equals(conditionValues[i])) {
                matches = false;
                break;
                }
            }

            if (matches)
                filteredRows.add(row);
        }
    }

    return filteredRows;
    }

    private static String[] parseCsvRow(String line) {
    List<String> values = new ArrayList<>();
    StringBuilder value = new StringBuilder();
    boolean insideQuotes = false;

    for (int i = 0; i < line.length(); i++) {
        char character = line.charAt(i);

        if (character == '"') {
            if (insideQuotes
                && i + 1 < line.length()
                && line.charAt(i + 1) == '"') {
                value.append('"');
                i++;
            } else {
                insideQuotes = !insideQuotes;
            }
        } else if (character == ',' && !insideQuotes) {
            values.add(value.toString());
            value.setLength(0);
        } else {
            value.append(character);
        }
    }

    if (insideQuotes)
        throw new IllegalArgumentException(
                "The CSV row contains an unclosed quoted field: " + line);

    values.add(value.toString());

    return values.toArray(new String[0]);
    }

    /**
     * Groups CSV rows by the value of one column and sorts each group in ascending
     * numeric order using another column.
     *
     * @param rows the CSV rows
     * @param groupColumn the column used to group the rows
     * @param sortColumn the numeric column used to sort each group
     * @return the grouped and sorted rows
     */
    public static Map<String, List<String[]>> groupAndSortCsvRows(
        List<String[]> rows,
        int groupColumn,
        int sortColumn) {

    Map<String, List<String[]>> groups = new HashMap<>();

    for (String[] row : rows)
        groups.computeIfAbsent(row[groupColumn], key -> new ArrayList<>()).add(row);

    for (List<String[]> group : groups.values())
        group.sort((row1, row2) -> Double.compare(
                Double.parseDouble(row1[sortColumn]),
                Double.parseDouble(row2[sortColumn])));

    return groups;
    }

    /**
 * Returns the index of a column in a CSV file header.
 *
 * @param inputFile the CSV file
 * @param columnName the column name to find
 * @return the column index
 * @throws IOException if the CSV file cannot be read
 */
public static int getCsvColumnIndex(
      Path inputFile,
      String columnName) throws IOException {

   try (BufferedReader reader = Files.newBufferedReader(
         inputFile, StandardCharsets.UTF_8)) {

      String headerLine = reader.readLine();

      if (headerLine == null)
         throw new IllegalArgumentException(
               "The CSV file is empty: " + inputFile);

      String[] header = parseCsvRow(headerLine);

      for (int i = 0; i < header.length; i++) {
         if (header[i].equals(columnName))
            return i;
      }
   }

   throw new IllegalArgumentException(
         "Column not found in the CSV header: " + columnName);
}

   public static void main(String[] args) throws IOException {
   Path[] inputFiles = {
         Path.of("/home/otman/Documents/GitHub/rqmc-experiments/R/R-results/owen_time_by_rep.csv"),
         Path.of("/home/otman/Documents/GitHub/rqmc-experiments/julia/jl-results/sciMl_time_by_rep.csv"),
         Path.of("/home/otman/Documents/GitHub/rqmc-experiments/python/py-results/qmcpy_time_by_rep.csv")
   };

   Path outputFile = Path.of(
         "/home/otman/Documents/GitHub/rqmc-experiments/results/all-methods.csv");

   mergeCsvFiles(inputFiles, outputFile, true);

   System.out.println("Merged CSV written to " + outputFile);

      Path inputFile = Path.of(
         "/home/otman/Documents/GitHub/rqmc-experiments/results/all-methods.csv");

   Map<String, String> conditions = Map.of(
         "s", "2",
         "k", "10"
   );

   List<String[]> rows = filterCsvRows(inputFile, conditions);

//    for (String[] row : rows)
//       System.out.println(String.join(",", row));

   Map<String, List<String[]>> groupedRows =
      groupAndSortCsvRows(rows, 0, 3);

for (Map.Entry<String, List<String[]>> entry : groupedRows.entrySet()) {
   String method = entry.getKey();

   System.out.println(method);

   for (String[] row : entry.getValue())
      System.out.println("m = " + row[3]
            + ", median_time = " + row[4]);
}
}
}
