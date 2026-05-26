package umontreal.ssj.rng;
import java.math.BigInteger;

/**
 * MWC64k3a2 generator.
 *
 * <p>This is a multiply-with-carry generator with:</p>
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
 * <p>The recurrence is:</p>
 *
 * <pre>
 * t   = a1*x_{n-1} + a2*x_{n-2} + a3*x_{n-3} + c_{n-1}
 * x_n = t mod 2^64
 * c_n = floor(t / 2^64)
 * </pre>
 *
 * <p>Since a1 = 0, the implemented recurrence is:</p>
 *
 * <pre>
 * t   = a2*x_{n-2} + a3*x_{n-3} + c_{n-1}
 * x_n = low 64 bits of t
 * c_n = high 64 bits of t
 * </pre>
 *
 * <p>The state is stored as:</p>
 *
 * <pre>
 * {x_{n-3}, x_{n-2}, x_{n-1}, carry}
 * </pre>
 */
public class MWC64k3a2 extends RandomStreamBase {
   private static final long serialVersionUID = 20260518L;

   /** Coefficient a2. The coefficient a1 is zero, so it is not stored. */
   private static final long A2 = 184698970548483715L;

   /** Coefficient a3. */
   private static final long A3 = 6028691832887L;

   /** 2^(-53), used to convert 53 random bits to a double. */
   private static final double NORM53 = 0x1.0p-53;

   /** Temporary stream spacing. 1L << 20 means 2^20 steps. */
   private static final long STREAM_ADVANCE = 1L << 20;

   /** Temporary substream spacing. 1L << 10 means 2^10 steps. */
   private static final long SUBSTREAM_ADVANCE = 1L << 10;

   /** Seed for the next created stream: {x_{n-3}, x_{n-2}, x_{n-1}, carry}. */
   private static long[] nextSeed = { 1L, 3L, 4L, 5L };

   /** Initial state of this stream. */
   private long[] Ig;

   /** Initial state of the current substream. */
   private long[] Bg;

   /** State component x_{n-3}, interpreted as unsigned 64-bit. */
   private long x0;

   /** State component x_{n-2}, interpreted as unsigned 64-bit. */
   private long x1;

   /** State component x_{n-1}, interpreted as unsigned 64-bit. */
   private long x2;

   /** Carry c_{n-1}. */
   private long carry;
   
   /**
   * BigInteger value of b = 2^64.
   */
  private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64);

  /**
   * BigInteger value of a2.
   */
  private static final BigInteger BI_A2 = BigInteger.valueOf(A2);

  /**
   * BigInteger value of a3.
   */
  private static final BigInteger BI_A3 = BigInteger.valueOf(A3);

  /**
   * LCG modulus:
   *
   * m = -1 + A2*b^2 + A3*b^3
   */
  private static final BigInteger BI_M =
        BI_A3.multiply(BI_B).add(BI_A2).multiply(BI_B).multiply(BI_B)
              .subtract(BigInteger.ONE);

  /**
   * Modular inverse of b modulo m.
   */
  private static final BigInteger BI_B_INV = BI_B.modInverse(BI_M);

   /**
    * Constructs a new stream.
    */
   public MWC64k3a2() {
      Ig = nextSeed.clone();              // Save this stream initial state.
      Bg = new long[4];                   // Allocate substream state.

      resetStartStream();                 // Set current state from Ig.

      advanceState(nextSeed, STREAM_ADVANCE); // Move package seed for next stream.
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
      checkSeed(seed);
      nextSeed = seed.clone();
   }

   /**
    * Sets the seed of this stream.
    *
    * @param seed seed {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);
      Ig = seed.clone();
      resetStartStream();
   }

   /**
    * Returns the current state.
    *
    * @return current state {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public long[] getState() {
      return new long[] { x0, x1, x2, carry };
   }

   /**
    * Resets this stream to the beginning of its stream.
    */
   public void resetStartStream() {
      Bg[0] = Ig[0];                      // Copy x_{n-3}.
      Bg[1] = Ig[1];                      // Copy x_{n-2}.
      Bg[2] = Ig[2];                      // Copy x_{n-1}.
      Bg[3] = Ig[3];                      // Copy carry.

      resetStartSubstream();
   }

   /**
    * Resets this stream to the beginning of the current substream.
    */
   public void resetStartSubstream() {
      x0 = Bg[0];                         // Restore x_{n-3}.
      x1 = Bg[1];                         // Restore x_{n-2}.
      x2 = Bg[2];                         // Restore x_{n-1}.
      carry = Bg[3];                      // Restore carry.
   }

   /**
    * Moves this stream to the beginning of the next substream.
    */
   public void resetNextSubstream() {
      advanceState(Bg, SUBSTREAM_ADVANCE);
      resetStartSubstream();
   }

   /**
    * Generates the next 64-bit MWC output.
    *
    * @return next x_n, stored in a signed long but interpreted as unsigned
    */
