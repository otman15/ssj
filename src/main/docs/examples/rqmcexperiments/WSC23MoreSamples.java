package rqmcexperiments;

import java.io.*;
import umontreal.ssj.hups64.*;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment64;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.*;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.util.Num;

/**
 * Tools to generate and store RQMC replicates for WSC 2023 paper. This class is
 * used by the main program in `WSC23MoreReps.java`. It uses the 64-bit version
 * of `hups`.
 */
public class WSC23MoreSamples extends RQMCExperiment64 {

   static String directory; // Must be set in main program `WSC23MoreReps`.

   // Lattice generating vector for n=2^{14} found with gamma_j = 2/(2+j), used for
   // the WSC23 paper.
   // static int a14[] = { 1, 6229, 2691, 3349, 5893, 7643, 7921, 7055, 4829, 5177,
   // 5459, 4863, 4901, 2833, 2385, 3729,
   // 981, 957, 4047, 1013, 1635, 2327, 7879, 2805, 2353, 1081, 3999, 879, 5337,
   // 7725, 4889, 5103 };
   // The following one is for n=2^{18}, found by CBC with same gamma_j.
   static int a18[] = { 1, 103259, 73357, 46713, 58781, 112041, 32459, 50551, 40125, 128245, 18285, 124265, 98539,
         130087, 113373, 22191, 120679, 98411, 94845, 33103, 47891, 15941, 30147, 43921, 81129, 3289, 50935, 63965,
         55749, 38101, 70631, 116243 };

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
    * `statReps` collector. We assume that the randomization may change the number
    * of points, as it sometimes happens when using `RandomLatticeParams` for instance.
    */
   public static void simulRepsRQMCSort(MonteCarloModelDouble model, PointSet p, PointSetRandomization rand, int m,
         TallyStore statReps) throws IOException {
      statReps.init();
      Tally statValue = new Tally();
      PointSetIterator stream = p.iterator();
      Chrono timer = new Chrono();
      for (int rep = 0; rep < m; rep++) {
         statValue.init();
         rand.randomize(p);
         // PointSetIterator stream = p.iterator(); // NO need to create a new iterator.
         stream.resetStartStream(); // This stream iterates over the points.
         simulateRuns(model, p.getNumPoints(), stream, statValue);
         statReps.add(statValue.average()); // For the estimator of the mean.
      }
      System.out.println(statReps.report());
      System.out.println("variance = " + statReps.variance());
      // System.out.println("skewness from Colt = " + statReps.skewness2());
      System.out.println("skewness, bias corrected = " + statReps.skewness(true));
      System.out.println("skewness, not corrected  = " + statReps.skewness(false));
      // System.out.println("excess kurtosis from Colt = " + statReps.kurtosis2());
      System.out.println("excess kurtosis, bias corrected = " + statReps.kurtosis(true, true));
      System.out.println("excess kurtosis, not corrected  = " + statReps.kurtosis(false, true));
      System.out.println("CPU time: " + timer.format() + "\n");
      statReps.quickSort();
      dataToFile(statReps);
   }

