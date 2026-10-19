package rqmcexperiments;

import java.util.Arrays;
import java.util.Locale;

import umontreal.ssj.hups64.CachedPointSet;
import umontreal.ssj.hups64.DigitalNetBase2;
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
import umontreal.ssj.rng.LFSR258;
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
 */
public class CompareNUSMethods {

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

   public static void main(String[] args) {
      int s = 10;          // Dimension.
      int k = 16  ;         // Number of points is 2^k.
      int m = 1000;       // Number of measured replications.
      int w = 31;         // Output precision.
      int numBits = w;    // Full scrambling at the selected output precision.
      int warmup = 100;
      long[] seed = { 1L, 2L, 3L, 4L};

      MWC64k3a2 ssjNusStream = new MWC64k3a2();
      ssjNusStream.setSeed(seed);
       MWC64k3a2 fk02Stream = ssjNusStream.clone();
      // MWC64k3a2 cachedStream = ssjNusStream.clone();
      MWC64k3a2 digitalStream = ssjNusStream.clone();
      // MWC64k3a2 digitalRngBufferStream = ssjNusStream.clone();
      MWC64k3a2 hckStream = ssjNusStream.clone();
      MWC64k3a2 hckLongStream = ssjNusStream.clone();
      MWC64k3a2 hckLongIterStream = ssjNusStream.clone();
      // MWC64k3a2 hckRngBufferStream = ssjNusStream.clone();

      Chrono preparationTimer = new Chrono();
      DigitalNetBase2 ssjNusNet = new SobolSequence(k, w, s);
      RQMCPointSet ssjNus = new RQMCPointSet(new CachedPointSet(ssjNusNet, false),
            new NestedUniformScrambling(ssjNusStream, numBits));
      // double ssjNusPreparationSeconds = preparationTimer.getSeconds();
      int n = ssjNus.getNumPoints();// to avoid refrencing the net at the end.

      preparationTimer.init();
      DigitalNetBase2 fk02Net = new SobolSequence(k, w, s);
      RQMCDigitalNet2FK02 fk02 = new RQMCDigitalNet2FK02(fk02Net,
            new CachedPointSet(fk02Net, false), fk02Stream, numBits);
      double fk02PreparationSeconds = preparationTimer.getSeconds();

      // preparationTimer.init();
      // CachedPointSet cachedPoints = new CachedPointSet(new SobolSequence(k, w, s));
      // // Use the same cache for input and output; the constructor prepares a snapshot.
      // RQMCCachedNUS cachedNus = new RQMCCachedNUS(cachedPoints, cachedPoints, cachedStream, w);
      // double cachedPreparationSeconds = preparationTimer.getSeconds();

      preparationTimer.init();
      DigitalNetBase2 digitalNet = new SobolSequence(k, w, s); 
      CachedPointSet digitalOutput = new CachedPointSet(digitalNet, false);
      // Only an output cache is needed: preprocessing reads integer generator columns directly.
      RQMCCachedNUS digitalNus = new RQMCCachedNUS(digitalNet, digitalOutput, digitalStream);
      double digitalPreparationSeconds = preparationTimer.getSeconds();

      // preparationTimer.init();
      // CachedPointSet digitalRngBufferOutput = new CachedPointSet(digitalNet, false);
      // RQMCCachedNUSRngBuffer digitalRngBufferNus = new RQMCCachedNUSRngBuffer(
      //       digitalNet, digitalRngBufferOutput, digitalRngBufferStream);
      // double digitalRngBufferPreparationSeconds = preparationTimer.getSeconds();

      preparationTimer.init();
      DigitalNetBase2 digitalNetHCK = new SobolSequence(k, w, s); 
      CachedPointSet hckOutput = new CachedPointSet(digitalNetHCK, false);
      RQMCDigitalNet2HCK digitalHCK = new RQMCDigitalNet2HCK(digitalNetHCK, hckOutput, hckStream, w);
      double hckPreparationSeconds = preparationTimer.getSeconds();

      preparationTimer.init();
      DigitalNetBase2 digitalNetHCKLong = new SobolSequence(k, w, s);
      RQMCDigitalNet2HCKLong digitalHCKLong = new RQMCDigitalNet2HCKLong(
            digitalNetHCKLong, hckLongStream, w);
      double hckLongPreparationSeconds = preparationTimer.getSeconds();

      preparationTimer.init();
      DigitalNetBase2 digitalNetHCKLongIter = new SobolSequence(k, w, s);
      RQMCDigNet2HCKlongIter digitalHCKLongIter = new RQMCDigNet2HCKlongIter(
            digitalNetHCKLongIter, hckLongIterStream, w);
      double hckLongIterPreparationSeconds = preparationTimer.getSeconds();

      // preparationTimer.init();
      // DigitalNetBase2 digitalNetHCKRngBuffer = new SobolSequence(k, w, s);
      // CachedPointSet hckRngBufferOutput = new CachedPointSet(digitalNetHCKRngBuffer, false);
      // RQMCDigitalNet2HCKRngBuffer digitalHCKRngBuffer = new RQMCDigitalNet2HCKRngBuffer(
      //       digitalNetHCKRngBuffer, hckRngBufferOutput, hckRngBufferStream, w);
      // double hckRngBufferPreparationSeconds = preparationTimer.getSeconds();

      // MC2 model = new MC2(s);
      Gaussian model = new Gaussian(s);
      Tally warmupEstimates = new Tally();

       RQMCExperiment64.simulReplicatesRQMC(model, fk02, warmup, warmupEstimates);
      // RQMCExperiment64.simulReplicatesRQMC(model, ssjNus, warmup, warmupEstimates);
      // RQMCExperiment64.simulReplicatesRQMC(model, cachedNus, warmup, warmupEstimates);
      RQMCExperiment64.simulReplicatesRQMC(model, digitalNus, warmup, warmupEstimates);
      // RQMCExperiment64.simulReplicatesRQMC(model, digitalRngBufferNus, warmup, warmupEstimates);
      RQMCExperiment64.simulReplicatesRQMC(model, digitalHCK, warmup, warmupEstimates);
      RQMCExperiment64.simulReplicatesRQMC(model, digitalHCKLong, warmup, warmupEstimates);
      RQMCExperiment64.simulReplicatesRQMC(model, digitalHCKLongIter, warmup, warmupEstimates);
      // RQMCExperiment64.simulReplicatesRQMC(model, digitalHCKRngBuffer, warmup, warmupEstimates);

      // Reset once per experiment, so successive replications use fresh randomness.
      // TallyStore ssjNusEstimates = new TallyStore(m);
       TallyStore fk02Estimates = new TallyStore(m);
      // TallyStore cachedEstimates = new TallyStore(m);
      TallyStore digitalEstimates = new TallyStore(m);
      // TallyStore digitalRngBufferEstimates = new TallyStore(m);
      TallyStore hckEstimates = new TallyStore(m);
      TallyStore hckLongEstimates = new TallyStore(m);
      TallyStore hckLongIterEstimates = new TallyStore(m);
      // TallyStore hckRngBufferEstimates = new TallyStore(m);

       double fk02Seconds = runExperiment(model, fk02, m, fk02Estimates);
      // double ssjNusSeconds = runExperiment(model, ssjNus, m, ssjNusEstimates);
      // double cachedSeconds = runExperiment(model, cachedNus, m, cachedEstimates);
      double digitalSeconds = runExperiment(model, digitalNus, m, digitalEstimates);
      // double digitalRngBufferSeconds = runExperiment(model, digitalRngBufferNus, m, digitalRngBufferEstimates);
      double hckSeconds = runExperiment(model, digitalHCK, m, hckEstimates);
      double hckLongSeconds = runExperiment(model, digitalHCKLong, m, hckLongEstimates);
      double hckLongIterSeconds = runExperiment(model, digitalHCKLongIter, m, hckLongIterEstimates);
      // double hckRngBufferSeconds = runExperiment(model, digitalHCKRngBuffer, m, hckRngBufferEstimates);

       double fk02ScramblingSeconds = timeRandomizations(fk02, m);
      // double ssjNusScramblingSeconds = timeRandomizations(ssjNus, m);
      // double cachedScramblingSeconds = timeRandomizations(cachedNus, m);
      double digitalScramblingSeconds = timeRandomizations(digitalNus, m);
      // double digitalRngBufferScramblingSeconds = timeRandomizations(digitalRngBufferNus, m);
      double hckScramblingSeconds = timeRandomizations(digitalHCK, m);
      double hckLongScramblingSeconds = timeRandomizations(digitalHCKLong, m);
      double hckLongIterScramblingSeconds = timeRandomizations(digitalHCKLongIter, m);
      // double hckRngBufferScramblingSeconds = timeRandomizations(digitalHCKRngBuffer, m);

      System.out.printf(Locale.ROOT, model.getTag()+": s=%d, n=%d, m=%d, w=%d, numBits=%d, warmup=%d%n",
            s, n, m, w, numBits, warmup);
      System.out.println("MWC64k3a2 seed: " + Arrays.toString(seed));
      System.out.println("Preparation includes net/cache construction and required coordinate generation.");
      System.out.println("Total CPU (s) is preparation plus the measured scrambling runs.");
      System.out.println(model.getTag()+" timings and speedups exclude preparation.");
      System.out.println("Statistics of replication estimates ("+model.getTag()+" exact mean = 0).");
      System.out.println("Skewness and excess kurtosis are bias corrected.");
      System.out.printf("%-26s %14s %14s %14s %12s %14s%n",
            "Method", "Mean", "Variance", "Std. deviation", "Skewness", "Excess kurtosis");
      // printStatistics("NestedUniformScrambling", ssjNusEstimates);
       printStatistics("RQMCDigitalNet2FK02", fk02Estimates);
      // printStatistics("RQMCCachedNUS (cache)", cachedEstimates);
      printStatistics("RQMCCachedNUS (digital)", digitalEstimates);
      // printStatistics("RQMCCachedNUS (dig, buf)", digitalRngBufferEstimates);
      printStatistics("HCK", hckEstimates);
      printStatistics("HCK (integer storage)", hckLongEstimates);
      printStatistics("HCK (integer, iterator)", hckLongIterEstimates);
      // printStatistics("HCK (53-bit RNG buffer)", hckRngBufferEstimates);

      System.out.println();
      System.out.printf("%-26s %12s %16s %12s %12s%n",
            "Method", "Prep CPU (s)", "Scramble CPU (s)", "Total CPU (s)", model.getTag()+" CPU (s)");
      // printTiming("NestedUniformScrambling", ssjNusPreparationSeconds, ssjNusSeconds, ssjNusScramblingSeconds);
       printTiming("RQMCDigitalNet2FK02", fk02PreparationSeconds, fk02Seconds, fk02ScramblingSeconds);
      // printTiming("RQMCCachedNUS (cache)", cachedPreparationSeconds, cachedSeconds, cachedScramblingSeconds);
      printTiming("RQMCCachedNUS (digital)", digitalPreparationSeconds, digitalSeconds, digitalScramblingSeconds);
      // printTiming("RQMCCachedNUS (dig, buf)", digitalRngBufferPreparationSeconds,
      //       digitalRngBufferSeconds, digitalRngBufferScramblingSeconds);
      printTiming("HCK", hckPreparationSeconds, hckSeconds, hckScramblingSeconds);
      printTiming("HCK (integer storage)", hckLongPreparationSeconds,
            hckLongSeconds, hckLongScramblingSeconds);
      printTiming("HCK (integer, iterator)", hckLongIterPreparationSeconds,
            hckLongIterSeconds, hckLongIterScramblingSeconds);
      // printTiming("HCK (53-bit RNG buffer)", hckRngBufferPreparationSeconds, hckRngBufferSeconds,
      //       hckRngBufferScramblingSeconds);
      // System.out.printf(Locale.ROOT, "FK02 speedup (ssjNus / FK02): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       ssjNusSeconds / fk02Seconds, ssjNusScramblingSeconds / fk02ScramblingSeconds);
      // System.out.printf(Locale.ROOT, "Cached NUS speedup (ssjNus / cached): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       ssjNusSeconds / cachedSeconds, ssjNusScramblingSeconds / cachedScramblingSeconds);
      // System.out.printf(Locale.ROOT, "Cached NUS speedup (FK02 / cached): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       fk02Seconds / cachedSeconds, fk02ScramblingSeconds / cachedScramblingSeconds);
      // System.out.printf(Locale.ROOT, "Digital-input NUS speedup (ssjNus / digital): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       ssjNusSeconds / digitalSeconds, ssjNusScramblingSeconds / digitalScramblingSeconds);
      // System.out.printf(Locale.ROOT, "Digital-input NUS speedup (FK02 / digital): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       fk02Seconds / digitalSeconds, fk02ScramblingSeconds / digitalScramblingSeconds);
      // System.out.printf(Locale.ROOT, "Buffered digital-input NUS speedup (digital / buffered): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       digitalSeconds / digitalRngBufferSeconds,
      //       digitalScramblingSeconds / digitalRngBufferScramblingSeconds);
      // System.out.printf(Locale.ROOT, "Digital-input NUS speedup (cached / digital): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       cachedSeconds / digitalSeconds, cachedScramblingSeconds / digitalScramblingSeconds);

      System.out.printf(Locale.ROOT, "HCK speedup (digital-input NUS / HCK): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
             digitalSeconds / hckSeconds, digitalScramblingSeconds / hckScramblingSeconds);
      System.out.printf(Locale.ROOT, "HCK integer-storage speedup (HCK / integer): "
            + model.getTag() + " %.3fx, scrambling %.3fx%n",
            hckSeconds / hckLongSeconds, hckScramblingSeconds / hckLongScramblingSeconds);
      System.out.printf(Locale.ROOT, "HCK iterator speedup (integer / iterator): "
            + model.getTag() + " %.3fx, scrambling %.3fx%n",
            hckLongSeconds / hckLongIterSeconds,
            hckLongScramblingSeconds / hckLongIterScramblingSeconds);
      // System.out.printf(Locale.ROOT, "HCK -bits RNG buffer speedup (HCK / buffered HCK): "+model.getTag()+" %.3fx, scrambling %.3fx%n",
      //       hckSeconds / hckRngBufferSeconds, hckScramblingSeconds / hckRngBufferScramblingSeconds);
      System.out.println("Cached NUS maps random words differently from ssjNus and FK02; exact equality with them is not required.");
      compareEstimates("HCK vs HCK (integer storage)", hckEstimates, hckLongEstimates);
      compareEstimates("HCK integer vs iterator", hckLongEstimates, hckLongIterEstimates);

      // int mismatches = compareEstimates("FK02 vs original NUS", ssjNusEstimates, fk02Estimates);
      // if (mismatches != 0)
      //    System.out.println("Their current mask calculations differ: FK02 uses highestOneBit; original NUS uses Num.log2.");
      // compareEstimates("Cached-input vs digital-input NUS", cachedEstimates, digitalEstimates);
      // compareEstimates("HCK vs HCK ( -bits RNG buffer)", hckEstimates, hckRngBufferEstimates);
      // compareEstimates("Digital-input NUS vs buffered digital-input NUS",
      //       digitalEstimates, digitalRngBufferEstimates);
   }
}
