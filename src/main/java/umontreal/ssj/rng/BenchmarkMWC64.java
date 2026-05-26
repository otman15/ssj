package umontreal.ssj.rng;

/**
 * Manual benchmark and validation for MWC64k2a2 and MWC64k3a2.
 *
 * It compares raw sums with MWCSpeed10.res and also measures nextDouble speed
 * with basic U(0,1) statistics.
 */
public class BenchmarkMWC64 {
   private static final long RAW_N = 10_000_000_000L;
   private static final long DOUBLE_N = 100_000_000L;

   private static final long[] SEED_K2 = { 12345L, 12345L, 12345L };
   private static final long[] SEED_K3 = { 12345L, 12345L, 12345L, 12345L };

   private static final long EXPECTED_K2_SUM =
         Long.parseUnsignedLong("5495659462671017987");

   private static final long EXPECTED_K3_SUM =
         Long.parseUnsignedLong("1042710103094466020");

   public static void main(String[] args) {
      long rawN = args.length >= 1 ? Long.parseLong(args[0]) : RAW_N;
      long doubleN = args.length >= 2 ? Long.parseLong(args[1]) : DOUBLE_N;

      System.out.println("Raw benchmark n = " + rawN);
      System.out.println("Double benchmark n = " + doubleN);
      System.out.println();

      benchmarkRawK2(rawN);
      benchmarkRawK3(rawN);

      benchmarkDoubleK2(doubleN);
      benchmarkDoubleK3(doubleN);
   }

   private static void benchmarkRawK2(long n) {
      MWC64k2a2 rng = new MWC64k2a2();
      rng.setSeed(SEED_K2);

      long sum = 0L;

      long start = System.nanoTime();
      for (long i = 0; i < n; i++)
         sum += rng.nextRaw();
      long end = System.nanoTime();

      printRawResult("MWC64k2a2", n, sum, EXPECTED_K2_SUM, end - start);
   }

   private static void benchmarkRawK3(long n) {
      MWC64k3a2 rng = new MWC64k3a2();
      rng.setSeed(SEED_K3);

      long sum = 0L;

      long start = System.nanoTime();
      for (long i = 0; i < n; i++)
         sum += rng.nextRaw();
      long end = System.nanoTime();

      printRawResult("MWC64k3a2", n, sum, EXPECTED_K3_SUM, end - start);
   }

   private static void benchmarkDoubleK2(long n) {
      MWC64k2a2 rng = new MWC64k2a2();
      rng.setSeed(SEED_K2);

      benchmarkDouble("MWC64k2a2.nextDouble", rng, n);
   }

   private static void benchmarkDoubleK3(long n) {
      MWC64k3a2 rng = new MWC64k3a2();
      rng.setSeed(SEED_K3);

      benchmarkDouble("MWC64k3a2.nextDouble", rng, n);
   }

   private static void benchmarkDouble(String name, RandomStreamBase rng, long n) {
      double sum = 0.0;
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;

      long start = System.nanoTime();
      for (long i = 0; i < n; i++) {
         double u = rng.nextDouble();

         sum += u;

         if (u < min)
            min = u;

         if (u > max)
            max = u;
      }
      long end = System.nanoTime();

      double seconds = (end - start) / 1.0e9;
      double mean = sum / n;
      double speed = n / seconds;

      System.out.println(name);
      System.out.println("  n        = " + n);
      System.out.println("  time     = " + seconds + " seconds");
      System.out.println("  speed    = " + speed + " doubles/second");
      System.out.println("  mean     = " + mean);
      System.out.println("  min      = " + min);
      System.out.println("  max      = " + max);
      System.out.println();
   }

   private static void printRawResult(
         String name, long n, long sum, long expected, long elapsedNano) {
      double seconds = elapsedNano / 1.0e9;
      double speed = n / seconds;

      System.out.println(name + ".nextRaw");
      System.out.println("  n        = " + n);
      System.out.println("  time     = " + seconds + " seconds");
      System.out.println("  speed    = " + speed + " raw values/second");
      System.out.println("  sum      = " + Long.toUnsignedString(sum));
      System.out.println("  expected = " + Long.toUnsignedString(expected));
      System.out.println("  same?    = " + (sum == expected));
      System.out.println();
   }
}