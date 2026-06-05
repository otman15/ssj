package umontreal.ssj.rng;
import java.math.BigInteger;


/**
 * This generator uses Math.unsignedMultiplyHigh which requires JDK 18 or later.
 * MWC generator with base b = 2^64, order k = 3, and coefficients:
 *
 * <pre>
 * a0 = -1
 * a1 = 42677777384320164
 * a2 = 224559258625446056
 * a3 = 89699660373453
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
 * The state is stored as:
 *
 * <pre>
 * {x_{n-3}, x_{n-2}, x_{n-1}, carry}
 * </pre>
 */
public class MWC64k3a3 extends RandomStreamBase {
   
   private static final long serialVersionUID = 20260518L;
   
   /** State components x_{n-1}, x_{n-2}, x_{n-3} and c_{n-1} interpreted as unsigned 64-bit. */
   private long x1, x2, x3, carry;

   /** First coefficient a1. */
   private static final long A1 = 42677777384320164L;
   /** Second coefficient a2. */
   private static final long A2 = 224559258625446056L;
   /** Third coefficient a3. */
   private static final long A3 = 89699660373453L;

   /** 2^(-53), used to convert 53 random bits to a double. */
   private static final double NORM53 = 0x1.0p-53;

   /** Stream spacing: 2^173 generated values. */
   private static final int STREAM_ADVANCE_EXPONENT = 173;
   /** Substream spacing: 2^122 generated values. */
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 122;

   /** Seed used for the next created stream: {x_{n-3}, x_{n-2}, x_{n-1}, carry}. */
   private static long[] nextSeed = {1L, 3L, 4L, 5L};

   /** Initial state of this stream. */
   private long[] Ig;

   /** Beginning state of the current substream of stream. */
   private long[] Bg;
  
