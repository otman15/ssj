/*
 * Class:        LFSR258Direct
 * Description:  64-bit composite linear feedback shift register proposed by L'Ecuyer
 * Environment:  Java
 * Software:     SSJ
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
 * @author
 * @since
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package umontreal.ssj.rng;

import java.io.Serializable;

/**
 * Direct implementation of @ref LFSR258 that implements @ref CloneableRandomStream
 * without extending @ref RandomStreamBase.
 *
 * <div class="SSJ-bigskip"></div>
 */
public class LFSR258Direct implements CloneableRandomStream, Serializable {

   private static final long serialVersionUID = 140406L;

   private static final double NORM = 0.5 / 0x7FFFFFFFFFFFFC00L;
   private static final double MAX = 0xFFFFFFFFFFFFF800L * NORM + 1.0;
   private static final double INVTWO24 = 5.9604644775390625e-8;
   private static final double EPSILON = 5.5511151231257827e-17;

   private static final long GERME = 123456789123456789L;

   private String name = null;
   private boolean prec53 = false;
   private boolean anti = false;

   private long z0, z1, z2, z3, z4; // l'etat

   // stream and substream variables :
   private long[] stream;
   private long[] substream;
   private static long[] curr_stream = { GERME, GERME, GERME, GERME, GERME };

   /**
    * Constructs a new stream.
    */
   public LFSR258Direct() {
      name = null;

      stream = new long[5];
      substream = new long[5];

      for (int i = 0; i < 5; i++)
         stream[i] = curr_stream[i];

      resetStartStream();

      // Les operations qui suivent permettent de faire sauter en avant
      // de 2^200 iterations chacunes des composantes du generateur.
      // L'etat interne apres le saut est cependant legerement different
      // de celui apres 2^200 iterations puisqu'il ignore l'etat dans
      // lequel se retrouvent les premiers bits de chaque composantes,
      // puisqu'ils sont ignores dans la recurrence. L'etat redevient
      // identique a ce que l'on aurait avec des iterations normales
      // apres un appel a nextDouble().

      long z, b;

      z = curr_stream[0] & 0xfffffffffffffffeL;
      b = z ^ (z << 1);
      z = (b >>> 58) ^ (b >>> 55) ^ (b >>> 46) ^ (b >>> 43) ^ (z << 5) ^ (z << 8) ^ (z << 17) ^ (z << 20);
      curr_stream[0] = z;

      z = curr_stream[1] & 0xfffffffffffffe00L;
      b = z ^ (z << 24);
      z = (b >>> 54) ^ (b >>> 53) ^ (b >>> 52) ^ (b >>> 50) ^ (b >>> 49) ^ (b >>> 48) ^ (b >>> 43) ^ (b >>> 41)
            ^ (b >>> 38) ^ (b >>> 37) ^ (b >>> 30) ^ (b >>> 25) ^ (b >>> 24) ^ (b >>> 23) ^ (b >>> 19) ^ (b >>> 16)
            ^ (b >>> 15) ^ (b >>> 14) ^ (b >>> 13) ^ (b >>> 11) ^ (b >>> 8) ^ (b >>> 7) ^ (b >>> 5) ^ (b >>> 3)
            ^ (z << 0) ^ (z << 2) ^ (z << 3) ^ (z << 6) ^ (z << 7) ^ (z << 8) ^ (z << 9) ^ (z << 10) ^ (z << 11)
            ^ (z << 12) ^ (z << 13) ^ (z << 14) ^ (z << 16) ^ (z << 18) ^ (z << 19) ^ (z << 21) ^ (z << 25) ^ (z << 30)
            ^ (z << 31) ^ (z << 32) ^ (z << 36) ^ (z << 39) ^ (z << 40) ^ (z << 41) ^ (z << 42) ^ (z << 44) ^ (z << 47)
            ^ (z << 48) ^ (z << 50) ^ (z << 52);
      curr_stream[1] = z;

      z = curr_stream[2] & 0xfffffffffffff000L;
      b = z ^ (z << 3);
      z = (b >>> 50) ^ (b >>> 49) ^ (b >>> 46) ^ (b >>> 42) ^ (b >>> 40) ^ (b >>> 39) ^ (b >>> 38) ^ (b >>> 37)
            ^ (b >>> 36) ^ (b >>> 32) ^ (b >>> 29) ^ (b >>> 28) ^ (b >>> 27) ^ (b >>> 25) ^ (b >>> 23) ^ (b >>> 20)
            ^ (b >>> 19) ^ (b >>> 15) ^ (b >>> 12) ^ (b >>> 11) ^ (b >>> 2) ^ (z << 1) ^ (z << 2) ^ (z << 3) ^ (z << 6)
            ^ (z << 10) ^ (z << 12) ^ (z << 13) ^ (z << 14) ^ (z << 15) ^ (z << 16) ^ (z << 20) ^ (z << 23) ^ (z << 24)
            ^ (z << 25) ^ (z << 27) ^ (z << 29) ^ (z << 32) ^ (z << 33) ^ (z << 37) ^ (z << 40) ^ (z << 41) ^ (z << 50);
      curr_stream[2] = z;

      z = curr_stream[3] & 0xfffffffffffe0000L;
      b = z ^ (z << 5);
      z = (b >>> 46) ^ (b >>> 44) ^ (b >>> 42) ^ (b >>> 41) ^ (b >>> 40) ^ (b >>> 38) ^ (b >>> 36) ^ (b >>> 32)
            ^ (b >>> 30) ^ (b >>> 25) ^ (b >>> 18) ^ (b >>> 16) ^ (b >>> 15) ^ (b >>> 14) ^ (b >>> 12) ^ (b >>> 11)
            ^ (b >>> 10) ^ (b >>> 9) ^ (b >>> 8) ^ (b >>> 6) ^ (b >>> 5) ^ (b >>> 4) ^ (b >>> 3) ^ (b >>> 2) ^ (z << 2)
            ^ (z << 5) ^ (z << 6) ^ (z << 7) ^ (z << 9) ^ (z << 11) ^ (z << 15) ^ (z << 17) ^ (z << 22) ^ (z << 29)
            ^ (z << 31) ^ (z << 32) ^ (z << 33) ^ (z << 35) ^ (z << 36) ^ (z << 37) ^ (z << 38) ^ (z << 39) ^ (z << 41)
            ^ (z << 42) ^ (z << 43) ^ (z << 44) ^ (z << 45);
      curr_stream[3] = z;

      z = curr_stream[4] & 0xffffffffff800000L;
      b = z ^ (z << 3);
      z = (b >>> 40) ^ (b >>> 29) ^ (b >>> 10) ^ (z << 1) ^ (z << 12) ^ (z << 31);
      curr_stream[4] = z;
   }

