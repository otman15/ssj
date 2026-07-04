package rqmcexperiments;

import umontreal.ssj.hups64.DigitalNetBase2;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.rng.RandomStream;

// Burley hash-based Owen scrambling applied on top of an SSJ Sobol point set.
public class BurleyOwen {

   static int hash(int x) {
      x ^= x >>> 16;
      x *= 0x85ebca6b;
      x ^= x >>> 13;
      x *= 0xc2b2ae35;
      x ^= x >>> 16;
      return x;
   }

   static int hashCombine(int seed, int v) {
      return seed ^ (v + (seed << 6) + (seed >>> 2));
   }

   static int laineKarrasPermutation(int x, int seed) {
      x += seed;
      x ^= x * 0x6c50b47c;
      x ^= x * 0xb82f1e52;
      x ^= x * 0xc7afe638;
      x ^= x * 0x8d22f6e6;
      return x;
   }

   static int nestedUniformScramble(int x, int seed) {
      x = Integer.reverse(x);
      x = laineKarrasPermutation(x, seed);
      x = Integer.reverse(x);
      return x;
   }

   public static class BurleyPointSet extends umontreal.ssj.hups64.PointSet {
      private final DigitalNetBase2 baseSobol;
      private final int n;
      private final int s;
      private final int pointMask;
      private int hashedSeed;

      public BurleyPointSet(DigitalNetBase2 baseSobol, int seed) {
         if (baseSobol == null)
            throw new IllegalArgumentException("baseSobol must not be null");

         this.baseSobol = baseSobol;
         this.n = baseSobol.getNumPoints();
         this.s = baseSobol.getDimension();

         if (n <= 0 || (n & (n - 1)) != 0)
            throw new IllegalArgumentException("number of points must be a power of two");
         if (s <= 0)
            throw new IllegalArgumentException("dimension must be positive");

         this.pointMask = n - 1;
         this.numPoints = n;
         this.dim = s;

         setSeed(seed);
      }

      public void setSeed(int seed) {
         this.hashedSeed = hash(seed);
      }

      private int value(int index, int dim) {
         int shuffledIndex = nestedUniformScramble(index, hashedSeed) & pointMask;

         double u = baseSobol.getCoordinate(shuffledIndex, dim);

         long bits = (long) (u * 0x1.0p32);
         int x = (int) bits;

         return nestedUniformScramble(x, hashCombine(hashedSeed, dim));
      }

      @Override
      public double getCoordinate(int i, int j) {
         if (i < 0 || i >= n)
            throw new IllegalArgumentException("point index out of range");
         if (j < 0 || j >= s)
            throw new IllegalArgumentException("coordinate index out of range");

         int x = value(i, j);
         return Integer.toUnsignedLong(x) * 0x1.0p-32;
      }
   }

   public static class Randomization implements PointSetRandomization {
      private RandomStream stream;

      public Randomization(RandomStream stream) {
         this.stream = stream;
      }

      @Override
      public void randomize(umontreal.ssj.hups64.PointSet p) {
         if (!(p instanceof BurleyPointSet))
            throw new IllegalArgumentException(
                  "BurleyOwen.Randomization requires a BurleyPointSet");

         BurleyPointSet pointSet = (BurleyPointSet) p;
         pointSet.setSeed((int) stream.nextLong(0, 0xffffffffL));
      }

      @Override
      public void setStream(RandomStream stream) {
         this.stream = stream;
      }

      @Override
      public RandomStream getStream() {
         return stream;
      }
   }
}