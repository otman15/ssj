package umontreal.ssj.rng;

/**
 * Implements a simple multiply-with-carry generator with base b = 2^32,
 * order k = 2, and coefficients
 *
 *     a0 = -1,
 *     a1 = 1111111464,
 *     a2 = 1111111464.
 *
 * The recurrence is
 *
 *     t   = a1 * x_{n-1} + a2 * x_{n-2} + c_{n-1},
 *     x_n = t mod 2^32,
 *     c_n = floor(t / 2^32).
 *
 * The state is stored as
 *
 *     {x_{n-2}, x_{n-1}, c_{n-1}}.
 *
 * Each x value is interpreted as an unsigned 32-bit integer stored in a long.
 *
 * Important: this first implementation gives a working SSJ-style stream class,
 * but the stream/substream jumps are simple repeated advances. For a final SSJ
 * implementation, resetNextSubstream() and the constructor should eventually
 * use mathematically derived jump-ahead matrices or an equivalent LCG jump.
 */
public class MWC32k2a extends RandomStreamBase {
   private static final long serialVersionUID = 20260518L;

   // MWC parameters.
   private static final long A1 = 1111111464L;
   private static final long A2 = 1111111464L;

   // b = 2^32.
   private static final long MASK32 = 0xFFFFFFFFL;
   private static final long TWO32 = 1L << 32;
   private static final double NORM = 0x1.0p-32;   // 2^(-32)

   // The carry should remain smaller than A1 + A2 for this recurrence.
   private static final long CARRY_BOUND = A1 + A2;

   /*
    * Temporary stream/substream spacings.
    *
    * These are NOT the final SSJ-quality values. They only make the class usable
    * while the real jump-ahead method is not implemented.
    */
   private static final long STREAM_ADVANCE = 1L << 20;
   private static final long SUBSTREAM_ADVANCE = 1L << 10;

   // Default package seed for the first stream.
   private static long[] nextSeed = { 12345L, 12345L, 12345L };

   // Ig = start of stream, Bg = start of current substream.
   private long[] Ig;
   private long[] Bg;

   // Current state: x0 = x_{n-2}, x1 = x_{n-1}, carry = c_{n-1}.
   private long x0;
   private long x1;
   private long carry;

   /**
    * Constructs a new stream.
    */
   public MWC32k2a() {
      name = null;
      anti = false;
      prec53 = false;

      Ig = nextSeed.clone();
      Bg = new long[3];

      resetStartStream();

      // Move the package seed for the next created stream.
      advanceState(nextSeed, STREAM_ADVANCE);
   }

   /**
    * Constructs a new stream with identifier {@code name}.
    *
    * @param name name of the stream
    */
   public MWC32k2a(String name) {
      this();
      this.name = name;
   }

   /**
    * Sets the initial package seed for the next created stream.
    *
    * The seed must contain three values:
    *
    *     seed[0] = x_{n-2},
    *     seed[1] = x_{n-1},
    *     seed[2] = carry.
    *
    * The first two values must be in [0, 2^32 - 1]. The carry must be in
    * [0, a1 + a2 - 1]. The all-zero state is forbidden.
    *
    * @param seed array of 3 seed values
    */
   public static void setPackageSeed(long[] seed) {
      checkSeed(seed);
      nextSeed = seed.clone();
   }

   /**
    * Sets the initial seed of this stream.
    *
    * This affects only this stream. Other streams are not modified.
    *
    * @param seed array of 3 seed values
    */
   public void setSeed(long[] seed) {
      checkSeed(seed);
      Ig = seed.clone();
      resetStartStream();
   }

   /**
    * Returns the current state of the stream.
    *
    * @return current state {x_{n-2}, x_{n-1}, carry}
    */
   public long[] getState() {
      return new long[] { x0, x1, carry };
   }

   /**
    * Resets the stream to its initial state.
    */
   public void resetStartStream() {
      Bg[0] = Ig[0];
      Bg[1] = Ig[1];
      Bg[2] = Ig[2];
      resetStartSubstream();
   }

   /**
    * Resets the stream to the beginning of the current substream.
    */
   public void resetStartSubstream() {
      x0 = Bg[0];
      x1 = Bg[1];
      carry = Bg[2];
   }

   /**
    * Moves to the next substream.
    *
    * This is a temporary implementation based on repeated iteration. For a final
    * SSJ implementation, replace this with a real jump-ahead method.
    */
   public void resetNextSubstream() {
      advanceState(Bg, SUBSTREAM_ADVANCE);
      resetStartSubstream();
   }
   
