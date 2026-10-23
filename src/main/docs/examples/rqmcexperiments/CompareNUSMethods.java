package rqmcexperiments;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;

import umontreal.ssj.hups64.CachedPointSet;
import umontreal.ssj.hups64.CachedNUSPointSet;
import umontreal.ssj.hups64.DigitalNetBase2;
import umontreal.ssj.hups64.HCKPointSet;
import umontreal.ssj.hups64.NestedUniformScrambling;
import umontreal.ssj.hups64.RQMCCachedNUS;
import umontreal.ssj.hups64.RQMCCachedNUSRngBuffer;
import umontreal.ssj.hups64.RQMCDigNet2HCKlongIter;
import umontreal.ssj.hups64.RQMCDigitalNet2FK02;
import umontreal.ssj.hups64.RQMCDigitalNet2HCK;
import umontreal.ssj.hups64.RQMCDigitalNet2HCKLong;
import umontreal.ssj.hups64.RQMCDigitalNet2HCKRngBuffer;
import umontreal.ssj.hups64.RQMCPointSet;
import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment64;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.util.Chrono;

/**
 * Compares original NUS, FK02, digital-input cached NUS with and without RNG
 * buffering, and HCK with double or integer storage on the selected model,
 * using the same seed and scrambling precision. Statistics describe the RQMC replication estimates;
 * skewness and excess kurtosis use bias correction. CPU timings exclude
 * preparation and warmup; preparation time is reported separately for all
 * methods and includes net/cache construction and required coordinate generation.
 * Exact estimate equality is reported for FK02 versus original NUS and for the
 * two cached NUS paths. The latter agree when cached conversion preserves the
 * net's integer coordinates; it can lose low bits above double precision.
 * Cached NUS maps random words differently from the original algorithms, so
 * their comparison uses statistics and timings.
 * HCKPointSet is compared with HCKLong using timings and exact estimate equality.
 */
public class CompareNUSMethods {

   // Keep results in test-call order until the grouped report is printed.
   private static final LinkedHashMap<String, TallyStore> estimates = new LinkedHashMap<>();
   // Each timing array contains preparation, experiment, and scrambling seconds.
   private static final LinkedHashMap<String, double[]> timings = new LinkedHashMap<>();

   private static double runExperiment(MonteCarloModelDouble model, RQMCPointSet points, int m, TallyStore estimates) {
      points.getRandomization().getStream().resetStartStream();
      Chrono timer = new Chrono();
      RQMCExperiment64.simulReplicatesRQMC(model, points, m, estimates);
      return timer.getSeconds();
   }

   private static double timeRandomizations(RQMCPointSet points, int m) {
      points.getRandomization().getStream().resetStartStream();
      Chrono timer = new Chrono();
      for (int rep = 0; rep < m; rep++)
         points.randomize();
      return timer.getSeconds();
   }

   private static void testAll(String name, MonteCarloModelDouble model, RQMCPointSet points,
         int m, int warmup, double preparationSeconds) throws InterruptedException {
      RQMCExperiment64.simulReplicatesRQMC(model, points, warmup, new Tally());
      TallyStore measuredEstimates = new TallyStore(m);
      double experimentSeconds = runExperiment(model, points, m, measuredEstimates);
      double scramblingSeconds = timeRandomizations(points, m);
      estimates.put(name, measuredEstimates);
      timings.put(name, new double[] { preparationSeconds, experimentSeconds, scramblingSeconds });

      // Cleanup and the pause are outside the measured timings.
      System.gc();
      Thread.sleep(100);
   }

   private static void printStatistics(String name, TallyStore estimates) {
      System.out.printf(Locale.ROOT, "%-26s %14.6e %14.6e %14.6e %12.6f %14.6f%n",
            name, estimates.average(), estimates.variance(), estimates.standardDeviation(),
            estimates.skewness(true), estimates.kurtosis(true, true));
   }

   private static void printTiming(String name, double preparationSeconds, double experimentSeconds,
         double scramblingSeconds) {
      System.out.printf(Locale.ROOT, "%-26s %12.6f %16.6f %12.6f %12.6f%n",
            name, preparationSeconds, scramblingSeconds, preparationSeconds + scramblingSeconds,
            experimentSeconds);
   }