   /**
    * Perform m RQMC runs for the given model with n=2^k points, for different
    * types of RQMC points. For each type, the sorted values are saved in a file,
    * and a report is printed to standard output, which can be redirected to a file
    * via `redirectToFile`.
    * 
    */
   public static void simulRepsAllTypes(MonteCarloModelDouble model, int s, int k, int m) throws IOException {
      String modelTag = model.getTag();
      // String ident; // Identifies the case, used in file names.
      int n = (int) Num.TWOEXP[k];
      RandomStream stream = new MWC64k3a2();
      Chrono timer = new Chrono();
      System.out.println("WSC23MoreSamples program, RQMC replicates with model: " + model.toString() + "\n");
      TallyStore statReps = new TallyStore(m);
/*
      // --------------------------
      // Objects for lattice points
      System.out.println("***  Lattice points ");
      Rank1Lattice pLat = new Rank1Lattice(n, a18, s);
      RandomShift randShift = new RandomShift(stream);
      BakerTransformedPointSet ptent = new BakerTransformedPointSet(pLat);
      RandomLatticeParams randLatPar = new RandomLatticeParams(true, stream); // Randomizes a for n =
      RandomLatticeParams randLatPar2 = new RandomLatticeParams(n / 2, n, stream); // This one also randomizes n.

      // Lat-RS
      System.out.println("*   Lattice with RS");
      statReps.setName(modelTag + "-" + s + "-Lat-RS-" + k + "-" + m);
      simulRepsRQMCSort(model, pLat, randShift, m, statReps);

      // Lat-RSB
      System.out.println("*   Lattice with RS + tent transform");
      statReps.setName(modelTag + "-" + s + "-Lat-RSB-" + k + "-" + m);
      simulRepsRQMCSort(model, ptent, randShift, m, statReps);

      // Lat-Rv, random a
      System.out.println("*   Lattice with random gen vector a, no shift");
      randLatPar.setRandShift(false);
      statReps.setName(modelTag + "-" + s + "-Lat-Rv-" + k + "-" + m);
      simulRepsRQMCSort(model, pLat, randLatPar, m, statReps);

      // Lat-Rpv, random n and a, no shift
      System.out.println("*   Lattice with random n and random gen vector a, no shift");
      randLatPar2.setRandShift(false);
      statReps.setName(modelTag + "-" + s + "-Lat-Rpv-" + k + "-" + m);
      simulRepsRQMCSort(model, pLat, randLatPar2, m, statReps);

      // Lat-RvRS, random a and RS
      System.out.println("*   Lattice with random gen vector a and RS");
      randLatPar.setRandShift(true);
      statReps.setName(modelTag + "-" + s + "-Lat-RvRS-" + k + "-" + m);
      simulRepsRQMCSort(model, pLat, randLatPar, m, statReps);

      // Lat-RvRSB, random a and RS + tent
      System.out.println("*   Lattice with random gen vector a and RS + tent");
      statReps.setName(modelTag + "-" + s + "-Lat-RvRSB-" + k + "-" + m);
      simulRepsRQMCSort(model, ptent, randLatPar, m, statReps);

      // Lat-RpvRS, random n and a and RS
      System.out.println("*   Lattice with random n and random gen vector a, and RS");
      randLatPar2.setRandShift(true);
      statReps.setName(modelTag + "-" + s + "-Lat-RpvRS-" + k + "-" + m);
      simulRepsRQMCSort(model, pLat, randLatPar2, m, statReps);

      // Lat-RpvRSB, random n and a and RS + tent
      System.out.println("*   Lattice with random n, random gen vector a, and RS + tent");
      statReps.setName(modelTag + "-" + s + "-Lat-RpvRSB-" + k + "-" + m);
      simulRepsRQMCSort(model, ptent, randLatPar2, m, statReps);
*/
      // -------------------------
      // Objects for Sobol' points
      // System.out.println("*** Sobol points ");
      // DigitalNetBase2 p = new SobolSequence(k, 32, s); // n = 2^{k} points in s dim.
/*    
      ptent = new BakerTransformedPointSet(p);
      // PointSetRandomization norand = new EmptyRandomization(); // No randomization
      PointSetRandomization rds = new RandomShift(stream); // Digital shift
      PointSetRandomization lms = new LMScramble(stream);
      PointSetRandomization lmsrds = new LMScrambleShift(stream);

      // Sob-RDS System.out.println("* Sobol with RDS alone");
      statReps.setName(modelTag + "-" + s + "-Sob-RDS-" + k + "-" + m);
      simulRepsRQMCSort(model, p, rds, m, statReps);

      // Sob-RDSB System.out.println("* Sobol with RDS + baker transform");
      statReps.setName(modelTag + "-" + s + "-Sob-RDSB-" + k + "-" + m);
      simulRepsRQMCSort(model, ptent, rds, m, statReps);

      // Sob-LMS System.out.println("* Sobol with LMS alone, no shift");
      statReps.setName(modelTag + "-" + s + "-Sob-LMS-" + k + "-" + m);
      simulRepsRQMCSort(model, p, lms, m, statReps);

      // Sob-LMS-RDS System.out.println("* Sobol with LMS+RDS");
      statReps.setName(modelTag + "-" + s + "-Sob-LMS-RDS-" + k + "-" + m);
      simulRepsRQMCSort(model, p, lmsrds, m, statReps);

      // Sob-LMS-RDS-IRB after k
      System.out.println("* Sobol with LMS+RDS+IRB (indep random bits after k)");
      statReps.setName(modelTag + "-" + s + "-Sob-LMS-RDS-IRB-" + k + "-" + m);
      p.addIndepRandomBits(new LFSR258());
      simulRepsRQMCSort(model, p, lmsrds, m, statReps);
      p.clearIndepRandomBits();
*/

      System.out.println("*** Sobol points ");
      DigitalNetBase2 p = new SobolSequence(k, 32, s); // n = 2^{k} points in s dim.

      // // Sob-NUS
      // System.out.println("* Sobol with NUS");
      // statReps.setName(modelTag + "-" + s + "-Sob-NUS-" + k + "-" + m);
      // CachedPointSet cp = new CachedPointSet(p);
      // stream.resetNextSubstream();
      // PointSetRandomization nus = new NestedUniformScrambling(stream, 30);
      // simulRepsRQMCSort(model, cp, nus, m, statReps);




// //////////////nus presorted/////////// nestedUniformScramble64Presorted
// /// 
      System.out.println("* Sobol with NUS64 presorted");
      statReps.setName(modelTag + "-" + s + "-Sob-NUS64-PRESORTED-" + k + "-" + m);
      CachedPointSet cp3 = new CachedPointSet(p);
      stream.resetNextSubstream();
      
      PointSetRandomization nusPresorted =
            new NestedUniformScramblingExperimental(
                  stream,
                  NestedUniformScramblingExperimental.Method.SSJ_NUS64_PRESORTED,
                  30);
      simulRepsRQMCSort(model, cp3, nusPresorted, m, statReps);
///////////////////
/// 
      // Copie expérimentale
      System.out.println("* Sobol with NUS64 ssj");
      statReps.setName(modelTag + "-" + s + "-Sob-NUS64-COPY-" + k + "-" + m);
      CachedPointSet cp2 = new CachedPointSet(p);
      stream.resetStartSubstream();
      PointSetRandomization nusCopy =
            new NestedUniformScramblingExperimental(
                  stream,
                  NestedUniformScramblingExperimental.Method.SSJ_NUS64_COPY,
                  30);
      simulRepsRQMCSort(model, cp2, nusCopy, m, statReps);

/////////////scimlJlOwenPacked
      // Sobol with SciML QuasiMonteCarlo.jl Owen scrambling structure
      System.out.println("* Sobol with scimlJlOwenPacked");
      statReps.setName(modelTag + "-" + s + "-Sob-SCIML-JL-OWEN-P-" + k + "-" + m);

      CachedPointSet cpScimlJlOwenPack = new CachedPointSet(p);
      stream.resetStartSubstream();
      PointSetRandomization ScimlJlOwenPack =
            new NestedUniformScramblingExperimental(
                  stream, NestedUniformScramblingExperimental.Method.SCIML_JL_OWEN_PACKED, 30);

      simulRepsRQMCSort(model, cpScimlJlOwenPack, ScimlJlOwenPack, m, statReps);

///////////////////SCIML_JL_OWEN_PACKED_CACHED
/// 
      System.out.println("* Sobol with scimlJlOwenPCashed");
      statReps.setName(modelTag + "-" + s + "-Sob-SCIML-JL-OWEN-PC-" + k + "-" + m);

      CachedPointSet cpScimlJlOwenPC = new CachedPointSet(p);
      stream.resetStartSubstream();
      PointSetRandomization ScimlJlOwenPC =
            new NestedUniformScramblingExperimental(
                  stream,NestedUniformScramblingExperimental.Method.SCIML_JL_OWEN_PACKED_CACHED, 30);

      simulRepsRQMCSort(model, cpScimlJlOwenPC, ScimlJlOwenPC, m, statReps);
/* 
// Sobol with SciML QuasiMonteCarlo.jl Owen scrambling structure
      System.out.println("* Sobol with SCIML_JL_OWEN");
      statReps.setName(modelTag + "-" + s + "-Sob-SCIML-JL-OWEN-" + k + "-" + m);

      CachedPointSet cpScimlJlOwen = new CachedPointSet(p);
      stream.resetStartSubstream();
      PointSetRandomization scimlJlOwen =
            new NestedUniformScramblingExperimental(
                  stream,
                  NestedUniformScramblingExperimental.Method.SCIML_JL_OWEN,
                  30);

      simulRepsRQMCSort(model, cpScimlJlOwen, scimlJlOwen, m, statReps);
   */
/////SCIML_JL_OWEN_INCREMENTAL
/* 
      // Sobol with SciML QuasiMonteCarlo.jl Owen scrambling structure
      System.out.println("* Sobol with SCIML_JL_OWEN_INCREMENTAL");
      statReps.setName(modelTag + "-" + s + "-Sob-SCIML-JL-OWEN-" + k + "-" + m);

      CachedPointSet cpScimlJlOwenInc = new CachedPointSet(p);
      stream.resetStartSubstream();
      PointSetRandomization ScimlJlOwenInc =
            new NestedUniformScramblingExperimental(
                  stream,
                  NestedUniformScramblingExperimental.Method.SCIML_JL_OWEN_INCREMENTAL,
                  30);

      simulRepsRQMCSort(model, cpScimlJlOwenInc, ScimlJlOwenInc, m, statReps);

*/

      // Sob-NUS 64
      // DigitalNetBase2Test p64 = new SobolSequenceTest(k, 32, s);
      // stream.resetStartStream();
      // System.out.println("* Sobol with NUS64");
      // statReps.setName(modelTag + "-" + s + "-Sob-NUS64-" + k + "-" + m);
      // CachedPointSet cp2 = new CachedPointSet(p64);
      // PointSetRandomization nus64 = new NestedUniformScramblingTest(stream, 18);
      // simulRepsRQMCSort(model, cp2, nus64, m, statReps);

      // // Sob-Burley Owen using the same Sobol base p
      // stream.resetStartStream();
      // System.out.println("* Burley Owen on SSJ Sobol");
      // statReps.setName(modelTag + "-" + s + "-BurleyOwen-" + k + "-" + m);
      // BurleyOwen.BurleyPointSet Burley =
      //       new BurleyOwen.BurleyPointSet(p, 1);
      // PointSetRandomization burleyOwen =
      //       new BurleyOwen.Randomization(stream);
      // simulRepsRQMCSort(model, Burley, burleyOwen, m, statReps);

      // // Sob Burley padded
      // stream.resetStartStream();
      // System.out.println("* Burley padded Sobol with hash-based NUS");
      // statReps.setName(modelTag + "-" + s + "-BurleyPadded-" + k + "-" + m);
      // BurleyPaddedSobol.PaddedPointSet pBurley =
      //       new BurleyPaddedSobol.PaddedPointSet(k, s, 1);
      // PointSetRandomization burleyNus =
      //       new BurleyPaddedSobol.Randomization(stream);
      // simulRepsRQMCSort(model, pBurley, burleyNus, m, statReps);

      /*
       * // Sob-Int2 Sob-interlaced-order2
       * System.out.println("* Interlaced Sobol points with LMS+RDS"); DigitalNetBase2
       * p2 = new SobolSequence(k, 60, 2*s); // n = 2^{k} points in 2s dim.
       * DigitalNetBase2 pitl = p2.matrixInterlace(2, s); ptent = new
       * BakerTransformedPointSet(p); // // System.out.println(p.formatPoints()); //
       * simulRepsRQMCSort(model, pitl, nus, m, statReps); simulRepsRQMCSort(model,
       * ptent, nus, m, statReps);
       */

      System.out.println(
            "Total time for simulRepsAllTypes: " + timer.format() + "\n=========================================== \n");
   }

