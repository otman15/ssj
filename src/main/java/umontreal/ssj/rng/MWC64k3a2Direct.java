package umontreal.ssj.rng;
import java.io.Serializable;
import java.math.BigInteger;


/**
 * <p>
 * This generator uses Math.unsignedMultiplyHigh which requires JDK 18 or later.
 * </p>
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
public class MWC64k3a2Direct implements CloneableRandomStream, Serializable {

   private static final long serialVersionUID = 20260518L;

   private String name = null;
   protected boolean prec53 = false;
   protected boolean anti = false;

   // State components x_{n-1}, x_{n-2}, x_{n-3} and c_{n-1} interpreted as unsigned 64-bit.
   private long x1, x2, x3, carry;
   // Coefficients a2 and a3. This class currently works with positive signed Java long constants: A2 and A3.
   private static final long A2 = 184698970548483715L, A3 = 6028691832887L;

   private static final double NORM53 = 0x1.0p-53;
   private static double invtwo24 = 5.9604644775390625e-8;
   private static double EPSILON = 5.5511151231257827e-17;
   private static final int STREAM_ADVANCE_EXPONENT = 169;
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 118;

   // Seed used for the next created stream: {x_{n-3}, x_{n-2}, x_{n-1}, carry}.
   private static long[] nextSeed = {1L, 3L, 4L, 5L};
   // Montgomery LCG state corresponding to nextSeed, stored as four little-endian 64-bit limbs.
   private static long[] nextSeedYMont;
   // Initial state of this stream.
   private long[] Ig;
   // Montgomery LCG state corresponding to the beginning of this stream, stored as four little-endian 64-bit limbs.
   private long[] IgYMont;
   // Beginning state of the current substream of stream.
   private long[] Bg;
   // Montgomery LCG state corresponding to the beginning of the current substream, stored as four little-endian 64-bit limbs.
   private long[] BgYMont;

   // Precomputed BigInteger constants for the MWC-to-LCG jump transformation.
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); 
   private static final BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); 
   private static final BigInteger BI_B3 = BigInteger.ONE.shiftLeft(192); 
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2);
   private static final BigInteger BI_A3 = BigInteger.valueOf(A3);
   private static final BigInteger BI_M = BI_A3.multiply(BI_B).add(BI_A2).multiply(BI_B).multiply(BI_B).subtract(BigInteger.ONE); // m = a3*b^3 + a2*b^2 - 1
   // b * (A2*b + A3*b^2) = A2*b^2 + A3*b^3 is congruent to 1 modulo m.
   private static final BigInteger BI_B_INV = BI_A2.multiply(BI_B).add(BI_A3.multiply(BI_B2));
   // A3*b^3 is congruent to 1 - A2*b^2 modulo m.
   private static final BigInteger BI_MAP_X3 = BI_A3.multiply(BI_B3);

   // Montgomery radix R = b^4, used for static setup of Montgomery constants.
   private static final BigInteger BI_R = BigInteger.ONE.shiftLeft(256);

   // Limbs of m = A3*b^3 + (A2 - 1)*b^2 + (b - 1)*b + (b - 1).
   private static final long MOD0 = -1L, MOD1 = -1L, MOD2 = A2 - 1L, MOD3 = A3;

   // Limbs of R^2 mod m, used to convert normal LCG limbs to Montgomery form.
   private static final long R2_MOD_0, R2_MOD_1, R2_MOD_2, R2_MOD_3;

   // Limbs of the Montgomery-form stream jump multiplier for a 2^169 step fixed jump.
   private static final long STREAM_JUMP_MONT_0, STREAM_JUMP_MONT_1, STREAM_JUMP_MONT_2, STREAM_JUMP_MONT_3;

   // Limbs of the Montgomery-form substream jump multiplier for a 2^118 step fixed jump.
   private static final long SUBSTREAM_JUMP_MONT_0, SUBSTREAM_JUMP_MONT_1, SUBSTREAM_JUMP_MONT_2, SUBSTREAM_JUMP_MONT_3;

   static {
      BigInteger r2Mod = BI_R.multiply(BI_R).mod(BI_M);
      R2_MOD_0 = r2Mod.shiftRight(64 * 0).longValue();
      R2_MOD_1 = r2Mod.shiftRight(64 * 1).longValue();
      R2_MOD_2 = r2Mod.shiftRight(64 * 2).longValue();
      R2_MOD_3 = r2Mod.shiftRight(64 * 3).longValue();

      BigInteger streamJumpMont = fixedJumpMultiplierMontgomery(STREAM_ADVANCE_EXPONENT);
      STREAM_JUMP_MONT_0 = streamJumpMont.shiftRight(64 * 0).longValue();
      STREAM_JUMP_MONT_1 = streamJumpMont.shiftRight(64 * 1).longValue();
      STREAM_JUMP_MONT_2 = streamJumpMont.shiftRight(64 * 2).longValue();
      STREAM_JUMP_MONT_3 = streamJumpMont.shiftRight(64 * 3).longValue();

      BigInteger substreamJumpMont = fixedJumpMultiplierMontgomery(SUBSTREAM_ADVANCE_EXPONENT);
      SUBSTREAM_JUMP_MONT_0 = substreamJumpMont.shiftRight(64 * 0).longValue();
      SUBSTREAM_JUMP_MONT_1 = substreamJumpMont.shiftRight(64 * 1).longValue();
      SUBSTREAM_JUMP_MONT_2 = substreamJumpMont.shiftRight(64 * 2).longValue();
      SUBSTREAM_JUMP_MONT_3 = substreamJumpMont.shiftRight(64 * 3).longValue();

      nextSeedYMont = new long[4];
      stateToMontgomeryLCG4(nextSeed, nextSeedYMont);
   }

   /**
    * Constructs a new stream.
    */
   public MWC64k3a2Direct() {
      Ig = nextSeed.clone();              // Save the start state of this stream.
      IgYMont = nextSeedYMont.clone();    // Save the matching Montgomery LCG state.
      Bg = new long[4];                   // Allocate the substream state.
      BgYMont = new long[4];              // Allocate the Montgomery substream state.

      resetStartStream();                 // Set Bg and current state from Ig.
      advanceLCGStateMont4(nextSeedYMont, STREAM_JUMP_MONT_0,
            STREAM_JUMP_MONT_1, STREAM_JUMP_MONT_2, STREAM_JUMP_MONT_3, nextSeed);
   }

   /**
    * Constructs a new stream with a name.
    *
    * @param name stream name
    */
   public MWC64k3a2Direct(String name) {
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
      nextSeedYMont = new long[4];        // Store a fresh matching Montgomery LCG state.
      stateToMontgomeryLCG4(nextSeed, nextSeedYMont);
   }

   /**
    * Sets the seed of this stream.
    *
    * @param seed seed {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      Ig = seed.clone();                  // Replace initial stream state.
      IgYMont = new long[4];              // Store the matching Montgomery LCG state.
      stateToMontgomeryLCG4(Ig, IgYMont);
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
      BgYMont[0] = IgYMont[0];            // Substream Montgomery LCG state = stream Montgomery LCG state.
      BgYMont[1] = IgYMont[1];
      BgYMont[2] = IgYMont[2];
      BgYMont[3] = IgYMont[3];

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
      advanceLCGStateMont4(BgYMont, SUBSTREAM_JUMP_MONT_0,
            SUBSTREAM_JUMP_MONT_1, SUBSTREAM_JUMP_MONT_2, SUBSTREAM_JUMP_MONT_3, Bg);
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
   public void increasedPrecision(boolean incp) {
      prec53 = incp;
   }

   @Override
   public double nextDouble() {
      double u = (nextNumber() >>> 11) * NORM53;
      if (prec53)
         u = (u + ((nextNumber() >>> 11) * NORM53) * invtwo24) % 1.0 + EPSILON;
      if (anti)
         return 1.0 - u;
      else
         return u;
   }

   /**
    * Calls {@code nextDouble} {@code n} times to fill the array {@code u}.
    *
    * @param u array in which the numbers will be stored
    * @param start first index of {@code u} to be used
    * @param n number of random numbers to put in {@code u}
    */
   public void nextArrayOfDouble(double[] u, int start, int n) {
      if (u.length == 0)
         throw new NullPointerException("The array must be initialized.");
      if (u.length < n + start)
         throw new IndexOutOfBoundsException("The array is too small.");
      if (start < 0)
         throw new IndexOutOfBoundsException("Must start at a non-negative index.");
      if (n < 0)
         throw new IllegalArgumentException("Must have a non-negative number of elements.");

      for (int ii = start; ii < start + n; ii++)
         u[ii] = nextDouble();
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
    * Calls {@code nextInt} {@code n} times to fill the array {@code u}.
    *
    * @param i lower bound
    * @param j upper bound
    * @param u array in which the numbers will be stored
    * @param start first index of {@code u} to be used
    * @param n number of random numbers to put in {@code u}
    */
   public void nextArrayOfInt(int i, int j, int[] u, int start, int n) {
      if (u == null)
         throw new NullPointerException("The array must be initialized.");
      if (u.length < n + start)
         throw new IndexOutOfBoundsException("The array is too small.");
      if (start < 0)
         throw new IndexOutOfBoundsException("Must start at a non-negative index.");
      if (n < 0)
         throw new IllegalArgumentException("Must have a non-negative number of elements.");

      for (int ii = start; ii < start + n; ii++)
         u[ii] = nextInt(i, j);
   }

   /**
    * Calls {@code nextLong} {@code n} times to fill the array {@code u}.
    *
    * @param i lower bound
    * @param j upper bound
    * @param u array in which the numbers will be stored
    * @param start first index of {@code u} to be used
    * @param n number of random numbers to put in {@code u}
    */
   public void nextArrayOfLong(long i, long j, long[] u, int start, int n) {
      if (u == null)
         throw new NullPointerException("The array must be initialized.");
      if (u.length < n + start)
         throw new IndexOutOfBoundsException("The array is too small.");
      if (start < 0)
         throw new IndexOutOfBoundsException("Must start at a non-negative index.");
      if (n < 0)
         throw new IllegalArgumentException("Must have a non-negative number of elements.");

      for (int ii = start; ii < start + n; ii++)
         u[ii] = nextLong(i, j);
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
   public MWC64k3a2Direct clone() {
      MWC64k3a2Direct copy = null;
      try {
         copy = (MWC64k3a2Direct) super.clone();
      } catch (CloneNotSupportedException cnse) {
         cnse.printStackTrace(System.err);
      }

      copy.Ig = Ig.clone();               // Copy stream-start state.
      copy.Bg = Bg.clone();               // Copy substream-start state.
      copy.IgYMont = IgYMont.clone();     // Deep-copy the stream-start Montgomery LCG state.
      copy.BgYMont = BgYMont.clone();     // Deep-copy the substream-start Montgomery LCG state.

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
    * Computes a fixed jump multiplier in Montgomery form for static setup.
    *
    * @param exponent fixed jump exponent, giving a jump of {@code 2^exponent}
    * @return {@code (b^(-1))^(2^exponent) * R mod m}
    */
   private static BigInteger fixedJumpMultiplierMontgomery(int exponent) {
      BigInteger jump = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(exponent), BI_M);
      return jump.multiply(BI_R).mod(BI_M);
   }

   /**
    * Maps a MWC state to Montgomery LCG form during seed setup.
    *
    * @param state MWC state {x_{n-3}, x_{n-2}, x_{n-1}, carry}
    * @param yMont destination for {@code stateToLCG(state) * R mod m}
    */
   private static void stateToMontgomeryLCG4(long[] state, long[] yMont) {
      BigInteger y = stateToLCG(state);
      montMulInto4(y.shiftRight(64 * 0).longValue(), y.shiftRight(64 * 1).longValue(),
            y.shiftRight(64 * 2).longValue(), y.shiftRight(64 * 3).longValue(),
            R2_MOD_0, R2_MOD_1, R2_MOD_2, R2_MOD_3, yMont);
   }

   /**
    * Advances a saved Montgomery LCG state by one fixed stream or substream jump.
    *
    * @param yMont saved LCG state in Montgomery form, updated in place
    * @param j0 low limb of the Montgomery jump multiplier
    * @param j1 second limb of the Montgomery jump multiplier
    * @param j2 third limb of the Montgomery jump multiplier
    * @param j3 high limb of the Montgomery jump multiplier
    * @param state MWC state to receive the recovered value
    */
   private static void advanceLCGStateMont4(long[] yMont, long j0, long j1,
                                            long j2, long j3, long[] state) {
      montMulInto4(j0, j1, j2, j3, yMont[0], yMont[1], yMont[2], yMont[3], yMont);
      stateFromMontgomeryLCG4(yMont[0], yMont[1], yMont[2], yMont[3], state);
   }

   /**
    * Multiplies two 4-limb values and writes the reduced result (all in little-endian limb order).
    * Computes a*b*R^(-1) mod m, where R = b^4 is the Montgomery radix.
    *
    * @param a0 low limb of the first factor
    * @param a1 second limb of the first factor
    * @param a2 third limb of the first factor
    * @param a3 high limb of the first factor
    * @param b0 low limb of the second factor
    * @param b1 second limb of the second factor
    * @param b2 third limb of the second factor
    * @param b3 high limb of the second factor
    * @param out destination for the reduced Montgomery product
    */
   private static void montMulInto4(long a0, long a1, long a2, long a3,
                                    long b0, long b1, long b2, long b3,
                                    long[] out) {
      long t0 = 0L;
      long t1 = 0L;
      long t2 = 0L;
      long t3 = 0L;
      long t4 = 0L;
      long t5 = 0L;
      long t6 = 0L;
      long t7 = 0L;
      long t8 = 0L;

      long carry = 0L;
      long old = t0;
      long sum;
      t0 = addMulLow(old, a0, b0, carry);
      carry = addMulCarry(old, a0, b0, carry);
      old = t1;
      t1 = addMulLow(old, a0, b1, carry);
      carry = addMulCarry(old, a0, b1, carry);
      old = t2;
      t2 = addMulLow(old, a0, b2, carry);
      carry = addMulCarry(old, a0, b2, carry);
      old = t3;
      t3 = addMulLow(old, a0, b3, carry);
      carry = addMulCarry(old, a0, b3, carry);
      old = t4;
      sum = old + carry;
      t4 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      sum = old + carry;
      t6 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      carry = 0L;
      old = t1;
      t1 = addMulLow(old, a1, b0, carry);
      carry = addMulCarry(old, a1, b0, carry);
      old = t2;
      t2 = addMulLow(old, a1, b1, carry);
      carry = addMulCarry(old, a1, b1, carry);
      old = t3;
      t3 = addMulLow(old, a1, b2, carry);
      carry = addMulCarry(old, a1, b2, carry);
      old = t4;
      t4 = addMulLow(old, a1, b3, carry);
      carry = addMulCarry(old, a1, b3, carry);
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      sum = old + carry;
      t6 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      carry = 0L;
      old = t2;
      t2 = addMulLow(old, a2, b0, carry);
      carry = addMulCarry(old, a2, b0, carry);
      old = t3;
      t3 = addMulLow(old, a2, b1, carry);
      carry = addMulCarry(old, a2, b1, carry);
      old = t4;
      t4 = addMulLow(old, a2, b2, carry);
      carry = addMulCarry(old, a2, b2, carry);
      old = t5;
      t5 = addMulLow(old, a2, b3, carry);
      carry = addMulCarry(old, a2, b3, carry);
      old = t6;
      sum = old + carry;
      t6 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      carry = 0L;
      old = t3;
      t3 = addMulLow(old, a3, b0, carry);
      carry = addMulCarry(old, a3, b0, carry);
      old = t4;
      t4 = addMulLow(old, a3, b1, carry);
      carry = addMulCarry(old, a3, b1, carry);
      old = t5;
      t5 = addMulLow(old, a3, b2, carry);
      carry = addMulCarry(old, a3, b2, carry);
      old = t6;
      t6 = addMulLow(old, a3, b3, carry);
      carry = addMulCarry(old, a3, b3, carry);
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      montReduceInto4(t0, t1, t2, t3, t4, t5, t6, t7, t8, out);
   }

   /**
    * Reduces a 9-limb product modulo {@code m} with Montgomery radix {@code b^4}.
    *
    * @param t0 product limb 0
    * @param t1 product limb 1
    * @param t2 product limb 2
    * @param t3 product limb 3
    * @param t4 product limb 4
    * @param t5 product limb 5
    * @param t6 product limb 6
    * @param t7 product limb 7
    * @param t8 product limb 8
    * @param out destination for the 4-limb reduced value
    */
   private static void montReduceInto4(long t0, long t1, long t2, long t3,
                                       long t4, long t5, long t6, long t7,
                                       long t8, long[] out) {
      long q = t0;
      t0 = 0L;
      long carry = q;
      // The skipped MOD1 operation would be: next + q*(b - 1) + q = next + q*b.
      // Therefore the next limb is unchanged and the carry remains q.
      long old = t2;
      long sum;
      t2 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t3;
      t3 = addMulLow(old, q, MOD3, carry);
      carry = addMulCarry(old, q, MOD3, carry);
      old = t4;
      sum = old + carry;
      t4 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      sum = old + carry;
      t6 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      q = t1;
      t1 = 0L;
      carry = q;
      old = t3;
      t3 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t4;
      t4 = addMulLow(old, q, MOD3, carry);
      carry = addMulCarry(old, q, MOD3, carry);
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      sum = old + carry;
      t6 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      q = t2;
      t2 = 0L;
      carry = q;
      old = t4;
      t4 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t5;
      t5 = addMulLow(old, q, MOD3, carry);
      carry = addMulCarry(old, q, MOD3, carry);
      old = t6;
      sum = old + carry;
      t6 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      q = t3;
      t3 = 0L;
      carry = q;
      old = t5;
      t5 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t6;
      t6 = addMulLow(old, q, MOD3, carry);
      carry = addMulCarry(old, q, MOD3, carry);
      old = t7;
      sum = old + carry;
      t7 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      t8 += carry;

      long r0 = t4;
      long r1 = t5;
      long r2 = t6;
      long r3 = t7;

      if (t8 != 0L || ge4(r0, r1, r2, r3, MOD0, MOD1, MOD2, MOD3))
         subModulus4(r0, r1, r2, r3, out);
      else {
         out[0] = r0;
         out[1] = r1;
         out[2] = r2;
         out[3] = r3;
      }
   }

   /**
    * Converts a Montgomery LCG value to normal form and recovers the MWC state.
    *
    * @param y0 low limb of the Montgomery LCG state
    * @param y1 second limb of the Montgomery LCG state
    * @param y2 third limb of the Montgomery LCG state
    * @param y3 high limb of the Montgomery LCG state
    * @param state MWC state to receive the recovered value
    */
   private static void stateFromMontgomeryLCG4(long y0, long y1, long y2,
                                               long y3, long[] state) {
      montReduceInto4(y0, y1, y2, y3, 0L, 0L, 0L, 0L, 0L, state);
      stateFromLCGParts4(state[0], state[1], state[2], state[3], state);
   }

   /**
    * Recovers a MWC state from four normal-form LCG limbs.
    *
    * @param y0 low limb of the normal LCG state
    * @param y1 second limb of the normal LCG state
    * @param y2 third limb of the normal LCG state
    * @param y3 high limb of the normal LCG state
    * @param state MWC state to update
    */
   private static void stateFromLCGParts4(long y0, long y1, long y2,
                                          long y3, long[] state) {
      long newX3 = y0;
      long newX2 = y1;
      long low = A2 * newX3;
      long high = Math.unsignedMultiplyHigh(A2, newX3);
      long newX1 = low + y2;
      long overflow = Long.compareUnsigned(newX1, low) < 0 ? 1L : 0L;
      long newCarry = high + overflow + y3;

      state[0] = newX3;
      state[1] = newX2;
      state[2] = newX1;
      state[3] = newCarry;
   }

   /**
    * Returns the low limb of {@code limb + a*b + carry}.
    *
    * @param limb existing limb value
    * @param a first unsigned factor
    * @param b second unsigned factor
    * @param carry incoming unsigned carry
    * @return low 64 bits of the sum
    */
   private static long addMulLow(long limb, long a, long b, long carry) {
      long productLow = a * b;
      long sum = limb + productLow;
      return sum + carry;
   }

   /**
    * Returns the carry from {@code limb + a*b + carry}.
    *
    * @param limb existing limb value
    * @param a first unsigned factor
    * @param b second unsigned factor
    * @param carry incoming unsigned carry
    * @return unsigned carry shifted down by 64 bits
    */
   private static long addMulCarry(long limb, long a, long b, long carry) {
      long productLow = a * b;
      long productHigh = Math.unsignedMultiplyHigh(a, b);
      long sum = limb + productLow;
      long overflow1 = Long.compareUnsigned(sum, limb) < 0 ? 1L : 0L;
      long sumWithCarry = sum + carry;
      long overflow2 = Long.compareUnsigned(sumWithCarry, sum) < 0 ? 1L : 0L;
      return productHigh + overflow1 + overflow2;
   }

   /**
    * Tests whether one 4-limb unsigned value is greater than or equal to another.
    *
    * @param a0 low limb of the first value
    * @param a1 second limb of the first value
    * @param a2 third limb of the first value
    * @param a3 high limb of the first value
    * @param b0 low limb of the second value
    * @param b1 second limb of the second value
    * @param b2 third limb of the second value
    * @param b3 high limb of the second value
    * @return {@code true} if {@code a >= b} as unsigned 256-bit values
    */
   private static boolean ge4(long a0, long a1, long a2, long a3,
                              long b0, long b1, long b2, long b3) {
      int cmp = Long.compareUnsigned(a3, b3);
      if (cmp != 0)
         return cmp > 0;

      cmp = Long.compareUnsigned(a2, b2);
      if (cmp != 0)
         return cmp > 0;

      cmp = Long.compareUnsigned(a1, b1);
      if (cmp != 0)
         return cmp > 0;

      return Long.compareUnsigned(a0, b0) >= 0;
   }

   /**
    * Subtracts the modulus from a 4-limb value.
    *
    * @param r0 low limb of the value
    * @param r1 second limb of the value
    * @param r2 third limb of the value
    * @param r3 high limb of the value
    * @param out destination for {@code r - m}
    */
   private static void subModulus4(long r0, long r1, long r2, long r3,
                                   long[] out) {
      long old = r0;
      r0 -= MOD0;
      long borrow = Long.compareUnsigned(old, MOD0) < 0 ? 1L : 0L;

      old = r1;
      long subtrahend = MOD1 + borrow;
      r1 -= subtrahend;
      borrow = Long.compareUnsigned(old, subtrahend) < 0
            || (borrow != 0L && subtrahend == 0L) ? 1L : 0L;

      old = r2;
      subtrahend = MOD2 + borrow;
      r2 -= subtrahend;
      borrow = Long.compareUnsigned(old, subtrahend) < 0
            || (borrow != 0L && subtrahend == 0L) ? 1L : 0L;

      r3 -= MOD3 + borrow;

      out[0] = r0;
      out[1] = r1;
      out[2] = r2;
      out[3] = r3;
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
    // BigInteger n allows for arbitrary jump sizes, to check against stream and substream fixed 
    // jump sizes and for testing with small jumps. We can use long n letter.
   public void advanceStateByJump(BigInteger n) {
      if (n.signum() < 0L)
         throw new IllegalArgumentException("Jump step n cannot be negative.");

      if (n.signum() == 0)
         return;

      BigInteger stateX3 = toUnsignedBigInt(x3);
      BigInteger stateX2 = toUnsignedBigInt(x2);
      BigInteger stateX1 = toUnsignedBigInt(x1);
      BigInteger stateCarry = BigInteger.valueOf(carry);

      BigInteger y =
            BI_MAP_X3.multiply(stateX3)
          .add(BI_B.multiply(stateX2))
          .add(BI_B2.multiply(stateX1))
          .add(BI_B3.multiply(stateCarry))
          .mod(BI_M);

      BigInteger sigma =
            BI_B_INV.modPow(n, BI_M)
          .multiply(y)
          .mod(BI_M);

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

   // Expose the raw 64-bit output for testing purposes.
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
         // Handle negative long bit patterns as unsigned 64-bit values
         return BigInteger.valueOf(value & 0x7FFFFFFFFFFFFFFFL).setBit(63);
      }
   }
}
