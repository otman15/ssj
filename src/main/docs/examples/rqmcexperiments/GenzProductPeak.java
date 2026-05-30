package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;

/**
 * Implements the general Genz product peak function taken from @cite iGEN87a.
 *
 * The function is defined by
 * @f[
 *   f(u_1,\dots,u_s) =
 *   \prod_{j=1}^s \left(c_j^{-2} + (u_j - w_j)^2\right)^{-1},
 * @f]
 * for @f$\bm u = (u_1,\dots,u_s) \in [0,1]^s@f$.
 */
public class GenzProductPeak implements MonteCarloModelDouble {

   private final int s;
   private final double[] c;
   private final double[] cInvSquared;
   private final double[] w;
   private final double exactMean;

   private double prod;

   /**
    * Constructs a Genz product peak function with user-specified parameters.
    *
    * @param s dimension of the function
    * @param c scale parameters, all strictly positive
    * @param w location parameters, all in the open interval @f$(0,1)@f$
    */
   public GenzProductPeak(int s, double[] c, double[] w) {
      if (s <= 0)
         throw new IllegalArgumentException("s must be positive");
      if (c == null || w == null || c.length != s || w.length != s)
         throw new IllegalArgumentException("c and w must have length s");

      this.s = s;
      this.c = c.clone();
      this.cInvSquared = new double[s];
      this.w = w.clone();

      for (int j = 0; j < s; j++) {
         if (!(this.c[j] > 0.0))
            throw new IllegalArgumentException("c[" + j + "] must be positive");
         if (!(this.w[j] > 0.0 && this.w[j] < 1.0))
            throw new IllegalArgumentException("w[" + j + "] must be in (0, 1)");

         cInvSquared[j] = 1.0 / (this.c[j] * this.c[j]);
      }

      this.exactMean = computeExactMean();
   }

   /**
    * Simulates one observation of the model.
    *
    * @param stream random stream used to generate the coordinates
    */
   @Override
   public void simulate(RandomStream stream) {
      prod = 1.0;
      for (int j = 0; j < s; j++) {
         double diff = stream.nextDouble() - w[j];
         prod *= 1.0 / (cInvSquared[j] + diff * diff);
      }
   }

   /**
    * Returns the centered performance from the last simulation.
    *
    * @return function value minus the exact mean
    */
   @Override
   public double getPerformance() {
      return prod - exactMean;
   }

   /**
    * Computes the exact mean of the Genz product peak function.
    *
    * @return exact integral over @f$[0,1]^s@f$
    */
   private double computeExactMean() {
      double integral = 1.0;

      for (int j = 0; j < s; j++)
         integral *= c[j] * (Math.atan(c[j] * w[j])
               + Math.atan(c[j] * (1.0 - w[j])));

      return integral;
   }

   @Override
   public String toString() {
      return "Genz product peak function";
   }

   @Override
   public String getTag() {
      return "GenzProductPeak";
   }
   
   /////////for test
   public double getExactMean() {
	   return exactMean;
	}
}