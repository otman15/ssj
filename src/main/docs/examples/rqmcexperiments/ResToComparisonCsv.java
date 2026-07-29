package rqmcexperiments;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ResToComparisonCsv {

   private static final String SSJ_METHOD = "SSJ";

   private static final Pattern FILE_NAME_PATTERN =
         Pattern.compile("^.+-(\\d+)-(\\d+)\\.res$");

   private static final Pattern REPORT_PATTERN =
         Pattern.compile(
               "^REPORT on Tally stat\\. collector ==> .+-(\\d+)-(\\d+)$");

   private static final Pattern VARIANCE_PATTERN =
         Pattern.compile("^variance\\s*=\\s*(\\S+)$");

   private static final Pattern KURTOSIS_PATTERN =
         Pattern.compile(
               "^excess kurtosis, bias corrected\\s*=\\s*(\\S+)$");

   private static final Pattern CPU_TIME_PATTERN =
         Pattern.compile("^CPU time:\\s*(\\S+)$");

   private ResToComparisonCsv() {}

   private static final class Result {
      final int s;
      final int k;
      final String method;
      final double variance;
      final double kurtosis;
      final double cpuTime;
      double timeRatioToSsj;

      Result(int s, int k, String method,
             double variance, double kurtosis, double cpuTime) {
         this.s = s;
         this.k = k;
         this.method = method;
         this.variance = variance;
         this.kurtosis = kurtosis;
         this.cpuTime = cpuTime;
      }
   }

   public static void writeComparisonTable(
         Path resDirectory,
         Path outputCsv) throws IOException {

      List<Result> results = readResDirectory(resDirectory);
      completeAndWrite(results, outputCsv);
   }

   public static void writeComparisonTable(
         Path resDirectory,
         Path additionalCsv,
         Path outputCsv) throws IOException {

      List<Result> results = readResDirectory(resDirectory);
      readCsv(additionalCsv, results);
      completeAndWrite(results, outputCsv);
   }

   public static void writeComparisonTable(
         Path resDirectory,
         List<Path> additionalCsvFiles,
         Path outputCsv) throws IOException {

      List<Result> results = readResDirectory(resDirectory);

      for (Path additionalCsv : additionalCsvFiles)
         readCsv(additionalCsv, results);

      completeAndWrite(results, outputCsv);
   }

   private static List<Result> readResDirectory(
         Path resDirectory) throws IOException {

      List<Result> results = new ArrayList<>();

      try (Stream<Path> files = Files.list(resDirectory)) {
         Path[] resFiles = files
               .filter(Files::isRegularFile)
               .filter(path ->
                     path.getFileName().toString().endsWith(".res"))
               .sorted()
               .toArray(Path[]::new);

         for (Path file : resFiles)
            readResFile(file, results);
      }

      return results;
   }

   private static void readResFile(
         Path resFile,
         List<Result> results) throws IOException {

      int s = extractDimension(resFile);

      String currentMethod = null;
      Integer currentK = null;
      Double currentVariance = null;
      Double currentKurtosis = null;

      try (BufferedReader reader = Files.newBufferedReader(
            resFile,
            StandardCharsets.UTF_8)) {

         String line;

         while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();

            if (trimmed.startsWith("* ")) {
               currentMethod = trimmed.substring(2).trim();
               continue;
            }

            Matcher reportMatcher =
                  REPORT_PATTERN.matcher(trimmed);

            if (reportMatcher.matches()) {
               currentK =
                     Integer.parseInt(reportMatcher.group(1));
               currentVariance = null;
               currentKurtosis = null;
               continue;
            }

            Matcher varianceMatcher =
                  VARIANCE_PATTERN.matcher(trimmed);

            if (varianceMatcher.matches() && currentK != null) {
               currentVariance =
                     Double.parseDouble(varianceMatcher.group(1));
               continue;
            }

            Matcher kurtosisMatcher =
                  KURTOSIS_PATTERN.matcher(trimmed);

            if (kurtosisMatcher.matches() && currentK != null) {
               currentKurtosis =
                     Double.parseDouble(kurtosisMatcher.group(1));
               continue;
            }

            Matcher cpuMatcher =
                  CPU_TIME_PATTERN.matcher(trimmed);

            if (cpuMatcher.matches()
                  && currentMethod != null
                  && currentK != null
                  && currentVariance != null
                  && currentKurtosis != null) {

               results.add(new Result(
                     s,
                     currentK,
                     currentMethod,
                     currentVariance,
                     currentKurtosis,
                     parseCpuTime(cpuMatcher.group(1))));

               currentK = null;
               currentVariance = null;
               currentKurtosis = null;
            }
         }
      }
   }

   private static void readCsv(
         Path csvFile,
         List<Result> results) throws IOException {

      try (BufferedReader reader = Files.newBufferedReader(
            csvFile,
            StandardCharsets.UTF_8)) {

         String header = reader.readLine();
         boolean hasKurtosis = header != null
               && Stream.of(header.split(",", -1))
                     .map(ResToComparisonCsv::unquote)
                     .anyMatch("kurtosis"::equals);

         String line;

         while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty())
               continue;

            String[] fields = line.split(",", -1);

            if (fields.length < 5) {
               throw new IllegalArgumentException(
                     "Invalid CSV row: " + line);
            }

            results.add(new Result(
                  Integer.parseInt(unquote(fields[0])),
                  Integer.parseInt(unquote(fields[1])),
                  unquote(fields[2]),
                  Double.parseDouble(unquote(fields[3])),
                  hasKurtosis
                        ? Double.parseDouble(unquote(fields[4]))
                        : Double.NaN,
                  Double.parseDouble(
                        unquote(fields[hasKurtosis ? 5 : 4]))));
         }
      }
   }

   private static void completeAndWrite(
         List<Result> results,
         Path outputCsv) throws IOException {

      computeTimeRatios(results);

      results.sort(
            Comparator.comparingInt((Result result) -> result.s)
                  .thenComparingInt(result -> result.k)
                  .thenComparing(result -> result.method));

      writeCsv(results, outputCsv);
   }

   private static void computeTimeRatios(
         List<Result> results) {

      for (Result result : results) {
         double ssjTime = Double.NaN;
         int ssjCount = 0;

         for (Result reference : results) {
            if (reference.s == result.s
                  && reference.k == result.k
                  && SSJ_METHOD.equals(reference.method)) {

               ssjTime = reference.cpuTime;
               ssjCount++;
            }
         }

         if (ssjCount != 1) {
            throw new IllegalArgumentException(
                  "Expected one SSJ result for s = "
                        + result.s
                        + ", k = "
                        + result.k
                        + ", found "
                        + ssjCount);
         }

         result.timeRatioToSsj =
               result.cpuTime / ssjTime;
      }
   }

   private static void writeCsv(
         List<Result> results,
         Path outputCsv) throws IOException {

      Path parent = outputCsv.getParent();

      if (parent != null)
         Files.createDirectories(parent);

      try (BufferedWriter writer = Files.newBufferedWriter(
            outputCsv,
            StandardCharsets.UTF_8)) {

         writer.write(
               "s,k,method,variance,kurtosis,cpu_time,"
                     + "time_ratio_to_ssj");
         writer.newLine();

         for (Result result : results) {
            writer.write(Integer.toString(result.s));
            writer.write(',');
            writer.write(Integer.toString(result.k));
            writer.write(',');
            writer.write(csvEscape(result.method));
            writer.write(',');
            writer.write(Double.toString(result.variance));
            writer.write(',');
            writer.write(Double.toString(result.kurtosis));
            writer.write(',');
            writer.write(Double.toString(result.cpuTime));
            writer.write(',');
            writer.write(String.format(java.util.Locale.US,"%.2f", result.timeRatioToSsj));
            writer.newLine();
         }
      }
   }

   private static int extractDimension(Path resFile) {
      String fileName = resFile.getFileName().toString();
      Matcher matcher =
            FILE_NAME_PATTERN.matcher(fileName);

      if (!matcher.matches()) {
         throw new IllegalArgumentException(
               "Cannot extract s from file name: "
                     + fileName);
      }

      return Integer.parseInt(matcher.group(1));
   }

   private static double parseCpuTime(String formattedTime) {
      String[] fields = formattedTime.split(":");

      if (fields.length != 3) {
         throw new IllegalArgumentException(
               "Invalid CPU time: " + formattedTime);
      }

      double hours = Double.parseDouble(fields[0]);
      double minutes = Double.parseDouble(fields[1]);
      double seconds = Double.parseDouble(fields[2]);

      return 3600.0 * hours
            + 60.0 * minutes
            + seconds;
   }

   private static String unquote(String value) {
      value = value.trim();

      if (value.length() >= 2
            && value.startsWith("\"")
            && value.endsWith("\"")) {
         return value.substring(1, value.length() - 1);
      }

      return value;
   }

   private static String csvEscape(String value) {
      return "\""
            + value.replace("\"", "\"\"")
            + "\"";
   }

   public static void main(String[] args) throws IOException {


      Path resDirectory = Paths.get("/home/otman/Documents/GitHub/Data/o-test/nus/comp/");
      Path outputCsv1 = Paths.get("/home/otman/Documents/GitHub/rqmc-experiments/results/comp1.csv");
      Path outputCsv2 = Paths.get("/home/otman/Documents/GitHub/rqmc-experiments/results/comp2.csv");
      Path csvfile = Paths.get("/home/otman/Documents/GitHub/rqmc-experiments/results/rqmc_fast_owen_results.csv");
      Path csvfile2 = Paths.get("/home/otman/Documents/GitHub/rqmc-experiments/results/scMl_res.csv");
      Path csvfile3 = Paths.get("/home/otman/Documents/GitHub/rqmc-experiments/results/qmcpy_results.csv");

      
  
      writeComparisonTable(resDirectory, outputCsv1);
      writeComparisonTable(
            resDirectory,
            java.util.Arrays.asList(csvfile, csvfile2,csvfile3),
            outputCsv2);

   }
}

      // Path resDirectory = Paths.get("/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/");
      // Path outputCsv = Paths.get("/home/otman/Documents/GitHub/Data/o-test/nus/nus-comp/comp.csv");
