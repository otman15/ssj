package rqmcexperiments;

import umontreal.ssj.stat.Tally;

public class TestTallyFillFromFile {

   private static void checkTally(String testName, Tally tally, int expectedCount,
         double expectedAverage, double expectedMin, double expectedMax) {
      if (tally.numberObs() != expectedCount ||
            Double.compare(tally.average(), expectedAverage) != 0 ||
            Double.compare(tally.min(), expectedMin) != 0 ||
            Double.compare(tally.max(), expectedMax) != 0) {
         throw new AssertionError(testName + " failed: count=" + tally.numberObs()
               + ", average=" + tally.average() + ", min=" + tally.min()
               + ", max=" + tally.max());
      }
      System.out.println(testName + " passed");
   }

   public static void main(String[] args) {
      String numbersFile = "/home/otman/Documents/GitHub/tests/multiline.res";
      String commentsFile = "/home/otman/Documents/GitHub/tests/multilineComment.res";

      Tally numbers = new Tally();
      numbers.fillFromFile(numbersFile);
      checkTally("fillFromFile(String)", numbers, 5, 0.2, 0, 0.4);

      Tally numbersWithComments = new Tally();
      numbersWithComments.fillFromFile(commentsFile, "%");
      checkTally("fillFromFile(String, String)", numbersWithComments, 5, 0.2, 0, 0.4);
   }
}
