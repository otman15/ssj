package umontreal.ssj.rng;
import java.math.BigInteger;


/**
 * This generator uses Math.unsignedMultiplyHigh which requires JDK 18 or later.
 * MWC generator with:
 *
 * <pre>
 * b  = 2^64
 * k  = 3
 * a0 = -1
 * a1 = 0
 * a2 = 184698970548483715
 * a3 = 6028691832887
 * </pre>
 * 
 * The recurrence is:
 *
 * <pre>
 * t   = a1*x_{n-1} + a2*x_{n-2} + a3*x_{n-3} + c_{n-1}
 * x_n = t mod 2^64
 * c_n = floor(t / 2^64)
 * </pre>
 *
 * Since a1 = 0, the implemented recurrence is:
 *
 * <pre>
 * t   = a2*x_{n-2} + a3*x_{n-3} + c_{n-1}
 * x_n = low 64 bits of t
 * c_n = high 64 bits of t
 * </pre>
 *
 * The state is stored as:
 *
 * <pre>
 * {x_{n-3}, x_{n-2}, x_{n-1}, carry}
 * </pre>
 */
public class MWC64k3a2 extends RandomStreamBase {
	
   private static final long serialVersionUID = 20260518L;
   
   /** State components x_{n-1}, x_{n-2}, x_{n-3} and c_{n-1} interpreted as unsigned 64-bit. */
   private long x1, x2, x3, carry;

   /** Second coefficient a2. */
   private static final long A2 = 184698970548483715L;
   /** Third coefficient a3. */
   private static final long A3 = 6028691832887L;
   
//   private static final long A2 = 0x320fbe97bef0f95L, A3 = 0x4a1849ec18bfa6L; // for jumps comparaison with cpp

   /** 2^(-53), used to convert 53 random bits to a double. */
   private static final double NORM53 = 0x1.0p-53;

   /** stream spacing: 1L << 20 = 2^20 generated values. */
   private static final long STREAM_ADVANCE = 1L << 20;

   /** substream spacing: 1L << 10 = 2^10 generated values. */
   private static final long SUBSTREAM_ADVANCE = 1L << 10;

   /** Seed used for the next created stream: {x_{n-3}, x_{n-2}, x_{n-1}, carry}. */
   private static long[] nextSeed = {1L, 3L, 4L, 5L};

   /** Initial state of this stream. */
   private long[] Ig;

   /** Beginning state of the current substream of stream. */
   private long[] Bg;
  
