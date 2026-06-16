package rqmcexperiments;

import umontreal.ssj.hups64.PointSet;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.rng.RandomStream;

// Randomization for the SSJ-based 30-bit Sobol-Owen point set.
// It only changes the seed before each RQMC repetition.
public class HashBasedSobolSSJ30BitRandomization implements PointSetRandomization {
   private RandomStream stream;

   public HashBasedSobolSSJ30BitRandomization(RandomStream stream) {
      this.stream = stream;
   }

   @Override
   public void randomize(PointSet p) {
      if (!(p instanceof HashBasedSobolSSJ30BitPointSet))
         throw new IllegalArgumentException(
               "HashBasedSobolSSJ30BitRandomization requires a HashBasedSobolSSJ30BitPointSet");

      HashBasedSobolSSJ30BitPointSet pointSet = (HashBasedSobolSSJ30BitPointSet) p;
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
