package rqmcexperiments;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import umontreal.ssj.hups64.CachedPointSet;
import umontreal.ssj.hups64.DigitalNetBase2;
import umontreal.ssj.hups64.NestedUniformScrambling;
import umontreal.ssj.hups64.NestedUniformScramblingExperimental;
import umontreal.ssj.hups64.PointSet;
import umontreal.ssj.hups64.PointSetIterator;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment64;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.util.Misc;

/**
 * Times the NUS, Adaptive Tiles, PBRT-Hashed-Owen32, and Burley padded
 * methods. The NUS and Burley padded methods are also used by
 * {@link WSC23MoreSamples}.
 */
public class NusTimingExperiment extends RQMCExperiment64 {

   private static final String TIMING = "TIMING";
   private static final String STATISTICS = "STATISTICS";
   private static final String[] MODEL_NAMES = {
         "MC2", "SumUeU", "SmoothPerB4", "Polynomial"
   };
   private static final Path OUTPUT_DIRECTORY = Path.of(
         "/home/otman/Documents/GitHub/Data/o-test/nus/nus-timing");
   private static final Path STATISTICS_OUTPUT_DIRECTORY =
         OUTPUT_DIRECTORY.resolve("dat");

   public static void main(String[] args) throws IOException {
      int[] sValues = {16, 32};
      int[] kValues = { 8, 10, 12, };
      int[] timingMValues = { 30, 50};
      int statisticsM = 1000;
      int timingRuns = 33;
      String mode = STATISTICS;
      boolean printResults = true;
      boolean writeCsv = true;

      if (TIMING.equals(mode)) {
         if (!printResults && !writeCsv)
            throw new IllegalArgumentException(
                  "At least one of printResults or writeCsv must be true.");

         for (String modelName : MODEL_NAMES) {
            String csvPath = OUTPUT_DIRECTORY.resolve(
                  "ssj_" + modelName + "_time_rep-" + timingRuns + ".csv")
                  .toString();
            try (BufferedWriter writer = writeCsv
                  ? new BufferedWriter(new FileWriter(csvPath))
                  : null) {
               writeHeader(writer, printResults);

               // Same experiment structure as WSC23MoreReps.
               for (int s : sValues) {
                  MonteCarloModelDouble model = createModel(modelName, s);
                  for (int k : kValues) {
                     for (int m : timingMValues)
                        simulRepsAllTypes(model, s, k, m, timingRuns, false,
                              writer, printResults);
                  }
               }
            }
         }
      } else if (STATISTICS.equals(mode)) {
         Files.createDirectories(STATISTICS_OUTPUT_DIRECTORY);

         for (String modelName : MODEL_NAMES) {
            for (int s : sValues) {
               MonteCarloModelDouble model = createModel(modelName, s);
               for (int k : kValues)
                  simulRepsAllTypes(model, s, k, statisticsM, 1, true, null,
                        false);
            }
         }
      } else {
         throw new IllegalArgumentException(
               "mode must be " + TIMING + " or " + STATISTICS);
      }
   }

   /** Creates the model with the given name for dimension {@code s}. */
   private static MonteCarloModelDouble createModel(String modelName, int s) {
      switch (modelName) {
      case "MC2":
         return new MC2(s);
      case "SumUeU":
         return new SumUeU(s);
      case "SmoothPerB4":
         return new SmoothPerB4(s, 1.0);
      case "Polynomial":
         return new Polynomial(s);
      default:
         throw new IllegalArgumentException("Unknown model: " + modelName);
      }
   }

