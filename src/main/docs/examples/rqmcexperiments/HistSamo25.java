package rqmcexperiments;

import java.io.IOException;

import rqmcexperiments.HistCollectionLatex.ComparisonPage;
import rqmcexperiments.HistCollectionLatex.DataFileNameMaker;
import rqmcexperiments.HistCollectionLatex.HistConfig;

public class HistSamo25 {
       public static void main(String[] args) throws IOException {
            String inputFolder = "/home/otman/Documents/dropbox_copy/samo25_copy/datapl/";
            String outputFolder = "/home/otman/Documents/GitHub/Data/samo25-test/latexNewConfig/";

            String[] modelTags = new String[] {"Polynomial", "PieceLinGauss"};
            //    "SmoothPerB4", "SumUeU", "MC2", "Polynomial", "Oscillatory",
            //    "Gaussian", "SmoothGauss", "PieceLinGauss", "IndSumNormal"
            // };

            int[] sDims = new int[] {2, 4};//, 8, 16, 32};
            int m = 10000;
            int[] ks = new int[] {10, 12, 14, 16};
            int leftExtMark = 2;
            int rightExtMark =2;

            String methodes1Title = "Rank-1 lattice";
            String methods1 = "Lat-RS,Lat-RSB,Lat-Rv,Lat-Rpv,Lat-RvRS,Lat-RvRSB,Lat-RpvRS,Lat-RpvRSB";
            int maxRowsPerPage = 4;

            // String methodes2Title = "Sobol";
            // String methods2 = "Sob-RDS,Sob-RDSB,Sob-LMS,Sob-LMS-RDS,Sob-LMS-RDS-IRB,Sob-NUS";

            ComparisonPage[] pages = new ComparisonPage[] {
                new ComparisonPage( methodes1Title, methods1, maxRowsPerPage),
                // new ComparisonPage(methodes2Title ,methods2, maxRowsPerPage )
            };

            DataFileNameMaker dataFileNameMaker = (modelTag, s, method, k, numObs) ->
                    modelTag + "-" + s + "-" + method + "-" + k + "-" + numObs + ".dat";
            String outputFileNamePattern = "%s-hist.tex";

            HistConfig samo25Config = HistConfig.create(
                inputFolder, outputFolder,
                modelTags, sDims, ks, m,
                pages,
                leftExtMark, rightExtMark,
                outputFileNamePattern,
                dataFileNameMaker

            );

            HistCollectionLatex.writeCollection(samo25Config);
   }
}
