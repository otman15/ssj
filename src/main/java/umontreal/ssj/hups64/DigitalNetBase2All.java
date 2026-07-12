package umontreal.ssj.hups64;

/**
 * Extension point for experimenting with additional scrambling methods and
 * nested uniform scrambling variants for digital nets in base 2, without
 * modifying @ref DigitalNetBase2.
 *
 * This class intentionally inherits the current @ref DigitalNetBase2 behavior
 * unchanged. New methods can be added here, or existing methods can be
 * overridden here, while keeping the original implementation available for
 * comparison.
 */
public class DigitalNetBase2All extends DigitalNetBase2 {

   /**
    * Constructs an empty digital net extension. As with @ref DigitalNetBase2,
    * subclasses or future methods are responsible for initializing the net data
    * before points are generated.
    */
   public DigitalNetBase2All() {
      super();
   }
}
