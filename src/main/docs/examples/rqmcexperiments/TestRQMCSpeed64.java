package rqmcexperiments;

import umontreal.ssj.hups64.*;
import umontreal.ssj.rng.LFSR258;  // With this one, we get negative output values!  ***** 
import umontreal.ssj.rng.MWC64k2a2;  // With this one, we get negative output values!  ***** 
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.util.Num;

public class TestRQMCSpeed64 {

   // static RandomStream noise = new LFSR258();
   static RandomStream noise = new MWC64k2a2();
   static Chrono timer = new Chrono();
   
   // Lattice generating vector in 16 dimensions for n=2^{14}, used for the WSC23 paper.
   static int a14[] = { 1, 6229, 2691, 3349, 5893, 7643, 7921, 7055, 4829, 5177, 5459, 4863, 4901, 2833, 2385, 3729 };

   /**
    * Here we only randomize the points `numRep` times, but do not generate them.
    */
   public static void testSpeedRandomize(int numReps, PointSet p, PointSetRandomization rand, int dim) {
      double sum = 0.0;
      noise.resetStartStream();
      // RQMCPointSet ps = new RQMCPointSet(p, rand);
      System.out.println("Time to randomize the points only: ");
      timer.init();
      for (int rep = 0; rep < numReps; rep++) {
         rand.randomize(p);
         sum += p.getNumPoints();
      }
      System.out.println("Total CPU time:      " + timer.getSeconds() + " seconds");
      System.out.println("Number of points (test) = " + sum / numReps + "\n");
   }

   /** Here we randomize the points, the generate them explicitly one point at a time, 
    *  and we compute the average of all the coordinates.
    */
   public static void testSpeedGeneratePoints(int numReps, PointSet p, PointSetRandomization rand, int dim) {
      int n = p.getNumPoints();
      // RQMCPointSet ps = new RQMCPointSet(p, rand);
      PointSetIterator iter = p.iterator();
      double[] point = new double[dim];
      double sum = 0.0;
      noise.resetStartStream();
      System.out.println("Time to randomize, generate with nextPoint(), and add the points coordinates: ");
      timer.init();
      for (int rep = 0; rep < numReps; rep++) {
         rand.randomize(p);
         iter.resetStartStream();
         for (int i = 0; i < n; i++) {
            iter.nextPoint(point, dim);
            for (int j = 0; j < dim; j++)
               sum += point[j];
         }
      }
      System.out.println("Total CPU time:      " + timer.getSeconds() + " seconds");
      System.out.println("Average = " + sum / (n * dim * numReps) + "\n");
   }

   /**
    * Same as previous method, except that here we generate only one coordinate at a time.
    */
   public static void testSpeedGenerateCoord(int numReps, PointSet p, PointSetRandomization rand, int dim) {
      int n = p.getNumPoints();
      // RQMCPointSet ps = new RQMCPointSet(p, rand);
      PointSetIterator stream = p.iterator();
      // double[] point = new double[dim];
      double sum = 0.0;
      noise.resetStartStream();
      System.out.println("Time to randomize, generate with nextDouble(), and add the points coordinates: ");
      timer.init();
      for (int rep = 0; rep < numReps; rep++) {
         rand.randomize(p);
         stream.resetStartStream();
         for (int i = 0; i < n; i++) {
            for (int j = 0; j < dim; j++)
               sum += stream.nextDouble();
            stream.resetNextSubstream();
         }
      }
      System.out.println("Total CPU time:      " + timer.getSeconds() + " seconds");
      System.out.println("Average = " + sum / (n * dim * numReps) + "\n");
   }

   /** 
    * Applies all the previous tests in succession.  
    */
   public static void testSpeedAll(int numReps, PointSet p, PointSetRandomization rand, int s) {
      // System.out.println("------------------------------------------------------- \n");
      // System.out.println("Speed test for n = " + p.getNumPoints() + ", s = " + p.getDimension()
      //         + ", and m = " + numReps + " replications.");
      testSpeedRandomize(numReps, p, rand, s);
      testSpeedGeneratePoints(numReps, p, rand, s);
      testSpeedGenerateCoord(numReps, p, rand, s);
      System.out.println("------------------------------------------------------- \n");
   }
   

