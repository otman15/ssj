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

public class CompareNUSAllCases {
   private static final String RESULT_DIR =
         "/home/otman/Documents/GitHub/Data/o-test/nus/30bt/";
   private static final int[] DIMENSIONS = {1, 2, 3, 4};
   private static final int[] K_VALUES = {8, 10, 12, 14, 16};

   private interface ModelFactory {
      MonteCarloModelDouble create(int s);
      String functionName();
      double theoreticalMean();
      String fileName();
   }

private static class X2Model implements MonteCarloModelDouble {
   private final int s;
   private double value;

   X2Model(int s) {
      this.s = s;
   }

   @Override
   public void simulate(RandomStream stream) {
      value = 0.0;
      for (int j = 0; j < s; j++) {
         double u = stream.nextDouble();
         value += u * u;
      }
   }

   @Override
   public double getPerformance() {
      return value - s / 3.0;
   }
}

   private static class MethodResult {
      private final TallyStore estimates;
      private final double cpuTimeSeconds;

      MethodResult(TallyStore estimates, double cpuTimeSeconds) {
         this.estimates = estimates;
         this.cpuTimeSeconds = cpuTimeSeconds;
      }
   }

   private static MethodResult runReplications(String label, PointSet pointSet,
                                               PointSetRandomization randomization,
                                               MonteCarloModelDouble model, int n, int m) {
      TallyStore estimates = new TallyStore(label, m);

      long start = System.nanoTime();
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
      long end = System.nanoTime();
      return new MethodResult(estimates, (end - start) * 1.0e-9);
   }

   private static MethodResult runSSJNUS(MonteCarloModelDouble model,
                                         RandomStream stream, int s, int k, int n, int m) {
      DigitalNetBase2 sobol = new SobolSequence(k, 32, s);
      CachedPointSet cachedSobol = new CachedPointSet(sobol);
      PointSetRandomization nus = new NestedUniformScrambling(stream, 32);
      return runReplications("SSJ NUS", cachedSobol, nus, model, n, m);
   }
   
   private static MethodResult runBurleyFull(MonteCarloModelDouble model,
                                             RandomStream stream, int s, int k, int n, int m) {
      HashBasedSobolPointSet pointSet = new HashBasedSobolPointSet(k, s, 1, true,
            HashBasedSobolPointSet.SeedMode.ORIGINAL_BURLEY);
      PointSetRandomization nus = new HashBasedSobolRandomization(stream);
      return runReplications("Burley shuffle + scramble", pointSet, nus, model, n, m);
   }

   // SSJ 30-bit version: same hash-based shuffle + scramble idea,
   // but using SSJ Sobol generator columns instead of Burley's 32-bit table.
   private static MethodResult runBurleySSJ30BitFull(MonteCarloModelDouble model,
                                                     RandomStream stream, int s, int k, int n, int m) {
      HashBasedSobolSSJ30BitPointSet pointSet = new HashBasedSobolSSJ30BitPointSet(k, s, 1);
      PointSetRandomization nus = new HashBasedSobolSSJ30BitRandomization(stream);
      return runReplications("Burley SSJ 30-bit shuffle + scramble", pointSet, nus, model, n, m);
   }

   // private static MethodResult runBurleyOriginalScrambleOnly(MonteCarloModelDouble model,
   //                                                           int s, int k, int n, int m) {
   //    RandomStream stream = new MWC64k3a2();
   //    HashBasedSobolPointSet pointSet = new HashBasedSobolPointSet(k, s, 1, false);
   //    PointSetRandomization nus = new HashBasedSobolRandomization(stream);
   //    return runMethod("Burley original scramble-only", pointSet, nus, model, n, m);
   // }

   // private static MethodResult runBurleyIndependentScrambleOnly(MonteCarloModelDouble model,
   //                                                              int s, int k, int n, int m) {
   //    RandomStream stream = new MWC64k3a2();
   //    HashBasedSobolPointSet pointSet = new HashBasedSobolPointSet(k, s, 1, false,
   //          HashBasedSobolPointSet.SeedMode.INDEPENDENT_SEEDS);
   //    PointSetRandomization nus = new HashBasedSobolRandomization(stream);
   //    return runMethod("Burley independent-seeds scramble-only", pointSet, nus, model, n, m);
   // }

   // private static MethodResult runBurleyIndependentFull(MonteCarloModelDouble model,
   //                                                      int s, int k, int n, int m) {
   //    RandomStream stream = new MWC64k3a2();
   //    HashBasedSobolPointSet pointSet = new HashBasedSobolPointSet(k, s, 1, true,
   //          HashBasedSobolPointSet.SeedMode.INDEPENDENT_SEEDS);
   //    PointSetRandomization nus = new HashBasedSobolRandomization(stream);
   //    return runMethod("Burley independent-seeds shuffle + scramble", pointSet, nus, model, n, m);
   // }

