package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Num;

/**
 * Implements the general Genz Gaussian peak function taken from @cite iGEN87a.
 *
 * The function is defined by
 * @f[
 *   f(u_1,\dots,u_s) =
 *   \exp\left(-\sum_{j=1}^s c_j^2 (u_j - w_j)^2\right),
 * @f]
 * for @f$\boldsymbol{u} = (u_1,\dots,u_s) \in [0,1]^s@f$.
 *
 * The exact solution is taken from @cite iKAA25a and is given by
 * @f[
 *   \int_{[0,1]^s} f(\boldsymbol{u})\,\mathrm{d}\boldsymbol{u}
 *   = \frac{\pi^{s/2}}{2^s}
 *     \prod_{j=1}^s
 *     \frac{\operatorname{erf}(c_j w_j)
 *       + \operatorname{erf}(c_j(1-w_j))}{c_j}.
 * @f]
 * @cite iKAA25a assumes @f$0 < w_j < 1@f$, whereas this implementation also
 * permits @f$w_j = 0@f$. The exact formula remains valid at this boundary
 * value.
 */
public class GenzGaussian implements MonteCarloModelDouble {

   private final int s;
   // Precomputed (C_j)^2 for simulate. C_j are needed only for the exact value.
   private final double[] cSquared;
   private final double[] w;
   private final double exactMean;

   private double sum;

   /**
    * Constructs a Genz Gaussian peak function with user-specified parameters.
    *
    * @param s dimension of the function
    * @param c scale parameters, all strictly positive
    * @param w location parameters, all in the half-open interval @f$[0,1)@f$
    */
   public GenzGaussian(int s, double[] c, double[] w) {
      if (s <= 0)
         throw new IllegalArgumentException("s must be positive");
      if (c == null || w == null || c.length != s || w.length != s)
         throw new IllegalArgumentException("c and w must have length s");

      this.s = s;
      this.cSquared = new double[s];
      this.w = w.clone();

      for (int j = 0; j < s; j++) {
         if (c[j] <= 0.0)
            throw new IllegalArgumentException("c[" + j + "] must be positive");
         if (w[j] < 0.0 || w[j] >= 1.0)
            throw new IllegalArgumentException("w[" + j + "] must be in [0, 1)");

         cSquared[j] = c[j] * c[j];
      }

      this.exactMean = computeExactMean(c, w);
   }

   /**
    * Simulates one observation of the model.
    *
    * @param stream random stream used to generate the coordinates
    */
   @Override
   public void simulate(RandomStream stream) {
      sum = 0.0;
      for (int j = 0; j < s; j++) {
         double diff = stream.nextDouble() - w[j];
         sum += cSquared[j] * diff * diff;
      }
   }

   /**
    * Returns the centered performance from the last simulation.
    *
    * @return function value minus the exact mean
    */
   @Override
   public double getPerformance() {
      return Math.exp(-sum) - exactMean;
   }

   /**
    * Computes the exact mean of the Genz Gaussian peak function.
    *
    * The computation multiplies the one-dimensional integral factors for the
    * supplied scale and location parameters.
    *
    * @param c scale parameters
    * @param w location parameters
    * @return exact integral over @f$[0,1]^s@f$
    */
   private static double computeExactMean(double[] c, double[] w) {
      double integral = 1.0;
      double sqrtPiOver2 = Math.sqrt(Math.PI) / 2.0;

      for (int j = 0; j < c.length; j++) {
         integral *= sqrtPiOver2
               * (Num.erf(c[j] * w[j]) + Num.erf(c[j] * (1.0 - w[j])))
               / c[j];
      }

      return integral;
   }

   @Override
   public String toString() {
      return "Genz Gaussian peak function";
   }

   @Override
   public String getTag() {
      return "GenzGaussian";
   }

   // for testing
   public double getExactMean() {
      return exactMean;
   }
}
