package umontreal.ssj.rng;
import java.math.BigInteger;


/**
 * This generator uses Math.unsignedMultiplyHigh which requires JDK 18 or later.
 * MWC generator with base b = 2^64, order k = 2, and coefficients:
 *
 * <pre>
 * a0 = -1
 * a1 = 193154555888013165
 * a2 = 1966812196490295
 * </pre>
 * 
 * The recurrence is:
 *
 * <pre>
 * t   = a1*x_{n-1} + a2*x_{n-2} + c_{n-1}
 * x_n = t mod 2^64
 * c_n = floor(t / 2^64)
 * </pre>
 *
 * The state is:
 *
 * <pre>
 * x2    = x_{n-2}
 * x1    = x_{n-1}
 * carry = c_{n-1}
 * </pre>
 */
public class MWC64k2a2 extends RandomStreamBase {
	
   private static final long serialVersionUID = 20260518L;
   
   // State components x_{n-1}, x_{n-2} and c_{n-1} interpreted as unsigned 64-bit. 
   private long x1, x2, carry;
   // First coefficient a1. 
   private static final long A1 = 193154555888013165L;
   // Second coefficient a2. 
   private static final long A2 = 1966812196490295L;
// private static final long  A1 = 556348944096481337L, A2 = 8250136865355103L; // Used in cpp code for jumps
   
   // 2^(-53), used to convert 53 random bits to a double. 
   private static final double NORM53 = 0x1.0p-53;
   // Stream spacing: 2^113 generated values. 
   private static final int STREAM_ADVANCE_EXPONENT =  113;
   // Substream spacing: 2^62	 generated values. 
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 62;

   // Seed used for the next created stream: {x_{n-2}, x_{n-1}, carry}. 
   private static long[] nextSeed = {12345L, 12345L, 12345L}; 
   //Initial state of this stream. 
   private long[] Ig;
   // Beginning state of the current substream of stream. 
   private long[] Bg;
  
   
    // Precomputed BigInteger constants for the MWC-to-LCG jump transformation.
    
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
   private static final BigInteger BI_A1 = BigInteger.valueOf(A1);
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2); 
   private static final BigInteger BI_M = BI_A2.multiply(BI_B).add(BI_A1).multiply(BI_B).subtract(BigInteger.ONE); // m = a2*b^2 + a1*b - 1
   private static final BigInteger BI_B_INV = BI_B.modInverse(BI_M); // b^(-1) mod m
   
   private static final BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); // b^2
   private static final BigInteger BI_MAP_X2 = BigInteger.ONE.subtract(BI_A1.multiply(BI_B)); // 1 - a1*b

   private static final BigInteger STREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT), BI_M); // J = (b^(-1))^(2^STREAM_JUMP_EXPONENT) mod m
   private static final BigInteger SUBSTREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT), BI_M); // J = (b^(-1))^(2^SUBSTREAM_JUMP_EXPONENT) mod m
   private static final BigInteger STREAM_K_X2 = STREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X2).mod(BI_M); // K_x2 = J*(1 - a1*b) mod m
   private static final BigInteger STREAM_K_X1 = STREAM_JUMP_MULTIPLIER.multiply(BI_B).mod(BI_M); // K_x1 = J*b mod m
   private static final BigInteger STREAM_K_C  = STREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M); // K_c = J*b^2 mod m
   private static final BigInteger SUBSTREAM_K_X2 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X2).mod(BI_M); // K_x2 = J*(1 - a1*b) mod m
   private static final BigInteger SUBSTREAM_K_X1 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B).mod(BI_M); // K_x1 = J*b mod m
   private static final BigInteger SUBSTREAM_K_C  = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M);// K_c = J*b^2 mod m
   
