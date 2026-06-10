package umontreal.ssj.rng;

import java.io.FileWriter;
import java.math.BigInteger;
import java.io.PrintWriter;
import java.io.IOException;


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
   private static final long N_JUMPS = 1000_000L;

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

   public static void main(String[] args) throws IOException {
	   
		StringBuilder out = new StringBuilder();
		   
		out.append("-----MWC64k2a2Tests------------------\n");
	   
	  //get_n_frst_values(10);
      //runSpeedRawTest();
      //runSpeedU01Test();
      runJumpTest(out);

      
      System.out.println(out);

//      FileWriter writer = new FileWriter("/home/otman/Documents/GitHub/Data/o-MWC-test/Jumps__precomputing.res");
//      writer.write(out.toString());
//      writer.close();
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
    * Java nextValue() uses the top 53 bits and rejects 0.
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
   

   /*
    * Same structure as MWCJump.res:
    *
    * 1. Print initial state.
    * 2. Make 4 successive jumps of size 5000.
    * 3. Make one large jump of size 20000 from the initial state.
    * 4. Make 1,000,000 jumps of size 5000 and print final state + time.
 * IMPORTANT for MWCJump.res comparison:
 *
 * TestMWCJump.cc uses different  constants than TestMWCSpeed.cc.
 *
 * To match the MWCJump.res output, temporarily change the constants
 * in MWC64k2a2.java to:
 *   A1 = 0x07b88c6ac008d039L;  // 556348944096481337
 *   A2 = 0x001d4f74ad35355fL;  // 8250136865355103
 ** in MWC64k3a2.java to:
 *A2 = 0x320fbe97bef0f95L, A3 = 0x4a1849ec18bfa6L; 
 */
    
   private static void runJumpTest(StringBuilder out) {
	   out.append("");
	   out.append("============================================================= \n");
	   out.append("MWC64k2a2 jump test \\n");
	   out.append("jumpSize = " + JUMP_SIZE +"\n");
	   out.append("n jumps for timing = " + N_JUMPS +"\n");

      MWC64k2a2 rng = new MWC64k2a2();
      rng.setSeed(SEED);

      out.append("");
      out.append("Successive jumps ahead: \\n");
      out.append("initial state = " + state(rng.getState()) + "\n");

      long start = System.nanoTime();

      for (int i = 1; i <= 4; i++) {
         rng.advanceStateByJump(BigInteger.valueOf(JUMP_SIZE));
         out.append("after jump " + i + " = " + state(rng.getState()) + "\n");
      }

      long end = System.nanoTime();

      out.append("time for 4 jumps = " + seconds(start, end) + "\n");

      /*
       * One big jump from the original seed.
       * This corresponds to jumpSize2 = n0 * jumpSize = 4 * 5000 = 20000.
       */
      MWC64k2a2 bigJump = new MWC64k2a2();
      bigJump.setSeed(SEED);
      rng.setSeed(SEED);

      start = System.nanoTime();

      rng.advanceStateByJump(BigInteger.valueOf(4L * JUMP_SIZE));

      end = System.nanoTime();

      out.append("\n");
      out.append("One large jump: \n");
      out.append("jumpSize2 = " + (4L * JUMP_SIZE) + "\n");
      out.append("state = " + state(bigJump.getState())+"\n");
      out.append("time = " + seconds(start, end) + "\n");

      /*
       * Timing test:
       * jump ahead by jumpSize, repeated N_JUMPS times.
       */
      MWC64k2a2 manyJumps = new MWC64k2a2();
      manyJumps.setSeed(SEED);
      rng.setSeed(SEED);

      start = System.nanoTime();

      for (long i = 0; i < N_JUMPS; i++) {
    	  rng.advanceStateByJump(BigInteger.valueOf(JUMP_SIZE));
      }

      end = System.nanoTime();

      out.append("\n");
      out.append("Repeated jump timing:\n");
      out.append("number of jumps = " + N_JUMPS +"\n");
      out.append("jumpSize = " + JUMP_SIZE+"\n");
      out.append("final state = " + state(manyJumps.getState()) + "\n");
      out.append("time = " + seconds(start, end) + "\n");
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
   

}