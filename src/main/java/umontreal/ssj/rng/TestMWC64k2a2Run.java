package umontreal.ssj.rng;

public class TestMWC64k2a2Run {

   /*
    * Fixed values chosen to match the C++ files:
    *
    * TestMWCSpeed.cc / MWCSpeed10.res:
    *   n = 10^10
    *   initial state: x1 = x2 = c = 12345
    *
    * TestMWCJump.cc / MWCJump.res:
    *   jumpSize = 5000
    *   n0 = 4 successive jumps
    *   n = 1,000,000 jumps for timing
    */
   private static final long N_SPEED = 10_000_000_000L;
   private static final long JUMP_SIZE = 5000L;
   private static final long N_JUMPS = 1_000_000L;

   /*
    * Java state order:
    *   {x0, x1, carry}
    *
    * This corresponds to:
    *   x0 = x_{n-2}
    *   x1 = x_{n-1}
    *   carry = c
    */
   private static final long[] SEED = {12345L, 12345L, 12345L};

   public static void main(String[] args) {
	   
	  //get_n_frst_values(10);
      //runSpeedRawTest();
      //runSpeedU01Test();
      //runJumpTest();
	   //runSpeedLontTest();
	   //TestNextBytes();
	   compareNextBitsLongVsNextLong(new MWC64k2a2(),63, 1000000000L);
   }
   
   /*
    * for raw vallues
    * */
   private static void get_n_frst_values(int n) {
	      MWC64k2a2 rng = new MWC64k2a2();
	      rng.setSeed(SEED);
           
	      System.out.println("Using the seed xxxxxx, the first " + n + " vlaues are :");
	      String raw;
	      for (long i = 0; i < n; i++) {
	    	  raw = Long.toUnsignedString(rng.nextRaw()) ;
	         System.out.println(raw+ "     ");
	      }
	      
   }
   
   

   /*
    * Same idea as the mwc64k2a2 raw speed block in TestMWCSpeed.cc:
    *
    * x1 = x2 = c = 12345;
    * sum = 0;
    * for i = 0 to n-1:
    *     sum += mwc64k2a2();
    *
    * Java long overflow automatically wraps modulo 2^64.
    */
   private static void runSpeedRawTest() {
      MWC64k2a2 rng = new MWC64k2a2();
      rng.setSeed(SEED);

      long sum = 0L;

      long start = System.nanoTime();

      for (long i = 0; i < N_SPEED; i++) {
         sum += rng.nextRaw();
      }

      long end = System.nanoTime();

      System.out.println("=============================================================");
      System.out.println("MWC64k2a2 raw speed test");
      System.out.println("n = " + N_SPEED);
      System.out.println("sum = " + Long.toUnsignedString(sum));
      System.out.println("time = " + seconds(start, end));
   }

   /*
    * Same idea as the U(0,1) speed block in TestMWCSpeed.cc:
    *
    * dsum = 0;
    * for i = 0 to n-1:
    *     dsum += mwc64k2a2U01();
    *
    * Your Java nextValue() uses the top 53 bits and rejects 0.
    */
   private static void runSpeedU01Test() {
      MWC64k2a2 rng = new MWC64k2a2();
      rng.setSeed(SEED);

      double sum = 0.0;

      long start = System.nanoTime();

      for (long i = 0; i < N_SPEED; i++) {
         sum += rng.nextValue();
      }

      long end = System.nanoTime();

      System.out.println();
      System.out.println("=============================================================");
      System.out.println("MWC64k2a2 U(0,1) speed test");
      System.out.println("n = " + N_SPEED);
      System.out.println("average = " + (sum / N_SPEED));
      System.out.println("time = " + seconds(start, end));
   }
   
   
   private static void runSpeedLontTest() {
	      MWC64k2a2 rng = new MWC64k2a2();
	      rng.setSeed(SEED);
	      
	      long m = 1000000000L;
	      
	      Long n = 10000000L;

	      long sum1 = 0;

	      long start1 = System.nanoTime();

	      for (long i = 0; i < m; i++) {
	         sum1 += rng.nextLong(0,m);
	      }

	      long end1 = System.nanoTime();

	      System.out.println();
	      System.out.println("===================Long java Random=============================");
	      System.out.println("MWC64k2a2 nextLong("+0+","+m +") speed test");
	      System.out.println("n sim = " + m);
	      System.out.println("sum = " + (sum1));
	      System.out.println("time = " + seconds(start1, end1));
	      
	      rng.setSeed(SEED);

	      long sum2= 0;

	      long start2 = System.nanoTime();

	      for (long i = 0; i < m; i++) {
	         sum2 += rng.nextLongSsj(0,m);
	      }

	      long end2 = System.nanoTime();

	      System.out.println();
	      System.out.println("===================Long ssj rng=============================");
	      System.out.println("MWC64k2a2 nextLongSsj("+0+","+m +") speed test");
	      System.out.println("n sim = " + m);
	      System.out.println("sum = " + (sum2));
	      System.out.println("time = " + seconds(start2, end2));
   }

