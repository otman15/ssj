package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;

/**
 * Implements the general Genz continuous function taken from @cite iGEN87a.
 *
 * The function is defined by
 * @f[
 *   f(u_1,\dots,u_s) =
 *   \exp\left(-\sum_{j=1}^s c_j |u_j - w_j|\right),
 * @f]
 * for @f$\boldsymbol{u} = (u_1,\dots,u_s) \in [0,1]^s@f$.
 *
 * The exact solution is discussed in @cite iKAA25a. However, the formula
 * printed there,
 * @f[
 *   \int_{[0,1]^s} f(\boldsymbol{u})\,\mathrm{d}\boldsymbol{u}
 *   = \prod_{j=1}^s
 *     \frac{\exp(c_j w_j-c_j)-\exp(-c_j w_j)}{c_j},
 * @f]
 * appears to contain an error. The corrected exact solution is
 * @f[
 *   \int_{[0,1]^s} f(\boldsymbol{u})\,\mathrm{d}\boldsymbol{u}
 *   = \prod_{j=1}^s
 *     \frac{2-\exp(-c_j w_j)-\exp(-c_j(1-w_j))}{c_j}.
 * @f]
 * @cite iKAA25a assumes @f$0 < w_j < 1@f$, whereas this implementation also
 * permits @f$w_j = 0@f$. The corrected formula remains valid at this boundary
 * value.
 */
public class GenzContinuous implements MonteCarloModelDouble {

   private final int s;
   private final double[] c;
   private final double[] w;
   private final double exactMean;

   private double sum;

   /**
    * Constructs a Genz continuous function with user-specified parameters.
    *
    * @param s dimension of the function
    * @param c scale parameters, all strictly positive
    * @param w location parameters, all in @f$[0,1)@f$
    */
   public GenzContinuous(int s, double[] c, double[] w) {
      if (s <= 0)
         throw new IllegalArgumentException("s must be positive");
      if (c == null || w == null || c.length != s || w.length != s)
         throw new IllegalArgumentException("c and w must have length s");

      for (int j = 0; j < s; j++) {
         if (!(c[j] > 0.0))
            throw new IllegalArgumentException("c[" + j + "] must be positive");
         if (!(w[j] >= 0.0 && w[j] < 1.0))
            throw new IllegalArgumentException("w[" + j + "] must be in [0, 1)");
      }

      this.s = s;
      this.c = c.clone();
      this.w = w.clone();
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
         sum += c[j] * Math.abs(stream.nextDouble() - w[j]);
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
    * Computes the exact mean of the Genz continuous function.
    *
    * The computation multiplies the one-dimensional integral factors and uses
    * `expm1` to improve numerical accuracy when its arguments are near zero.
    *
    * @return exact integral over @f$[0,1]^s@f$
    */
   private double computeExactMean() {
      double integral = 1.0;

      for (int j = 0; j < s; j++) {
         integral *= (-Math.expm1(-c[j] * w[j])
               - Math.expm1(-c[j] * (1.0 - w[j]))) / c[j];
      }

      return integral;
   }

   @Override
   public String toString() {
      return "Genz continuous function";
   }

   @Override
   public String getTag() {
      return "GenzContinuous";
   }

   // for testing
   public double getExactMean() {
      return exactMean;
   }
}