   /**
    * Constructs a new stream with the identifier `name`.
    *
    * @param name name of the stream
    */
   public LFSR258Direct(String name) {
      this();
      this.name = name;
   }

   /**
    * Sets the initial seed for the class `LFSR258Direct` to the five integers of
    * array `seed[0..4]`.
    *
    * @param seed array of 5 elements representing the seed
    */
   public static void setPackageSeed(long seed[]) {
      checkSeed(seed);
      for (int i = 0; i < 5; i++)
         curr_stream[i] = seed[i];
   }

   private static void checkSeed(long seed[]) {
      if (seed.length < 5)
         throw new IllegalArgumentException("Seed must contain 5 values");
      if ((seed[0] >= 0 && seed[0] < 2) || (seed[1] >= 0 && seed[1] < 512) || (seed[2] >= 0 && seed[2] < 4096)
            || (seed[3] >= 0 && seed[3] < 131072) || (seed[4] >= 0 && seed[4] < 8388608))
         throw new IllegalArgumentException(
               "The seed elements must be either negative or greater than 1, 511, 4095, 131071 and 8388607 respectively");
   }

   /**
    * Initializes the stream at the beginning of a stream with the initial seed
    * `seed[0..4]`.
    *
    * @param seed array of 5 elements representing the seed
    */
   public void setSeed(long seed[]) {
      checkSeed(seed);
      for (int i = 0; i < 5; i++)
         stream[i] = seed[i];
      resetStartStream();
   }

   /**
    * Returns the current state of the stream, represented as an array of five
    * integers.
    *
    * @return the current state of the stream
    */
   public long[] getState() {
      return new long[] { z0, z1, z2, z3, z4 };
   }

   /**
    * After calling this method with `incp = true`, each call to the RNG will
    * return a uniform random number with more bits of precision.
    *
    * @param incp if the generator will be set to high precision mode
    */
   public void increasedPrecision(boolean incp) {
      prec53 = incp;
   }

   /**
    * Clones the current generator and return its copy.
    *
    * @return A deep copy of the current generator
    */
   public LFSR258Direct clone() {
      LFSR258Direct retour = null;
      try {
         retour = (LFSR258Direct) super.clone();
      } catch (CloneNotSupportedException cnse) {
         cnse.printStackTrace(System.err);
      }
      retour.stream = new long[5];
      retour.substream = new long[5];
      for (int i = 0; i < 5; i++) {
         retour.substream[i] = substream[i];
         retour.stream[i] = stream[i];
      }
      return retour;
   }

   public void resetStartStream() {
      for (int i = 0; i < 5; i++)
         substream[i] = stream[i];
      resetStartSubstream();
   }

   public void resetStartSubstream() {
      z0 = substream[0];
      z1 = substream[1];
      z2 = substream[2];
      z3 = substream[3];
      z4 = substream[4];
   }