   /**
    * Times each configured method. Timing executions repeat the blocks to obtain
    * median times, while statistics executions save one set of RQMC replication
    * estimates for each block.
    */
   private static void simulRepsAllTypes(MonteCarloModelDouble model, int s,
         int k, int m, int nRuns, boolean isStatistics,
         BufferedWriter writer, boolean printResults) throws IOException {
      int effectiveRuns = isStatistics ? 1 : nRuns;
      double[] nusSsjTimes = isStatistics ? null : new double[effectiveRuns];
      double[] nusPresortedTimes =
            isStatistics ? null : new double[effectiveRuns];
      double[] adaptiveTilesTimes =
            isStatistics ? null : new double[effectiveRuns];
      double[] pbrtHashedOwen32Times =
            isStatistics ? null : new double[effectiveRuns];
      double[] burleyPaddedTimes =
            isStatistics ? null : new double[effectiveRuns];
      String modelTag = model.getTag();

      for (int run = 0; run < effectiveRuns; run++) {
         RandomStream stream = new MWC64k3a2();
         TallyStore statReps = new TallyStore(m);

         // Objects for Sobol' points.
         DigitalNetBase2 p = new SobolSequence(k, 32, s);

         // Sob-NUS
         CachedPointSet cp = new CachedPointSet(p);
         stream.resetNextSubstream();
         PointSetRandomization nus = new NestedUniformScrambling(stream, 30);
         statReps.setName(modelTag + "-" + s + "-NUS-SSJ-" + k + "-" + m);
         double nusSsjTime = simulRepsRQMCSort(
               model, cp, nus, m, statReps, isStatistics);
         if (!isStatistics)
            nusSsjTimes[run] = nusSsjTime;

         // NUS presorted: nestedUniformScramble64Presorted
         CachedPointSet cp3 = new CachedPointSet(p);
         stream.resetStartSubstream();
         PointSetRandomization nusPresorted =
               new NestedUniformScramblingExperimental(
                     stream,
                     NestedUniformScramblingExperimental.Method.SSJ_NUS64_PRESORTED,
                     30);
         statReps.setName(
               modelTag + "-" + s + "-SSJ-PRESORTED-" + k + "-" + m);
         double nusPresortedTime = simulRepsRQMCSort(
               model, cp3, nusPresorted, m, statReps, isStatistics);
         if (!isStatistics)
            nusPresortedTimes[run] = nusPresortedTime;

         // Adaptive Tiles using SSJ Sobol directions
         CachedPointSet cpAdaptiveTiles = new CachedPointSet(p);
         stream.resetStartSubstream();
         PointSetRandomization adaptiveTiles =
               new NestedUniformScramblingExperimental(
                     stream,
                     NestedUniformScramblingExperimental.Method.ADAPTIVE_TILES_SSJ_DIR,
                     30);
         statReps.setName(
               modelTag + "-" + s + "-Adaptive-Tiles-SSJ-" + k + "-" + m);
         double adaptiveTilesTime = simulRepsRQMCSort(
               model, cpAdaptiveTiles, adaptiveTiles, m, statReps, isStatistics);
         if (!isStatistics)
            adaptiveTilesTimes[run] = adaptiveTilesTime;

         // PBRT-Hashed-Owen32 using SSJ Sobol directions
         CachedPointSet cpPbrtHashedOwen32 = new CachedPointSet(p);
         stream.resetStartSubstream();
         PointSetRandomization pbrtHashedOwen32 =
               new NestedUniformScramblingExperimental(
                     stream,
                     NestedUniformScramblingExperimental.Method.PBRT_HASHED_OWEN32_SSJ_DIR,
                     30);
         statReps.setName(
               modelTag + "-" + s + "-PBRT-Hashed-Owen32-SSJ-" + k + "-" + m);
         double pbrtHashedOwen32Time = simulRepsRQMCSort(
               model, cpPbrtHashedOwen32, pbrtHashedOwen32, m, statReps,
               isStatistics);
         if (!isStatistics)
            pbrtHashedOwen32Times[run] = pbrtHashedOwen32Time;

         // Burley padded using SSJ Sobol directions
         stream.resetStartSubstream();
         BurleySSJPadded.PaddedPointSet pBurleySSJ =
               new BurleySSJPadded.PaddedPointSet(k, s, 32, 1);
         PointSetRandomization burleySSJRandomization =
               new BurleySSJPadded.Randomization(stream);
         statReps.setName(
               modelTag + "-" + s + "-Burley-Padded-" + k + "-" + m);
         double burleyPaddedTime = simulRepsRQMCSort(model, pBurleySSJ,
               burleySSJRandomization, m, statReps, isStatistics);
         if (!isStatistics)
            burleyPaddedTimes[run] = burleyPaddedTime;
      }

      if (!isStatistics) {
         outputResult(writer, printResults, "NUS64-SSJ", s, k, m,
               Misc.getMedian(nusSsjTimes, nusSsjTimes.length));
         outputResult(writer, printResults, "NUS64-PRESORTED", s, k, m,
               Misc.getMedian(nusPresortedTimes, nusPresortedTimes.length));
         outputResult(writer, printResults, "ADAPTIVE-TILES-SSJ", s, k, m,
               Misc.getMedian(adaptiveTilesTimes, adaptiveTilesTimes.length));
         outputResult(writer, printResults, "PBRT-HASHED-OWEN32-SSJ", s, k, m,
               Misc.getMedian(pbrtHashedOwen32Times,
                     pbrtHashedOwen32Times.length));
         outputResult(writer, printResults, "Burley-Padded", s, k, m,
               Misc.getMedian(burleyPaddedTimes, burleyPaddedTimes.length));
      }
   }

