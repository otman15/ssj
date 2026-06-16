package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import umontreal.ssj.hups64.CachedPointSet;
import umontreal.ssj.hups64.DigitalNetBase2;
import umontreal.ssj.hups64.NestedUniformScrambling;
import umontreal.ssj.hups64.PointSet;
import umontreal.ssj.hups64.PointSetIterator;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.TallyStore;

public class CompareNUS {
   private static final String RESULT_FILE =
         "/home/otman/Documents/GitHub/Data/o-test/nus/NusSmallTest.res";

   private static class DiagnosticModel implements MonteCarloModelDouble {
      private final String label;
      private final double expected;
      private final int type;
      private double value;

      DiagnosticModel(String label, double expected, int type) {
         this.label = label;
         this.expected = expected;
         this.type = type;
      }

      @Override
      public void simulate(RandomStream stream) {
         double u0 = stream.nextDouble();
         if (type == 0) {
            value = u0;
            return;
         }

         double u1 = stream.nextDouble();
         if (type == 1)
            value = u1;
         else
            value = u0 * u1;
      }

      @Override
      public double getPerformance() {
         return value;
      }

      @Override
      public String toString() {
         return label;
      }

      @Override
      public String getTag() {
         return label;
      }

      double expectedValue() {
         return expected;
      }
   }

   private static TallyStore runRQMC(String label, PointSet pointSet,
                                     PointSetRandomization randomization,
                                     MonteCarloModelDouble model, int n, int m) {
      TallyStore estimates = new TallyStore(label, m);

      for (int rep = 0; rep < m; rep++) {
         randomization.randomize(pointSet);
         PointSetIterator iterator = pointSet.iterator();
         double sum = 0.0;

         for (int i = 0; i < n; i++) {
            model.simulate(iterator);
            sum += model.getPerformance();
            iterator.resetNextSubstream();
         }
         estimates.add(sum / n);
      }
      return estimates;
   }

   private static void compareModel(PrintWriter out, DiagnosticModel model, int s, int k, int n, int m) {
      RandomStream streamNus = new MWC64k3a2();
      DigitalNetBase2 sobol = new SobolSequence(k, 32, s);
      CachedPointSet cachedSobol = new CachedPointSet(sobol);
      PointSetRandomization nus = new NestedUniformScrambling(streamNus, 32);

      // RandomStream streamHashOriginalScrambleOnly = new MWC64k3a2();
      // HashBasedSobolPointSet hashOriginalScrambleOnly =
      //       new HashBasedSobolPointSet(k, s, 1, false);
      // PointSetRandomization hashOriginalScrambleOnlyNus =
      //       new HashBasedSobolRandomization(streamHashOriginalScrambleOnly);

      // RandomStream streamHashIndependentScrambleOnly = new MWC64k3a2();
      // HashBasedSobolPointSet hashIndependentScrambleOnly =
      //       new HashBasedSobolPointSet(k, s, 1, false,
      //             HashBasedSobolPointSet.SeedMode.INDEPENDENT_SEEDS);
      // PointSetRandomization hashIndependentScrambleOnlyNus =
      //       new HashBasedSobolRandomization(streamHashIndependentScrambleOnly);

      RandomStream streamHashOriginalFull = new MWC64k3a2();
      HashBasedSobolPointSet hashOriginalFull =
            new HashBasedSobolPointSet(k, s, 1, true,
                  HashBasedSobolPointSet.SeedMode.ORIGINAL_BURLEY);
      PointSetRandomization hashOriginalFullNus =
            new HashBasedSobolRandomization(streamHashOriginalFull);

      // RandomStream streamHashIndependentFull = new MWC64k3a2();
      // HashBasedSobolPointSet hashIndependentFull =
      //       new HashBasedSobolPointSet(k, s, 1, true,
      //             HashBasedSobolPointSet.SeedMode.INDEPENDENT_SEEDS);
      // PointSetRandomization hashIndependentFullNus =
      //       new HashBasedSobolRandomization(streamHashIndependentFull);

      TallyStore hashBasedOriginalFull = runRQMC("Burley shuffle + scramble",
                                                hashOriginalFull, hashOriginalFullNus, model, n, m);

      TallyStore ssjNus = runRQMC("SSJ NUS", cachedSobol, nus, model, n, m);
      // TallyStore hashBasedOriginalScrambleOnly = runRQMC("Burley original scramble-only",
      //                                                   hashOriginalScrambleOnly,
      //                                                   hashOriginalScrambleOnlyNus, model, n, m);
      // TallyStore hashBasedIndependentScrambleOnly = runRQMC("Burley independent-seeds scramble-only",
      //                                                      hashIndependentScrambleOnly,
      //                                                      hashIndependentScrambleOnlyNus, model, n, m);
      // TallyStore hashBasedIndependentFull = runRQMC("Burley independent-seeds shuffle + scramble",
      //                                              hashIndependentFull, hashIndependentFullNus, model, n, m);

      out.println();
      out.println("============================================================");
      out.println(model);
      out.println("============================================================");
      out.println("theoretical mean = " + model.expectedValue());
      printReport(out, "SSJ NUS", ssjNus);
      // printReport("Burley original scramble-only", hashBasedOriginalScrambleOnly);
      // printReport("Burley independent-seeds scramble-only", hashBasedIndependentScrambleOnly);
      printReport(out, "Burley shuffle + scramble", hashBasedOriginalFull);
      // printReport("Burley independent-seeds shuffle + scramble", hashBasedIndependentFull);
   }

   private static void printReport(PrintWriter out, String label, TallyStore estimates) {
      out.println("------------------------------------------------------------");
      out.println(label);
      out.println("mean = " + estimates.average());
      out.println("variance = " + estimates.variance());
      out.println("corrected excess kurtosis = " + estimates.kurtosis(true, true));
   }

   public static void main(String[] args) throws IOException {
      int s = 2;
      int k = 10;
      int m = 10000;
      int n = 1 << k;

      DiagnosticModel[] models = {
         new DiagnosticModel("f(u) = u0", 0.5, 0),
         new DiagnosticModel("f(u) = u1", 0.5, 1),
         new DiagnosticModel("f(u) = u0 * u1", 0.25, 2)
      };

      File resultFile = new File(RESULT_FILE);
      File resultDir = resultFile.getParentFile();
      if (resultDir != null && !resultDir.exists())
         resultDir.mkdirs();

      try (PrintWriter out = new PrintWriter(new FileWriter(resultFile))) {
         out.println("s = " + s + ", k = " + k + ", n = " + n + ", m = " + m);
         for (DiagnosticModel model : models) {
            compareModel(out, model, s, k, n, m);
         }
      }
      System.out.println("Results written to " + RESULT_FILE);
   }
}