//   /*For A1 = 193154555888013165L; A2 = 1966812196490295L; STREAM_ADVANCE_EXPONENT = 113; SUBSTREAM_ADVANCE_EXPONENT = 62
//    * These values are precalculated and hardcoded here 
//    * */
//   private static final BigInteger STREAM_K_X2 = new BigInteger("550287765979488443922754971547870548747622917622202515");//Only for the given A1, A2 and the given jumpsizes
//   private static final BigInteger STREAM_K_X1 = new BigInteger("590664228179752031471436901652469203082748750179775222");//Only for the given A1, A2 and the given jumpsizes
//   private static final BigInteger STREAM_K_C  = new BigInteger("521510005202858839425516464682983339842500770120428048");//Only for the given A1, A2 and the given jumpsizes
//   private static final BigInteger  SUBSTREAM_K_X2 = new BigInteger("253956167587244238733053471042992883266608765200613794");// Only for the given A1, A2 and the given jumpsizes
//   private static final BigInteger SUBSTREAM_K_X1 = new BigInteger("334142836076716064087784195971406862825997835485950288");//Only for the given A1, A2 and the given jumpsizes
//   private static final BigInteger SUBSTREAM_K_C  = new BigInteger("475660625350411904999072789094749200216738394742542956");//Only for the given A1, A2 and the given jumpsizes
 
   /**
    * Constructs a new stream.
    */
   public MWC64k2a2() {
      Ig = nextSeed.clone();              // Save the start state of this stream.
      Bg = new long[3];                   // Allocate the substream state.

      resetStartStream();                 // Set Bg and current state from Ig.
      advanceStateFixedJump(nextSeed, STREAM_K_X2,STREAM_K_X1, STREAM_K_C); // using precomputed constant for Stream advance
   }        

   /**
    * Constructs a new stream with a name.
    *
    * @param name stream name
    */
   public MWC64k2a2(String name) {
      this();                            
      this.name = name;                  
   }

   /**
    * Sets the package seed for the next created stream.
    *
    * @param seed seed {x_{n-2}, x_{n-1}, carry}
    */
   public static void setPackageSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      nextSeed = seed.clone();            // Copy seed to avoid external mutation.
   }

   /**
    * Sets the seed of this stream.
    *
    * @param seed seed {x_{n-2}, x_{n-1}, carry}
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      Ig = seed.clone();                  // Replace initial stream state.
      resetStartStream();                 // Restart stream from new seed.
   }

   /**
    * Returns the current state.
    *
    * @return current state {x_{n-2}, x_{n-1}, carry}
    */
   public long[] getState() {
      return new long[] { x2, x1, carry };
   }

   /**
    * Resets this stream to the beginning of its stream.
    */
   public void resetStartStream() {
      Bg[0] = Ig[0];                      // Substream start = stream start.
      Bg[1] = Ig[1];
      Bg[2] = Ig[2];

      resetStartSubstream();              // Current state = substream start.
   }

   /**
    * Resets this stream to the beginning of its current substream.
    */
   public void resetStartSubstream() {
      x2 = Bg[0];                         // Restore x_{n-2}.
      x1 = Bg[1];                         // Restore x_{n-1}.
      carry = Bg[2];                      // Restore carry.
   }

   /**
    * Moves this stream to the beginning of the next substream.
    */
   public void resetNextSubstream() {
	  advanceStateFixedJump(Bg, SUBSTREAM_K_X2, SUBSTREAM_K_X1, SUBSTREAM_K_C);
      resetStartSubstream();   
   }

   /**
    * Generates one MWC step and returns the old x_{n-1},
    * Compatibility: JDK18 or later. Math.unsignedMultiplyHigh was introduced since JDK18.
    *
    * @return old x_{n-1}, interpreted as unsigned 64-bit
    */
   private long nextNumber() { 
      long out = x1;                        

      long low1 = A1 * x1;                  
      long high1 = Math.unsignedMultiplyHigh(A1, x1);

      long low2 = A2 * x2;               
      long high2 = Math.unsignedMultiplyHigh(A2, x2);

      long low = low1 + low2;
      long overflow1 = Long.compareUnsigned(low, low1) < 0 ? 1L : 0L;

      long lowWithCarry = low + carry;
      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = high1 + high2 + overflow1 + overflow2; // we can replace high1,2,overflow1,2 directly here, kept for readability 

      x2 = x1;
      x1 = lowWithCarry;
      carry = high;

      return out;
   }

   /**
    * Returns the next uniform in (0,1).
    *
    * This follows the C style:
    *
    * <pre>
    * block53 = nextNumber() >>> 11
    * if block53 == 0, try again
    * return block53 * 2^(-53)
    * </pre>
    *
    * @return next uniform in (0,1)
    */
   protected double nextValue() {
      long block53;                       // Will contain the top 53 bits.

      do {
         block53 = nextNumber() >>> 11;   // Keep top 53 bits of 64-bit output.
      } while (block53 == 0L);            // Reject 0 to avoid returning 0.0.

      return block53 * NORM53;            // Convert to double.
   }
   
   /**
    * Returns a random long in [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random long in [i, j]
    */
   
   // This method implements the "unbiased bounded integer generation" algorithm used by java.util.Random.nextint, which is based on rejection sampling to avoid modulo bias.
   //It handles the case where the range size may exceed 2^63 by using a different approach (n <= 0 due to overflow). 
   //also includes optimizations for power-of-two range sizes.
   // uses 63 bits entropy in the case of n >0
   public long nextLong(long i, long j) {
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");

      long n = j - i + 1L;                

      if (n > 0L) {                       
         long r = nextNumber() >>> 1;     
         long m = n - 1L;                 
         
         if ((n & m) == 0L)          
            return i + (r & m);           
       
         //Handling Modulo Bias (Rejection Sampling). Crop using (r % n) will introduce bias if n does not divide the number of possible r values. 
         long u = r;                      
         while (u + m - (r = u % n) < 0L)  
            u = nextNumber() >>> 1;      

         return i + r;                    
      }

      long r;                             // Case: range size is larger than 2^63. 
      do {
         r = nextNumber();                
      } while (r < i || r > j);           

      return r;
   }
   
   // LRSR version: range only up to 2^62  : should add guard to handle case where range is bigger
   public long nextLongssj(long i, long j) { 
	      if (i > j)
	         throw new IllegalArgumentException(i + " is larger than " + j + ".");
	      long d = j - i + 1;
	      long q = 0x4000000000000000L / d;  // 0x4000000000000000L = 2^{62} in hexadecimal.
	      long r = 0x4000000000000000L % d;
	      long res;
	      do {
	         res = nextNumber() >>> 2;   // Integer smaller than 2^{62}.
	      } while (res >= 0x4000000000000000L - r);

	      return i + (res / q);
	   }
   
   // return a block of b bits (int):
   public long nextBitsLong(int b) {
	    return nextNumber() >>> (64 - b);
	}

   /**
    * Returns a random int in [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random int in [i, j]
    */
   public int nextInt(int i, int j) {
      return (int) nextLong(i, j);
   }

   /**
    * Returns the current state as a string.
    *
    * @return current state string
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("The current state of MWC64k2a2");

      if (name != null && name.length() > 0)
         sb.append(" ").append(name);

      sb.append(" is: { ");
      sb.append(Long.toUnsignedString(x2)).append(", ");
      sb.append(Long.toUnsignedString(x1)).append(", ");
      sb.append(Long.toUnsignedString(carry)).append(" }");

      return sb.toString();
   }

   /**
    * Returns stream, substream, and current state.
    *
    * @return detailed state string
    */
   public String toStringFull() {
      String nl = System.lineSeparator();
      StringBuilder sb = new StringBuilder();

      sb.append("MWC64k2a2 stream");

      if (name != null && name.length() > 0)
         sb.append(" ").append(name);

      sb.append(":").append(nl);

      sb.append(" Ig = { ")
        .append(Long.toUnsignedString(Ig[0])).append(", ")
        .append(Long.toUnsignedString(Ig[1])).append(", ")
        .append(Long.toUnsignedString(Ig[2])).append(" }").append(nl);

      sb.append(" Bg = { ")
        .append(Long.toUnsignedString(Bg[0])).append(", ")
        .append(Long.toUnsignedString(Bg[1])).append(", ")
        .append(Long.toUnsignedString(Bg[2])).append(" }").append(nl);

      sb.append(" Cg = { ")
        .append(Long.toUnsignedString(x2)).append(", ")
        .append(Long.toUnsignedString(x1)).append(", ")
        .append(Long.toUnsignedString(carry)).append(" }").append(nl);

      return sb.toString();
   }

   /**
    * Clones this stream.
    *
    * @return independent copy of this stream
    */
   public MWC64k2a2 clone() {
      MWC64k2a2 copy = (MWC64k2a2) super.clone();

      copy.Ig = Ig.clone();               // Copy stream-start state.
      copy.Bg = Bg.clone();               // Copy substream-start state.

      return copy;
   }

   /**
    * Checks if a seed is usable.
    *
    * @param seed seed to check
    */
   private static final long MAX_CARRY = A1 + A2 - 1L;

   private static void checkSeed(long[] seed) {
      if (seed == null)
         throw new NullPointerException("Seed must not be null.");

      if (seed.length != 3)
         throw new IllegalArgumentException("Seed must contain 3 values.");

      if (seed[2] < 0L || seed[2] > MAX_CARRY)
         throw new IllegalArgumentException(
               "The carry must be in [0, " + MAX_CARRY + "].");

      if (seed[0] == 0L && seed[1] == 0L && seed[2] == 0L)
         throw new IllegalArgumentException("The all-zero state is not allowed.");

      if (seed[0] == -1L && seed[1] == -1L && seed[2] == MAX_CARRY)
         throw new IllegalArgumentException(
               "The all-ones/max-carry state is not allowed.");
   }

   /**
    * Advances by a fixed jump size predefined by precomputed constants
    *
    * The state is {x_{n-2}, x_{n-1}, carry}.
    *
    * @param state state to advance
    * @param K_X2 x2 multiplier 
    * @param K_X1  x1  multiplier
    * @param K_C   carry multiplier
    */
   
   private static void advanceStateFixedJump(long[] state,BigInteger K_X2, BigInteger K_X1, BigInteger K_C) {
	   BigInteger stateX2 = toUnsignedBigInt(state[0]);
	   BigInteger stateX1 = toUnsignedBigInt(state[1]);
	   BigInteger stateCarry = BigInteger.valueOf(state[2]);

	   BigInteger sigma =
	         K_X2.multiply(stateX2)
	       .add(K_X1.multiply(stateX1))
	       .add(K_C.multiply(stateCarry))
	       .mod(BI_M);

	   long newX2 = sigma.longValue();
	   sigma = sigma.shiftRight(64);

	   sigma = sigma.add(BI_A1.multiply(toUnsignedBigInt(newX2)));

	   long newX1 = sigma.longValue();
	   long newCarry = sigma.shiftRight(64).longValue();

	   state[0] = newX2;
	   state[1] = newX1;
	   state[2] = newCarry;
	}
   
   /**
    * Advances the current stream state by n steps.
    *
    * This method is for a general jump size n. It does not use
    * advanceStateFixedJump and does not compute fixed-jump coefficients.
    *
    * It maps the current MWC state to the equivalent LCG state, applies
    * the LCG jump, then converts the result back to the MWC state.
    *
    * @param n number of steps to jump
    */
   public void advanceStateByJump(long n) {
      if (n < 0)
         throw new IllegalArgumentException("Jump step n cannot be negative.");

      if (n == 0)
         return;

      BigInteger stateX2 = toUnsignedBigInt(x2);
      BigInteger stateX1 = toUnsignedBigInt(x1);
      BigInteger stateCarry = BigInteger.valueOf(carry);

//       Map the current MWC state to the equivalent LCG state:
//        y =   (1 - A1*b)*x2 + b*x1 + b^2*carry mod m

      BigInteger y =
            BI_MAP_X2.multiply(stateX2)
          .add(BI_B.multiply(stateX1))
          .add(BI_B2.multiply(stateCarry))
          .mod(BI_M);

      // Apply the LCG jump: y_new = (b^(-1))^n * y mod m.
      BigInteger sigma =
            BI_B_INV.modPow(BigInteger.valueOf(n), BI_M)
          .multiply(y)
          .mod(BI_M);

       // Convert the jumped LCG state back to the MWC state.
      long newX2 = sigma.longValue();
      sigma = sigma.shiftRight(64);

      sigma = sigma.add(BI_A1.multiply(toUnsignedBigInt(newX2)));

      long newX1 = sigma.longValue();
      long newCarry = sigma.shiftRight(64).longValue();

      x2 = newX2;
      x1 = newX1;
      carry = newCarry;
   }
   
   //for test
   public long nextRaw()
   { return nextNumber();}
   
   /**
    * Converts an unsigned 64-bit long to a positive BigInteger.
    */
   private static BigInteger toUnsignedBigInt(long value) {
      if (value >= 0) {
         return BigInteger.valueOf(value);
      } else {
         // Handle negative long bit patterns as unsigned 64-bit values
         return BigInteger.valueOf(value & 0x7FFFFFFFFFFFFFFFL).setBit(63);
      }
   }
}