//   private long nextNumber() {
//      long lo2 = A2 * x1;                 // Low 64 bits of A2*x_{n-2}.
//      long hi2 = unsignedMultiplyHighPositive(A2, x1); // High 64 bits.
//      //long hi2 = Math.unsignedMultiplyHigh(A2, x1); // High 64 bits of signed multiply.
//
//      long lo3 = A3 * x0;                 // Low 64 bits of A3*x_{n-3}.
//      long hi3 = unsignedMultiplyHighPositive(A3, x0); // High 64 bits.
//      //long hi3 = Math.unsignedMultiplyHigh(A3, x0); // High 64 bits of signed multiply.
//
//      long low = lo2 + lo3;               // Low 64 bits of product sum.
//      long overflow1 = Long.compareUnsigned(low, lo2) < 0 ? 1L : 0L;
//
//      long lowWithCarry = low + carry;    // Add old carry to low part.
//      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;
//
//      long high = hi2 + hi3 + overflow1 + overflow2; // New carry.
//
//      x0 = x1;                            // x_{n-2} becomes new x_{n-3}.
//      x1 = x2;                            // x_{n-1} becomes new x_{n-2}.
//      x2 = lowWithCarry;                  // x_n becomes new x_{n-1}.
//      carry = high;                       // Store c_n.
//
//      return x2;
//   }
   
   /**
    * Generates one MWC step and returns the old x_{n-1},
    * to match the C++ speed-test convention.
    *
    * @return old x_{n-1}, interpreted as unsigned 64-bit
    */
   private long nextNumber() {
      long out = x2;                         // Java x2 corresponds to C++ x1.

      long lo2 = A2 * x1;                    // Low 64 bits of A2*x_{n-2}.
      long hi2 = unsignedMultiplyHighPositive(A2, x1);

      long lo3 = A3 * x0;                    // Low 64 bits of A3*x_{n-3}.
      long hi3 = unsignedMultiplyHighPositive(A3, x0);

      long low = lo2 + lo3;
      long overflow1 = Long.compareUnsigned(low, lo2) < 0 ? 1L : 0L;

      long lowWithCarry = low + carry;
      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = hi2 + hi3 + overflow1 + overflow2;

      x0 = x1;
      x1 = x2;
      x2 = lowWithCarry;
      carry = high;

      return out;
   }

   /**
    * Returns the next uniform value in (0,1).
    *
    * <p>This follows the C-style conversion:</p>
    *
    * <pre>
    * block53 = nextNumber() >>> 11
    * if block53 == 0, try again
    * return block53 * 2^(-53)
    * </pre>
    *
    * @return next uniform value in (0,1)
    */
   protected double nextValue() {
      long block53;

      do {
         block53 = nextNumber() >>> 11;   // Keep top 53 bits.
      } while (block53 == 0L);            // Avoid returning exactly 0.

      return block53 * NORM53;
   }
   
