package rqmcexperiments;

import umontreal.ssj.hups64.PointSet;

// Minimal PointSet adapter for Burley's hash-based Sobol scrambling prototype.
public class HashBasedSobolPointSet extends PointSet {
   public enum SeedMode { // Added test, not in the article
      ORIGINAL_BURLEY,
      INDEPENDENT_SEEDS
   }

   private final int k;
   private final int n;
   private final int s;
   private final boolean shuffleIndex;
   private final SeedMode seedMode;
   private int seed;
   private int hashedSeed;
   private int indexSeed;
   private final int[] dimSeeds;

   public HashBasedSobolPointSet(int k, int s, int seed, boolean shuffleIndex) {
      this(k, s, seed, shuffleIndex, SeedMode.ORIGINAL_BURLEY);
   }

   public HashBasedSobolPointSet(int k, int s, int seed, boolean shuffleIndex, SeedMode seedMode) {
      if (k < 0 || k > 30)
         throw new IllegalArgumentException("k must be between 0 and 30");
      if (s <= 0 || s > 5)
         throw new IllegalArgumentException("s must be between 1 and 5");
      if (seedMode == null)
         throw new NullPointerException("seedMode");

      this.k = k;
      this.n = 1 << k;
      this.s = s;
      this.shuffleIndex = shuffleIndex;
      this.seedMode = seedMode;
      this.dimSeeds = new int[s];
      this.numPoints = n;
      this.dim = s;
      setSeed(seed);
   }

   public int getK() {
      return k;
   }

   public int getN() {
      return n;
   }

   public int getS() {
      return s;
   }

   public int getSeed() {
      return seed;
   }

   public SeedMode getSeedMode() {
      return seedMode;
   }

   public void setSeed(int seed) {
      this.seed = seed;
      this.hashedSeed = SobolOwen.hash(seed);
      this.indexSeed = hashedSeed;
      for (int j = 0; j < s; j++)
         this.dimSeeds[j] = SobolOwen.hashCombine(hashedSeed, j);
   }

   public void setIndependentSeeds(int indexSeed, int[] dimSeeds) {
      if (dimSeeds.length < s)
         throw new IllegalArgumentException("dimSeeds must have at least s entries");
      this.indexSeed = indexSeed;
      for (int j = 0; j < s; j++)
         this.dimSeeds[j] = dimSeeds[j];
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
      int value;
      if (shuffleIndex) {
         int shuffledIndex = SobolOwen.nestedUniformScrambleBase2(i, indexSeed);
         value = SobolOwen.nestedUniformScrambleBase2(
               SobolOwen.sobol(shuffledIndex, j),
               dimSeeds[j]);
      }
      else {
         value = SobolOwen.nestedUniformScrambleBase2(
               SobolOwen.sobol(i, j),
               dimSeeds[j]);
      }
      return Integer.toUnsignedLong(value) * 0x1.0p-32;
   }
}