   public void resetNextSubstream() {
      // Les operations qui suivent permettent de faire sauter en avant
      // de 2^100 iterations chacunes des composantes du generateur.
      // L'etat interne apres le saut est cependant legerement different
      // de celui apres 2^100 iterations puisqu'il ignore l'etat dans
      // lequel se retrouvent les premiers bits de chaque composantes,
      // puisqu'ils sont ignores dans la recurrence. L'etat redevient
      // identique a ce que l'on aurait avec des iterations normales
      // apres un appel a nextDouble().

      long z, b;

      z = substream[0] & 0xfffffffffffffffeL;
      b = z ^ (z << 1);
      z = (b >>> 61) ^ (b >>> 59) ^ (b >>> 58) ^ (b >>> 57) ^ (b >>> 51) ^ (b >>> 47) ^ (b >>> 46) ^ (b >>> 45)
            ^ (b >>> 43) ^ (b >>> 39) ^ (b >>> 30) ^ (b >>> 29) ^ (b >>> 23) ^ (b >>> 15) ^ (z << 2) ^ (z << 4)
            ^ (z << 5) ^ (z << 6) ^ (z << 12) ^ (z << 16) ^ (z << 17) ^ (z << 18) ^ (z << 20) ^ (z << 24) ^ (z << 33)
            ^ (z << 34) ^ (z << 40) ^ (z << 48);
      substream[0] = z;

      z = substream[1] & 0xfffffffffffffe00L;
      b = z ^ (z << 24);
      z = (b >>> 52) ^ (b >>> 50) ^ (b >>> 49) ^ (b >>> 46) ^ (b >>> 43) ^ (b >>> 40) ^ (b >>> 37) ^ (b >>> 34)
            ^ (b >>> 30) ^ (b >>> 28) ^ (b >>> 26) ^ (b >>> 25) ^ (b >>> 23) ^ (b >>> 21) ^ (b >>> 20) ^ (b >>> 19)
            ^ (b >>> 17) ^ (b >>> 15) ^ (b >>> 13) ^ (b >>> 12) ^ (b >>> 10) ^ (b >>> 8) ^ (b >>> 7) ^ (b >>> 6)
            ^ (b >>> 2) ^ (z << 1) ^ (z << 4) ^ (z << 6) ^ (z << 7) ^ (z << 11) ^ (z << 14) ^ (z << 15) ^ (z << 16)
            ^ (z << 17) ^ (z << 21) ^ (z << 22) ^ (z << 25) ^ (z << 27) ^ (z << 29) ^ (z << 30) ^ (z << 32) ^ (z << 34)
            ^ (z << 35) ^ (z << 36) ^ (z << 38) ^ (z << 40) ^ (z << 42) ^ (z << 43) ^ (z << 45) ^ (z << 47) ^ (z << 48)
            ^ (z << 49) ^ (z << 53);
      substream[1] = z;

      z = substream[2] & 0xfffffffffffff000L;
      b = z ^ (z << 3);
      z = (b >>> 49) ^ (b >>> 45) ^ (b >>> 41) ^ (b >>> 40) ^ (b >>> 32) ^ (b >>> 27) ^ (b >>> 23) ^ (b >>> 14)
            ^ (b >>> 1) ^ (z << 2) ^ (z << 3) ^ (z << 7) ^ (z << 11) ^ (z << 12) ^ (z << 20) ^ (z << 25) ^ (z << 29)
            ^ (z << 38) ^ (z << 51);
      substream[2] = z;

      z = substream[3] & 0xfffffffffffe0000L;
      b = z ^ (z << 5);
      z = (b >>> 45) ^ (b >>> 32) ^ (b >>> 27) ^ (b >>> 22) ^ (b >>> 17) ^ (b >>> 13) ^ (b >>> 12) ^ (b >>> 7)
            ^ (b >>> 3) ^ (b >>> 2) ^ (z << 3) ^ (z << 15) ^ (z << 20) ^ (z << 25) ^ (z << 30) ^ (z << 34) ^ (z << 35)
            ^ (z << 40) ^ (z << 44) ^ (z << 45);
      substream[3] = z;

      z = substream[4] & 0xffffffffff800000L;
      b = z ^ (z << 3);
      z = (b >>> 40) ^ (b >>> 39) ^ (b >>> 38) ^ (b >>> 37) ^ (b >>> 35) ^ (b >>> 34) ^ (b >>> 31) ^ (b >>> 30)
            ^ (b >>> 29) ^ (b >>> 28) ^ (b >>> 27) ^ (b >>> 26) ^ (b >>> 24) ^ (b >>> 23) ^ (b >>> 21) ^ (b >>> 20)
            ^ (b >>> 18) ^ (b >>> 15) ^ (b >>> 12) ^ (b >>> 10) ^ (b >>> 9) ^ (b >>> 7) ^ (b >>> 6) ^ (b >>> 5)
            ^ (b >>> 4) ^ (b >>> 3) ^ (z << 1) ^ (z << 2) ^ (z << 3) ^ (z << 4) ^ (z << 6) ^ (z << 7) ^ (z << 10)
            ^ (z << 11) ^ (z << 12) ^ (z << 13) ^ (z << 14) ^ (z << 15) ^ (z << 17) ^ (z << 18) ^ (z << 20) ^ (z << 21)
            ^ (z << 23) ^ (z << 26) ^ (z << 29) ^ (z << 31) ^ (z << 32) ^ (z << 34) ^ (z << 35) ^ (z << 36) ^ (z << 37)
            ^ (z << 38);
      substream[4] = z;

      resetStartSubstream();
   }

