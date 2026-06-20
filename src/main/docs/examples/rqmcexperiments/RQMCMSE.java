package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;

public class RQMCMSE {

   private static final double TARGET = 0.0; 
   private static final String SKIP = "%";

   private static double mse(Tally tally) {
      double bias = tally.average() - TARGET;
     // return tally.sumSquares() / tally.numberObs() + bias * bias;
      return tally.variance() + bias * bias;
   }

   private static double[] computeMseFromFile(String filename, int m, int r, RandomStream stream) {
      TallyStore simulations = new TallyStore();
      simulations.fillFromFile(filename); // use skip if file contains comments
      int numbSim = simulations.numberObs();
      if (numbSim == 0)
         throw new IllegalArgumentException("No simulation values found in " + filename);

      double[] values = simulations.getArray();
      double[] sample = new double[r];
      Tally ArTally = new Tally("A_r");
      Tally MrTally = new Tally("M_r");

      for (int i = 0; i < m; i++) {
         double sum = 0.0;
         for (int j = 0; j < r; j++) {
            double value = values[stream.nextInt(0, numbSim - 1)];
            sample[j] = value;
            sum += value;
         }

         ArTally.add(sum / r);
         Arrays.sort(sample);
         if ((r & 1) == 0) 
            MrTally.add((sample[r / 2 - 1] + sample[r / 2]) / 2.0);
         else
            MrTally.add(sample[r / 2]);
      }

      double ArMse = mse(ArTally);
      double MrMse = mse(MrTally);
      return new double[] {ArMse, MrMse};
   }

   private static String makeTable(String title, String functionName, int s,
         String header, String rows) {
      return title + " - " + functionName + ", s=" + s
            + System.lineSeparator() + header
            + System.lineSeparator() + rows;
   }

   private static void writeTable(File file, String table) {
      try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
         out.print(table);
      } catch (IOException e) {
         throw new RuntimeException("Could not write " + file.getAbsolutePath(), e);
      }
   }

   private static void computeFolderMse(String dataDir, String resultDir,
         String functionName, int s,
         String[] methods, int[] ks, int numObs,
         int m, int r, RandomStream stream) {
      StringBuilder header = new StringBuilder(String.format("%-24s", "Method"));
      for (int k : ks)
         header.append(String.format("%16s", "k=" + k));

      StringBuilder ArRows = new StringBuilder();
      StringBuilder MrRows = new StringBuilder();
      for (int i = 0; i < methods.length; i++) {
         if (i > 0) {
            ArRows.append(System.lineSeparator());
            MrRows.append(System.lineSeparator());
         }

         String method = methods[i];
         ArRows.append(String.format("%-24s", method));
         MrRows.append(String.format("%-24s", method));

         for (int k : ks) {
            String fileName = functionName + "-" + s + "-" + method
                  + "-" + k + "-" + numObs + ".dat";
            File file = new File(dataDir, fileName);

            if (!file.isFile()) {
               ArRows.append(String.format("%16s", "Missing"));
               MrRows.append(String.format("%16s", "Missing"));
               System.out.println("Missing file: " + file.getAbsolutePath());
               continue;
            }

            double[] result = computeMseFromFile(file.getAbsolutePath(), m, r, stream);
            ArRows.append(String.format(Locale.US, "%16.8e", result[0]));
            MrRows.append(String.format(Locale.US, "%16.8e", result[1]));
         }
      }

      String ArTable = makeTable(
            "A_r MSE", functionName, s, header.toString(), ArRows.toString());
      String MrTable = makeTable(
            "M_r MSE", functionName, s, header.toString(), MrRows.toString());

      System.out.println();
      System.out.println(ArTable);
      System.out.println();
      System.out.println(MrTable);

      File resultFolder = new File(resultDir);
      if (!resultFolder.exists() && !resultFolder.mkdirs())
         throw new IllegalArgumentException(
               "Could not create result folder " + resultFolder.getAbsolutePath());

      writeTable(new File(resultFolder, functionName + "-" + s + "-ArMse.res"), ArTable);
      writeTable(new File(resultFolder, functionName + "-" + s + "-MrMse.res"), MrTable);
   }

   public static void main(String[] args) {
      int m = 100000;
      int r = 10;
      int numObs = 10000;
      String dataDir = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
      String resultDir = "/home/otman/Documents/GitHub/Data/o-test/testNewC/";
      String[] functionNames = {"MC2", "Oscillatory"};
      int[] dimensions = {2, 4, 8, 16, 32};
      String[] methods = {
            "Lat-RS", "Lat-RSB", "Lat-Rpv", "Lat-RpvRS", "Lat-RpvRSB",
            "Lat-Rv", "Lat-RvRS", "Lat-RvRSB", "Sob-LMS", "Sob-LMS-RDS",
            "Sob-LMS-RDS-IRB", "Sob-NUS", "Sob-RDS", "Sob-RDSB"
      };
      int[] ks = {8, 10, 12, 14, 16};

      RandomStream stream = new MWC64k3a2();
      for (String functionName : functionNames)
         for (int s : dimensions)
            computeFolderMse(dataDir, resultDir, functionName, s,
                  methods, ks, numObs, m, r, stream);
   }
}