   public static void main(String[] args) {
      int s = 10;    // Dimension
      int m = 1000;    // Number of QMC randomizations
      int k = 20;
      int n = (int) Num.TWOEXP[k];   // Number of points.

      // This LCG point set has infinite dim; we have to reset dim for the random shift.
      LCGPointSet lcg = new LCGPointSet(n, a14[1]);
      lcg.setDimension(s);
      Rank1Lattice lat = new Rank1Lattice(n, a14, s);
      CachedPointSet clat = new CachedPointSet(lat);
      DigitalNetBase2 p = new SobolSequence(k, 53, s);   // 2^{k} points.
      CachedPointSet cp = new CachedPointSet(p);

      // PointSetRandomization rs = new RandomShift(noise);   // Random shift mod 1
      // PointSetRandomization rds = new RandomShift(noise);   // Random digital shift
      // PointSetRandomization lmsrds = new LMScrambleShift(noise); // LMS
      PointSetRandomization nus = new NestedUniformScrambling(noise, 53); // NUS
      
      System.out.println("Speed tests and comparisons for RQMC point sets with 64-bit implementation.");
      System.out.println("The same stream of random numbers is reused for all cases.");
      System.out.println("See the program TestRQMCSpeed64.java for the details. \n");
      System.out.println("Speed test for n = " + n + ", s = " + s + ", and m = " + m + " replications.");
      System.out.println("------------------------------------------------------- \n");

      // System.out.println("Sobol points with NUS, new HCK \n");
      // RQMCPointSet hck0 = new RQMCDigitalNet2HCK(p, cp, noise, 53);
      // // testSpeedAll(m, p, hck0.getRandomization(), s);
      // testSpeedAll(m, hck0.getPointSet(), hck0.getRandomization(), s);
      //  This one does not work .....
 
      // System.out.println("Korobov lattice with random shift, implemented as an LCG \n");
      // testSpeedAll(m, lcg, rs, s);

      // System.out.println("Rank-1 lattice with random shift \n");
      // testSpeedAll(m, lat, rs, s);

      // System.out.println("Rank-1 lattice with random shift, cached \n");
      // testSpeedAll(m, clat, rs, s);
      
      // System.out.println("Rank-1 lattice with random shift + tent transformation \n");
      // BakerTransformedPointSet lattent = new BakerTransformedPointSet(lat);
      // testSpeedAll(m, lattent, rs, s);

      // System.out.println("Sobol points with no randomization \n");
      // testSpeedAll(m, p, new EmptyRandomization(), s);
      
      // System.out.println("Sobol points with random digital shift only \n");
      // testSpeedAll(m, p, rds, s);

      // System.out.println("Sobol points with random digital shift + tent transformation \n");
      // BakerTransformedPointSet ptent = new BakerTransformedPointSet(p);
      // testSpeedAll(m, ptent, rs, s);
     
      // System.out.println("Sobol points with LMS + RDS \n");
      // testSpeedAll(m, p, lmsrds, s);
      
      // System.out.println("Sobol points with LMS + RDS, cached \n");
      // testSpeedAll(m, cp, lmsrds, s);
  
      // System.out.println("Sobol points with LMS + RDS + indep random bits after k \n");
      // p.addIndepRandomBits(new LFSR258());
      // testSpeedAll(m, p, lmsrds, s);
      p.clearIndepRandomBits();
 
      p.unrandomize();

      System.out.println("Sobol points with NUS, new FK \n");
      RQMCPointSet fk = new RQMCCachedNUS(p, cp, noise);
      testSpeedAll(m, fk.getPointSet(), fk.getRandomization(), s);
      
      System.out.println("Sobol points with NUS, new HCK \n");
      RQMCPointSet hck = new RQMCDigitalNet2HCK(p, cp, noise, 53);
      testSpeedAll(m, hck.getPointSet(), hck.getRandomization(), s);

      System.out.println("Sobol points with NUS, new HCKLong \n");
      RQMCPointSet hckLong = new RQMCDigitalNet2HCKLong(p, noise, 53);
      testSpeedAll(m, hckLong.getPointSet(), hckLong.getRandomization(), s);

      System.out.println("Sobol points with NUS, new HCKLong iterator \n");
      RQMCPointSet hckLongIter = new RQMCDigNet2HCKlongIter(p, noise, 53);
      testSpeedAll(m, hckLongIter.getPointSet(), hckLongIter.getRandomization(), s);
      
      System.out.println("Sobol points with NUS, old SSJ \n");
      testSpeedAll(m, cp, nus, s);
         
      // System.out.println("Independent random points cached \n");
      // IndependentPointsCached cip = new IndependentPointsCached (n, s);
      // testSpeedAll(m, cip, rs, s);
      
   }
}