   /**
    * Same thing, but for just a few selected types of RQMC method.
    */
   public static void simulRepsSelectedTypes(MonteCarloModelDouble model, int s, int k, int m) throws IOException {
      String modelTag = model.getTag();
      // String ident; // Identifies the case, used in file names.
      int n = (int) Num.TWOEXP[k];
      RandomStream stream = new MWC64k3a2();
      Chrono timer = new Chrono();
      System.out.println("WSC23MoreSamples program, RQMC replicates with model: " + model.toString() + "\n");
      TallyStore statReps = new TallyStore(m);

      // --------------------------
      // Objects for lattice points
      System.out.println("***  Lattice points ");
      Rank1Lattice pLat = new Rank1Lattice(n, a18, s);
      RandomShift randShift = new RandomShift(stream);
      BakerTransformedPointSet ptent = new BakerTransformedPointSet(pLat);
      RandomLatticeParams randLatPar = new RandomLatticeParams(true, stream); // Randomizes a for n =
      RandomLatticeParams randLatPar2 = new RandomLatticeParams(n / 2, n, stream); // This one also randomizes n.

      // Lat-Rv, random a
      System.out.println("*   Lattice with random gen vector a, no shift");
      randLatPar.setRandShift(false);
      statReps.setName(modelTag + "-" + s + "-Lat-Rv-" + k + "-" + m);
      // simulRepsRQMCSort(model, pLat, randLatPar, m, statReps);

      // Lat-Rpv, random n and a, no shift
      System.out.println("*   Lattice with random n and random gen vector a, no shift");
      randLatPar2.setRandShift(false);
      statReps.setName(modelTag + "-" + s + "-Lat-Rpv-" + k + "-" + m);
      // simulRepsRQMCSort(model, pLat, randLatPar2, m, statReps);

      // -------------------------
      // Objects for Sobol' points
      System.out.println("*** Sobol points ");
      DigitalNetBase2 p = new SobolSequence(k, 53, s); // n = 2^{k} points in s dim.
      ptent = new BakerTransformedPointSet(p);
      // PointSetRandomization norand = new EmptyRandomization(); // No randomization
      PointSetRandomization rds = new RandomShift(stream); // Digital shift
      PointSetRandomization lms = new LMScramble(stream);
      PointSetRandomization lmsrds = new LMScrambleShift(stream);

      // Sob-RDSB System.out.println("* Sobol with RDS + baker transform");
      statReps.setName(modelTag + "-" + s + "-Sob-RDSB-" + k + "-" + m);
      simulRepsRQMCSort(model, ptent, rds, m, statReps);

      System.out.println(
            "Total time for simulRepsSelectedTypes: " + timer.format() + "\n=========================================== \n");
   }

   
   /**
    * For one model, perform m RQMC runs for all point set sizes k from mink to
    * maxk, by steps of 2, and puts the results in arrays. After that, the arrays
    * are used to output data sets in files.
    */
   public static void simulRepsAllSizes(MonteCarloModelDouble model, int s, int mink, int maxk, int m)
         throws IOException {
      // redirectToFile(model.getTag() + "-" + s + "-" + m);
      System.out.println("RQMC replicates with model: " + model.toString() + ", s = " + s + "\n");
      Chrono timer = new Chrono();
      for (int k = mink; k <= maxk; k += 2) { // For each point set size
         simulRepsAllTypes(model, s, k, m);
         // simulRepsSelectedTypes(model, s, k, m);
      }
      System.out.println(
            "\nTotal time for simulAllSizes: " + timer.format() + "\n=========================================== \n");
   }

}
