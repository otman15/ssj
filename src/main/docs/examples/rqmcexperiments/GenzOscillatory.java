package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;

/**
 * Implements the general Genz oscillatory function taken from @cite iGEN87a.
 *
 * The function is defined by
 * @f[
 *   f(u_1,\dots,u_s) =
 *   \cos\left(2\pi w_1 + \sum_{j=1}^s c_j u_j\right),
 * @f]
 * for @f$\bm u = (u_1,\dots,u_s) \in [0,1]^s@f$.
 */
public class GenzOscillatory implements MonteCarloModelDouble {

   private final int s;
   private final double[] c;
   private final double w1;
   private final double exactMean;

   private double sum;

   /**
    * Constructs a Genz oscillatory function with user-specified parameters.
    *
    * @param s dimension of the function
    * @param c scale parameters, all strictly positive
    * @param w1 phase parameter in the open interval @f$(0,1)@f$
    */
   public GenzOscillatory(int s, double[] c, double w1) {
      if (s <= 0)
         throw new IllegalArgumentException("s must be positive");
      if (c == null || c.length != s)
         throw new IllegalArgumentException("c must have length s");
      if (!(w1 > 0.0 && w1 < 1.0))
         throw new IllegalArgumentException("w1 must be in (0, 1)");

      for (int j = 0; j < s; j++) {
         if (!(c[j] > 0.0))
            throw new IllegalArgumentException("c[" + j + "] must be positive");
      }

      this.s = s;
      this.c = c.clone();
      this.w1 = w1;
      this.exactMean = computeExactMean();
   }

   /**
    * Simulates one observation of the model.
    *
    * @param stream random stream used to generate the coordinates
    */
   @Override
   public void simulate(RandomStream stream) {
      sum = 0.0;
      for (int j = 0; j < s; j++)
         sum += c[j] * stream.nextDouble();
   }

   /**
    * Returns the centered performance from the last simulation.
    *
    * @return function value minus the exact mean
    */
   @Override
   public double getPerformance() {
      return Math.cos(2.0 * Math.PI * w1 + sum) - exactMean;
   }

   /**
    * Computes the exact mean of the Genz oscillatory function.
    *
    * @return exact integral over @f$[0,1]^s@f$
    */
   private double computeExactMean() {
      double prod = 1.0;
      double halfSum = 0.0;

      for (int j = 0; j < s; j++) {
         prod *= 2.0 * Math.sin(c[j] / 2.0) / c[j];
         halfSum += c[j] / 2.0;
      }

      return Math.cos(2.0 * Math.PI * w1 + halfSum) * prod;
   }

   @Override
   public String toString() {
      return "Genz oscillatory function";
   }

   @Override
   public String getTag() {
      return "GenzOscillatory";
   }
   
   /////////for test
   public double getExactMean() {
	   return exactMean;
	}
}