   /**
    * The experiment from WSC23MoreSamples.simulRepsRQMCSort. Statistics
    * executions report and save the sorted replication estimates; timing
    * executions return only their elapsed time.
    */
   private static double simulRepsRQMCSort(MonteCarloModelDouble model,
         PointSet p, PointSetRandomization rand, int m, TallyStore statReps,
         boolean isStatistics) throws IOException {
      statReps.init();
      Tally statValue = new Tally();
      PointSetIterator stream = p.iterator();
      Chrono timer = new Chrono();
      for (int rep = 0; rep < m; rep++) {
         statValue.init();
         rand.randomize(p);
         stream.resetStartStream();
         simulateRuns(model, p.getNumPoints(), stream, statValue);
         statReps.add(statValue.average());
      }
      double elapsedTime = timer.getSeconds();

      if (isStatistics) {
         // Use the existing SSJ statistics from WSC23MoreSamples.
         System.out.println(statReps.report());
         System.out.println("variance = " + statReps.variance());
         System.out.println(
               "skewness, bias corrected = " + statReps.skewness(true));
         System.out.println(
               "skewness, not corrected  = " + statReps.skewness(false));
         System.out.println("excess kurtosis, bias corrected = "
               + statReps.kurtosis(true, true));
         System.out.println("excess kurtosis, not corrected  = "
               + statReps.kurtosis(false, true));
         System.out.println(
               "CPU time: " + Chrono.format(elapsedTime) + "\n");
         statReps.quickSort();
         writeStatisticsData(statReps);
      }
      return elapsedTime;
   }

   /** Writes the sorted RQMC estimates with no header, one value per line. */
   private static void writeStatisticsData(TallyStore tally) throws IOException {
      Path outputFile =
            STATISTICS_OUTPUT_DIRECTORY.resolve(tally.getName() + ".dat");
      try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
         double[] values = tally.getArray();
         for (int i = 0; i < tally.numberObs(); i++) {
            writer.write(Double.toString(values[i]));
            writer.newLine();
         }
      }
   }

   private static void writeHeader(BufferedWriter writer, boolean printResults)
         throws IOException {
      String header = "method,s,k,m,median_time";
      if (printResults)
         System.out.println(header);
      if (writer != null) {
         writer.write(header);
         writer.newLine();
      }
   }

   private static void outputResult(BufferedWriter writer,
         boolean printResults, String method, int s, int k, int m,
         double medianTime) throws IOException {
      String result = String.format(Locale.US, "%s,%d,%d,%d,%.9f",
            method, s, k, m, medianTime);
      if (printResults)
         System.out.println(result);
      if (writer != null) {
         writer.write(result);
         writer.newLine();
      }
   }

}
