package rqmcexperiments;

import umontreal.ssj.hups64.PointSet;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.rng.RandomStream;

public class HashBasedSobolRandomization implements PointSetRandomization {
   private RandomStream stream;

   public HashBasedSobolRandomization(RandomStream stream) {
      this.stream = stream;
   }

   @Override
   public void randomize(PointSet p) {
      if (!(p instanceof HashBasedSobolPointSet))
         throw new IllegalArgumentException(
               "HashBasedSobolRandomization requires a HashBasedSobolPointSet");

      HashBasedSobolPointSet pointSet = (HashBasedSobolPointSet) p;
      if (pointSet.getSeedMode() == HashBasedSobolPointSet.SeedMode.ORIGINAL_BURLEY) {
         pointSet.setSeed((int) stream.nextLong(0, 0xffffffffL));
      }
      else {
         int indexSeed = (int) stream.nextLong(0, 0xffffffffL);
         int[] dimSeeds = new int[pointSet.getS()];
         for (int j = 0; j < pointSet.getS(); j++)
            dimSeeds[j] = (int) stream.nextLong(0, 0xffffffffL);
         pointSet.setIndependentSeeds(indexSeed, dimSeeds);
      }
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
