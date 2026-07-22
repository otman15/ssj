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

   private static final Pattern FILE_NAME_PATTERN =
         Pattern.compile("^.+-(\\d+)-(\\d+)\\.res$");

   private static final Pattern REPORT_PATTERN =
         Pattern.compile(
               "^REPORT on Tally stat\\. collector ==> .+-(\\d+)-(\\d+)$");

   private static final Pattern VARIANCE_PATTERN =
         Pattern.compile("^variance\\s*=\\s*(\\S+)$");

   private static final Pattern CPU_TIME_PATTERN =
         Pattern.compile("^CPU time:\\s*(\\S+)$");

   private ResToComparisonCsv() {}

   private static final class Result {
      final int s;
      final int k;
      final String method;
      final double variance;
      final double cpuTime;

      Result(int s, int k, String method, double variance, double cpuTime) {
         this.s = s;
         this.k = k;
         this.method = method;
         this.variance = variance;
         this.cpuTime = cpuTime;
      }
   }

   public static void writeComparisonTable(
         Path resDirectory,
         Path outputCsv) throws IOException {

      List<Result> results = new ArrayList<>();

      try (Stream<Path> files = Files.list(resDirectory)) {
         files.filter(Files::isRegularFile)
               .filter(path -> path.getFileName().toString().endsWith(".res"))
               .sorted()
               .forEach(path -> {
                  try {
                     readResFile(path, results);
                  } catch (IOException e) {
                     throw new RuntimeException(
                           "Cannot read " + path, e);
                  }
               });
      } catch (RuntimeException e) {
         if (e.getCause() instanceof IOException)
            throw (IOException) e.getCause();
         throw e;
      }

      results.sort(
            Comparator.comparingInt((Result result) -> result.s)
                  .thenComparingInt(result -> result.k)
                  .thenComparing(result -> result.method));

      Path parent = outputCsv.getParent();
      if (parent != null)
         Files.createDirectories(parent);

      try (BufferedWriter writer = Files.newBufferedWriter(
            outputCsv,
            StandardCharsets.UTF_8)) {

         writer.write("s,k,method,variance,cpu_time");
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
            writer.write(Double.toString(result.cpuTime));
            writer.newLine();
         }
      }
   }

   private static void readResFile(
         Path resFile,
         List<Result> results) throws IOException {

      int s = extractDimension(resFile);

      String currentMethod = null;
      Integer currentK = null;
      Double currentVariance = null;

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

            Matcher reportMatcher = REPORT_PATTERN.matcher(trimmed);
            if (reportMatcher.matches()) {
               currentK = Integer.parseInt(reportMatcher.group(1));
               currentVariance = null;
               continue;
            }

            Matcher varianceMatcher = VARIANCE_PATTERN.matcher(trimmed);
            if (varianceMatcher.matches() && currentK != null) {
               currentVariance =
                     Double.parseDouble(varianceMatcher.group(1));
               continue;
            }

            Matcher cpuMatcher = CPU_TIME_PATTERN.matcher(trimmed);
            if (cpuMatcher.matches()
                  && currentMethod != null
                  && currentK != null
                  && currentVariance != null) {

               double cpuTime = parseCpuTime(cpuMatcher.group(1));

               results.add(new Result(
                     s,
                     currentK,
                     currentMethod,
                     currentVariance,
                     cpuTime));

               currentK = null;
               currentVariance = null;
            }
         }
      }
   }

   private static int extractDimension(Path resFile) {
      String fileName = resFile.getFileName().toString();
      Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);

      if (!matcher.matches()) {
         throw new IllegalArgumentException(
               "Cannot extract s from file name: " + fileName);
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

      return 3600.0 * hours + 60.0 * minutes + seconds;
   }

   private static String csvEscape(String value) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         System.err.println(
               "Usage: ResToComparisonCsv <res-directory> <output-csv>");
         return;
      }

      Path resDirectory = Paths.get(args[0]);
      Path outputCsv = Paths.get(args[1]);

      writeComparisonTable(resDirectory, outputCsv);

      System.out.println(
            "Comparison table written to " + outputCsv.toAbsolutePath());
   }
}
