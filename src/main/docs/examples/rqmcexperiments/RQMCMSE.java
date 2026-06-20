package rqmcexperiments;

import java.util.Arrays;

import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.TallyStore;

public class RQMCMSE {

   private static final double TARGET = 0.0;
   private static final String SKIP = "%";

   private static double mse(Tally tally) {
      double bias = tally.average() - TARGET;
      return tally.sumSquares() / tally.numberObs() + bias * bias;
   }

   private static void computeMseFromFile(String filename, int m, int r, RandomStream stream) {
      TallyStore simulations = new TallyStore();
      simulations.fillFromFile(filename, SKIP);
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
      System.out.println("File: " + filename);
      System.out.println("A_r MSE: " + ArMse);
      System.out.println("M_r MSE: " + MrMse);
   }

   public static void main(String[] args) {
      int m = 1000;
      int r = 10;
      String[] filenames = {
            "path/to/simulations.dat"
      };

      if (m <= 0 || r <= 0)
         throw new IllegalArgumentException("m and r must be positive");

      RandomStream stream = new MWC64k3a2();
      for (String filename : filenames)
         computeMseFromFile(filename, m, r, stream);
   }
}