   /**
    * Precomputed BigInteger constants for the MWC-to-LCG jump transformation.
    * For fixed params: A2,A3, streamAdvance and SubstreamAdvance, we can so more. For now this work in general
    */
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2); 
   private static final BigInteger BI_A3 = BigInteger.valueOf(A3); 
   private static final BigInteger BI_M = BI_A3.multiply(BI_B).add(BI_A2).multiply(BI_B).multiply(BI_B).subtract(BigInteger.ONE); // m = a3 * b^3 + a2 * b^2 + a1 * b - 1
   private static final BigInteger BI_B_INV = BI_B.modInverse(BI_M);   //b^-1
   private static final BigInteger SUBSTREAM_JUMP_MULTIPLIER =  BI_B_INV.modPow(BigInteger.valueOf(SUBSTREAM_ADVANCE), BI_M); // fixed jump multiplier for substream jumps
   private static final BigInteger STREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.valueOf(STREAM_ADVANCE), BI_M); // for stream jumps
   
   /**
    * Constructs a new stream.
    */
   public MWC64k3a2() {
      Ig = nextSeed.clone();              // Save the start state of this stream.
      Bg = new long[4];                   // Allocate the substream state.

      resetStartStream();                 // Set Bg and current state from Ig.
      advanceStateFixedJump(nextSeed, STREAM_JUMP_MULTIPLIER);
   }

   /**
    * Constructs a new stream with a name.
    *
    * @param name stream name
    */
   public MWC64k3a2(String name) {
      this();                            
      this.name = name;                  
   }

   /**
    * Sets the package seed for the next created stream.
    *
    * @param seed seed {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public static void setPackageSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      nextSeed = seed.clone();            // Copy seed to avoid external mutation.
   }

   /**
    * Sets the seed of this stream.
    *
    * @param seed seed {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      Ig = seed.clone();                  // Replace initial stream state.
      resetStartStream();                 // Restart stream from new seed.
   }

   /**
    * Returns the current state.
    *
    * @return current state {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public long[] getState() {
      return new long[] { x3, x2, x1, carry };
   }

   /**
    * Resets this stream to the beginning of its stream.
    */
   public void resetStartStream() {
      Bg[0] = Ig[0];                      // Substream start = stream start.
      Bg[1] = Ig[1];
      Bg[2] = Ig[2];
      Bg[3] = Ig[3];

      resetStartSubstream();              // Current state = substream start.
   }

   /**
    * Resets this stream to the beginning of its current substream.
    */
   public void resetStartSubstream() {
      x3 = Bg[0];                         // Restore x_{n-3}.
      x2 = Bg[1];                         // Restore x_{n-2}.
      x1 = Bg[2];                         // Restore x_{n-1}.
      carry = Bg[3];                      // Restore carry.
   }

   /**
    * Moves this stream to the beginning of the next substream.
    */
   public void resetNextSubstream() {
      advanceStateFixedJump(Bg, SUBSTREAM_JUMP_MULTIPLIER);
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

      long low2 = A2 * x2;                  
      long high2 = Math.unsignedMultiplyHigh(A2, x2);

      long low3 = A3 * x3;               
      long high3 = Math.unsignedMultiplyHigh(A3, x3);

      long low = low2 + low3;
      long overflow1 = Long.compareUnsigned(low, low2) < 0 ? 1L : 0L;

      long lowWithCarry = low + carry;
      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = high2 + high3 + overflow1 + overflow2; // we can replace high2,3,overflow1,2 directly here, kept for readability 

      x3 = x2;
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
    * Another possibility to avoid returning 0 ?
    */
//   protected double nextValue2() {
//      return ((nextNumber() >>> 11) + 0.5) * NORM53;
//   }
   

   /**
    * Returns a random long in [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random long in [i, j]
    */
   
   // This method implements the "unbiased bounded integer generation" algorithm used by java.util.Random.nextint.
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
   

   // return a block of b bits (int)
   public long nextBitsLong(int b) {
      if (b < 0 || b > 63) {
         throw new IllegalArgumentException("b must be between 0 and 63");
      }

      if (b == 0) {
         return 0L;
      }

      long z = nextNumber();

      return z >>> (64 - b);
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

      sb.append("The current state of MWC64k3a2");

      if (name != null && name.length() > 0)
         sb.append(" ").append(name);

      sb.append(" is: { ");
      sb.append(Long.toUnsignedString(x3)).append(", ");
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

      sb.append("MWC64k3a2 stream");

      if (name != null && name.length() > 0)
         sb.append(" ").append(name);

      sb.append(":").append(nl);

      sb.append(" Ig = { ")
        .append(Long.toUnsignedString(Ig[0])).append(", ")
        .append(Long.toUnsignedString(Ig[1])).append(", ")
        .append(Long.toUnsignedString(Ig[2])).append(", ")
        .append(Long.toUnsignedString(Ig[3])).append(" }").append(nl);

      sb.append(" Bg = { ")
        .append(Long.toUnsignedString(Bg[0])).append(", ")
        .append(Long.toUnsignedString(Bg[1])).append(", ")
        .append(Long.toUnsignedString(Bg[2])).append(", ")
        .append(Long.toUnsignedString(Bg[3])).append(" }").append(nl);

      sb.append(" Cg = { ")
        .append(Long.toUnsignedString(x3)).append(", ")
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
   public MWC64k3a2 clone() {
      MWC64k3a2 copy = (MWC64k3a2) super.clone();

      copy.Ig = Ig.clone();               // Copy stream-start state.
      copy.Bg = Bg.clone();               // Copy substream-start state.

      return copy;
   }

   /**
    * Checks if a seed is usable.
    *
    * @param seed seed to check
    */
   private static final long MAX_CARRY = A2 + A3 - 1L;

   private static void checkSeed(long[] seed) {
      if (seed == null)
         throw new NullPointerException("Seed must not be null.");

      if (seed.length != 4)
         throw new IllegalArgumentException("Seed must contain 4 values.");

      if (seed[3] < 0L || seed[3] > MAX_CARRY)
         throw new IllegalArgumentException(
               "The carry must be in [0, " + MAX_CARRY + "].");

      if (seed[0] == 0L && seed[1] == 0L && seed[2] == 0L && seed[3] == 0L)
         throw new IllegalArgumentException("The all-zero state is not allowed.");

      if (seed[0] == -1L && seed[1] == -1L && seed[2] == -1L && seed[3] == MAX_CARRY)
         throw new IllegalArgumentException(
               "The all-ones/max-carry state is not allowed.");
   }

   /**
    * Advances by a fixed jump size predefined by jumpMultiplier
    *
    * The state is {x_{n-3}, x_{n-2}, x_{n-1}, carry}.
    *
    * @param state state to advance
    * @param jumpMultiplier precomputed constant for the jump size 
    */
   
   private static void advanceStateFixedJump(long[] state, BigInteger jumpMultiplier) {
      BigInteger bigX3 = toUnsignedBigInt(state[0]);
      BigInteger bigX2 = toUnsignedBigInt(state[1]);
      BigInteger bigX1 = toUnsignedBigInt(state[2]);
      BigInteger bigCr  = toUnsignedBigInt(state[3]); // we can use valueoff: carry is positive ?

      // y = x3 + x2*b + (x1 - A2*x3)*b^2 + c*b^3, here shift is used instead of *b
      BigInteger y = bigX1.subtract(BI_A2.multiply(bigX3));

      y = bigCr.shiftLeft(192)
                   .add(y.shiftLeft(128))
                   .add(bigX2.shiftLeft(64))
                   .add(bigX3);

      // One modular reduction is enough here: (y mod m) * J mod m = y * J mod m
      y = y.multiply(jumpMultiplier).mod(BI_M);

      // // new x3 = low 64 bits of y
      long newX3 = y.longValue();

      // y = floor(y / 2^64)
      y = y.shiftRight(64);

      long newX2 = y.longValue();

      y = y.shiftRight(64);

      y = y.add(BI_A2.multiply(toUnsignedBigInt(newX3))); // y = y + A2*newX3 = newX1 + newCarry*b

      long newX1 = y.longValue();

      // new carry = floor(y / 2^64)
      long newCarry = y.shiftRight(64).longValue();

      state[0] = newX3;
      state[1] = newX2;
      state[2] = newX1;
      state[3] = newCarry;
   }
   
   /**
    * Advances the current stream state by n steps.
    *
    * @param n number of steps
    */
   void advanceStateByJump(long n) {
      if (n < 0) {
         throw new IllegalArgumentException("Jump step n cannot be negative.");
      }
      if (n == 0) {
         return;
      }

      long[] state = getState();

      BigInteger jumpMultiplier =
            BI_B_INV.modPow(BigInteger.valueOf(n), BI_M);

      advanceStateFixedJump(state, jumpMultiplier);

      x3 = state[0];
      x2 = state[1];
      x1 = state[2];
      carry = state[3];
   }
   
   //public method for nextnumber test
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