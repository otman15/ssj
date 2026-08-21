package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Num;

/**
 * Implements a Gaussian function from Genz @cite iGEN87a, defined as 
 * @f[
 *   f(u_1,\dots,u_s) = \exp\left(-\sum_{j=1}^s u_j^2\right) - \mu
 * @f]
 * for @f$\bm u = (u_1,\dots,u_s) \in [0,1]^s@f$, where
 * @f$\mu = \left(\sqrt{\pi}\operatorname{erf}(1)/2\right)^s@f$.
 */
public class Gaussian implements MonteCarloModelDouble {

   int s;
   double sum;
   double exactMean;

   // Constructor.
   public Gaussian(int s) {
      this.s = s;
      exactMean = Math.pow(Math.sqrt(Math.PI) * Num.erf(1.0) / 2.0, s);
   }

   // Generates and returns X, without IS.
   public void simulate(RandomStream stream) {
      sum = 0.0;
      double u;
      for (int j = 0; j < s; j++) {
         u = stream.nextDouble();
         sum += u * u;
      }
   }

   // Generates and returns X, without IS.
   public double getPerformance() {
      return Math.exp(-sum) - exactMean;
   }

   // Descriptor of this model.
   @Override
   public String toString() {
      return "Gaussian: centered exponential of minus the sum of squares";
   }

   // Short descriptor (tag) for this model.
   @Override
   public String getTag() {
      return "Gaussian";
   }
}
