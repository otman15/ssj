package rqmcexperiments;

import umontreal.ssj.hups64.PointSet;

// Minimal PointSet adapter for the SSJ-based 30-bit Sobol-Owen adaptation.
// This keeps the hash-based shuffle + scramble idea, but uses SobolOwenSSJ30Bit
// instead of the exact 32-bit Burley/C++ direction table.
public class HashBasedSobolSSJ30BitPointSet extends PointSet {
   private final int n;
   private final int s;
   private final SobolOwenSSJ30Bit sobolOwen;
   private int hashedSeed;

   public HashBasedSobolSSJ30BitPointSet(int k, int s, int seed) {
      if (k < 0 || k > 30)
         throw new IllegalArgumentException("k must be between 0 and 30");
      if (s <= 0)
         throw new IllegalArgumentException("s must be positive");

      this.n = 1 << k;
      this.s = s;
      this.sobolOwen = new SobolOwenSSJ30Bit(s);
      this.numPoints = n;
      this.dim = s;
      setSeed(seed);
   }

   public void setSeed(int seed) {
      this.hashedSeed = SobolOwen.hash(seed);
   }

   @Override
   public double getCoordinate(int i, int j) {
      if (i < 0 || i >= n)
         throw new IllegalArgumentException("point index out of range");
      if (j < 0 || j >= s)
         throw new IllegalArgumentException("coordinate index out of range");

      int value = sobolOwen.sobol_owen(i, j, hashedSeed);
      return SobolOwenSSJ30Bit.toDouble(value);
   }
}
