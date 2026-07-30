package rqmcexperiments;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
 * Times the two NUS methods and the Burley padded method used by
 * {@link WSC23MoreSamples}.
 */
public class NusTimingExperiment extends RQMCExperiment64 {

   public static void main(String[] args) throws IOException {
      int[] sValues = { 2, 4, 8, 16, 32};
      int[] kValues = { 8, 10, 12, 14, 16};
      int[] mValues = { 1, 2, 5, 10, 20, 30, 50};
      int nRuns = 11;
      String csvPath =  "/home/otman/Documents/GitHub/Data/o-test/nus/nus-timing/nus-ssj-timing-"+ nRuns + ".csv";
      boolean printResults = true;
      boolean writeCsv = true;

      if (!printResults && !writeCsv)
         throw new IllegalArgumentException(
               "At least one of printResults or writeCsv must be true.");

      try (BufferedWriter writer = writeCsv
            ? new BufferedWriter(new FileWriter(csvPath))
            : null) {
         writeHeader(writer, printResults);

         // Same experiment structure as WSC23MoreReps.
         for (int s : sValues) {
            MonteCarloModelDouble model = new MC2(s);
            for (int k : kValues) {
               for (int m : mValues)
                  simulRepsAllTypes(
                        model, s, k, m, nRuns, writer, printResults);
            }
         }
      }
   }

   /**
    * The two NUS blocks and the Burley padded block from
    * WSC23MoreSamples.simulRepsAllTypes, repeated to obtain timing medians.
    */
   private static void simulRepsAllTypes(MonteCarloModelDouble model, int s,
         int k, int m, int nRuns, BufferedWriter writer, boolean printResults)
         throws IOException {
      double[] nusSsjTimes = new double[nRuns];
      double[] nusPresortedTimes = new double[nRuns];
      double[] burleyPaddedTimes = new double[nRuns];

      for (int run = 0; run < nRuns; run++) {
         RandomStream stream = new MWC64k3a2();
         TallyStore statReps = new TallyStore(m);

         // Objects for Sobol' points.
         DigitalNetBase2 p = new SobolSequence(k, 32, s);

         // Sob-NUS
         CachedPointSet cp = new CachedPointSet(p);
         stream.resetNextSubstream();
         PointSetRandomization nus = new NestedUniformScrambling(stream, 30);
         nusSsjTimes[run] =
               simulRepsRQMCSort(model, cp, nus, m, statReps);

         // NUS presorted: nestedUniformScramble64Presorted
         CachedPointSet cp3 = new CachedPointSet(p);
         stream.resetStartSubstream();
         PointSetRandomization nusPresorted =
               new NestedUniformScramblingExperimental(
                     stream,
                     NestedUniformScramblingExperimental.Method.SSJ_NUS64_PRESORTED,
                     30);
         nusPresortedTimes[run] =
               simulRepsRQMCSort(model, cp3, nusPresorted, m, statReps);

         // Burley padded using SSJ Sobol directions
         stream.resetStartSubstream();
         BurleySSJPadded.PaddedPointSet pBurleySSJ =
               new BurleySSJPadded.PaddedPointSet(k, s, 32, 1);
         PointSetRandomization burleySSJRandomization =
               new BurleySSJPadded.Randomization(stream);
         burleyPaddedTimes[run] =
               simulRepsRQMCSort(
                     model, pBurleySSJ, burleySSJRandomization, m, statReps);
      }

      outputResult(writer, printResults, "NUS64-SSJ", s, k, m,
            Misc.getMedian(nusSsjTimes, nusSsjTimes.length));
      outputResult(writer, printResults, "NUS64-PRESORTED", s, k, m,
            Misc.getMedian(nusPresortedTimes, nusPresortedTimes.length));
      outputResult(writer, printResults, "Burley-Padded", s, k, m,
            Misc.getMedian(burleyPaddedTimes, burleyPaddedTimes.length));
   }

   /**
    * The timed experiment from WSC23MoreSamples.simulRepsRQMCSort. Only its
    * reporting, sorting, and data-file output are omitted from this timing class.
    */
   private static double simulRepsRQMCSort(MonteCarloModelDouble model,
         PointSet p, PointSetRandomization rand, int m, TallyStore statReps) {
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
      return timer.getSeconds();
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
