package umontreal.ssj.rng;
import java.math.BigInteger;


/**
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

   /** First coefficient a1. */
   private static final long A1 = 193154555888013165L;

   /** Second coefficient a2. */
   private static final long A2 = 1966812196490295L;
   
//   private static final long  A1 = 556348944096481337L; // for jump
//   private static final long  A2 = 8250136865355103L;

   /** 2^(-53), used to convert 53 random bits to a double. */
   private static final double NORM53 = 0x1.0p-53;

   /** Temporary stream spacing: 1L << 20 = 2^20 generated values. */
   private static final long STREAM_ADVANCE = 1L << 20;

   /** Temporary substream spacing: 1L << 10 = 2^10 generated values. */
   private static final long SUBSTREAM_ADVANCE = 1L << 10;

   /** Seed used for the next created stream: {x_{n-2}, x_{n-1}, carry}. */
   private static long[] nextSeed = { 12345L, 67890L, 1L };

   /** Initial state of this stream. */
   private long[] Ig;

   /** Beginning state of the current substream of stream. */
   private long[] Bg;

   /** State component x_{n-2}, interpreted as unsigned 64-bit. */
   private long x2;

   /** State component x_{n-1}, interpreted as unsigned 64-bit. */
   private long x1;

   /** Carry c_{n-1}. */
   private long carry;
    
   /**
    * Precomputed BigInteger constants for the 128-bit MWC-to-LCG transformation.
    */
   private static final BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
   private static final BigInteger BI_A1 = BigInteger.valueOf(A1);
   private static final BigInteger BI_A2 = BigInteger.valueOf(A2);
   
   // m = a2 * b^2 + a1 * b - 1
   private static final BigInteger BI_M = BI_A2.multiply(BI_B).add(BI_A1).multiply(BI_B).subtract(BigInteger.ONE);
   
   // Precomputed modular inverse of base b modulo m: b^(-1) mod m
   private static final BigInteger BI_B_INV = BI_B.modInverse(BI_M);
   
   private static final BigInteger BI_B_MINUS_ONE = BI_B.subtract(BigInteger.ONE);
   
   

   /**
    * Constructs a new stream.
    */
   public MWC64k2a2() {
      Ig = nextSeed.clone();              // Save the start state of this stream.
      Bg = new long[3];                   // Allocate the substream state.

      resetStartStream();                 // Set Bg and current state from Ig.

      advanceState(nextSeed, STREAM_ADVANCE); // Move package seed for next stream.
   }

   /**
    * Constructs a new stream with a name.
    *
    * @param name stream name
    */
   public MWC64k2a2(String name) {
      this();                             // Build stream normally.
      this.name = name;                   // Store stream name.
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
      advanceState(Bg, SUBSTREAM_ADVANCE); // Move substream state forward.
      resetStartSubstream();               // Current state = new substream start.
   }

   /**
    * Generates the next 64-bit value x_n.
    *
    * @return next 64-bit output, interpreted as unsigned
    */
