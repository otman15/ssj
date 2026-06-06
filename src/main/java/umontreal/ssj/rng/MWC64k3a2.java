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
   
   // State components x_{n-1}, x_{n-2}, x_{n-3} and c_{n-1} interpreted as unsigned 64-bit. 
   private long x1, x2, x3, carry;
   //Second coefficient a2. 
   private static final long A2 = 184698970548483715L;
   //Third coefficient a3. 
   private static final long A3 = 6028691832887L;   
//   private static final long A2 = 0x320fbe97bef0f95L, A3 = 0x4a1849ec18bfa6L; // for jumps comparaison with cpp

   //2^(-53), used to convert 53 random bits to a double. 
   private static final double NORM53 = 0x1.0p-53;
   private static final int STREAM_ADVANCE_EXPONENT = 169;
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 118;

   //Seed used for the next created stream: {x_{n-3}, x_{n-2}, x_{n-1}, carry}. 
   private static long[] nextSeed = {1L, 3L, 4L, 5L};
   // Initial state of this stream. 
   private long[] Ig;
   // Beginning state of the current substream of stream. 
   private long[] Bg;
   // LCG state corresponding to nextSeed.
   private static BigInteger nextSeedY;
   // LCG state at the beginning of this stream.
   private BigInteger IgY;
   // LCG state at the beginning of the current substream.
   private BigInteger BgY;
  
   
   // Precomputed BigInteger constants for the MWC-to-LCG jump transformation.
    
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
   private static final BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); // b^2
   private static final BigInteger BI_B3 = BigInteger.ONE.shiftLeft(192); // b^3
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2);
   private static final BigInteger BI_A3 = BigInteger.valueOf(A3);
   private static final BigInteger BI_M = BI_A3.multiply(BI_B).add(BI_A2).multiply(BI_B).multiply(BI_B).subtract(BigInteger.ONE); // m = a3*b^3 + a2*b^2 - 1
   private static final BigInteger BI_B_INV = BI_B.modInverse(BI_M); // b^(-1) mod m
   private static final BigInteger BI_MAP_X3 = BigInteger.ONE.subtract(BI_A2.multiply(BI_B2)); // 1 - a2*b^2
   
   private static final BigInteger STREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT), BI_M);
   private static final BigInteger SUBSTREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT), BI_M);

   static {
      nextSeedY = stateToLCG(nextSeed);
   }
   
   /**
    * Constructs a new stream.
    */
   public MWC64k3a2() {
      Ig = nextSeed.clone();              // Save the start state of this stream.
      IgY = nextSeedY;                    // Save the corresponding LCG state.
      Bg = new long[4];                   // Allocate the substream state.

      resetStartStream();                 // Set Bg and current state from Ig.
      nextSeedY = advanceLCGState(nextSeedY, STREAM_JUMP_MULTIPLIER, nextSeed);
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
      nextSeedY = stateToLCG(nextSeed);   // Store the corresponding LCG state.
   }

   /**
    * Sets the seed of this stream.
    *
    * @param seed seed {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      Ig = seed.clone();                  // Replace initial stream state.
      IgY = stateToLCG(Ig);               // Store the corresponding LCG state.
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
      BgY = IgY;                          // LCG state of the current substream start.

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
      BgY = advanceLCGState(BgY, SUBSTREAM_JUMP_MULTIPLIER, Bg);
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
    * Returns the next uniform in [0, 1).
    *
    * <p>
    * This method keeps the top 53 bits of the 64-bit output and multiplies
    * by 2^(-53). It may return 0.0, but it never returns 1.0.
    * The RandomStream interface provides nextDoubleNonzero() when a nonzero
    * uniform is needed.
    * </p>
    *
    * <pre>
    * return (nextNumber() >>> 11) * 2^(-53)
    * </pre>
    *
    * @return the next uniform in [0, 1)
    */
   protected double nextValue() {
      return (nextNumber() >>> 11) * NORM53;
   }
   
   /**
    * Returns a random long in the inclusive range {@code [i, j]}.
    *
    * Uses 63 random bits and rejection sampling when the range size fits in a
    * positive long. For larger ranges, it samples full 64-bit values until one
    * falls inside the interval.
    *
    * @param i lower bound, inclusive
    * @param j upper bound, inclusive
    * @return a random long in {@code [i, j]}
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
  
   /**
    * Returns the top b bits of the next 64-bit output.
    *
    * The selected bits are shifted to the right, so the result is stored in the
    * least significant b bits of the returned long.
    *
    * @param b number of bits to return, between 1 and 64
    * @return the top b bits of the next 64-bit output
    */
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
      copy.IgY = IgY;                     // BigInteger is immutable.
      copy.BgY = BgY;                     // BigInteger is immutable.

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
    * Maps a MWC state to the corresponding LCG state.
    *
    * @param state state {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    * @return corresponding LCG state
    */
   private static BigInteger stateToLCG(long[] state) {
      BigInteger stateX3 = toUnsignedBigInt(state[0]);
      BigInteger stateX2 = toUnsignedBigInt(state[1]);
      BigInteger stateX1 = toUnsignedBigInt(state[2]);
      BigInteger stateCarry = BigInteger.valueOf(state[3]);

      return BI_MAP_X3.multiply(stateX3)
            .add(BI_B.multiply(stateX2))
            .add(BI_B2.multiply(stateX1))
            .add(BI_B3.multiply(stateCarry))
            .mod(BI_M);
   }

   /**
    * Recovers a MWC state from the corresponding LCG state.
    *
    * @param y corresponding LCG state
    * @param state state array to update
    */
   private static void stateFromLCG(BigInteger y, long[] state) {
      BigInteger sigma = y;

      long newX3 = sigma.longValue();
      sigma = sigma.shiftRight(64);

      long newX2 = sigma.longValue();
      sigma = sigma.shiftRight(64);

      sigma = sigma.add(BI_A2.multiply(toUnsignedBigInt(newX3)));

      long newX1 = sigma.longValue();
      long newCarry = sigma.shiftRight(64).longValue();

      state[0] = newX3;
      state[1] = newX2;
      state[2] = newX1;
      state[3] = newCarry;
   }

   /**
    * Advances a stored LCG state by a fixed multiplier and updates the MWC state.
    *
    * @param y current LCG state
    * @param jumpMultiplier fixed jump multiplier
    * @param state MWC state to update
    * @return advanced LCG state
    */
   private static BigInteger advanceLCGState(BigInteger y, BigInteger jumpMultiplier, long[] state) {
      BigInteger newY = jumpMultiplier.multiply(y).mod(BI_M);
      stateFromLCG(newY, state);
      return newY;
   }
   
   /**
    * Advances the current stream state by n steps.
    *
    * This method is used for a general jump size n. It does not use the
    * precomputed fixed-jump constants, because those constants are useful only
    * for fixed stream/substream jumps.
    *
    * The method first maps the current MWC state to the equivalent LCG state,
    * applies the LCG jump, then converts the result back to the MWC state.
    *
    * @param n number of steps to jump
    */
   public void advanceStateByJump(BigInteger n) {
      if (n.signum() < 0L)
         throw new IllegalArgumentException("Jump step n cannot be negative.");

      if (n.signum() == 0)
         return;

      BigInteger stateX3 = toUnsignedBigInt(x3);
      BigInteger stateX2 = toUnsignedBigInt(x2);
      BigInteger stateX1 = toUnsignedBigInt(x1);
      BigInteger stateCarry = BigInteger.valueOf(carry);

//       Map the current MWC state to the equivalent LCG state:
//        y =  (1 - A2*b^2)*x3 + b*x2 + b^2*x1 + b^3*carry mod m
      BigInteger y =
            BI_MAP_X3.multiply(stateX3)
          .add(BI_B.multiply(stateX2))
          .add(BI_B2.multiply(stateX1))
          .add(BI_B3.multiply(stateCarry))
          .mod(BI_M);

      // Apply the LCG jump: y_new = (b^(-1))^n * y mod m.
      BigInteger sigma =
            BI_B_INV.modPow(n, BI_M)
          .multiply(y)
          .mod(BI_M);

//      Convert the jumped LCG state back to the MWC state. For MWC64k3a2, A1 = 0, so the inverse reconstruction is:
//        newX3 = low 64 bits of sigma
//        newX2 = next 64 bits
//        then correct the remaining part with A2*newX3.

      long newX3 = sigma.longValue();
      sigma = sigma.shiftRight(64);

      long newX2 = sigma.longValue();
      sigma = sigma.shiftRight(64);

      sigma = sigma.add(BI_A2.multiply(toUnsignedBigInt(newX3)));

      long newX1 = sigma.longValue();
      long newCarry = sigma.shiftRight(64).longValue();

      x3 = newX3;
      x2 = newX2;
      x1 = newX1;
      carry = newCarry;
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