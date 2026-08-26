package rqmcexperiments;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jfree.data.xy.XYDataset;

import umontreal.ssj.charts.XYLineChart;

/**
 * Creates a portrait-letter LaTeX report containing the execution-time plots
 * generated from the merged all-methods CSV file.
 *
 * <p>This class deliberately does not merge CSV files. It uses the same
 * parameter loops as {@link AllMethodsExp}, writes report-specific TikZ
 * fragments, and arranges them in two breakable tables: one for plots varying
 * {@code m}, and one for plots varying {@code k}.</p>
 */
public final class AllMethodsLatex {

   private static final int RUNS = 11;
   private static final String OUTPUT_DIR =
         "/home/otman/Documents/GitHub/rqmc-experiments/";

   // Keep these experiment parameters in step with AllMethodsExp.
   private static final int[] S_VALUES = {2, 4, 8, 16, 32};
   private static final int[] M_VALUES = {1, 5, 20, 50};
   private static final int[] K_VALUES = {10, 12, 14, 16};
   private static final boolean LOG_Y_AXIS = true;

   /*
    * These LaTeX length ratios control the grid layout. They are written into
    * the report header so a single change applies to every plot in the PDF.
    */
   private static final double AXIS_WIDTH_FRACTION = 0.99;
   private static final double AXIS_HEIGHT_TO_WIDTH = 0.98;

   private static final Color[] BASE_COLORS = {
         Color.RED, Color.BLUE, new Color(0, 160, 0),
         Color.MAGENTA, Color.ORANGE, Color.CYAN, Color.BLACK,
         new Color(128, 0, 255), new Color(255, 20, 147),
         new Color(128, 64, 0), new Color(128, 255, 0)
   };

   private static final expUtil.TikzPictureOptions REPORT_M_PLOT_OPTIONS =
         new expUtil.TikzPictureOptions(false, true, false, false, false,
               "font=\\scriptsize", null, null);
   private static final expUtil.TikzPictureOptions REPORT_K_PLOT_OPTIONS =
         new expUtil.TikzPictureOptions(false, true, false, false, true,
               "font=\\scriptsize", null, null);

   private AllMethodsLatex() {
   }

   /**
    * Writes the report source and its report-specific TikZ fragments.
    *
    * @param args ignored
    * @throws IOException if the CSV or a LaTeX output file cannot be accessed
    */
   public static void main(String[] args) throws IOException {
      String yLogScaled = LOG_Y_AXIS ? "yLogScaled" : "";
      Path csvFile = Path.of(OUTPUT_DIR + "data/allMetTime" + RUNS + ".csv");
      Path reportFile = Path.of(OUTPUT_DIR + "plots/median-time-report-r"
            + RUNS + yLogScaled + ".tex");
      Path reportPlotsDir = Path.of(OUTPUT_DIR + "plots/report-r" + RUNS
            + yLogScaled);

      Map<String, Color> methodColors = methodColors(csvFile);
      Files.createDirectories(reportFile.getParent());

      try (BufferedWriter out = Files.newBufferedWriter(reportFile,
            StandardCharsets.UTF_8)) {
         writeLatexHeader(out, methodColors);
         writeMTable(out, csvFile, reportPlotsDir, yLogScaled, methodColors);
         out.write("\\clearpage");
         out.newLine();
         out.newLine();
         writeKTable(out, csvFile, reportPlotsDir, yLogScaled, methodColors);
         out.write("\\end{document}");
         out.newLine();
      }

      System.out.println("LaTeX report created:");
      System.out.println(reportFile.toAbsolutePath());
   }

   private static void writeMTable(BufferedWriter out, Path csvFile,
         Path reportPlotsDir, String yLogScaled,
         Map<String, Color> methodColors) throws IOException {
      writeTableStart(out,
            "Median execution time (sec., logarithmic scale): varying $m$",
            "k", K_VALUES, methodColors);

      for (int s : S_VALUES) {
         out.write("\\timerowlabel{s=" + s + "}");
         for (int k : K_VALUES) {
            XYLineChart chart = ExecutionTimePlots.createMedianTimePlot(csvFile,
                  "s", s, "k", k, "m", "$m$ rep.", 50.0,
                  x -> x, LOG_Y_AXIS);
            applyMethodColors(chart, methodColors);

            String fileName = "median-time-r" + RUNS + "-s" + s + "-k" + k
                  + yLogScaled + ".tex";
            Path tikzFile = reportPlotsDir.resolve(fileName);
            expUtil.writeTikzPictureFile(chart, tikzFile.toString(),
                  "\\timeaxiswidth", "\\timeaxisheight", LOG_Y_AXIS,
                  REPORT_M_PLOT_OPTIONS);
            out.write(" & \\makebox[\\timecellwidth][c]{\\input{report-r"
                  + RUNS + yLogScaled + "/" + fileName + "}}");
         }
         out.write("\\\\[2mm]");
         out.newLine();
      }

      out.write("\\end{longtable}");
      out.newLine();
   }

