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
