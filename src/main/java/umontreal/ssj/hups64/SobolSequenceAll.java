package umontreal.ssj.hups64;

/**
 * Experimental extension point for @ref SobolSequence.
 *
 * This class keeps all Sobol initialization in @ref SobolSequence and adds no
 * scrambling behavior for now. It exists only to provide a clean subclass where
 * experimental methods can be added later without modifying the original
 * SobolSequence implementation.
 */
public class SobolSequenceAll extends SobolSequence {

   /**
    * Constructs a Sobol point set with @f$2^k@f$ points, @f$w@f$ output digits,
    * and dimension `s`.
    *
    * @param k there will be @f$2^k@f$ points
    * @param w number of output digits
    * @param s dimension of the point set
    */
   public SobolSequenceAll(int k, int w, int s) {
      super(k, w, s);
   }
}