   private static void writeKTable(BufferedWriter out, Path csvFile,
         Path reportPlotsDir, String yLogScaled,
         Map<String, Color> methodColors) throws IOException {
      writeTableStart(out,
            "Median execution time (sec., logarithmic scale): varying $k$",
            "m", M_VALUES, methodColors);

      for (int s : S_VALUES) {
         out.write("\\timerowlabel{s=" + s + "}");
         for (int m : M_VALUES) {
            XYLineChart chart = ExecutionTimePlots.createMedianTimePlot(csvFile,
                  "s", s, "m", m, "k", "$n$ points", 16.0,
                  kValue -> Math.pow(2.0, kValue), LOG_Y_AXIS);
            applyMethodColors(chart, methodColors);

            String fileName = "median-time-r" + RUNS + "-s" + s + "-m" + m
                  + yLogScaled + ".tex";
            Path tikzFile = reportPlotsDir.resolve(fileName);
            expUtil.writeTikzPictureFile(chart, tikzFile.toString(),
                  "\\timeaxiswidth", "\\timeaxisheight", LOG_Y_AXIS,
                  REPORT_K_PLOT_OPTIONS);
            out.write(" & \\makebox[\\timecellwidth][c]{\\input{report-r"
                  + RUNS + yLogScaled + "/" + fileName + "}}");
         }
         out.write("\\\\[2mm]");
         out.newLine();
      }

      out.write("\\end{longtable}");
      out.newLine();
   }

   private static void writeLatexHeader(BufferedWriter out,
         Map<String, Color> methodColors) throws IOException {
      out.write("\\documentclass[letterpaper]{article}");
      out.newLine();
      out.write("\\usepackage[margin=0.2in]{geometry}");
      out.newLine();
      out.write("\\usepackage{array}");
      out.newLine();
      out.write("\\usepackage{longtable}");
      out.newLine();
      out.write("\\usepackage{pgfplots}");
      out.newLine();
      out.write("\\pgfplotsset{compat=1.18}");
      out.newLine();
      out.write("\\pagestyle{empty}");
      out.newLine();
      out.newLine();

      for (Map.Entry<String, Color> entry : methodColors.entrySet()) {
         Color color = entry.getValue();
         out.write("\\definecolor{" + colorName(entry.getKey()) + "}{RGB}{"
               + color.getRed() + "," + color.getGreen() + ","
               + color.getBlue() + "}");
         out.newLine();
      }

      out.write("\\newlength{\\timerowlabelwidth}");
      out.newLine();
      out.write("\\newlength{\\timecellwidth}");
      out.newLine();
      out.write("\\newlength{\\timeaxiswidth}");
      out.newLine();
      out.write("\\newlength{\\timeaxisheight}");
      out.newLine();
      out.write("\\setlength{\\timerowlabelwidth}{1.2cm}");
      out.newLine();
      out.write("\\newcommand{\\settimewidths}[1]{% ");
      out.newLine();
      out.write("  \\setlength{\\timecellwidth}{\\dimexpr(\\textwidth-"
            + "\\timerowlabelwidth)/#1\\relax}%");
      out.newLine();
      out.write("  \\setlength{\\timeaxiswidth}{" + AXIS_WIDTH_FRACTION
            + "\\timecellwidth}%");
      out.newLine();
      out.write("  \\setlength{\\timeaxisheight}{" + AXIS_HEIGHT_TO_WIDTH
            + "\\timeaxiswidth}%");
      out.newLine();
      out.write("}");
      out.newLine();
      out.write("\\newcommand{\\timerowlabel}[1]{% ");
      out.newLine();
      out.write("  \\raisebox{0.45\\timeaxisheight}[0pt][0pt]{"
            + "\\normalsize $#1$}}" );
      out.newLine();
      out.write("\\newcommand{\\timelegendentry}[2]{% ");
      out.newLine();
      out.write("  \\raisebox{0.15ex}{\\tikz[baseline=-0.6ex]"
            + "\\draw[color=#1,line width=0.7pt] (0,0)--(0.45,0)"
            + " node[pos=.5,circle,fill=#1,inner sep=0.8pt] {}; }~#2\\quad}");
      out.newLine();
      out.newLine();
      out.write("\\begin{document}");
      out.newLine();
      out.newLine();
   }

