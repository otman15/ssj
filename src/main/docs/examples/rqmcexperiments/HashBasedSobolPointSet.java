package rqmcexperiments;

import umontreal.ssj.hups64.PointSet;

// Minimal PointSet adapter for Burley's hash-based Sobol scrambling prototype.
public class HashBasedSobolPointSet extends PointSet {
   private final int n;
   private final int s;
   private int hashedSeed;

   public HashBasedSobolPointSet(int k, int s, int seed) {
      if (k < 0 || k > 30)
         throw new IllegalArgumentException("k must be between 0 and 30");
      if (s <= 0 || s > 5)
         throw new IllegalArgumentException("s must be between 1 and 5");

      this.n = 1 << k;
      this.s = s;
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
      return coordinate(i, j);
   }
   
   private double coordinate(int i, int j) {
      int value = SobolOwen.sobol_owen(i, j, hashedSeed);
      return Integer.toUnsignedLong(value) * 0x1.0p-32;
   }
}
