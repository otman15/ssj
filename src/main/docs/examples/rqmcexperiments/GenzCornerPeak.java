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
//   public GenzCornerPeak(int s, double[] c) {
//      if (s <= 0)
//         throw new IllegalArgumentException("s must be positive");
//      if (c == null || c.length != s)
//         throw new IllegalArgumentException("c must have length s");
//
//      for (int j = 0; j < s; j++) {
//         if (!(c[j] > 0.0))
//            throw new IllegalArgumentException("c[" + j + "] must be positive");
//      }
//
//      this.s = s;
//      this.c = c.clone();
//      this.exactMean = computeExactMean();
//   }
   
   //// for test:
   public GenzCornerPeak(int s, double[] c) {
	   this(s, c, true);
	}

	public GenzCornerPeak(int s, double[] c, boolean computeMean) {
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
	   this.exactMean = computeMean ? computeExactMean() : Double.NaN;
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
    * The exact formula contains a sum over all @f$2^s@f$ subsets of the
    * scale parameters. This implementation enumerates the subsets using a
    * Gray-code ordering. Since two consecutive Gray codes differ by only one bit,
    * the current subset sum can be updated by adding or removing one @f$c_j@f$,
    * instead of recomputing the sum from scratch for each subset.
    *
    * Kahan summation is used separately from the Gray-code enumeration to reduce
    * floating-point roundoff in the alternating subset sum.
    *
    * A recursive include/exclude implementation gives the same mathematical result
    * and is shorter and easier to read, but this iterative Gray-code version is relatively
    * faster for larger dimensions. The cost remains @f$O(2^s)@f$.
    *
    * @return exact integral over @f$[0,1]^s@f$
    * @throws IllegalArgumentException if @f$s \ge 63@f$, since @f$2^s@f$ subsets
    *         cannot be represented safely with a `long`
    */
   public double computeExactMean() {
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
   
   /////////for test
   public double getExactMean() {
	   return exactMean;
	}
}