   // Compare replication estimates outside the timed loops.
   private static int compareEstimates(String name, TallyStore first, TallyStore second) {
      double[] firstValues = first.getArray();
      double[] secondValues = second.getArray();
      int m = first.numberObs();
      int mismatches = 0;
      double maxDifference = 0.0;
      for (int rep = 0; rep < m; rep++) {
         if (Double.doubleToLongBits(firstValues[rep]) != Double.doubleToLongBits(secondValues[rep]))
            mismatches++;
         maxDifference = Math.max(maxDifference, Math.abs(firstValues[rep] - secondValues[rep]));
      }
      System.out.printf(Locale.ROOT, "%s: exactly matching replication estimates: %d/%d%n",
            name, m - mismatches, m);
      System.out.printf(Locale.ROOT, "%s: maximum absolute difference: %.6e%n", name, maxDifference);
      return mismatches;
   }

   private static void printSpeedup(String name, String modelTag, String firstName, String secondName) {
      double[] first = timings.get(firstName);
      double[] second = timings.get(secondName);
      if (first == null || second == null)
         return;
      System.out.printf(Locale.ROOT, "%s: %s %.3fx, scrambling %.3fx%n",
            name, modelTag, first[1] / second[1], first[2] / second[2]);
   }

   private static int compareMethods(String name, String firstName, String secondName) {
      TallyStore first = estimates.get(firstName);
      TallyStore second = estimates.get(secondName);
      if (first == null || second == null)
         return 0;
      return compareEstimates(name, first, second);
   }

   private static void printReport(MonteCarloModelDouble model, int s, int n, int m, int w,
         int numBits, int warmup, long[] seed) {
      String modelTag = model.getTag();
      System.out.printf(Locale.ROOT, modelTag + ": s=%d, n=%d, m=%d, w=%d, numBits=%d, warmup=%d%n",
            s, n, m, w, numBits, warmup);
      System.out.println("MWC64k3a2 seed: " + Arrays.toString(seed));
      System.out.println("Preparation includes net/cache construction and required coordinate generation.");
      System.out.println("Total CPU (s) is preparation plus the measured scrambling runs.");
      System.out.println(modelTag + " timings and speedups exclude preparation.");
      System.out.println("Statistics of replication estimates (" + modelTag + " exact mean = 0).");
      System.out.println("Skewness and excess kurtosis are bias corrected.");
      System.out.printf("%-26s %14s %14s %14s %12s %14s%n",
            "Method", "Mean", "Variance", "Std. deviation", "Skewness", "Excess kurtosis");
      for (String name : estimates.keySet())
         printStatistics(name, estimates.get(name));

      System.out.println();
      System.out.printf("%-26s %12s %16s %12s %12s%n",
            "Method", "Prep CPU (s)", "Scramble CPU (s)", "Total CPU (s)", modelTag + " CPU (s)");
      for (String name : timings.keySet()) {
         double[] seconds = timings.get(name);
         printTiming(name, seconds[0], seconds[1], seconds[2]);
      }

      // These comparisons are printed only when both methods have been tested.
      printSpeedup("FK02 speedup (ssjNus / FK02)", modelTag,
            "NestedUniformScrambling", "RQMCDigitalNet2FK02");
      printSpeedup("Cached NUS speedup (ssjNus / cached)", modelTag,
            "NestedUniformScrambling", "RQMCCachedNUS (cache)");
      printSpeedup("Cached NUS speedup (FK02 / cached)", modelTag,
            "RQMCDigitalNet2FK02", "RQMCCachedNUS (cache)");
      printSpeedup("Digital-input NUS speedup (ssjNus / digital)", modelTag,
            "NestedUniformScrambling", "RQMCCachedNUS (digital)");
      printSpeedup("Digital-input NUS speedup (FK02 / digital)", modelTag,
            "RQMCDigitalNet2FK02", "RQMCCachedNUS (digital)");
      printSpeedup("Buffered digital-input NUS speedup (digital / buffered)", modelTag,
            "RQMCCachedNUS (digital)", "RQMCCachedNUS (dig, buf)");
      printSpeedup("Digital-input NUS speedup (cached / digital)", modelTag,
            "RQMCCachedNUS (cache)", "RQMCCachedNUS (digital)");
      printSpeedup("CachedNUSPointSet speedup (RQMCCachedNUS / CachedNUSPointSet)", modelTag,
            "RQMCCachedNUS (digital)", "CachedNUSPointSet");
      printSpeedup("HCK speedup (digital-input NUS / HCK)", modelTag,
            "RQMCCachedNUS (digital)", "HCK");
      printSpeedup("HCK integer-storage speedup (HCK / integer)", modelTag,
            "HCK", "HCK (integer storage)");
      printSpeedup("HCKPointSet speedup (HCKLong / HCKPointSet)", modelTag,
            "HCK (integer storage)", "HCKPointSet");
      printSpeedup("HCK iterator speedup (integer / iterator)", modelTag,
            "HCK (integer storage)", "HCK (integer, iterator)");
      printSpeedup("HCK -bits RNG buffer speedup (HCK / buffered HCK)", modelTag,
            "HCK", "HCK (53-bit RNG buffer)");

      System.out.println("Cached NUS maps random words differently from ssjNus and FK02; exact equality with them is not required.");
      compareMethods("RQMCCachedNUS vs CachedNUSPointSet",
            "RQMCCachedNUS (digital)", "CachedNUSPointSet");
      compareMethods("HCK vs HCK (integer storage)", "HCK", "HCK (integer storage)");
      compareMethods("HCKLong vs HCKPointSet", "HCK (integer storage)", "HCKPointSet");
      compareMethods("HCK integer vs iterator", "HCK (integer storage)", "HCK (integer, iterator)");
      int mismatches = compareMethods("FK02 vs original NUS",
            "NestedUniformScrambling", "RQMCDigitalNet2FK02");
      if (mismatches != 0)
         System.out.println("Their current mask calculations differ: FK02 uses highestOneBit; original NUS uses Num.log2.");
      compareMethods("Cached-input vs digital-input NUS", "RQMCCachedNUS (cache)", "RQMCCachedNUS (digital)");
      compareMethods("HCK vs HCK ( -bits RNG buffer)", "HCK", "HCK (53-bit RNG buffer)");
      compareMethods("Digital-input NUS vs buffered digital-input NUS",
            "RQMCCachedNUS (digital)", "RQMCCachedNUS (dig, buf)");
   }

