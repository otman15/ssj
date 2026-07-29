package rqmcexperiments;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jfree.chart.title.LegendTitle;

import umontreal.ssj.charts.XYLineChart;


/**
 * Creates execution-time plots from merged experiment results.
 */
public class ExecutionTimePlots {

/**
 * Creates a median execution-time plot for rows matching two filtering
 * conditions.
 *
 * @param inputFile the merged timing CSV file
 * @param filterColumn1 the first filtering column
 * @param filterValue1 the required value in the first filtering column
 * @param filterColumn2 the second filtering column
 * @param filterValue2 the required value in the second filtering column
 * @param xColumn the CSV column used for the x-axis: {@code "m"} or {@code "k"}
 * @param xAxisLabel the x-axis label
 * @return the execution-time chart
 * @throws IOException if the CSV file cannot be read
 */
public static XYLineChart createMedianTimePlot(
      Path inputFile,
      String filterColumn1,
      int filterValue1,
      String filterColumn2,
      int filterValue2,
      String xColumn,
      String xAxisLabel) throws IOException {

   int xColumnIndex = expUtil.getCsvColumnIndex(inputFile, xColumn);

   Map<String, String> conditions = Map.of(
         filterColumn1, Integer.toString(filterValue1),
         filterColumn2, Integer.toString(filterValue2)
   );

   List<String[]> rows =
         expUtil.filterCsvRows(inputFile, conditions);

   if (rows.isEmpty())
      throw new IllegalArgumentException(
            "No results found for the specified conditions.");

   Map<String, List<String[]>> groupedRows =
         expUtil.groupAndSortCsvRows(rows, 0, xColumnIndex);

   double[][][] data = new double[groupedRows.size()][][];
   String[] methodNames = new String[groupedRows.size()];

   int series = 0;

   double xmin = Double.POSITIVE_INFINITY;
   double xmax = Double.NEGATIVE_INFINITY;
   double ymin = Double.POSITIVE_INFINITY;
double ymax = Double.NEGATIVE_INFINITY;

   for (Map.Entry<String, List<String[]>> entry
         : groupedRows.entrySet()) {

      List<String[]> methodRows = entry.getValue();

      double[] xValues = new double[methodRows.size()];
      double[] medianTimes = new double[methodRows.size()];

      for (int i = 0; i < methodRows.size(); i++) {
         String[] row = methodRows.get(i);

         xValues[i] = Double.parseDouble(row[xColumnIndex]);
         medianTimes[i] = Double.parseDouble(row[4]);
         ymin = Math.min(ymin, medianTimes[i]);
         ymax = Math.max(ymax, medianTimes[i]);
      }

      xmin = Math.min(xmin, xValues[0]);
      xmax = Math.max(xmax, xValues[xValues.length - 1]);


      data[series] = new double[][] {
            xValues,
            medianTimes
      };

      methodNames[series] = entry.getKey();
      series++;
   }

   String title = "Median execution time, "
         + filterColumn1 + " = " + filterValue1
         + ", " + filterColumn2 + " = " + filterValue2;

   XYLineChart chart = new XYLineChart(
         title,
         xAxisLabel,
         "Median execution time (seconds)",
         data[0]
   );

   chart.getSeriesCollection().setName(0, methodNames[0]);
   chart.getSeriesCollection().setMarksType(0, "*");
   chart.getSeriesCollection().setPlotStyle(0, "sharp plot");

   for (int i = 1; i < data.length; i++) {
      int index = chart.add(
            data[i][0],
            data[i][1],
            methodNames[i],
            "sharp plot"
      );

      chart.getSeriesCollection().setMarksType(index, "*");
   }

   chart.getJFreeChart().addLegend(
         new LegendTitle(chart.getJFreeChart().getPlot())
   );

   double[] range = {xmin- xmin*0.01, xmax+xmax*0.01, ymin - ymin*0.01, ymax+ymax*0.01};
chart.setManualRange(range);

   return chart;
}
   public static void main(String[] args) throws IOException {

      //   Path inputFile = Path.of("/home/otman/Documents/GitHub/rqmc-experiments/results/all-methods.csv");

   
      //   XYLineChart chart = createMedianTimePlot(inputFile,"s", 4, "k", 10, "m", "Number of replications m");
      //   chart.view(800, 500);
      //   chart.toLatexFile(
      //       "/home/otman/Documents/GitHub/rqmc-experiments/results/median-time-s"+4+"-k"+10+".tex",
      //       10,
      //       9
      //   );

      //   XYLineChart chart2 = createMedianTimePlot(inputFile,"s", 4, "m", 1, "k", "k (n = 2\\^k)");
      //   chart2.view(800, 500);

      //   chart2.toLatexFile(
      //       "/home/otman/Documents/GitHub/rqmc-experiments/results/median-time-s"+4+"-m"+1+".tex",
      //       10,
      //       9
      //   );

      Path[] inputFiles = {
      Path.of("/home/otman/Dropbox/Nus-comparisons/owen_time_by_rep.csv"),
      Path.of("/home/otman/Dropbox/Nus-comparisons/sciMl_time_by_rep.csv"),
      Path.of("/home/otman/Dropbox/Nus-comparisons/qmcpy_time_by_rep.csv")
      };

      Path allmethodsFile = Path.of(
      "/home/otman/Dropbox/Nus-comparisons/all-methods.csv");

      expUtil.mergeCsvFiles(inputFiles, allmethodsFile, true);
      
      int s = 16, k=14, m =1;
      // Time vs m
      XYLineChart chart = createMedianTimePlot(allmethodsFile,"s", s, "k", k, "m", "Number of replications m");
      chart.view(800, 500);

      chart.toLatexFile(
      "//home/otman/Dropbox/Nus-comparisons/median-time-s"+s+"-k"+k+".tex",
      10, 9);

      // Time vs k
      XYLineChart chart2 = createMedianTimePlot(allmethodsFile,"s", s, "m", m, "k", "k (n = 2\\^k)");
      chart2.view(800, 500);

      chart2.toLatexFile(
      "//home/otman/Dropbox/Nus-comparisons/median-time-s"+s+"-m"+m+".tex",
      10, 9);
        
    }

}