   /*
    * Same structure as MWCJump.res:
    *
    * 1. Print initial state.
    * 2. Make 4 successive jumps of size 5000.
    * 3. Make one large jump of size 20000 from the initial state.
    * 4. Make 1,000,000 jumps of size 5000 and print final state + time.
    *
    * Important:
    * This uses your Java class constants.
    * It will match C++ jump output only if the C++ jump file uses the same constants.
    * /*
 * IMPORTANT for MWCJump.res comparison:
 *
 * TestMWCJump.cc uses different mwc64k2a2 constants than TestMWCSpeed.cc.
 *
 * To match the MWCJump.res output, temporarily change the constants
 * in MWC64k2a2.java to:
 *
 *   A1 = 0x07b88c6ac008d039L;  // 556348944096481337
 *   A2 = 0x001d4f74ad35355fL;  // 8250136865355103
 *
 * The normal speed-test constants are:
 *
 *   A1 = 0x02ae390b92740f6dL;  // 193154555888013165
 *   A2 = 0x0006fcce264fcc37L;  // 1966812196490295
 *
 * Use the jump constants only when comparing with MWCJump.res.
 */
    
   private static void runJumpTest() {
      System.out.println();
      System.out.println("=============================================================");
      System.out.println("MWC64k2a2 jump test");
      System.out.println("jumpSize = " + JUMP_SIZE);
      System.out.println("n jumps for timing = " + N_JUMPS);

      MWC64k2a2 rng = new MWC64k2a2();
      rng.setSeed(SEED);

      System.out.println();
      System.out.println("Successive jumps ahead:");
      System.out.println("initial state = " + state(rng.getState()));

      long start = System.nanoTime();

      for (int i = 1; i <= 4; i++) {
         rng.advanceStateByJump(JUMP_SIZE);
         System.out.println("after jump " + i + " = " + state(rng.getState()));
      }

      long end = System.nanoTime();

      System.out.println("time for 4 jumps = " + seconds(start, end));

      /*
       * One big jump from the original seed.
       * This corresponds to jumpSize2 = n0 * jumpSize = 4 * 5000 = 20000.
       */
      MWC64k2a2 bigJump = new MWC64k2a2();
      bigJump.setSeed(SEED);

      start = System.nanoTime();

      bigJump.advanceStateByJump(4L * JUMP_SIZE);

      end = System.nanoTime();

      System.out.println();
      System.out.println("One large jump:");
      System.out.println("jumpSize2 = " + (4L * JUMP_SIZE));
      System.out.println("state = " + state(bigJump.getState()));
      System.out.println("time = " + seconds(start, end));

      /*
       * Timing test:
       * jump ahead by jumpSize, repeated N_JUMPS times.
       */
      MWC64k2a2 manyJumps = new MWC64k2a2();
      manyJumps.setSeed(SEED);

      start = System.nanoTime();

      for (long i = 0; i < N_JUMPS; i++) {
         manyJumps.advanceStateByJump(JUMP_SIZE);
      }

      end = System.nanoTime();

      System.out.println();
      System.out.println("Repeated jump timing:");
      System.out.println("number of jumps = " + N_JUMPS);
      System.out.println("jumpSize = " + JUMP_SIZE);
      System.out.println("final state = " + state(manyJumps.getState()));
      System.out.println("time = " + seconds(start, end));
   }