//   private long nextNumber() {
//      long lo1 = A1 * x1;                 // Low 64 bits of A1*x1.
//      long hi1 = unsignedMultiplyHighPositive(A1, x1); // High 64 bits. for java less than 18
//      //long hi1 = Math.unsignedMultiplyHigh(A1, x1);
//
//      long lo2 = A2 * x2;                 // Low 64 bits of A2*x2.
//      long hi2 = unsignedMultiplyHighPositive(A2, x2); // High 64 bits. for java less than 18
//      //long hi2 = Math.unsignedMultiplyHigh(A2, x2);
//
//      long low = lo1 + lo2;               // Low part of product sum.
//      long overflow1 = Long.compareUnsigned(low, lo1) < 0 ? 1L : 0L;
//
//      long lowWithCarry = low + carry;    // Add old carry to low part.
//      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;
//
//      long high = hi1 + hi2 + overflow1 + overflow2; // New carry.
//
//      x2 = x1;                            // Shift state.
//      x1 = lowWithCarry;                  // New x_n.
//      carry = high;                       // New carry.
//
//      return x1;
//   }
   /**
    * Generates one MWC step and returns the old x_{n-1},
    *
    * @return old x_{n-1}, interpreted as unsigned 64-bit
    */
   private long nextNumber() {
      long out = x1;                         // C++ returns old x1 first.

      long lo1 = A1 * x1;                    // Low 64 bits of A1*x_{n-1}.
      long hi1 = Math.unsignedMultiplyHigh(A1, x1);

      long lo2 = A2 * x2;                    // Low 64 bits of A2*x_{n-2}.
      long hi2 = Math.unsignedMultiplyHigh(A2, x2);

      long low = lo1 + lo2;
      long overflow1 = Long.compareUnsigned(low, lo1) < 0 ? 1L : 0L;

      long lowWithCarry = low + carry;
      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = hi1 + hi2 + overflow1 + overflow2;

      x2 = x1;
      x1 = lowWithCarry;
      carry = high;

      return out;
   }
   
   /*
    * to add comment
    * */
   public long nextLongXor() {
	   long out = x1 ^ x2;

	   long lo1 = A1 * x1;
	   long hi1 = Math.unsignedMultiplyHigh(A1, x1);

	   long lo2 = A2 * x2;
	   long hi2 = Math.unsignedMultiplyHigh(A2, x2);

	   long low = lo1 + lo2;
	   long overflow1 = Long.compareUnsigned(low, lo1) < 0 ? 1L : 0L;

	   long lowWithCarry = low + carry;
	   long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

	   long high = hi1 + hi2 + overflow1 + overflow2;

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
    * block53 = nextNumber() >> 11
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
    * Returns the next U(0,1) value.
    *
    * We use (x + 0.5) / 2^32 to avoid returning exactly 0 or 1.
    */
   protected double nextValue2() {
      return ((nextNumber() >>> 11) + 0.5) * NORM53;
   }
   


   /**
    * Returns a random long in [i, j].
    *
    * @param i lower bound
    * @param j upper bound
    * @return random long in [i, j]
    */
   
   // This method implements the "unbiased bounded integer generation" algorithm use by java.util.Random, which is based on rejection sampling to avoid modulo bias.
   //It handles the case where the range size may exceed 2^63 by using a different approach when n <= 0 due to overflow. 
   //also includes optimizations for power-of-two range sizes.
   public long nextLong(long i, long j) {
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");

      long n = j - i + 1L;                // Range size, may overflow. If i is a large negative number and j is a large positive number, then n may be larger than 2^63, which is the largest positive long.

      if (n > 0L) {                       // Case: range size fits in signed long.
         long r = nextNumber() >>> 1;     // Use 63 nonnegative random bits (Clear the sign bit to avoid negative values).
         long m = n - 1L;                 // Used for power-of-two optimization. Just crop to the range of n, without bias, if n is a power of 2.
         
         if ((n & m) == 0L)               // If n is a power of 2. (n-1) is all 1's in binary, so n & (n-1) == 0. Use (n-1) as a bitmask to reduce r modulo n without bias.
            return i + (r & m);           // If n is a power of 2. Fast unbiased mapping. (r & (n-1)) is equivalent to r % n, it crops the random bits to the range of n, adding i shifts it to the desired range [i, j].
       
         //Handling Modulo Bias (Rejection Sampling). Crop using (r % n) will introduce bias if n does not divide the number of possible r values. 
         long u = r;                      // Candidate random value.
         while (u + m - (r = u % n) < 0L)  // 	Tail limit = max_u - (max_u % n),then rejection happens when u >= max_u - (max_u % n), the trick is to rearrange the inequality to avoid computing max_u. The condition u + (n-1) - (u % n) < 0 is equivalent to u >= max_u - (max_u % n), but it avoids the need to compute max_u, which is 2^63 for a signed long. This is a common technique in rejection sampling to ensure uniformity without bias.
            u = nextNumber() >>> 1;       // Reject biased candidates.

         return i + r;                    // Shift result into [i, j].
      }

      long r;                             // Case: range size is larger than 2^63. n <= 0L due to overflow, so we cannot use the previous method. We must use rejection sampling without modulo bias correction, since the range is too large to fit in a signed long. We will generate full 64-bit random values and reject those that fall outside the desired range [i, j].
      do {
         r = nextNumber();                // Generate full signed long.
      } while (r < i || r > j);           // Rejection inside [i, j].

      return r;
   }
   
   public long nextLongSsj(long i, long j) {
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
   
//   public long nextBitsLong(int b) {
//	    if (b < 0 || b > 63) {
//	        throw new IllegalArgumentException("b must be between 0 and 63");
//	    }
//
//	    if (b == 0) {
//	        return 0L;
//	    }
//
//	    long r = nextNumber() >>> 1;
//
//	    if (b == 63) {
//	        return r;
//	    }
//
//	    return r & ((1L << b) - 1L);
//	}
   
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
      return (int) nextLong(i, j);         // Reuse unbiased long method.
   }

   /**
    * Fills an array with random longs in [i, j].
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
    * Fills the whole byte array using random bytes.
    *
    * This version is closer to java.util.Random.nextBytes().
    * It extracts bytes from each 64-bit output from the least
    * significant byte to the most significant byte.
    *
    * Example:
    * nextNumber() = 0x1122334455667788
    * output bytes = 88 77 66 55 44 33 22 11
    */
   
   public void nextBytes1(byte[] bytes) {
	    if (bytes == null)
	        throw new NullPointerException("bytes is null");
	    for (int i = 0; i < bytes.length; ) {
	        long rnd = nextNumber();
	        int n = Math.min(bytes.length - i, 8);

	        while (n-- > 0) {
	            bytes[i++] = (byte) rnd;
	            rnd >>>= 8;
	        }
	    }
	}
   
//   =================High-first:=========================
//		   96 CF F7 1F C7 B3 4D DD 99 8A 71 31 34 91 BA 0B
//		   time = 14.29018546
//
//		   =================Java-style low-first=========================
//		   DD 4D B3 C7 1F F7 CF 96 0B BA 91 34 31 71 8A 99
//		   time = 25.18534711
   
   /**
    * Fills the whole byte array using random bytes.
    *
    * This version extracts bytes from each 64-bit output from
    * the most significant byte to the least significant byte.
    *
    * Example:
    * nextNumber() = 0x1122334455667788
    * output bytes = 11 22 33 44 55 66 77 88
    */
   
		public void nextBytes(byte[] bytes) {
		    if (bytes == null)
		        throw new NullPointerException("bytes is null");
		    
		    int length = bytes.length;
	
		    int i = 0;
	
		    while (i + 8 <= length) {
		        long x = nextNumber();
	
		        bytes[i++] = (byte) (x >>> 56);
		        bytes[i++] = (byte) (x >>> 48);
		        bytes[i++] = (byte) (x >>> 40);
		        bytes[i++] = (byte) (x >>> 32);
		        bytes[i++] = (byte) (x >>> 24);
		        bytes[i++] = (byte) (x >>> 16);
		        bytes[i++] = (byte) (x >>> 8);
		        bytes[i++] = (byte) x;
		    }
	
		    if (i < length) {
		        long x = nextNumber();
	
		        for (int shift = 56; i < length; shift -= 8) {
		            bytes[i++] = (byte) (x >>> shift);
		        }
		    }
		}

   /**
    * Sets antithetic mode.
    *
    * @param anti true for 1-u, false for u
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

      if (seed.length < 3)
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
    * Advances a given state by one step.
    *
    * @param state state {x_{n-2}, x_{n-1}, carry}
    */
   private static void advanceState(long[] state) {
      long lo1 = A1 * state[1];
      long hi1 = Math.unsignedMultiplyHigh(A1, state[1]);

      long lo2 = A2 * state[0];
      long hi2 = Math.unsignedMultiplyHigh(A2, state[0]); 

      long low = lo1 + lo2;
      long overflow1 = Long.compareUnsigned(low, lo1) < 0 ? 1L : 0L;

      long lowWithCarry = low + state[2];
      long overflow2 = Long.compareUnsigned(lowWithCarry, low) < 0 ? 1L : 0L;

      long high = hi1 + hi2 + overflow1 + overflow2;

      state[0] = state[1];
      state[1] = lowWithCarry;
      state[2] = high;
   }

//   /**
//    * Advances a given state by n steps instantly using the dual LCG mapping.
//    * This replaces the slow, O(n) loop implementation with a fast O(log n) implementation.
//    *
//    * @param state state to advance {x_{n-2}, x_{n-1}, carry}
//    * @param n number of steps to jump forward
//    */
//   private static void advanceState(long[] state, long n) {
//      if (n < 0) {
//         throw new IllegalArgumentException("Jump step n cannot be negative.");
//      }
//      if (n == 0) {
//         return; // Nothing to do
//      }
//      if (n == 1) {
//         advanceState(state); // Use the simple primitive step for a single iteration
//         return;
//      }
//
//      // 1. Map current MWC state {x2, x1, carry} to LCG integer state y_n
//      BigInteger x2_bi = toUnsignedBigInt(state[0]);
//      BigInteger x1_bi = toUnsignedBigInt(state[1]);
//      BigInteger carry_bi = toUnsignedBigInt(state[2]);
//
//      // y = carry * b^2 + x1 * b + x2
//      BigInteger y = carry_bi.multiply(BI_B).add(x1_bi).multiply(BI_B).add(x2_bi);
//      
//      // Ensure y is fully reduced modulo m
//      y = y.mod(BI_M);
//
//      // 2. Compute the jump multiplier: (b^(-1))^n mod m
//      BigInteger jumpMultiplier = BI_B_INV.modPow(BigInteger.valueOf(n), BI_M);
//
//      // 3. Perform the jump step in the LCG framework
//      y = y.multiply(jumpMultiplier).mod(BI_M);
//
//      // 4. Deconstruct the new LCG state back into MWC components
//      // Following the note: y_n/m fractional base-b expansion properties.
//      // x2 = y mod b
//      state[0] = y.longValue(); 
//      y = y.shiftRight(64); // Divide by b
//      
//      // x1 = y mod b
//      state[1] = y.longValue(); 
//      y = y.shiftRight(64); // Divide by b
//      
//      // carry = remaining bits
//      state[2] = y.longValue();
//   }
   
   
   /**
    * Advances a state by n steps using the LCG representation.
    *
    * The state is {x_{n-2}, x_{n-1}, carry}.
    *
    * @param state state to advance
    * @param n number of MWC steps
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

      BigInteger x2 = toUnsignedBigInt(state[0]);
      BigInteger x1 = toUnsignedBigInt(state[1]);
      BigInteger c  = toUnsignedBigInt(state[2]);

      // MWCtoLCGStateLagk for k = 2, a0 = -1:
      // y = x2 + (x1 - A1*x2)*b + c*b^2 mod m
      BigInteger y = x1.subtract(BI_A1.multiply(x2));
      y = c.multiply(BI_B).add(y).multiply(BI_B).add(x2);
      y = y.mod(BI_M);

      // Jump ahead by n MWC steps: multiplier = (b^-1)^n mod m.
      BigInteger jumpMultiplier = BI_B_INV.modPow(BigInteger.valueOf(n), BI_M);
      y = y.multiply(jumpMultiplier).mod(BI_M);

      // LCGtoMWCStateLagk for k = 2, a0 = -1.
      BigInteger sigma = y;

      BigInteger newx2 = sigma.and(BI_B_MINUS_ONE);
      sigma = sigma.subtract(newx2).shiftRight(64);

      sigma = sigma.add(BI_A1.multiply(newx2));

      BigInteger newX1 = sigma.and(BI_B_MINUS_ONE);
      sigma = sigma.subtract(newX1).shiftRight(64);

      BigInteger newCarry = sigma;

      state[0] = newx2.longValue();
      state[1] = newX1.longValue();
      state[2] = newCarry.longValue();
   }
   
   /**
    * Advances the current stream state by n steps.
    *
    * @param n number of steps
    */
   void advanceStateByJump(long n) {
      long[] state = getState();
      advanceState(state, n);

      x2 = state[0];
      x1 = state[1];
      carry = state[2];
   }
   
   //////////////For jump test
   /**
    * Generates one raw value and advances the state by one step.
    *
    * @return raw output value
    */
   long nextRaw() {
      return nextNumber();
   }

   /**
    * For Java versions less than 18, computes the high 64 bits of unsigned a*x
    *
    * This assumes a is positive. It is equivalent to unsignedMultiplyHigh(a, x)
    * for this use case.
    *
    * @param a positive multiplier
    * @param x unsigned 64-bit value stored in a long
    * @return high 64 bits of unsigned a*x
    */
//   private static long unsignedMultiplyHighPositive(long a, long x) {
//      long high = Math.multiplyHigh(a, x); // Signed high 64 bits.
//
//      if (x < 0)
//         high += a;                       // Correct signed result to unsigned.
//
//      return high;
//   }
   
   
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