// protected double nextValue() {
//   long block53;
//
//   do {
//      block53 = nextNumber() >>> 11;  // top 53 bits
//   } while (block53 == 0L);           // reject 0 like the C code
//
//   return block53 * 0x1.0p-53;        // block53 / 2^53
//}

   /**
    * Returns a random long in the interval [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random long in [i, j]
    */
   public long nextLong(long i, long j) {
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");

      long n = j - i + 1L;                // Number of possible values.

      if (n > 0L) {                       // Normal case: range size fits in long.
         long r = nextNumber() >>> 1;     // Use 63 nonnegative random bits.
         long m = n - 1L;

         if ((n & m) == 0L)               // Fast case: n is a power of 2.
            return i + (r & m);

         long u = r;
         while (u + m - (r = u % n) < 0L)
            u = nextNumber() >>> 1;       // Reject biased candidates.

         return i + r;
      }

      long r;                             // Huge interval case.
      do {
         r = nextNumber();
      } while (r < i || r > j);

      return r;
   }

   /**
    * Returns a random int in the interval [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random int in [i, j]
    */
   public int nextInt(int i, int j) {
      return (int) nextLong(i, j);
   }

   /**
    * Fills part of an array with random long values in [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @param u output array
    * @param start first index to fill
    * @param n number of values to generate
    */
   public void nextArrayOfLong(long i, long j, long[] u, int start, int n) {
      if (u == null)
         throw new NullPointerException("The array must be initialized.");

      if (start < 0)
         throw new IndexOutOfBoundsException("start must be non-negative.");

      if (n < 0)
         throw new IllegalArgumentException("n must be non-negative.");

      if (u.length < start + n)
         throw new IndexOutOfBoundsException("The array is too small.");

      for (int p = start; p < start + n; p++)
         u[p] = nextLong(i, j);
   }

   /**
    * Sets antithetic mode.
    *
    * @param anti true to return 1-u, false to return u
    */
   public void setAntithetic(boolean anti) {
      this.anti = anti;
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
      sb.append(Long.toUnsignedString(x0)).append(", ");
      sb.append(Long.toUnsignedString(x1)).append(", ");
      sb.append(Long.toUnsignedString(x2)).append(", ");
      sb.append(Long.toUnsignedString(carry)).append(" }");

      return sb.toString();
   }

   /**
    * Returns the stream, substream, and current states.
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
        .append(Long.toUnsignedString(x0)).append(", ")
        .append(Long.toUnsignedString(x1)).append(", ")
        .append(Long.toUnsignedString(x2)).append(", ")
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

      copy.Ig = Ig.clone();
      copy.Bg = Bg.clone();

      return copy;
   }

   /**
    * Checks whether a seed is usable.
    *
    * @param seed seed to check
    */
   private static void checkSeed(long[] seed) {
      if (seed == null)
         throw new NullPointerException("Seed must not be null.");

      if (seed.length < 4)
         throw new IllegalArgumentException("Seed must contain 4 values.");

      if (seed[0] == 0L && seed[1] == 0L && seed[2] == 0L && seed[3] == 0L)
         throw new IllegalArgumentException("The all-zero state is not allowed.");
   }

   /**
    * Advances a given state by one MWC step.
    *
    * @param state state {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   private static void advanceState(long[] state) {
      long lo2 = A2 * state[1];           // Low 64 bits of A2*x_{n-2}.
      long hi2 = unsignedMultiplyHighPositive(A2, state[1]);

      long lo3 = A3 * state[0];           // Low 64 bits of A3*x_{n-3}.
      long hi3 = unsignedMultiplyHighPositive(A3, state[0]);

      long low = lo2 + lo3;               // Add low product parts.
      long overflow1 = Long.compareUnsigned(low, lo2) < 0 ? 1L : 0L;

      long lowWithCarry = low + state[3]; // Add old carry.
      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = hi2 + hi3 + overflow1 + overflow2;

      state[0] = state[1];                // x_{n-2} becomes x_{n-3}.
      state[1] = state[2];                // x_{n-1} becomes x_{n-2}.
      state[2] = lowWithCarry;            // x_n becomes x_{n-1}.
      state[3] = high;                    // c_n becomes carry.
   }

//   /**
//    * Advances a given state by n MWC steps.
//    *
//    * @param state state to advance
//    * @param n number of steps
//    */
//   private static void advanceState(long[] state, long n) {
//      for (long p = 0; p < n; p++)
//         advanceState(state);
//   }
/**
 * Advances a given state by n steps using the LCG representation.
 *
 * State order:
 * {x_{n-3}, x_{n-2}, x_{n-1}, carry}
 *
 * @param state state to advance
 * @param n number of steps
 */
