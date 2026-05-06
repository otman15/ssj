package rqmcexperiments;

import java.io.*;
import umontreal.ssj.hups64.*;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment64;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.*;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.util.Num;

/**
 * Tools to generate and store RQMC replicates for WSC 2023 paper.
 * This class is used by the main program in `WSC23MoreReps.java`.
 * It uses the 64-bit version of `hups`.
 */
public class WSC23MoreSamples extends RQMCExperiment64 {

   static String directory;   // Must be set in main program.

   // Lattice generating vector for n=2^{14} found with gamma_j = 2/(2+j), used for the WSC23 paper.
   //static int a14[] = { 1, 6229, 2691, 3349, 5893, 7643, 7921, 7055, 4829, 5177, 5459, 4863, 4901, 2833, 2385, 3729,
   //      981, 957, 4047, 1013, 1635, 2327, 7879, 2805, 2353, 1081, 3999, 879, 5337, 7725, 4889, 5103 };
   // This one is for n=2^{18}, found by CBC with same gamma_j.  
   static int a18[] = { 1, 103259, 73357, 46713, 58781, 112041, 32459, 50551, 40125, 128245, 
       18285, 124265, 98539, 130087, 113373, 22191, 120679, 98411, 94845, 33103, 47891, 15941, 
       30147, 43921, 81129, 3289, 50935, 63965, 55749, 38101, 70631, 116243 };

   /**
    * Redirect the output to a .res file with the given name, in `directory`.
    */
   public static void redirectToFile(String modelName) throws IOException {
      File file = new File(WSC23MoreSamples.directory + modelName + ".res");        
      PrintStream printStreamToFile = new PrintStream(file);
      System.setOut(printStreamToFile);
   }
   
   /**
    * Writes a summary report with mean, variance, etc., to a .sum file with the
    * given name, in given directory.
    */
   public static void reportToFile(TallyStore tally) throws IOException {
      FileWriter file = new FileWriter(directory + tally.getName() + ".sum");
      file.write(tally.shortReport());
      file.close();
   }

   /**
    * Returns the observations stored in this object as a `String`, with a line
    * feed after each observation.
    */
   public static String dataToString(TallyStore tally) {
      StringBuilder sb = new StringBuilder();
      double[] array = tally.getArray();
      for (int i = 0; i < tally.numberObs(); i++)
         sb.append(array[i] + "\n");
      return sb.toString();
   }

   /**
    * Writes the sorted observations in `tally` as a `String` and prints them in a
    * file with the given name, in given directory. Also calls `reportToFile`.
    */
   public static void dataToFile(TallyStore tally) throws IOException {
      // reportToFile (tally, fileName);
      FileWriter file = new FileWriter(directory + tally.getName() + ".dat");
      file.write(dataToString(tally));
      file.close();
   }

   /**
    * Performs m independent RQMC replications and save the sorted output in the
    * `statReps` collector.
    */
   public static void simulRepsRQMCSort(MonteCarloModelDouble model, PointSet p, 
         PointSetRandomization rand, int m, TallyStore statReps) throws IOException {
      statReps.init();
      int n = p.getNumPoints();
      Tally statValue = new Tally();
      PointSetIterator stream = p.iterator();
      Chrono timer = new Chrono();
      for (int rep = 0; rep < m; rep++) {
         statValue.init();
         rand.randomize(p);
         stream.resetStartStream(); // This stream iterates over the points.
         simulateRuns(model, n, stream, statValue);
         statReps.add(statValue.average()); // For the estimator of the mean.
      }
      System.out.println(statReps.report());
      System.out.println("variance = " + statReps.variance());
      System.out.println("skewness = " + statReps.skewness());
      System.out.println("excess kurtosis = " + statReps.kurtosis());
      System.out.println("CPU time: " + timer.format() + "\n");
      statReps.quickSort();
      dataToFile(statReps);
   }

