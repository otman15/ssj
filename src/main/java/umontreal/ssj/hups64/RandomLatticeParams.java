/*
 * Class:        RandomLatticeParams
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
package umontreal.ssj.hups64;

import umontreal.ssj.rng.RandomStream;

/**
 * This type of randomization applies to a lattice rule of rank 1. It permit one
 * to randomize the generating vector and perhaps also the number of points of
 * the lattice rule. The randomization itself is always performed by
 * `randomize(PointSet)`, where `PointSet` must be a `Rank1Lattice`.
 * 
 * ... to be continued and implemented.
 *
 * <div class="SSJ-bigskip"></div><div class="SSJ-bigskip"></div>
 */
public class RandomLatticeParams implements PointSetRandomization {

   protected RandomStream stream;
   protected int nmin = 0, nmax = 0;  // Bounds for n when generated as a random prime.
   protected boolean nPow2 = false;  // True when n is a power of 2.
   protected boolean randShift = true;

   /**
    * Empty constructor: No stream is passed here for the randomization; one must
    * be passed later by #setStream. **Pierre:** Not sure if we should keep this;
    * we always need a stream!
    */
   public RandomLatticeParams() {
   }

   /**
    * Constructor that sets the internal @ref umontreal.ssj.rng.RandomStream to
    * `stream`.
    * 
    * @param stream stream to use in the randomization
    */
   public RandomLatticeParams(RandomStream stream) {
      this.stream = stream;
   }

   /**
    * This constructor also sets the boolean `nPow2` for the case when `n` is a
    * fixed power of 2, so all the coordinates of the generating vector will have
    * to be odd numbers.
    * 
    * @param stream stream to use in the randomization
    */
   public RandomLatticeParams(boolean nPow2, RandomStream stream) {
      this.nPow2 = nPow2;
      this.stream = stream;
   }

   /**
    * This constructor is for when we want to generate the number of points as a
    * random prime number strictly between `nmin` and `nmax`,
    * 
    * @param stream stream to use in the randomization
    */
   public RandomLatticeParams(int nmin, int nmax, RandomStream stream) {
      this.stream = stream;
      this.nmin = nmin;
      this.nmax = nmax;
   }

   /**
    * Randomize the point set according to the selected options.
    * 
    * @param p Point set to randomize
    */
   public void randomize(PointSet p) {
      if (p instanceof Rank1Lattice) {
         if (nPow2)
            ((Rank1Lattice) p).setRandomAforPow2n(stream);
         else {
            if (nmax == 0)
               ((Rank1Lattice) p).setRandomAforPrimen(stream);
            else
               ((Rank1Lattice) p).setRandomAandn(nmin, nmax, stream);
         }
         if (randShift)
            ((Rank1Lattice) p).addRandomShift(stream);
      } else if (p instanceof ContainerPointSet) {
         randomize(((ContainerPointSet) p).getOriginalPointSet());
      } else if (p instanceof CachedPointSet) {
         randomize(((CachedPointSet) p).getParentPointSet());
      } else
         throw new IllegalArgumentException("RandomLatticeParams" + " can only randomize a Rank1Lattice");
   }

   /**
    * Sets the internal `randShift` of this object. A random shift will be applied
    * iff it is set to `true`, which is the default value.
    * 
    * @param boolean randShift indicates if we use a random shift or not.
    */
   public void setRandShift(boolean randShift) {
      this.randShift = randShift;
   }

   /**
    * Sets the internal @ref umontreal.ssj.rng.RandomStream to `stream`.
    * 
    * @param stream stream to use in the randomization
    */
   public void setStream(RandomStream stream) {
      this.stream = stream;
   }

   /**
    * Returns the internal @ref umontreal.ssj.rng.RandomStream.
    * 
    * @return stream used in the randomization
    */
   public RandomStream getStream() {
      return stream;
   }

   /**
    * Resets the parameter `nPow2`.
    * 
    * @param stream stream to use in the randomization
    */
   public void setPow2(boolean nPow2) {
      this.nPow2 = nPow2;
   }
   
   /**
    * Returns a descriptor of this object.
    */
   public String toString() {
      return "Random shift (digital if applied to a digital net)";
   }

}