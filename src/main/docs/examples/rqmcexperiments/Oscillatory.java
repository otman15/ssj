package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;

/**
 * Implements an oscillatory function from Genz @cite iGEN87a, defined as 
 * @f[
 *   f(u_1,\dots,u_s) = \cos\left(\sum_{j=1}^s a_j u_j \right) - \mu
 * @f]
 * for @f$\bm u = (u_1,\dots,u_s) \in [0,1]^s@f$, with @f$a_j = j/s@f$.
 * Here
 * @f$\mu = \cos((s+1)/4)\prod_{j=1}^s 2\sin(a_j/2)/a_j@f$.
 * This function is smooth, but not one-periodic.
 */
public class Oscillatory implements MonteCarloModelDouble {

   int s;
   double sum;
   double[] a;
   double exactMean;

   // Constructor.
   public Oscillatory(int s) {
      this.s = s;
      a = new double[s];
      double prod = 1.0;
      for (int j = 0; j < s; j++) {
         a[j] = (double) (j + 1) / (double) s;
         prod *= 2.0 * Math.sin(a[j] / 2.0) / a[j];
      }
      exactMean = Math.cos((s + 1.0) / 4.0) * prod;
   }

   // Generates the values and compute the sum.   
   public void simulate(RandomStream stream) {
      sum = 0.0;
      for (int j = 0; j < s; j++) {
         sum += a[j] * stream.nextDouble();
      }
   }

   // Return the centered value X of the function.
   public double getPerformance() {
      return Math.cos(sum) - exactMean;
   }

   // Descriptor.
   @Override
   public String toString() {
      return "Oscillatory function from Genz";
   }

   // Short descriptor (tag) for this model.
   @Override
   public String getTag() {
      return "Oscillatory";
   }
}
