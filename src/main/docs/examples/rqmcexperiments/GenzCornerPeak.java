package rqmcexperiments;

import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Num;

/**
 * Implements the general Genz corner peak function taken from @cite iGEN87a.
 *
 * The function is defined by
 * @f[
 *   f(u_1,\dots,u_s) =
 *   \left(1 + \sum_{j=1}^s c_j u_j\right)^{-(s+1)},
 * @f]
 * for @f$\bm u = (u_1,\dots,u_s) \in [0,1]^s@f$.
 */
public class GenzCornerPeak implements MonteCarloModelDouble {

   private final int s;
   private final double[] c;
   private final double exactMean;

   private double sum;

   /**
    * Constructs a Genz corner peak function with user-specified parameters.
    *
    * @param s dimension of the function
    * @param c scale parameters, all strictly positive
    */
   public GenzCornerPeak(int s, double[] c) {
      if (s <= 0)
         throw new IllegalArgumentException("s must be positive");
      if (c == null || c.length != s)
         throw new IllegalArgumentException("c must have length s");

      for (int j = 0; j < s; j++) {
         if (!(c[j] > 0.0))
            throw new IllegalArgumentException("c[" + j + "] must be positive");
      }

      this.s = s;
      this.c = c.clone();
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
      return Math.pow(1.0 + sum, -(s + 1)) - exactMean;
   }

   /**
    * Computes the exact mean of the Genz corner peak function.
    *
    * @return exact integral over @f$[0,1]^s@f$
    */
   private double computeExactMean() {
      double prod = 1.0;
      for (int j = 0; j < s; j++)
         prod *= c[j];

      return subsetSum(0, 0.0, 0) / (Num.factorial(s) * prod); // Using recursive function, iteration maybe more efficient but long 
   }

   /**
    * Computes the subset sum in the exact integral formula.
    *
    * @param j current coordinate index
    * @param partialSum sum of selected parameters
    * @param cardinality number of selected parameters
    * @return contribution to the subset sum
    */
   private double subsetSum(int j, double partialSum, int cardinality) {
      if (j == s) {
         double sign = (cardinality % 2 == 0) ? 1.0 : -1.0;
         return sign / (1.0 + partialSum);
      }

      return subsetSum(j + 1, partialSum, cardinality)
            + subsetSum(j + 1, partialSum + c[j], cardinality + 1);
   }


   @Override
   public String toString() {
      return "Genz corner peak function";
   }

   @Override
   public String getTag() {
      return "GenzCornerPeak";
   }
   
   /////////for test
   public double getExactMean() {
	   return exactMean;
	}
}