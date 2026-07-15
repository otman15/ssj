package rqmcexperiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TestCollectionLatex {

   private static final String INPUT_FOLDER =
         "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
   private static final String OUTPUT_FOLDER =
         "/home/otman/Documents/GitHub/Data/samo25-test/latexNewConfig/TestCollectionLatex/";

   private static final String MODEL_TAG = "MC2";
   private static final int S = 2;
   private static final int K = 10;
   private static final int M = 10000;
   private static final String METHOD = "Lat-RS";

   private static final String[] MODEL_TAGS = new String[] {MODEL_TAG};
   private static final int[] S_DIMS = new int[] {S};
   private static final int[] KS = new int[] {10, 16};
   private static final String[] METHODS = new String[] {
      "Lat-RS","Lat-RSB","Lat-Rv","Lat-Rpv","Lat-RvRS",
      "Lat-RvRSB","Lat-RpvRS","Lat-RpvRSB",
      "Sob-RDS","Sob-RDSB", "Sob-LMS","Sob-LMS-RDS",
      "Sob-LMS-RDS-IRB","Sob-NUS"
   };

   public static void main(String[] args) throws IOException {
      File outputFolder = new File(OUTPUT_FOLDER);
      outputFolder.mkdirs();

      File sampleFile = inputFile();
//// some methods were changed this code needs to be updated
      //String histogramLatex = HistCollectionLatex.makeHistogramLatex("");
      // File histogramFile = new File(outputFolder,
      //       "01-makeHistogramLatex-MC2-s2-Lat-RS-k10-m10000.tex");
      // writeText(histogramFile, histogramLatex);

      String modelFileOutputFolder = OUTPUT_FOLDER + "02-writeModelFile-output/";
      HistCollectionLatex.writeModelFile(
            INPUT_FOLDER, modelFileOutputFolder,
            MODEL_TAG, METHODS, S_DIMS, KS, M);

      String collectionOutputFolder = OUTPUT_FOLDER + "03-writeCollection-output/";
      HistCollectionLatex.writeCollection(
            INPUT_FOLDER, collectionOutputFolder,
            MODEL_TAGS, METHODS, S_DIMS, KS, M);

      File resultList = new File(outputFolder, "00-result-files.txt");
      // writeResultList(resultList, sampleFile, histogramFile,
      //       new File(modelFileOutputFolder, MODEL_TAG + "-hist.tex"),
      //       new File(collectionOutputFolder, MODEL_TAG + "-hist.tex"));

      System.out.println("HistCollectionLatex2 public method result files:");
      System.out.println(resultList.getAbsolutePath());
   }

   private static File inputFile() {
      return new File(INPUT_FOLDER,
            MODEL_TAG + "-" + S + "-" + METHOD + "-" + K + "-" + M + ".dat");
   }

   private static void writeResultList(
         File file,
         File sampleFile,
         File histogramFile,
         File modelFile,
         File collectionFile) throws IOException {

      try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
         out.println("Input folder used from HistSamo25:");
         out.println(INPUT_FOLDER);
         out.println();
         out.println("Sample input file used for makeHistogramLatex:");
         out.println(sampleFile.getAbsolutePath());
         out.println();
         out.println("Results written by public method calls:");
         out.println("makeHistogramLatex(File): "
               + histogramFile.getAbsolutePath());
         out.println("writeModelFile(...): "
               + modelFile.getAbsolutePath());
         out.println("writeCollection(...): "
               + collectionFile.getAbsolutePath());
      }
   }

   private static void writeText(File file, String text) throws IOException {
      try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
         out.print(text);
      }
   }
}
