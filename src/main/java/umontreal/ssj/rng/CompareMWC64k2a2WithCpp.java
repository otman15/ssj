package umontreal.ssj.rng;

/**
 * Produces raw outputs and sums for comparison with LatMRG's TestMWCSpeed.cc,
 * function mwc64k2a2().
 */
public class CompareMWC64k2a2WithCpp {
	   public static void main(String[] args) {
		      long n = 10_000_000_000L;
		      long expected = Long.parseUnsignedLong("5495659462671017987");
		      long expected3 = Long.parseUnsignedLong("1042710103094466020");

		      MWC64k2a2 rng = new MWC64k2a2();
		      MWC64k3a2 rng3 = new MWC64k3a2();
		      rng.setSeed(new long[] { 12345L, 12345L, 12345L });
		      rng3.setSeed(new long[] { 12345L, 12345L, 12345L, 12345L });

		      long sum = 0L;
		      long sum3 = 0L;

		      for (long i = 0; i < n; i++) {
		         sum += rng.nextRaw();
		         sum3 += rng3.nextRaw(); 
		      }
		      
		      System.out.println("------------MWC64k2a2-------------------");
		      System.out.println("-------------------------------");

		      System.out.println("sum      = " + Long.toUnsignedString(sum));
		      System.out.println("expected = " + Long.toUnsignedString(expected));
		      System.out.println("same?    = " + (sum == expected));
		      System.out.println("-------------------------------");
		      System.out.println("-------------------------------");
//		      sum      = 5495659462671017987
//		    		  expected = 5495659462671017987
//		    		  same?    = true
		      System.out.println("----------MWC64k3a2---------------------");
		      System.out.println("-------------------------------");

		      System.out.println("sum      = " + Long.toUnsignedString(sum3));
		      System.out.println("expected = " + Long.toUnsignedString(expected3));
		      System.out.println("same?    = " + (sum3 == expected3));
		      System.out.println("-------------------------------");
		      System.out.println("-------------------------------");
		   }
//   public static void main(String[] args) {
//      long[] seed = { 12345L, 12345L, 12345L };
//
//      printFirstValues(seed, 20);
//      printSum(seed, 10L);
//      printSum(seed, 1000L);
//      printSum(seed, 1_000_000L);
//   }
//
//   private static void printFirstValues(long[] seed, int n) {
//      MWC64k2a2 rng = new MWC64k2a2();
//      rng.setSeed(seed);
//
//      System.out.println("First " + n + " raw mwc64k2a2 outputs:");
//      for (int i = 0; i < n; i++) {
//         long x = rng.nextRaw();
//         System.out.println(i + " " + Long.toUnsignedString(x));
//      }
//
//      System.out.println("Final state:");
//      printState(rng.getState());
//      System.out.println();
//   }
//
//   private static void printSum(long[] seed, long n) {
//      MWC64k2a2 rng = new MWC64k2a2();
//      rng.setSeed(seed);
//
//      long sum = 0L;
//
//      for (long i = 0; i < n; i++)
//         sum += rng.nextRaw(); // Java long overflow gives sum mod 2^64.
//
//      System.out.println("n = " + n);
//      System.out.println("sum mod 2^64 = " + Long.toUnsignedString(sum));
//      System.out.print("state = ");
//      printState(rng.getState());
//      System.out.println();
//   }
//
//   private static void printState(long[] state) {
//      System.out.println("{ "
//            + Long.toUnsignedString(state[0]) + ", "
//            + Long.toUnsignedString(state[1]) + ", "
//            + Long.toUnsignedString(state[2]) + " }");
//   }
}