   /**
    * Precomputed BigInteger constants for the MWC-to-LCG jump transformation.
    */
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
   private static final BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); // b^2
   private static final BigInteger BI_B3 = BigInteger.ONE.shiftLeft(192); // b^3
   private static final BigInteger BI_A1 = BigInteger.valueOf(A1);
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2);
   private static final BigInteger BI_A3 = BigInteger.valueOf(A3);
   private static final BigInteger BI_M = BI_A3.multiply(BI_B3)
         .add(BI_A2.multiply(BI_B2))
         .add(BI_A1.multiply(BI_B))
         .subtract(BigInteger.ONE); // m = a3*b^3 + a2*b^2 + a1*b - 1
   private static final BigInteger BI_B_INV = BI_B.modInverse(BI_M); // b^(-1) mod m
   private static final BigInteger BI_MAP_X3 = BigInteger.ONE
         .subtract(BI_A1.multiply(BI_B))
         .subtract(BI_A2.multiply(BI_B2)); // 1 - a1*b - a2*b^2
   private static final BigInteger BI_MAP_X2 = BI_B.subtract(BI_A1.multiply(BI_B2)); // b - a1*b^2
   
   private static final BigInteger STREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT), BI_M); // J = (b^(-1))^(2^STREAM_ADVANCE_EXPONENT) mod m
   private static final BigInteger SUBSTREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT), BI_M); // J = (b^(-1))^(2^SUBSTREAM_ADVANCE_EXPONENT) mod m
   private static final BigInteger STREAM_K_X3 = STREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X3).mod(BI_M); // K_x3 = J*(1 - a1*b - a2*b^2) mod m
   private static final BigInteger STREAM_K_X2 = STREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X2).mod(BI_M); // K_x2 = J*(b - a1*b^2) mod m
   private static final BigInteger STREAM_K_X1 = STREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M); // K_x1 = J*b^2 mod m
   private static final BigInteger STREAM_K_C = STREAM_JUMP_MULTIPLIER.multiply(BI_B3).mod(BI_M); // K_c = J*b^3 mod m
   private static final BigInteger SUBSTREAM_K_X3 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X3).mod(BI_M); // K_x3 = J*(1 - a1*b - a2*b^2) mod m
   private static final BigInteger SUBSTREAM_K_X2 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X2).mod(BI_M); // K_x2 = J*(b - a1*b^2) mod m
   private static final BigInteger SUBSTREAM_K_X1 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M); // K_x1 = J*b^2 mod m
   private static final BigInteger SUBSTREAM_K_C = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B3).mod(BI_M); // K_c = J*b^3 mod m
   
   /**
    * Constructs a new stream.
    */
   public MWC64k3a3() {
      Ig = nextSeed.clone();              // Save the start state of this stream.
      Bg = new long[4];                   // Allocate the substream state.

      resetStartStream();                 // Set Bg and current state from Ig.
      advanceStateFixedJump(nextSeed, STREAM_K_X3, STREAM_K_X2, STREAM_K_X1, STREAM_K_C);
   }

   /**
    * Constructs a new stream with a name.
    *
    * @param name stream name
    */
   public MWC64k3a3(String name) {
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
      advanceStateFixedJump(Bg, SUBSTREAM_K_X3, SUBSTREAM_K_X2, SUBSTREAM_K_X1, SUBSTREAM_K_C);
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

      long low3 = A3 * x3;
      long high3 = Math.unsignedMultiplyHigh(A3, x3);

      long low12 = low1 + low2;
      long overflow1 = Long.compareUnsigned(low12, low1) < 0 ? 1L : 0L;

      long low = low12 + low3;
      long overflow2 = Long.compareUnsigned(low, low12) < 0 ? 1L : 0L;

      long lowWithCarry = low + carry;
      long overflow3 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = high1 + high2 + high3 + overflow1 + overflow2 + overflow3;

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
    * Returns a random long in [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random long in [i, j]
    */
   public long nextLong(long i, long j) {
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");

      long n = j - i + 1L;                

      if (n > 0L) {                       
         long r = nextNumber() >>> 1;     
         long m = n - 1L;                 
         
         if ((n & m) == 0L)          
            return i + (r & m);           
       
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
   
   // LRSR version: range only up to 2^62.
   public long nextLongssj(long i, long j) { 
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");
      long d = j - i + 1;
      long q = 0x4000000000000000L / d;  // 0x4000000000000000L = 2^{62} in hexadecimal.
      long r = 0x4000000000000000L % d;
      long res;
      do {
         res = nextNumber() >>> 2;        // Integer smaller than 2^{62}.
      } while (res >= 0x4000000000000000L - r);

      return i + (res / q);
   }

   // Return a block of b bits.
   public long nextBitsLong(int b) {
      if (b < 0 || b > 63) {
         throw new IllegalArgumentException("b must be between 0 and 63");
      }

      if (b == 0) {
         return 0L;
      }

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

      sb.append("The current state of MWC64k3a3");

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

      sb.append("MWC64k3a3 stream");

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
   public MWC64k3a3 clone() {
      MWC64k3a3 copy = (MWC64k3a3) super.clone();

      copy.Ig = Ig.clone();               // Copy stream-start state.
      copy.Bg = Bg.clone();               // Copy substream-start state.

      return copy;
   }

   /**
    * Checks if a seed is usable.
    *
    * @param seed seed to check
    */
   private static final long MAX_CARRY = A1 + A2 + A3 - 1L;

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
    * Advances by a fixed jump size using precomputed constants.
    *
    * The state is {x_{n-3}, x_{n-2}, x_{n-1}, carry}.
    *
    * @param state state to advance
    * @param kX3 x3 multiplier
    * @param kX2 x2 multiplier
    * @param kX1 x1 multiplier
    * @param kCarry carry multiplier
    */
   private static void advanceStateFixedJump(long[] state, BigInteger kX3, BigInteger kX2, BigInteger kX1, BigInteger kCarry) {
      BigInteger stateX3 = toUnsignedBigInt(state[0]);
      BigInteger stateX2 = toUnsignedBigInt(state[1]);
      BigInteger stateX1 = toUnsignedBigInt(state[2]);
      BigInteger stateCarry = BigInteger.valueOf(state[3]);
      
      BigInteger sigma =
            kX3.multiply(stateX3)
          .add(kX2.multiply(stateX2))
          .add(kX1.multiply(stateX1))
          .add(kCarry.multiply(stateCarry))
          .mod(BI_M);
      
      long newX3 = sigma.longValue();
      sigma = sigma.shiftRight(64);
      
      sigma = sigma.add(BI_A1.multiply(toUnsignedBigInt(newX3)));
      long newX2 = sigma.longValue();
      sigma = sigma.shiftRight(64);
      
      sigma = sigma.add(BI_A1.multiply(toUnsignedBigInt(newX2)))
            .add(BI_A2.multiply(toUnsignedBigInt(newX3)));
      long newX1 = sigma.longValue();
      long newCarry = sigma.shiftRight(64).longValue();
      
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
   public void advanceStateByJump(long n) {
      if (n < 0) {
         throw new IllegalArgumentException("Jump step n cannot be negative.");
      }
      if (n == 0) {
         return;
      }

      long[] state = getState();

      BigInteger jumpMultiplier =
            BI_B_INV.modPow(BigInteger.valueOf(n), BI_M);

      BigInteger kX3 =
            jumpMultiplier.multiply(BI_MAP_X3).mod(BI_M);

      BigInteger kX2 =
            jumpMultiplier.multiply(BI_MAP_X2).mod(BI_M);

      BigInteger kX1 =
            jumpMultiplier.multiply(BI_B2).mod(BI_M);

      BigInteger kCarry =
            jumpMultiplier.multiply(BI_B3).mod(BI_M);

      advanceStateFixedJump(state, kX3, kX2, kX1, kCarry);

      x3 = state[0];
      x2 = state[1];
      x1 = state[2];
      carry = state[3];
   }
   
   // Public method for nextNumber tests.
   public long nextRaw() {
      return nextNumber();
   }
   
   /**
    * Converts an unsigned 64-bit long to a positive BigInteger.
    */
   private static BigInteger toUnsignedBigInt(long value) {
      if (value >= 0) {
         return BigInteger.valueOf(value);
      } else {
         // Handle negative long bit patterns as unsigned 64-bit values.
         return BigInteger.valueOf(value & 0x7FFFFFFFFFFFFFFFL).setBit(63);
      }
   }
}