   /*
    * Print unsigned 64-bit state values.
    */
   private static String state(long[] s) {
      return "[" +
         Long.toUnsignedString(s[0]) + " " +
         Long.toUnsignedString(s[1]) + " " +
         Long.toUnsignedString(s[2]) +
      "]";
   }

   private static double seconds(long start, long end) {
      return (end - start) / 1.0e9;
   }
   
   private static String toHex(byte[] bytes) {
       StringBuilder sb = new StringBuilder();

       for (byte b : bytes) {
           sb.append(String.format("%02X ", b & 0xFF));
       }

       return sb.toString().trim();
   }
   
   private static void bintostr(byte[] bytes){

       for (byte b : bytes) {
    	   System.out.print("rep bin: " + Integer.toBinaryString(b & 0xFF ) + ", ");
       }
       System.out.println();
   }
   
   
   public static void TestNextBytes() {
	   
	   long m = 1000000L;

	   MWC64k2a2 rng1 = new MWC64k2a2();
	   rng1.setSeed(SEED);

       byte[] a = new byte[16];
	   long start1 = System.nanoTime();
	   for (int i = 0; i <= m; i++) {
		   rng1.nextBytes(a);}
	   long end1 = System.nanoTime();

       System.out.println("High-first:");
	
       System.out.println();
       System.out.println("=================High-first:=========================");
       System.out.println("rep hex: " + toHex(a));
       bintostr(a);
       System.out.println("time = " + seconds(start1, end1));

       rng1.setSeed(SEED);

       byte[] b = new byte[16];
	   long start2 = System.nanoTime();
	   for (int i = 0; i <= m; i++) {
		   rng1.nextBytes1(b);}
	   long end2 = System.nanoTime();
	   
      System.out.println();
      System.out.println("=================Java-style low-first=========================");
      System.out.println("rep hex: " +toHex(b));
      bintostr(b);
      System.out.println("time = " + seconds(start2, end2));
       

   }
   
   public static void compareNextBitsLongVsNextLong(MWC64k2a2 stream, int b, long m) {
	   b=45;
	   m=10000000000L;
	    if (b < 0 || b > 63) {
	        throw new IllegalArgumentException("b must be between 0 and 62");
	    }

	    long upper = (1L << b) - 1L;

	    long sum1 = 0;
	    stream.resetStartStream();

	    long t0 = System.nanoTime();
	    for (long i = 0; i < m; i++) {
	         stream.nextLong(0L, upper);//sum1 +=
	    }
	    long t1 = System.nanoTime();

	    long sum2 = 0;
	    stream.resetStartStream();

	    long t2 = System.nanoTime();
	    for (long i = 0; i < m; i++) {
	        stream.nextBitsLong(b);//sum2 += 
	    }
	    long t3 = System.nanoTime();

	    double timeLong = (t1 - t0) / 1_000_000.0;
	    double  timeBits = (t3 - t2) / 1_000_000.0;
	    
	    
	    System.out.println("-------------------Compare Nextlong(2**b-1) and nextBitsLong(b)----------------------");
	    System.out.println("b = " + b);
	    System.out.println("m = " + m);
	    System.out.printf("nextBitsLong(%d): %.3f ms, sum = %d%n", b, timeBits, sum2);
	    System.out.printf("nextLong(0, 2^%d - 1): %.3f ms, sum = %d%n", b, timeLong, sum1);
	    System.out.printf("speedup = %.3f%n", timeLong / timeBits);
	}
}