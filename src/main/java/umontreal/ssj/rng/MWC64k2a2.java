package umontreal.ssj.rng;
import java.math.BigInteger;


/**
 * <p>
 * This generator uses Math.unsignedMultiplyHigh which requires JDK 18 or later.
 * </p>
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
   // Coefficients a1 and a2. This class currently works with positive signed Java long constants: A1 and A2.
   private static final long A1 = 193154555888013165L, A2 = 1966812196490295L;
   // private static final long A1 = 556348944096481337L, A2 = 8250136865355103L; // Used in C++ code for jumps.

   // 2^(-53), used to convert 53 random bits to a double.
   private static final double NORM53 = 0x1.0p-53;
   // Stream spacing: 2^113 generated values.
   private static final int STREAM_ADVANCE_EXPONENT = 113;
   // Substream spacing: 2^62 generated values.
   private static final int SUBSTREAM_ADVANCE_EXPONENT = 62;

   // Seed used for the next created stream: {x_{n-2}, x_{n-1}, carry}.
   private static long[] nextSeed = {12345L, 12345L, 12345L};
   // Montgomery LCG state corresponding to nextSeed, stored as three little-endian 64-bit limbs.
   private static long[] nextSeedYMont;
   // Initial state of this stream.
   private long[] Ig;
   // Montgomery LCG state corresponding to the beginning of this stream, stored as three little-endian 64-bit limbs.
   private long[] IgYMont;
   // Beginning state of the current substream of stream.
   private long[] Bg;
   // Montgomery LCG state corresponding to the beginning of the current substream, stored as three little-endian 64-bit limbs.
   private long[] BgYMont;

   // Precomputed BigInteger constants for the MWC-to-LCG jump transformation.
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
   private static final BigInteger BI_A1 = BigInteger.valueOf(A1);
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2);
   private static final BigInteger BI_M = BI_A2.multiply(BI_B).add(BI_A1).multiply(BI_B).subtract(BigInteger.ONE); // m = a2*b^2 + a1*b - 1
   // b * (A1 + A2*b) = A1*b + A2*b^2 is congruent to 1 modulo m, so b^(-1) is congruent to A1 + A2*b modulo m.
   private static final BigInteger BI_B_INV = BI_A1.add(BI_A2.multiply(BI_B));
   private static final BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); // b^2
   // A2*b^2 is congruent to 1 - A1*b modulo m.
   private static final BigInteger BI_MAP_X2 = BI_A2.multiply(BI_B2);

   // Montgomery radix R = b^3, used for static setup of Montgomery constants.
   private static final BigInteger BI_R = BigInteger.ONE.shiftLeft(192);

   // Limbs of the modulus m = A2*b^2 + (A1 - 1)*b + (b - 1).
   private static final long MOD0 = -1L, MOD1 = A1 - 1L, MOD2 = A2;

   // Limbs of R^2 mod m, used to convert normal LCG limbs to Montgomery form.
   private static final long R2_MOD_0, R2_MOD_1, R2_MOD_2;

   // Limbs of the Montgomery-form stream jump multiplier for a 2^113 step fixed jump.
   private static final long STREAM_JUMP_MONT_0, STREAM_JUMP_MONT_1, STREAM_JUMP_MONT_2;

   // Limbs of the Montgomery-form substream jump multiplier for a 2^62 step fixed jump.
   private static final long SUBSTREAM_JUMP_MONT_0, SUBSTREAM_JUMP_MONT_1, SUBSTREAM_JUMP_MONT_2;

   // Initialize the Montgomery fixed-jump constants and the default package seed.
   static {
      BigInteger r2Mod = BI_R.multiply(BI_R).mod(BI_M); // R^2 mod m.
      R2_MOD_0 = r2Mod.shiftRight(64 * 0).longValue();
      R2_MOD_1 = r2Mod.shiftRight(64 * 1).longValue();
      R2_MOD_2 = r2Mod.shiftRight(64 * 2).longValue();

      BigInteger streamJumpMont = fixedJumpMultiplierMontgomery(STREAM_ADVANCE_EXPONENT);
      STREAM_JUMP_MONT_0 = streamJumpMont.shiftRight(64 * 0).longValue();
      STREAM_JUMP_MONT_1 = streamJumpMont.shiftRight(64 * 1).longValue();
      STREAM_JUMP_MONT_2 = streamJumpMont.shiftRight(64 * 2).longValue();

      BigInteger substreamJumpMont = fixedJumpMultiplierMontgomery(SUBSTREAM_ADVANCE_EXPONENT);
      SUBSTREAM_JUMP_MONT_0 = substreamJumpMont.shiftRight(64 * 0).longValue();
      SUBSTREAM_JUMP_MONT_1 = substreamJumpMont.shiftRight(64 * 1).longValue();
      SUBSTREAM_JUMP_MONT_2 = substreamJumpMont.shiftRight(64 * 2).longValue();

      nextSeedYMont = new long[3];
      stateToMontgomeryLCG(nextSeed, nextSeedYMont);
   }

   /**
    * Constructs a new stream.
    */
   public MWC64k2a2() {
      Ig = nextSeed.clone();              // Save the start MWC state of this stream.
      IgYMont = nextSeedYMont.clone();    // Save the matching Montgomery LCG state.
      Bg = new long[3];                   // Allocate the substream state.
      BgYMont = new long[3];              // Allocate the Montgomery substream state.

      resetStartStream();                 // Set Bg and current state from Ig.

      advanceLCGStateMont(nextSeedYMont, STREAM_JUMP_MONT_0,
            STREAM_JUMP_MONT_1, STREAM_JUMP_MONT_2, nextSeed); // Prepare the next stream seed.
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
      nextSeedYMont = new long[3];        // Store a new matching Montgomery LCG state.
      stateToMontgomeryLCG(nextSeed, nextSeedYMont);
   }

   /**
    * Sets the seed of this stream.
    *
    * @param seed seed {x_{n-2}, x_{n-1}, carry}
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);                    // Validate seed.
      Ig = seed.clone();                  // Replace initial stream state.
      IgYMont = new long[3];              // Store the matching Montgomery LCG state.
      stateToMontgomeryLCG(Ig, IgYMont);
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

      BgYMont[0] = IgYMont[0]; // Substream Montgomery LCG state = stream Montgomery LCG state.
      BgYMont[1] = IgYMont[1];
      BgYMont[2] = IgYMont[2];

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
      advanceLCGStateMont(BgYMont, SUBSTREAM_JUMP_MONT_0,
            SUBSTREAM_JUMP_MONT_1, SUBSTREAM_JUMP_MONT_2, Bg); // Advance the substream Montgomery LCG state.
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
      copy.IgYMont = IgYMont.clone();     // Deep-copy the stream-start Montgomery LCG state.
      copy.BgYMont = BgYMont.clone();     // Deep-copy the substream-start Montgomery LCG state.

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
    * @param state MWC state {x_{n-2}, x_{n-1}, carry}
    * @param yMont destination for {@code stateToLCG(state) * R mod m}
    */
   private static void stateToMontgomeryLCG(long[] state, long[] yMont) {
      stateToLCGParts(state, yMont);
      montMulInto(yMont[0], yMont[1], yMont[2],
            R2_MOD_0, R2_MOD_1, R2_MOD_2, yMont);
   }

   /**
    * Maps a MWC state to normal-form LCG limbs without allocating objects.
    *
    * @param state MWC state {x_{n-2}, x_{n-1}, carry}
    * @param y destination for the canonical normal-form LCG limbs
    */
   private static void stateToLCGParts(long[] state, long[] y) {
      long newX2 = state[0];
      long low = A1 * newX2;
      long high = Math.unsignedMultiplyHigh(A1, newX2);
      long y0 = newX2;
      long y1 = state[1] - low;
      long borrow = Long.compareUnsigned(state[1], low) < 0 ? 1L : 0L;
      long y2 = state[2] - high - borrow;

      while (y2 < 0L) {
         long old = y0;
         y0 += MOD0;
         long carry = Long.compareUnsigned(y0, old) < 0 ? 1L : 0L;

         old = y1;
         y1 += MOD1;
         long carry1 = Long.compareUnsigned(y1, old) < 0 ? 1L : 0L;

         old = y1;
         y1 += carry;
         long carry2 = Long.compareUnsigned(y1, old) < 0 ? 1L : 0L;

         y2 += MOD2 + carry1 + carry2;
      }

      while (y2 > MOD2 || ge3(y0, y1, y2, MOD0, MOD1, MOD2)) {
         long old = y0;
         y0 -= MOD0;
         borrow = Long.compareUnsigned(old, MOD0) < 0 ? 1L : 0L;

         old = y1;
         long subtrahend = MOD1 + borrow;
         y1 -= subtrahend;
         borrow = Long.compareUnsigned(old, subtrahend) < 0
               || (borrow != 0L && subtrahend == 0L) ? 1L : 0L;

         y2 -= MOD2 + borrow;
      }

      y[0] = y0;
      y[1] = y1;
      y[2] = y2;
   }

   /**
    * Advances a saved Montgomery LCG state by one fixed stream or substream jump.
    *
    * @param yMont saved LCG state in Montgomery form, updated in place
    * @param j0 low limb of the Montgomery jump multiplier
    * @param j1 middle limb of the Montgomery jump multiplier
    * @param j2 high limb of the Montgomery jump multiplier
    * @param state MWC state to receive the recovered value
    */
   private static void advanceLCGStateMont(long[] yMont, long j0, long j1,
                                           long j2, long[] state) {
      montMulInto(j0, j1, j2, yMont[0], yMont[1], yMont[2], yMont);
      stateFromMontgomeryLCG(yMont[0], yMont[1], yMont[2], state);
   }

   /**
    * Multiplies two 3-limb values and writes the reduced result (all in little-endian limb order).
    * a*b*R^(-1) mod m, where R = b^3 is the Montgomery radix.
    *
    * @param a0 low limb of the first factor
    * @param a1 middle limb of the first factor
    * @param a2 high limb of the first factor
    * @param b0 low limb of the second factor
    * @param b1 middle limb of the second factor
    * @param b2 high limb of the second factor
    * @param out destination for the reduced Montgomery product
    */
   private static void montMulInto(long a0, long a1, long a2,
                                   long b0, long b1, long b2, long[] out) {
      long t0 = 0L;
      long t1 = 0L;
      long t2 = 0L;
      long t3 = 0L;
      long t4 = 0L;
      long t5 = 0L;
      long t6 = 0L;

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
      sum = old + carry;
      t3 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t4;
      sum = old + carry;
      t4 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      t6 = old + carry;

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
      sum = old + carry;
      t4 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      t6 = old + carry;

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
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      t6 = old + carry;

      montReduceInto(t0, t1, t2, t3, t4, t5, t6, out);
   }

   /**
    * Reduces a 7-limb product modulo {@code m} with Montgomery radix {@code b^3}.
    *
    * @param t0 product limb 0
    * @param t1 product limb 1
    * @param t2 product limb 2
    * @param t3 product limb 3
    * @param t4 product limb 4
    * @param t5 product limb 5
    * @param t6 product limb 6
    * @param out destination for the 3-limb reduced value
    */
   private static void montReduceInto(long t0, long t1, long t2, long t3,
                                      long t4, long t5, long t6, long[] out) {
      long q = t0;
      t0 = 0L;
      long carry = q;
      long old = t1;
      t1 = addMulLow(old, q, MOD1, carry);
      carry = addMulCarry(old, q, MOD1, carry);
      old = t2;
      t2 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t3;
      long sum = old + carry;
      t3 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t4;
      sum = old + carry;
      t4 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      t6 = old + carry;

      q = t1;
      t1 = 0L;
      carry = q;
      old = t2;
      t2 = addMulLow(old, q, MOD1, carry);
      carry = addMulCarry(old, q, MOD1, carry);
      old = t3;
      t3 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t4;
      sum = old + carry;
      t4 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      t6 = old + carry;

      q = t2;
      t2 = 0L;
      carry = q;
      old = t3;
      t3 = addMulLow(old, q, MOD1, carry);
      carry = addMulCarry(old, q, MOD1, carry);
      old = t4;
      t4 = addMulLow(old, q, MOD2, carry);
      carry = addMulCarry(old, q, MOD2, carry);
      old = t5;
      sum = old + carry;
      t5 = sum;
      carry = Long.compareUnsigned(sum, old) < 0 ? 1L : 0L;
      old = t6;
      t6 = old + carry;

      long r0 = t3;
      long r1 = t4;
      long r2 = t5;

      if (t6 != 0L || ge3(r0, r1, r2, MOD0, MOD1, MOD2))
         subModulusInto(r0, r1, r2, out);
      else {
         out[0] = r0;
         out[1] = r1;
         out[2] = r2;
      }
   }

   /**
    * Converts a Montgomery LCG value to normal form and recovers the MWC state.
    *
    * @param y0 low limb of the Montgomery LCG state
    * @param y1 middle limb of the Montgomery LCG state
    * @param y2 high limb of the Montgomery LCG state
    * @param state MWC state to receive the recovered value
    */
   private static void stateFromMontgomeryLCG(long y0, long y1, long y2,
                                              long[] state) {
      montReduceInto(y0, y1, y2, 0L, 0L, 0L, 0L, state);
      stateFromLCGParts(state[0], state[1], state[2], state);
   }

   /**
    * Recovers a MWC state from three normal-form LCG limbs.
    *
    * @param y0 low limb of the normal LCG state
    * @param y1 middle limb of the normal LCG state
    * @param y2 high limb of the normal LCG state
    * @param state MWC state to update
    */
   private static void stateFromLCGParts(long y0, long y1, long y2,
                                         long[] state) {
      long newX2 = y0;
      long low = A1 * newX2;
      long high = Math.unsignedMultiplyHigh(A1, newX2);
      long newX1 = low + y1;
      long overflow = Long.compareUnsigned(newX1, low) < 0 ? 1L : 0L;
      long newCarry = high + overflow + y2;

      state[0] = newX2;
      state[1] = newX1;
      state[2] = newCarry;
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
    * Tests whether one 3-limb unsigned value is greater than or equal to another.
    *
    * @param a0 low limb of the first value
    * @param a1 middle limb of the first value
    * @param a2 high limb of the first value
    * @param b0 low limb of the second value
    * @param b1 middle limb of the second value
    * @param b2 high limb of the second value
    * @return {@code true} if {@code a >= b} as unsigned 192-bit values
    */
   private static boolean ge3(long a0, long a1, long a2,
                              long b0, long b1, long b2) {
      int cmp = Long.compareUnsigned(a2, b2);
      if (cmp != 0)
         return cmp > 0;

      cmp = Long.compareUnsigned(a1, b1);
      if (cmp != 0)
         return cmp > 0;

      return Long.compareUnsigned(a0, b0) >= 0;
   }

   /**
    * Subtracts the modulus from a 3-limb value.
    *
    * @param r0 low limb of the value
    * @param r1 middle limb of the value
    * @param r2 high limb of the value
    * @param out destination for {@code r - m}
    */
   private static void subModulusInto(long r0, long r1, long r2, long[] out) {
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

      out[0] = r0;
      out[1] = r1;
      out[2] = r2;
   }

   /**
    * Advances the current stream state by n steps.
    *
    * This method is for a general jump size n. It maps the current MWC
    * state to the equivalent LCG state, applies
    * the LCG jump, then converts the result back to the MWC state.
    *
    * @param n number of steps to jump
    */
   // BigInteger n allows for arbitrary jump sizes, to check against stream and substream fixed 
   // jump sizes and for testing with small jumps. We can use long n letter.
   public void advanceStateByJump(BigInteger n) { 
      if (n.signum() < 0)
         throw new IllegalArgumentException("Jump step n cannot be negative.");

      if (n.signum() == 0)
         return;

      BigInteger stateX2 = toUnsignedBigInt(x2);
      BigInteger stateX1 = toUnsignedBigInt(x1);
      BigInteger stateCarry = BigInteger.valueOf(carry);

      BigInteger y =
            BI_MAP_X2.multiply(stateX2)
          .add(BI_B.multiply(stateX1))
          .add(BI_B2.multiply(stateCarry))
          .mod(BI_M);

      // Apply the LCG jump: y_new = (b^(-1))^n * y mod m.
      BigInteger sigma =
            BI_B_INV.modPow(n, BI_M)
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
