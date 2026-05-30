package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;

/**
 * Implements the general Genz discontinuous function taken from @cite iGEN87a.
 *
 * The function is defined by
 * @f[
 *   f(u_1,\dots,u_s) =
 *   \chi_{[0,w_1]\times[0,w_2]\times[0,1]^{s-2}}(u_1,\dots,u_s)
 *   \exp\left(\sum_{j=1}^s c_j u_j\right),
 * @f]
 * for @f$\bm u = (u_1,\dots,u_s) \in [0,1]^s@f$.
 */
public class GenzDiscontinuous implements MonteCarloModelDouble {

   private final int s;
   private final double[] c;
   private final double[] w;
   private final double exactMean;

   private double sum;
   private boolean inside;

   /**
    * Constructs a Genz discontinuous function with user-specified parameters.
    *
    * @param s dimension of the function
    * @param c scale parameters, all strictly positive
    * @param w discontinuity parameters, of length 2, both in @f$(0,1)@f$
    */
   public GenzDiscontinuous(int s, double[] c, double[] w) {
      if (s < 2)
         throw new IllegalArgumentException("s must be at least 2");
      if (c == null || c.length != s)
         throw new IllegalArgumentException("c must have length s");
      if (w == null || w.length != 2)
         throw new IllegalArgumentException("w must have length 2");

      for (int j = 0; j < s; j++) {
         if (!(c[j] > 0.0))
            throw new IllegalArgumentException("c[" + j + "] must be positive");
      }

      for (int j = 0; j < 2; j++) {
         if (!(w[j] > 0.0 && w[j] < 1.0))
            throw new IllegalArgumentException("w[" + j + "] must be in (0, 1)");
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
      inside = true;

      for (int j = 0; j < s; j++) {
         double u = stream.nextDouble();
         sum += c[j] * u;

         if (j < 2 && u > w[j])
            inside = false;
      }
   }

   /**
    * Returns the centered performance from the last simulation.
    *
    * @return function value minus the exact mean
    */
   @Override
   public double getPerformance() {
      return (inside ? Math.exp(sum) : 0.0) - exactMean;
   }

   /**
    * Computes the exact mean of the Genz discontinuous function.
    *
    * @return exact integral over @f$[0,1]^s@f$
    */
   private double computeExactMean() {
      double integral = Math.expm1(c[0] * w[0]) / c[0];
      integral *= Math.expm1(c[1] * w[1]) / c[1];

      for (int j = 2; j < s; j++)
         integral *= Math.expm1(c[j]) / c[j];

      return integral;
   }

   @Override
   public String toString() {
      return "Genz discontinuous function";
   }

   @Override
   public String getTag() {
      return "GenzDiscontinuous";
   }
   /////////for test
   public double getExactMean() {
	   return exactMean;
	}
   
}