private static void advanceState(long[] state, long n) {
   if (n < 0)
      throw new IllegalArgumentException("Jump step n cannot be negative.");

   if (n == 0)
      return;

   if (n == 1) {
      advanceState(state);
      return;
   }

   BigInteger x0 = toUnsignedBigInt(state[0]);
   BigInteger x1 = toUnsignedBigInt(state[1]);
   BigInteger x2 = toUnsignedBigInt(state[2]);
   BigInteger c = toUnsignedBigInt(state[3]);

   /*
    * MWCtoLCGStateLagk for k = 3, a0 = -1, a1 = 0:
    *
    * y = x0
    *   + x1*b
    *   + (x2 - A2*x0)*b^2
    *   + c*b^3      mod m
    */
   BigInteger y = x2.subtract(BI_A2.multiply(x0));

   y = c.multiply(BI_B).add(y)
        .multiply(BI_B).add(x1)
        .multiply(BI_B).add(x0)
        .mod(BI_M);

   /*
    * Jump ahead by n MWC steps.
    */
   BigInteger jumpMultiplier = BI_B_INV.modPow(BigInteger.valueOf(n), BI_M);
   y = y.multiply(jumpMultiplier).mod(BI_M);

   /*
    * LCGtoMWCStateLagk for k = 3, a0 = -1, a1 = 0.
    */
   BigInteger sigma = y;

   BigInteger newX0 = sigma.mod(BI_B);
   sigma = sigma.subtract(newX0).shiftRight(64);

   BigInteger newX1 = sigma.mod(BI_B);
   sigma = sigma.subtract(newX1).shiftRight(64);

   sigma = sigma.add(BI_A2.multiply(newX0));

   BigInteger newX2 = sigma.mod(BI_B);
   sigma = sigma.subtract(newX2).shiftRight(64);

   BigInteger newCarry = sigma;

   state[0] = newX0.longValue();
   state[1] = newX1.longValue();
   state[2] = newX2.longValue();
   state[3] = newCarry.longValue();
}
   
   /**
    * Advances the current object state by n steps using the BigInteger jump.
    *
    * @param n number of steps
    */
   void advanceStateByJump(long n) {
      long[] state = getState();

      advanceState(state, n);

      x0 = state[0];
      x1 = state[1];
      x2 = state[2];
      carry = state[3];
   }
   
   

   /**
    *  For Java versions less than 18, computes the high 64 bits of unsigned a*x.
    *
    * <p>This assumes a is positive. It is equivalent to
    * Math.unsignedMultiplyHigh(a, x) for this use case.</p>
    *
    * @param a positive multiplier
    * @param x unsigned 64-bit value stored in a long
    * @return high 64 bits of unsigned a*x
    */
   private static long unsignedMultiplyHighPositive(long a, long x) {
      long high = Math.multiplyHigh(a, x);

      if (x < 0)
         high += a;

      return high;
   }
   

/**
 * Converts an unsigned 64-bit long to a positive BigInteger.
 *
 * @param value long value interpreted as unsigned
 * @return positive BigInteger
 */
private static BigInteger toUnsignedBigInt(long value) {
   if (value >= 0)
      return BigInteger.valueOf(value);

   return BigInteger.valueOf(value & 0x7FFFFFFFFFFFFFFFL).setBit(63);
}

   ///////////////for test
   /**
    * Generates one raw value and advances the state by one step.
    *
    * @return raw output value
    */
   long nextRaw() {
      return nextNumber();
   }
}