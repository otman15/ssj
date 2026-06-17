package rqmcexperiments;

import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.rng.RandomStream;

// Burley hash-based Sobol-Owen generator padded beyond four dimensions.
public class BurleyPaddedSobol {
   private static final int[][] DIRECTIONS_4D = {
      {
         0x80000000, 0x40000000, 0x20000000, 0x10000000,
         0x08000000, 0x04000000, 0x02000000, 0x01000000,
         0x00800000, 0x00400000, 0x00200000, 0x00100000,
         0x00080000, 0x00040000, 0x00020000, 0x00010000,
         0x00008000, 0x00004000, 0x00002000, 0x00001000,
         0x00000800, 0x00000400, 0x00000200, 0x00000100,
         0x00000080, 0x00000040, 0x00000020, 0x00000010,
         0x00000008, 0x00000004, 0x00000002, 0x00000001
      },
      {
         0x80000000, 0xc0000000, 0xa0000000, 0xf0000000,
         0x88000000, 0xcc000000, 0xaa000000, 0xff000000,
         0x80800000, 0xc0c00000, 0xa0a00000, 0xf0f00000,
         0x88880000, 0xcccc0000, 0xaaaa0000, 0xffff0000,
         0x80008000, 0xc000c000, 0xa000a000, 0xf000f000,
         0x88008800, 0xcc00cc00, 0xaa00aa00, 0xff00ff00,
         0x80808080, 0xc0c0c0c0, 0xa0a0a0a0, 0xf0f0f0f0,
         0x88888888, 0xcccccccc, 0xaaaaaaaa, 0xffffffff
      },
      {
         0x80000000, 0xc0000000, 0x60000000, 0x90000000,
         0xe8000000, 0x5c000000, 0x8e000000, 0xc5000000,
         0x68800000, 0x9cc00000, 0xee600000, 0x55900000,
         0x80680000, 0xc09c0000, 0x60ee0000, 0x90550000,
         0xe8808000, 0x5cc0c000, 0x8e606000, 0xc5909000,
         0x6868e800, 0x9c9c5c00, 0xeeee8e00, 0x5555c500,
         0x8000e880, 0xc0005cc0, 0x60008e60, 0x9000c590,
         0xe8006868, 0x5c009c9c, 0x8e00eeee, 0xc5005555
      },
      {
         0x80000000, 0xc0000000, 0x20000000, 0x50000000,
         0xf8000000, 0x74000000, 0xa2000000, 0x93000000,
         0xd8800000, 0x25400000, 0x59e00000, 0xe6d00000,
         0x78080000, 0xb40c0000, 0x82020000, 0xc3050000,
         0x208f8000, 0x51474000, 0xfbea2000, 0x75d93000,
         0xa0858800, 0x914e5400, 0xdbe79e00, 0x25db6d00,
         0x58800080, 0xe54000c0, 0x79e00020, 0xb6d00050,
         0x800800f8, 0xc00c0074, 0x200200a2, 0x50050093
      }
   };

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

   static int sobol4d(int index, int localDim) {
      if (localDim < 0 || localDim >= 4)
         throw new IllegalArgumentException("localDim must be between 0 and 3");

      int x = 0;
      for (int bit = 0; bit < 32; bit++) {
         int mask = (index >>> bit) & 1;
         x ^= mask * DIRECTIONS_4D[localDim][bit];
      }
      return x;
   }

   static int value(int index, int dim, int seed) {
      int group = dim / 4;
      int localDim = dim % 4;
      int seedGroup = group == 0 ? seed : hashCombine(seed, group);
      int shuffledIndex = nestedUniformScramble(index, seedGroup);
      int x = sobol4d(shuffledIndex, localDim);
      return nestedUniformScramble(x, hashCombine(seedGroup, localDim));
   }

   // Lazy SSJ point set: coordinates are generated on demand in getCoordinate.
   public static class PaddedPointSet extends umontreal.ssj.hups64.PointSet {
      private final int n;
      private final int s;
      private int hashedSeed;

      public PaddedPointSet(int k, int s, int seed) {
         if (k < 0 || k > 30)
            throw new IllegalArgumentException("k must be between 0 and 30");
         if (s <= 0)
            throw new IllegalArgumentException("s must be positive");

         this.n = 1 << k;
         this.s = s;
         this.numPoints = n;
         this.dim = s;
         setSeed(seed);
      }

      public void setSeed(int seed) {
         this.hashedSeed = hash(seed);
      }

      @Override
      public double getCoordinate(int i, int j) {
         if (i < 0 || i >= n)
            throw new IllegalArgumentException("point index out of range");
         if (j < 0 || j >= s)
            throw new IllegalArgumentException("coordinate index out of range");

         int x = value(i, j, hashedSeed);
         return Integer.toUnsignedLong(x) * 0x1.0p-32;
      }
   }

   // RQMC randomization: one replication seed, reused for every point.
   public static class Randomization implements PointSetRandomization {
      private RandomStream stream;

      public Randomization(RandomStream stream) {
         this.stream = stream;
      }

      @Override
      public void randomize(umontreal.ssj.hups64.PointSet p) {
         if (!(p instanceof PaddedPointSet))
            throw new IllegalArgumentException(
                  "BurleyPaddedSobol.Randomization requires a PaddedPointSet");

         PaddedPointSet pointSet = (PaddedPointSet) p;
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