   private static void writeTableStart(BufferedWriter out, String title,
         String columnParameter, int[] columnValues,
         Map<String, Color> methodColors) throws IOException {
      out.write("\\settimewidths{" + columnValues.length + "}");
      out.newLine();
      out.write("\\begin{longtable}{@{}>{\\centering\\arraybackslash}m{"
            + "\\timerowlabelwidth}");
      for (int ignored : columnValues)
         out.write("@{}>{\\centering\\arraybackslash}p{\\timecellwidth}");
      out.write("@{}}");
      out.newLine();
      writeTableBanner(out, title, columnValues.length + 1, methodColors);
      writeColumnHeader(out, columnParameter, columnValues);
      out.write("\\endfirsthead");
      out.newLine();
      writeTableBanner(out, title + " (continued)", columnValues.length + 1,
            methodColors);
      writeColumnHeader(out, columnParameter, columnValues);
      out.write("\\endhead");
      out.newLine();
   }

   private static void writeTableBanner(BufferedWriter out, String title,
         int columns, Map<String, Color> methodColors) throws IOException {
      out.write("\\multicolumn{" + columns + "}{@{}c@{}}{\\large\\textbf{" + title
            + "}} \\\\[1.5mm]");
      out.newLine();
      out.write("\\multicolumn{" + columns + "}{@{}c@{}}{\\parbox{\\textwidth}{"
            + "\\centering\\scriptsize ");
      for (String method : methodColors.keySet()) {
         out.write("\\timelegendentry{" + colorName(method) + "}{"
               + escapeLatex(method) + "}");
      }
      out.write("}} \\\\[1.5mm]");
      out.newLine();
   }

   private static void writeColumnHeader(BufferedWriter out,
         String columnParameter, int[] columnValues) throws IOException {
      out.write("{}");
      for (int value : columnValues) {
         out.write(" & \\makebox[\\timecellwidth][c]{\\scriptsize $"
               + columnParameter + "=" + value + "$}");
      }
      out.write(" \\\\[1.5mm]");
      out.newLine();
   }

   private static Map<String, Color> methodColors(Path csvFile) throws IOException {
      List<String[]> rows = expUtil.filterCsvRows(csvFile, Map.of());
      SortedSet<String> methods = new TreeSet<>();
      for (String[] row : rows)
         methods.add(row[0]);

      Map<String, Color> colors = new LinkedHashMap<>();
      int index = 0;
      for (String method : methods)
         colors.put(method, colorFor(index++));
      return colors;
   }

   private static Color colorFor(int index) {
      if (index < BASE_COLORS.length)
         return BASE_COLORS[index];
      float hue = (float) ((index * 0.61803398875) % 1.0);
      return Color.getHSBColor(hue, 0.75f, 0.75f);
   }

   private static void applyMethodColors(XYLineChart chart,
         Map<String, Color> methodColors) {
      XYDataset dataset = chart.getSeriesCollection().getSeriesCollection();
      for (int series = 0; series < dataset.getSeriesCount(); series++) {
         String method = dataset.getSeriesKey(series).toString();
         Color color = methodColors.get(method);
         if (color == null)
            throw new IllegalArgumentException("No color was assigned to method: "
                  + method);
         chart.getSeriesCollection().setColor(series, color);
      }
   }

   private static String colorName(String method) {
      StringBuilder name = new StringBuilder("timemethod");
      for (int i = 0; i < method.length(); i++) {
         char character = method.charAt(i);
         if (Character.isLetterOrDigit(character))
            name.append(character);
         else
            name.append('x').append(Integer.toHexString(character));
      }
      return name.toString();
   }

   private static String escapeLatex(String text) {
      return text.replace("\\", "\\textbackslash{}")
            .replace("_", "\\_")
            .replace("%", "\\%")
            .replace("&", "\\&")
            .replace("#", "\\#")
            .replace("^", "\\^{}");
   }
}