   private static void writeMethod(PrintWriter out, String label, MethodResult result) {
      TallyStore estimates = result.estimates;
      out.println();
      out.println("Method: " + label);
      out.println("mean = " + estimates.average());
      out.println("variance = " + estimates.variance());
      out.println("corrected excess kurtosis = " + estimates.kurtosis(true, true));
      out.println("CPU time = " + result.cpuTimeSeconds);
   }

   private static void writeFunctionFile(ModelFactory factory, int m) throws IOException {
      File dir = new File(RESULT_DIR);
      if (!dir.exists() && !dir.mkdirs())
         throw new IOException("Cannot create result directory: " + RESULT_DIR);

      File file = new File(dir, factory.fileName());
      try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
         out.println("Function: " + factory.functionName());
         out.println("theoretical mean = " + factory.theoreticalMean());
         out.println("m = " + m);

         for (int s : DIMENSIONS) {
            out.println();
            out.println("============================================================");
            out.println("s = " + s);

            for (int k : K_VALUES) {
               int n = 1 << k;
               out.println();
               out.println("------------------------------------------------------------");
               out.println("k = " + k);
               out.println("n = " + n);

               RandomStream stream = new MWC64k3a2();

               writeMethod(out, "Burley shuffle + scramble",
                     runBurleyFull(factory.create(s), stream, s, k, n, m));

               // SSJ 30-bit version: explicit label in the output because it is
               // not expected to match Burley's 32-bit C++ output bit-for-bit.
               stream.resetStartStream();
               writeMethod(out, "Burley SSJ 30-bit shuffle + scramble",
                     runBurleySSJ30BitFull(factory.create(s), stream, s, k, n, m));

               stream.resetStartStream();
               writeMethod(out, "SSJ NUS",
                     runSSJNUS(factory.create(s), stream, s, k, n, m));
               // writeMethod(out, "Burley original scramble-only",
               //       runBurleyOriginalScrambleOnly(factory.create(s), s, k, n, m));
               // writeMethod(out, "Burley independent-seeds scramble-only",
               //       runBurleyIndependentScrambleOnly(factory.create(s), s, k, n, m));
               // writeMethod(out, "Burley independent-seeds shuffle + scramble",
               //       runBurleyIndependentFull(factory.create(s), s, k, n, m));
            }
         }
      }
      System.out.println("Finished " + file.getAbsolutePath());
   }

   private static ModelFactory[] factories() {
      return new ModelFactory[] {
         new ModelFactory() {
            public MonteCarloModelDouble create(int s) {
               return new X2Model(s);
            }

            public String functionName() {
               return "x^2";
            }

            public double theoreticalMean() {
               return 0.0;
            }

            public String fileName() {
               return "CompareNUS-x2.res";
            }
         },
         new ModelFactory() {
            public MonteCarloModelDouble create(int s) {
               double[] c = new double[s];
               for (int j = 0; j < s; j++)
                  c[j] = 1.0;

               double w1 = 0.5;
               return new GenzOscillatory(s, c, w1);
            }

            public String functionName() {
               return "GenzOscillatory";
            }

            public double theoreticalMean() {
               return 0.0;
            }

            public String fileName() {
               return "CompareNUS-GenzOscillatory.res";
            }
         },
         new ModelFactory() {
            public MonteCarloModelDouble create(int s) {
               return new SmoothPerB4(s, 1.0);
            }

            public String functionName() {
               return "SmoothPerB4";
            }

            public double theoreticalMean() {
               return 0.0;
            }

            public String fileName() {
               return "CompareNUS-SmoothPerB4.res";
            }
         },
         new ModelFactory() {
            public MonteCarloModelDouble create(int s) {
               return new MC2(s);
            }

            public String functionName() {
               return "MC2";
            }

            public double theoreticalMean() {
               return 0.0;
            }

            public String fileName() {
               return "CompareNUS-MC2.res";
            }
         }
      };
   }

   public static void main(String[] args) throws IOException {
      // if (args.length != 1) {
      //    System.err.println("Usage: java rqmcexperiments.CompareNUSAllCases m");
      //    return;
      // }

      // int m = Integer.parseInt(args[0]);
      // if (m <= 0)
      //    throw new IllegalArgumentException("m must be positive");

      int m = 1000; // default number of replications

      for (ModelFactory factory : factories())
         writeFunctionFile(factory, m);
   }
}