   public String toString() {
      if (name == null)
         return "The state of the LFSR258Direct is: " + z0 + "L, " + z1 + "L, " + z2 + "L, " + z3 + "L, " + z4 + "L";
      else
         return "The state of " + name + " is: " + z0 + "L, " + z1 + "L, " + z2 + "L, " + z3 + "L, " + z4 + "L";
   }

   private long nextNumber() {
      long b;
      b = (((z0 << 1) ^ z0) >>> 53);
      z0 = (((z0 & 0xFFFFFFFFFFFFFFFEL) << 10) ^ b);
      b = (((z1 << 24) ^ z1) >>> 50);
      z1 = (((z1 & 0xFFFFFFFFFFFFFE00L) << 5) ^ b);
      b = (((z2 << 3) ^ z2) >>> 23);
      z2 = (((z2 & 0xFFFFFFFFFFFFF000L) << 29) ^ b);
      b = (((z3 << 5) ^ z3) >>> 24);
      z3 = (((z3 & 0xFFFFFFFFFFFE0000L) << 23) ^ b);
      b = (((z4 << 3) ^ z4) >>> 33);
      z4 = (((z4 & 0xFFFFFFFFFF800000L) << 8) ^ b);
      return (z0 ^ z1 ^ z2 ^ z3 ^ z4);
   }

   @Override
   public double nextDouble() {
      long res = nextNumber();
      double u = (res <= 0) ? (res * NORM + MAX) : res * NORM;
      if (prec53) {
         res = nextNumber();
         double v = (res <= 0) ? (res * NORM + MAX) : res * NORM;
         u = (u + v * INVTWO24) % 1.0 + EPSILON;
      }
      if (anti)
         return 1.0 - u;
      else
         return u;
   }

   @Override
   public void nextArrayOfDouble(double[] u, int start, int n) {
      if (u.length == 0)
         throw new NullPointerException("The array must be initialized.");
      if (u.length < n + start)
         throw new IndexOutOfBoundsException("The array is too small.");
      if (start < 0)
         throw new IndexOutOfBoundsException("Must start at a " + "non-negative index.");
      if (n < 0)
         throw new IllegalArgumentException("Must have a non-negative " + "number of elements.");

      for (int ii = start; ii < start + n; ii++)
         u[ii] = nextDouble();
   }

   @Override
   public int nextInt(int i, int j) {
      return (int) nextLong((long) i, (long) j);
   }

   @Override
   public void nextArrayOfInt(int i, int j, int[] u, int start, int n) {
      if (u == null)
         throw new NullPointerException("The array must be " + "initialized.");
      if (u.length < n + start)
         throw new IndexOutOfBoundsException("The array is too small.");
      if (start < 0)
         throw new IndexOutOfBoundsException("Must start at a " + "non-negative index.");
      if (n < 0)
         throw new IllegalArgumentException("Must have a non-negative " + "number of elements.");

      for (int ii = start; ii < start + n; ii++)
         u[ii] = nextInt(i, j);
   }

   @Override
   public long nextLong(long i, long j) {
      if (i > j)
         throw new IllegalArgumentException(i + " is larger than " + j + ".");
      long d = j - i + 1;
      long q = 0x4000000000000000L / d;
      long r = 0x4000000000000000L % d;
      long res;
      do {
         res = nextNumber() >>> 2;
      } while (res >= 0x4000000000000000L - r);

      return i + (res / q);
   }

   @Override
   public void nextArrayOfLong(long i, long j, long[] u, int start, int n) {
      if (u == null)
         throw new NullPointerException("The array must be " + "initialized.");
      if (u.length < n + start)
         throw new IndexOutOfBoundsException("The array is too small.");
      if (start < 0)
         throw new IndexOutOfBoundsException("Must start at a " + "non-negative index.");
      if (n < 0)
         throw new IllegalArgumentException("Must have a non-negative " + "number of elements.");

      for (int ii = start; ii < start + n; ii++)
         u[ii] = nextLong(i, j);
   }

}
