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
 * for @f$\boldsymbol{u} = (u_1,\dots,u_s) \in [0,1]^s@f$.
 *
 * The exact solution is taken from @cite iKAA25a and is given by
 * @f[
 *   \int_{[0,1]^s} f(\boldsymbol{u})\,\mathrm{d}\boldsymbol{u}
 *   = \frac{1}{s!\prod_{j=1}^s c_j}
 *     \sum_{v\subseteq\{1,\dots,s\}}
 *     \frac{(-1)^{|v|}}{1+\sum_{j\in v}c_j}.
 * @f]
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
    * The implementation enumerates the @f$2^s@f$ subsets using a Gray-code
    * ordering. Since two consecutive Gray codes differ by only one bit, the
    * current subset sum is updated by adding or removing one @f$c_j@f$ instead
    * of being recomputed from scratch.
    *
    * Kahan summation is used separately from the Gray-code enumeration to reduce
    * floating-point roundoff in the alternating subset sum.
    *
    * A recursive include/exclude implementation is simpler and has the same
    * @f$O(2^s)@f$ complexity, but this iterative Gray-code implementation is
    * relatively faster in high dimensions.
    *
    * @warning Although Kahan summation reduces roundoff, this computation can be
    * numerically unstable when the scale parameters are small or the dimension is
    * large. The alternating subset sum may suffer severe cancellation, and division
    * by the product of the scale parameters may amplify the error.
    *
    * @return exact integral over @f$[0,1]^s@f$
    * @throws IllegalArgumentException if @f$s \ge 63@f$, since @f$2^s@f$ subsets
    *         cannot be represented safely with a `long`
    */
   private double computeExactMean() {
	   if (s >= 63) {
	      throw new IllegalArgumentException(
	         "Exact subset enumeration needs 2^s subsets; s is too large."
	      );
	   }

	   double prod = 1.0;
	   for (int j = 0; j < s; j++)
	      prod *= c[j];

	   double subsetSum = 0.0;
	   double compensation = 0.0; // Kahan compensation

	   long previousGray = 0L;
	   double partialSum = 0.0;
	   int cardinality = 0;

	   long nSubsets = 1L << s;

	   for (long mask = 0; mask < nSubsets; mask++) {
	      long gray = mask ^ (mask >> 1);

	      if (mask != 0) {
	         long changedBit = gray ^ previousGray;
	         int j = Long.numberOfTrailingZeros(changedBit);

	         if ((gray & changedBit) != 0L) {
	            partialSum += c[j];
	            cardinality++;
	         } else {
	            partialSum -= c[j];
	            cardinality--;
	         }
	      }

	      double sign = (cardinality % 2 == 0) ? 1.0 : -1.0;
	      double term = sign / (1.0 + partialSum);

	      // Kahan summation
	      double y = term - compensation;
	      double t = subsetSum + y;
	      compensation = (t - subsetSum) - y;
	      subsetSum = t;

	      previousGray = gray;
	   }

	   return subsetSum / (Num.factorial(s) * prod);
	}


   @Override
   public String toString() {
      return "Genz corner peak function";
   }

   @Override
   public String getTag() {
      return "GenzCornerPeak";
   }

   // for testing
   public double getExactMean() {
      return exactMean;
   }

}
