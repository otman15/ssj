package rqmcexperiments;

import java.io.*;
import umontreal.ssj.hups.*;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment;
import umontreal.ssj.rng.LFSR113;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.*;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.util.Num;

/**
 * Tools to generate and store RQMC replicates for WSC 2023 paper.
 * This class is used by the main program in `WSC23RepsRQMC.java`.
 */
public class WSC23RQMCSamples extends RQMCExperiment {

   // static String directory = "src/main/resources/";
   // static String directory = "C:/Users/Lecuyer/Documents/conf/samo25/mydata/";
   static String directory = "C:/Users/Lecuyer/Dropbox/wsc23/RepsRQMC/";

   // Lattice generating vector for n=2^{14} found with gamma_j = 2/(2+j), used for the WSC23 paper.
   static int a14[] = { 1, 6229, 2691, 3349, 5893, 7643, 7921, 7055, 4829, 5177, 5459, 4863, 4901, 2833, 2385, 3729,
         981, 957, 4047, 1013, 1635, 2327, 7879, 2805, 2353, 1081, 3999, 879, 5337, 7725, 4889, 5103 };

   /**
    * Redirect the output to a .res file with the given name, in `directory`.
    */
   public static void redirectToFile(String modelName) throws IOException {
      File file = new File(WSC23RQMCSamples.directory + modelName + ".res");        
      PrintStream printStreamToFile = new PrintStream(file);
      System.setOut(printStreamToFile);
   }
   