   /**
    * Generates one MWC step and returns the old x_{n-1},
    * to match the C++ speed-test convention.
    *
    * @return old x_{n-1}, interpreted as unsigned 32-bit
    */
   private long nextNumber() {
      long out = x1;                 // Return old x_{n-1}, like C++ returns x1.

      long t = A1 * x1 + A2 * x0 + carry;

      long xNew = t & MASK32;        // New x_n = low 32 bits.
      long cNew = t >>> 32;          // New carry = high bits.

      x0 = x1;                       // Shift x_{n-1} to x_{n-2}.
      x1 = xNew;                     // Store new x_n.
      carry = cNew;                  // Store new carry.

      return out;
   }

//   /**
//    * Advances the current state by one step and returns the new 32-bit value.
//    *
//    * The arithmetic uses Java long overflow intentionally. For these parameters,
//    * each product fits in a signed long, and the full sum is smaller than 2^64.
//    * Thus the low 32 bits and the high 32 bits of the unsigned 64-bit sum are
//    * still recovered correctly by masking and unsigned shift.
//    *
//    * @return the new x_n as an unsigned 32-bit value stored in a long
//    */
//   private long nextNumber() {
//      long t = A1 * x1 + A2 * x0 + carry;
//
//      long xNew = t & MASK32;
//      long cNew = t >>> 32;
//
//      x0 = x1;
//      x1 = xNew;
//      carry = cNew;
//
//      return xNew;
//   }

   /**
    * Returns the next U(0,1) value.
    *
    * We use (x + 0.5) / 2^32 to avoid returning exactly 0 or 1.
    */
   protected double nextValue() {
      return (nextNumber() + 0.5) * NORM;
   }

   /**
    * Faster integer generation using the 32-bit output directly.
    */
   public int nextInt(int i, int j) {
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");

      long bound = (long) j - (long) i + 1L;

      // Rejection threshold to avoid modulo bias.
      long limit = TWO32 - (TWO32 % bound);

      long r;
      do {
         r = nextNumber();
      } while (r >= limit);

      long value = (long) i + (r % bound);
      return (int) value;
   }
   
   
   public void setAntithetic(boolean anti) {
	   this.anti = anti;
	}

   /**
    * Returns a string containing the current state.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("The current state of the MWC32k2a");
      if (name != null && name.length() > 0)
         sb.append(" ").append(name);

      sb.append(" is: { ");
      sb.append(x0).append(", ");
      sb.append(x1).append(", ");
      sb.append(carry).append(" }");

      return sb.toString();
   }

   /**
    * Returns a detailed string containing the stream, substream, and current
    * states.
    *
    * @return detailed state
    */
   public String toStringFull() {
      String nl = System.lineSeparator();
      StringBuilder sb = new StringBuilder();

      sb.append("The MWC32k2a stream");
      if (name != null && name.length() > 0)
         sb.append(" ").append(name);

      sb.append(":").append(nl);
      sb.append(" anti = ").append(anti).append(nl);
      sb.append(" prec53 = ").append(prec53).append(nl);

      sb.append(" Ig = { ")
        .append(Ig[0]).append(", ")
        .append(Ig[1]).append(", ")
        .append(Ig[2]).append(" }").append(nl);

      sb.append(" Bg = { ")
        .append(Bg[0]).append(", ")
        .append(Bg[1]).append(", ")
        .append(Bg[2]).append(" }").append(nl);

      sb.append(" Cg = { ")
        .append(x0).append(", ")
        .append(x1).append(", ")
        .append(carry).append(" }").append(nl);

      return sb.toString();
   }

   /**
    * Clones this stream.
    *
    * @return independent copy of this stream
    */
   public MWC32k2a clone() {
      MWC32k2a retour = (MWC32k2a) super.clone();
      retour.Ig = Ig.clone();
      retour.Bg = Bg.clone();
      return retour;
   }

   /**
    * Checks if a seed is valid.
    */
   private static void checkSeed(long[] seed) {
      if (seed == null)
         throw new NullPointerException("Seed must not be null.");

      if (seed.length < 3)
         throw new IllegalArgumentException("Seed must contain 3 values.");

      if ((seed[0] & ~MASK32) != 0 || (seed[1] & ~MASK32) != 0)
         throw new IllegalArgumentException(
            "The first two seed values must be in [0, 2^32 - 1].");

      if (seed[2] < 0 || seed[2] >= CARRY_BOUND)
         throw new IllegalArgumentException(
            "The carry must be in [0, " + (CARRY_BOUND - 1) + "].");

      if (seed[0] == 0 && seed[1] == 0 && seed[2] == 0)
         throw new IllegalArgumentException("The all-zero state is not allowed.");
   }

   /**
    * Advances a state by one step.
    *
    * The state array is {x_{n-2}, x_{n-1}, carry}.
    */
   private static void advanceState(long[] state) {
      long t = A1 * state[1] + A2 * state[0] + state[2];

      long xNew = t & MASK32;
      long cNew = t >>> 32;

      state[0] = state[1];
      state[1] = xNew;
      state[2] = cNew;
   }

   /**
    * Advances a state by n steps using repeated iteration.
    */
   private static void advanceState(long[] state, long n) {
      for (long i = 0; i < n; i++)
         advanceState(state);
   }
}