   public static void main(String[] args) throws InterruptedException {
      int s = 10;          // Dimension.
      int k = 12;         // Number of points is 2^k.
      int m = 10000;       // Number of measured replications.
      int w = 31;         // Output precision.
      int numBits = w;    // Full scrambling at the selected output precision.
      int warmup = 1000;
      long[] seed = { 1L, 2L, 3L, 4L};

      estimates.clear();
      timings.clear();
      MWC64k3a2 ssjNusStream = new MWC64k3a2();
      ssjNusStream.setSeed(seed);
      // MWC64k3a2 fk02Stream = ssjNusStream.clone();
      // MWC64k3a2 cachedStream = ssjNusStream.clone();
      MWC64k3a2 digitalStream = ssjNusStream.clone();
      MWC64k3a2 cachedNUSPointSetStream = ssjNusStream.clone();
      // MWC64k3a2 digitalRngBufferStream = ssjNusStream.clone();
      // MWC64k3a2 hckStream = ssjNusStream.clone();
      MWC64k3a2 hckLongStream = ssjNusStream.clone();
      MWC64k3a2 hckPointSetStream = ssjNusStream.clone();
      // MWC64k3a2 hckLongIterStream = ssjNusStream.clone();
      // MWC64k3a2 hckRngBufferStream = ssjNusStream.clone();

      // MC2 model = new MC2(s);
      Gaussian model = new Gaussian(s);

      // Comment out a test call to omit its rows and dependent comparisons.
      Chrono preparationTimer = new Chrono();
      // DigitalNetBase2 ssjNusNet = new SobolSequence(k, w, s);
      // RQMCPointSet ssjNus = new RQMCPointSet(new CachedPointSet(ssjNusNet, false),
      //       new NestedUniformScrambling(ssjNusStream, numBits));
      // double ssjNusPreparationSeconds = preparationTimer.getSeconds();
      // testAll("NestedUniformScrambling", model, ssjNus, m, warmup, ssjNusPreparationSeconds);

      // preparationTimer.init();
      // DigitalNetBase2 fk02Net = new SobolSequence(k, w, s);
      // RQMCDigitalNet2FK02 fk02 = new RQMCDigitalNet2FK02(fk02Net,
      //       new CachedPointSet(fk02Net, false), fk02Stream, numBits);
      // double fk02PreparationSeconds = preparationTimer.getSeconds();
      // testAll("RQMCDigitalNet2FK02", model, fk02, m, warmup, fk02PreparationSeconds);

      // preparationTimer.init();
      // CachedPointSet cachedPoints = new CachedPointSet(new SobolSequence(k, w, s));
      // // Use the same cache for input and output; the constructor prepares a snapshot.
      // RQMCCachedNUS cachedNus = new RQMCCachedNUS(cachedPoints, cachedPoints, cachedStream, w);
      // double cachedPreparationSeconds = preparationTimer.getSeconds();
      // testAll("RQMCCachedNUS (cache)", model, cachedNus, m, warmup, cachedPreparationSeconds);

      preparationTimer.init();
      DigitalNetBase2 digitalNet = new SobolSequence(k, w, s);
      CachedPointSet digitalOutput = new CachedPointSet(digitalNet, false);
      // Only an output cache is needed: preprocessing reads integer generator columns directly.
      RQMCCachedNUS digitalNus = new RQMCCachedNUS(digitalNet, digitalOutput, digitalStream);
      double digitalPreparationSeconds = preparationTimer.getSeconds();
      testAll("RQMCCachedNUS (digital)", model, digitalNus, m, warmup, digitalPreparationSeconds);

      preparationTimer.init();
      DigitalNetBase2 cachedNUSPointSetNet = new SobolSequence(k, w, s);
      CachedNUSPointSet cachedNUSPointSet = new CachedNUSPointSet(
            cachedNUSPointSetNet, cachedNUSPointSetStream);
      RQMCPointSet cachedNUSPoints = new RQMCPointSet(
            cachedNUSPointSet, cachedNUSPointSet.getRandomization());
      double cachedNUSPointSetPreparationSeconds = preparationTimer.getSeconds();
      testAll("CachedNUSPointSet", model, cachedNUSPoints, m, warmup, cachedNUSPointSetPreparationSeconds);

      // preparationTimer.init();
      // DigitalNetBase2 digitalRngBufferNet = new SobolSequence(k, w, s);
      // CachedPointSet digitalRngBufferOutput = new CachedPointSet(digitalRngBufferNet, false);
      // RQMCCachedNUSRngBuffer digitalRngBufferNus = new RQMCCachedNUSRngBuffer(
      //       digitalRngBufferNet, digitalRngBufferOutput, digitalRngBufferStream);
      // double digitalRngBufferPreparationSeconds = preparationTimer.getSeconds();
      // testAll("RQMCCachedNUS (dig, buf)", model, digitalRngBufferNus, m, warmup,
      //       digitalRngBufferPreparationSeconds);

      // preparationTimer.init();
      // DigitalNetBase2 digitalNetHCK = new SobolSequence(k, w, s);
      // CachedPointSet hckOutput = new CachedPointSet(digitalNetHCK, false);
      // RQMCDigitalNet2HCK digitalHCK = new RQMCDigitalNet2HCK(digitalNetHCK, hckOutput, hckStream, w);
      // double hckPreparationSeconds = preparationTimer.getSeconds();
      // testAll("HCK", model, digitalHCK, m, warmup, hckPreparationSeconds);

      preparationTimer.init();
      DigitalNetBase2 digitalNetHCKLong = new SobolSequence(k, w, s);
      RQMCDigitalNet2HCKLong digitalHCKLong = new RQMCDigitalNet2HCKLong(
            digitalNetHCKLong, hckLongStream, w);
      double hckLongPreparationSeconds = preparationTimer.getSeconds();
      testAll("HCK (integer storage)", model, digitalHCKLong, m, warmup, hckLongPreparationSeconds);

      preparationTimer.init();
      DigitalNetBase2 digitalNetHCKPointSet = new SobolSequence(k, w, s);
      HCKPointSet hckPointSet = new HCKPointSet(digitalNetHCKPointSet, hckPointSetStream, w);
      RQMCPointSet hckPoints = new RQMCPointSet(hckPointSet, hckPointSet.getRandomization());
      double hckPointSetPreparationSeconds = preparationTimer.getSeconds();
      testAll("HCKPointSet", model, hckPoints, m, warmup, hckPointSetPreparationSeconds);

      // preparationTimer.init();
      // DigitalNetBase2 digitalNetHCKLongIter = new SobolSequence(k, w, s);
      // RQMCDigNet2HCKlongIter digitalHCKLongIter = new RQMCDigNet2HCKlongIter(
      //       digitalNetHCKLongIter, hckLongIterStream, w);
      // double hckLongIterPreparationSeconds = preparationTimer.getSeconds();
      // testAll("HCK (integer, iterator)", model, digitalHCKLongIter, m, warmup, hckLongIterPreparationSeconds);

      // preparationTimer.init();
      // DigitalNetBase2 digitalNetHCKRngBuffer = new SobolSequence(k, w, s);
      // CachedPointSet hckRngBufferOutput = new CachedPointSet(digitalNetHCKRngBuffer, false);
      // RQMCDigitalNet2HCKRngBuffer digitalHCKRngBuffer = new RQMCDigitalNet2HCKRngBuffer(
      //       digitalNetHCKRngBuffer, hckRngBufferOutput, hckRngBufferStream, w);
      // double hckRngBufferPreparationSeconds = preparationTimer.getSeconds();
      // testAll("HCK (53-bit RNG buffer)", model, digitalHCKRngBuffer, m, warmup,
      //       hckRngBufferPreparationSeconds);

      printReport(model, s, 1 << k, m, w, numBits, warmup, seed);
   }
}
