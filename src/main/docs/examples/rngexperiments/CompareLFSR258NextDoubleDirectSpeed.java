package rngexperiments;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.LFSR258Direct;
import umontreal.ssj.rng.RandomStream;

public class CompareLFSR258NextDoubleDirectSpeed {

   public static void main(String[] args) throws IOException {
      String suffix = args.length > 0 ? args[0] : "normal";
      String runMode = args.length > 1 ? args[1] : "multi";

      long totalValues = 1_000_000_000L;
      long RUNS;
      long N;
      String filePrefix;

      if (runMode.equals("one")) {
         RUNS = 4L;
         N = totalValues / RUNS;
         filePrefix = "LFSR258_nextDouble_direct_vs_indirect_4Runs_";
      } else {
         RUNS = 1000L;
         N = totalValues / RUNS;
         filePrefix = "LFSR258_nextDouble_direct_vs_indirect_MultiRuns_";
      }

      PrintWriter out = new PrintWriter(new FileWriter(
            "/home/otman/Documents/GitHub/Data/o-test/RSBase/" + filePrefix + suffix + ".res"));

      printHeader(out, suffix, RUNS, N, totalValues);
      testPair(out, RUNS, N, totalValues);
      out.close();
   }

   private static void testPair(PrintWriter out, long RUNS, long N, long totalValues) {
      long[] seed = {123456789123456789L, 123456789123456789L, 123456789123456789L,
            123456789123456789L, 123456789123456789L};
      double sumDoubleIndirect = 0.0;
      double sumDoubleDirect = 0.0;
      long totalTimeIndirectDouble = 0L;
      long totalTimeDirectDouble = 0L;

      for (long r = 0; r < RUNS; r++) {
         if (r % 2 == 0) {
            LFSR258.setPackageSeed(seed);
            RandomStream indirect = new LFSR258();

            long start = System.nanoTime();
            sumDoubleIndirect += runLFSR258Indirect(indirect, N);
            totalTimeIndirectDouble += System.nanoTime() - start;

            LFSR258Direct.setPackageSeed(seed);
            RandomStream direct = new LFSR258Direct();

            start = System.nanoTime();
            sumDoubleDirect += runLFSR258Direct(direct, N);
            totalTimeDirectDouble += System.nanoTime() - start;
         } else {
            LFSR258Direct.setPackageSeed(seed);
            RandomStream direct = new LFSR258Direct();

            long start = System.nanoTime();
            sumDoubleDirect += runLFSR258Direct(direct, N);
            totalTimeDirectDouble += System.nanoTime() - start;

            LFSR258.setPackageSeed(seed);
            RandomStream indirect = new LFSR258();

            start = System.nanoTime();
            sumDoubleIndirect += runLFSR258Indirect(indirect, N);
            totalTimeIndirectDouble += System.nanoTime() - start;
         }
      }

      printResults(out, "LFSR258", totalValues, totalTimeIndirectDouble, totalTimeDirectDouble,
            sumDoubleIndirect, sumDoubleDirect);
   }

   private static void printHeader(PrintWriter out, String suffix, long RUNS, long N, long totalValues) {
      out.println("mode = " + suffix);
      if (suffix.contains("noinline")) {
         out.println("execution = sans inline pour les nextDouble testes");
         out.println("java options = -XX:CompileCommand=dontinline,umontreal.ssj.rng.RandomStreamBase::nextDouble "
               + "-XX:CompileCommand=dontinline,umontreal.ssj.rng.LFSR258::nextValue "
               + "-XX:CompileCommand=dontinline,umontreal.ssj.rng.LFSR258Direct::nextDouble");
      } else {
         out.println("execution = normale\n");
      }
      out.println("RUNS = " + RUNS);
      out.println("N = " + N);
      out.println("total values = " + totalValues);
      out.println();
   }

   private static void printResults(PrintWriter out, String name, long totalValues,
                                    long totalTimeIndirectDouble, long totalTimeDirectDouble,
                                    double sumDoubleIndirect, double sumDoubleDirect) {
      double secondsIndirectDouble = totalTimeIndirectDouble / 1.0e9;
      double secondsDirectDouble = totalTimeDirectDouble / 1.0e9;
      double speedIndirectDouble = totalValues / secondsIndirectDouble;
      double speedDirectDouble = totalValues / secondsDirectDouble;
      double ratioDirectIndirect = speedDirectDouble / speedIndirectDouble;

      out.println("======== " + name + " ========");
      out.printf("nextDouble indirect total time seconds = %.6f%n", secondsIndirectDouble);
      out.printf("nextDouble direct total time seconds = %.6f%n", secondsDirectDouble);
      out.printf("nextDouble indirect values/s = %.3f%n", speedIndirectDouble);
      out.printf("nextDouble direct values/s = %.3f%n", speedDirectDouble);
      out.printf("nextDouble: direct est %.2f fois plus rapide que indirect%n", ratioDirectIndirect);

      out.println();
      out.println("nextDouble indirect sum = " + sumDoubleIndirect);
      out.println("nextDouble direct sum = " + sumDoubleDirect);
      out.println("nextDouble sums equal: " + (Double.compare(sumDoubleIndirect, sumDoubleDirect) == 0));
      out.println();
   }

   private static double runLFSR258Indirect(RandomStream stream, long N) {
      double sum = 0.0;
      for (long k = 0; k < N; k++)
         sum += stream.nextDouble();
      return sum;
   }

   private static double runLFSR258Direct(RandomStream stream, long N) {
      double sum = 0.0;
      for (long k = 0; k < N; k++)
         sum += stream.nextDouble();
      return sum;
   }
}
