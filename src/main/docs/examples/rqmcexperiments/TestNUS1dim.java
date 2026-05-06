package rqmcexperiments;

import java.io.*;
import java.util.ArrayList;
import umontreal.ssj.hups.*;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperimentSeries;
import umontreal.ssj.rng.LFSR113;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.DataTable;

// Generate and store RQMC replicates for various models, for WSC 2023 paper

public class TestNUS1dim {

   public static void main(String[] args) throws IOException {

      final String directory = "C:/Users/Lecuyer/Dropbox/wsc23/RepsRQMC/";

      int dim = 1;
      int base = 2; // Basis for the loglog plots.
      int numSets = 12; // Number of sets in the series.
      int numSkipReg = 1; // Number of sets skipped for the regression.
      int i;
      int m = 100000; // Number of RQMC randomizations.
      MonteCarloModelDouble model = new SumUeU(dim);

      // Create a list of series of RQMC point sets.
      ArrayList<RQMCPointSet[]> listOfSeries = new ArrayList<RQMCPointSet[]>();
      PointSet p;
      CachedPointSet cp;
      PointSetRandomization randLMS, randNUS;
      RandomStream noise = new LFSR113();

      // Sobol with LMS+shift and with NUS
      RQMCPointSet[] theRQMCSetLMS = new RQMCPointSet[numSets];
      RQMCPointSet[] theRQMCSetNUS = new RQMCPointSet[numSets];
      randLMS = new LMScrambleShift(noise);
      randNUS = new NestedUniformScrambling(noise);
      int mink = 6; // Smallest power of 2 considered.

      for (i = 0; i < numSets; ++i) {
         p = (new SobolSequence(i + mink, 31, dim));
         cp = new CachedPointSet(p);
         theRQMCSetLMS[i] = new RQMCPointSet(p, randLMS);
         theRQMCSetNUS[i] = new RQMCPointSet(cp, randNUS);
      }
      theRQMCSetLMS[0].setLabel("Sobol+LMS+Shift");
      theRQMCSetNUS[0].setLabel("Sobol+NUS");
      listOfSeries.add(theRQMCSetLMS);
      listOfSeries.add(theRQMCSetNUS);

      RQMCExperimentSeries experSeries = new RQMCExperimentSeries(theRQMCSetNUS, base);
      // experSeries.testVarianceRate(model, m);
      // System.out.println(experSeries.reportVarianceRate(numSkipReg, true));
      // System.out.println((experSeries.toPgfDataTable("Sobol +
      // NUS")).drawPgfPlotSingleCurve("Sobol+NUS", "axis", 3, 4,
      // 2, "", "marks=*"));

      // Perform an experiment with a list of series of RQMC point sets.
      ArrayList<DataTable> listCurves = new ArrayList<DataTable>();
      System.out.println(experSeries.testVarianceRateManyPointTypes(model, listOfSeries, m, numSkipReg, true, true,
            true, listCurves));
      System.out.println("\n Now printing data table for the two curves  *****  \n\n\n");
      // Prints the data of each curve as a table.
      for (DataTable curve : listCurves)
         System.out.println(curve.formatTable());
      // Produces LaTeX code to draw these curves with pgfplot.
      String plot = DataTable.drawPgfPlotManyCurves("Sobol with LMS vs NUS", "loglogaxis", 0, 2, listCurves, 2, "",
            " ");
      System.out.println(plot);

      // Produces a complete LaTeX file with the plots.
      String pfile = (DataTable.pgfplotFileHeader() + plot + DataTable.pgfplotEndDocument());
      Writer file = new FileWriter(directory + "TestNUS1dim100000.tex");
      file.write(pfile);
      file.close();
   }

}