   /**
    * Perform m RQMC runs for the given model with n=2^k points,
    * for different types of RQMC points.  For each type, the sorted values 
    * are saved in a file, and a report is printed to standard output,
    * which can be redirected to a file via `redirectToFile`.
    * 
    */ 
   public static void simulRepsAllTypes(MonteCarloModelDouble model, int s, int k, int m) 
          throws IOException {
      String modelTag = model.getTag();
      // String ident;        // Identifies the case, used in file names.
      int n = (int) Num.TWOEXP[k]; 
      RandomStream stream = new LFSR258();
      Chrono timer = new Chrono();
      System.out.println("WSC23MoreSamples program, RQMC replicates with model: " + model.toString() + "\n");
      TallyStore statReps = new TallyStore(m);

      // --------------
      // Lattice points  
      System.out.println("***  Lattice points ");
      Rank1Lattice pLat = new Rank1Lattice(n, a18, s);
      RandomShift randShift = new RandomShift(stream);
      BakerTransformedPointSet ptent = new BakerTransformedPointSet(pLat);

      // Lat-RS
      System.out.println("*   Lattice points with RS");
      statReps.setName(modelTag + "-" + s + "-Lat-RS-" + k + "-" + m);      
      simulRepsRQMCSort(model, pLat, randShift, m, statReps);

      // Lat-RSB
      System.out.println("*   Lattice points with RS + tent transform");
      statReps.setName(modelTag + "-" + s + "-Lat-RST-" + k + "-" + m);      
      simulRepsRQMCSort(model, ptent, randShift, m, statReps);

      // Lat-RvRS, random a
      //System.out.println("*   Lattice points with random gen vector a and RS");
      //statReps.setName(modelTag + "-" + s + "-Lat-RS-" + k + "-" + m);      
      //simulRepsRQMCSort(model, pLat, randShift, m, statReps);
     
      // Lat-RvRSB, random a
      //System.out.println("*   Lattice points with random gen vector a and RS + tent");
      //statReps.setName(modelTag + "-" + s + "-Lat-RS-" + k + "-" + m);      
      //simulRepsRQMCSort(model, pLat, randShift, m, statReps);

      // -------------      
      // Sobol' points
      System.out.println("*** Sobol points ");
      DigitalNetBase2 p = new SobolSequence(k, 53, s); // n = 2^{k} points in s dim.
      ptent = new BakerTransformedPointSet(p);
      // PointSetRandomization norand = new EmptyRandomization(); // No randomization
      PointSetRandomization rds = new RandomShift(stream); // Digital shift
      PointSetRandomization lms = new LMScramble(stream);
      PointSetRandomization lmsrds = new LMScrambleShift(stream);

      // Sob-RDS
      System.out.println("*   Sobol with RDS alone");
      statReps.setName(modelTag + "-" + s + "-Sob-RDS-" + k + "-" + m);      
      simulRepsRQMCSort(model, p, rds, m, statReps);

      // Sob-RDST
      System.out.println("*   Sobol with RDS + tent transform");
      statReps.setName(modelTag + "-" + s + "-Sob-RDST-" + k + "-" + m);      
      simulRepsRQMCSort(model, ptent, rds, m, statReps);
      
      // Sob-LMS
      System.out.println("*   Sobol with LMS alone, no shift");
      statReps.setName(modelTag + "-" + s + "-Sob-LMS-" + k + "-" + m);      
      simulRepsRQMCSort(model, p, lms, m, statReps);
      
      // Sob-LMS-RDS
      System.out.println("*   Sobol with LMS+RDS");
      statReps.setName(modelTag + "-" + s + "-Sob-LMS-RDS-" + k + "-" + m);      
      simulRepsRQMCSort(model, p, lmsrds, m, statReps);

      // Sob-LMS-RDS-IRB after k
      System.out.println("*   Sobol with LMS+RDS+IRB  (indep random bits after k)");
      statReps.setName(modelTag + "-" + s + "-Sob-LMS-RDS-IRB-" + k + "-" + m);      
      p.addIndepRandomBits(new LFSR258());
      simulRepsRQMCSort(model, p, lmsrds, m, statReps);
      p.clearIndepRandomBits();
      
      // Sob-NUS
      System.out.println("*   Sobol with NUS");
      statReps.setName(modelTag + "-" + s + "-Sob-NUS-" + k + "-" + m);      
      CachedPointSet cp = new CachedPointSet(p);
      PointSetRandomization nus = new NestedUniformScrambling (stream, 53);
      simulRepsRQMCSort(model, cp, nus, m, statReps);

      
/*
      // Sob-Int2    Sob-interlaced-order2
      System.out.println("*   Interlaced Sobol points with LMS+RDS");
      DigitalNetBase2 p2 = new SobolSequence(k, 60, 2*s);     // n = 2^{k} points in 2s dim.
      DigitalNetBase2 pitl = p2.matrixInterlace(2, s);
      ptent = new BakerTransformedPointSet(p);
      // System.out.println(p.formatPoints());
      // simulRepsRQMCSort(model, pitl, nus, m, statReps);
      // simulRepsRQMCSort(model, ptent, nus, m, statReps);
*/
      System.out.println(
            "Total time for simulRepsAllTypes: " + timer.format() + "\n=========================================== \n");
   }

   /**
    * For one model, perform m RQMC runs for all point set sizes k from mink to maxk, by steps of 2, 
    * and puts the results in arrays.  After that, the arrays are used to output data sets in files. 
    */
   public static void simulRepsAllSizes(MonteCarloModelDouble model, int s, int mink, int maxk, int m)
         throws IOException {
      System.out.println("RQMC replicates with model: " + model.toString() + ", s = " + s + "\n");
      Chrono timer = new Chrono();
      for (int k = mink; k <= maxk; k += 2) { // For each point set size
         simulRepsAllTypes(model, s, k, m);
      }
      System.out.println("\nTotal time for simulAllSizes: " + timer.format() 
            + "\n=========================================== \n");
   }
   
}