   /**
    * Writes a summary report with mean, variance, etc., to a .sum file with the
    * given name, in given directory.
    */
   public static void reportToFile(TallyStore tally, String fileName) throws IOException {
      FileWriter file = new FileWriter(directory + fileName + ".sum");
      file.write(tally.reportAndCIStudent(0.95, 8));
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
   public static void dataToFile(TallyStore tally, String fileName) throws IOException {
      // reportToFile (tally, fileName);
      FileWriter file = new FileWriter(directory + fileName + ".dat");
      file.write(dataToString(tally));
      file.close();
   }

   /**
    * Performs m independent RQMC replications and save the sorted output in the
    * `statReps` collector.
    */
   public static void simulRepsRQMCSort(MonteCarloModelDouble model, PointSet p, PointSetRandomization rand, int m,
         TallyStore statReps) {
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
      statReps.quickSort();
      System.out.println(statReps.reportAndCIStudent(0.95, 5));
      System.out.println("CPU time: " + timer.format() + "\n");
   }

   // Perform m RQMC runs for given model with n=2^k points,
   // for 5 different types of RQMC points.
   // The sorted values are saved in a file for each of the 5 types.
   public static void simulRepsAllTypes(MonteCarloModelDouble model, int s, int k, int m) throws IOException {
      int n = (int) Num.TWOEXP[k];
      TallyStore statReps;
      RandomStream stream = new LFSR113();
      String modelTag = model.getTag();
      System.out.println("RQMC replicates with model: " + model.toString() + "\n");
      Chrono timer = new Chrono();

      // Lat-RS
      Rank1Lattice pLat = new Rank1Lattice(n, a14, s);
      RandomShift randShift = new RandomShift(stream);
      statReps = new TallyStore(modelTag + "-" + s + "-Lat-RS " + k);
      simulRepsRQMCSort(model, pLat, randShift, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Lat-RS-" + k + "-" + m);

      // Lat-RSB
      BakerTransformedPointSet pLatBaker = new BakerTransformedPointSet(pLat);
      statReps = new TallyStore(modelTag + "-" + s + "-Lat-RSB " + k);
      simulRepsRQMCSort(model, pLatBaker, randShift, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Lat-RSB-" + k + "-" + m);

      // Sob-DS
      DigitalNet p = new SobolSequence(k, 31, s); // n = 2^{k} points in s dim.
      PointSetRandomization randnet = new RandomShift(stream); // Digital shift
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-DS " + k);
      simulRepsRQMCSort(model, p, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-DS-" + k + "-" + m);

      // Sob-DSB
      BakerTransformedPointSet pBaker = new BakerTransformedPointSet(p);
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-DSB " + k);
      simulRepsRQMCSort(model, pBaker, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-DSB-" + k + "-" + m);

      // Sob-LMS
      randnet = new LMScrambleShift(stream);
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-LMS " + k);
      simulRepsRQMCSort(model, p, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-LMS-" + k + "-" + m);

      // Sob-LMSB
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-LMSB " + k);
      simulRepsRQMCSort(model, pBaker, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-LMSB-" + k + "-" + m);

      // Sob-NUS
      CachedPointSet cp = new CachedPointSet(p);
      randnet = new NestedUniformScrambling(stream, 31);
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-NUS " + k);
      simulRepsRQMCSort(model, cp, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-NUS-" + k + "-" + m);

      // Sob-NUSB
      // CachedPointSet cp = new CachedPointSet(p);
      BakerTransformedPointSet cpBaker = new BakerTransformedPointSet(cp);
      // randnet = new NestedUniformScrambling(stream, 31);
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-NUSB " + k);
      simulRepsRQMCSort(model, cpBaker, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-NUSB-" + k + "-" + m);

      System.out.println(
            "Total time for simulRepsAllTypes: " + timer.format() + "\n=========================================== \n");
   }

   // Perform m RQMC runs for given model with n=2^k points, for NUS only.
   // The sorted values are saved in a file for each of the 5 types.
   public static void simulRepsNUS(MonteCarloModelDouble model, int s, int k, int m) throws IOException {
      TallyStore statReps;
      RandomStream stream = new LFSR113();
      String modelTag = model.getTag();
      System.out.println("RQMC replicates with model: " + model.toString() + "\n");
      Chrono timer = new Chrono();

      // Sob-NUS
      DigitalNet p = new SobolSequence(k, 31, s); // n = 2^{k} points in s dim.
      CachedPointSet cp = new CachedPointSet(p);
      PointSetRandomization randnet = new NestedUniformScrambling(stream, 31);
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-NUS " + k);
      simulRepsRQMCSort(model, cp, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-NUS-" + k + "-" + m);

      System.out.println(
            "Total time for simulRepsNUS: " + timer.format() + "\n=========================================== \n");
   }

   // Perform m RQMC runs for given model with n=2^k points, for Sob-RDS only.
   // The sorted values are saved in a file for each of the 5 types.
   public static void simulRepsDS(MonteCarloModelDouble model, int s, int k, int m) throws IOException {
      TallyStore statReps;
      RandomStream stream = new LFSR113();
      String modelTag = model.getTag();
      System.out.println("RQMC replicates with model: " + model.toString() + "\n");
      Chrono timer = new Chrono();

      // Sob-RDS
      DigitalNet p = new SobolSequence(k, 31, s); // n = 2^{k} points in s dim.
      PointSetRandomization randnet = new RandomShift(stream); // Digital shift
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-DS " + k);
      simulRepsRQMCSort(model, p, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-DS-" + k + "-" + m);

      System.out.println(
            "Total time for simulRepsDS: " + timer.format() + "\n=========================================== \n");
   }

   // Perform m RQMC runs for given model with n=2^k points, for LMS only.
   // The sorted values are saved in a file for each of the 5 types.
   public static void simulRepsLMS(MonteCarloModelDouble model, int s, int k, int m) throws IOException {
      TallyStore statReps;
      RandomStream stream = new LFSR113();
      String modelTag = model.getTag();
      System.out.println("RQMC replicates with model: " + model.toString() + "\n");
      Chrono timer = new Chrono();

      // Sob-LMS
      DigitalNet p = new SobolSequence(k, 31, s); // n = 2^{k} points in s dim.
      PointSetRandomization randnet = new LMScrambleShift(stream);
      statReps = new TallyStore(modelTag + "-" + s + "-Sob-LMS=" + k);
      simulRepsRQMCSort(model, p, randnet, m, statReps);
      dataToFile(statReps, modelTag + "-" + s + "-Sob-LMS-" + k + "-" + m);
      System.out.println("average = " + statReps.average());
      System.out.println("variance = " + statReps.variance());
      System.out.println("log variance = " + Math.log10(statReps.variance()));
      
      System.out.println(
            "Total time for simulRepsLMS: " + timer.format() + "\n=========================================== \n");
   }

}
