package rngexperiments;

import java.io.FileWriter;
import java.io.IOException;

import umontreal.ssj.gof.GofStat;
import umontreal.ssj.probdist.ChiSquareDist;
import umontreal.ssj.probdist.NormalDist;
import umontreal.ssj.probdist.UniformDist;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.stat.Tally;

/**
 * Phase 1 basic goodness-of-fit tests for the MWC64 generators.
 *
 * This class tests only the standard SSJ output stream.nextDouble().
 * It does not test:
 * - raw bits,
 * - reversed bits,
 * - stream jumps,
 * - substream jumps,
 * - lattice or spectral structure.
 *
 * Those belong to later phases.
 *
 * The goal here is only to perform a first statistical screening using
 * basic SSJ goodness-of-fit tools.
 */
public class RngPhase1BasicGOF {

    // Number of uniforms generated per run.
    static final int M = 10_000_000;

    // Number of repeated consecutive blocks tested.
    static final int N = 3;

    // Number of bins for the 1D chi-square test.
    static final int K1D = 100;

    // Number of bins per axis for the 2D chi-square test.
    static final int K2D = 20;

    // Number of bins per axis for the 3D chi-square test.
    static final int K3D = 10;

    // Threshold used to flag extreme p-values.
    static final double SUSPICIOUS_P = 1.0e-6;

    // The target distribution for nextDouble().
    static final UniformDist U01 = new UniformDist(0.0, 1.0);

    /**
     * Runs Phase 1 tests on the two MWC64 generators.
     *
     * Each run is a consecutive block of nextDouble() values.
     * We do not call resetNextSubstream() here because stream/substream
     * behavior is tested later in Phase 5.
     */
    public static void main(String[] args) throws IOException  {
    	
    	StringBuilder out = new StringBuilder();
    	boolean write_file = true;
        String output_file = "/home/otman/Documents/GitHub/Data/o-MWC-test/RngPhase1BasicGOF.res";
        
        testGenerator("MWC64k2a2", new MWC64k2a2(), out);
        testGenerator("MWC64k3a2", new MWC64k3a2(), out);
        
        System.out.println(out );

        if (write_file) {
            FileWriter writer = new FileWriter(output_file);
            writer.write(out.toString());
            writer.close();
        }
    }

    /**
     * Applies all Phase 1 tests to one random stream.
     *
     * @param name generator name printed in the output
     * @param stream SSJ random stream to test
     */
    static void testGenerator(String name, RandomStream stream, StringBuilder out) {
    	out.append("========================================\n");
        out.append(name + "\n");
        out.append("Phase 1: SSJ Basic GOF on nextDouble() \n");
        out.append("M = " + M + " values per run \n");
        out.append("N = " + N + " consecutive runs \n");
        out.append("======================================== \n");

        for (int run = 1; run <= N; run++) {
            out.append("\n");
            out.append("Run " + run + "\n");

            double[] data = generateBlock(stream, M);

            testMoments(data, out);
            testChiSquare1D(data, out);
            testKolmogorovSmirnov(data, out);
            testAndersonDarling(data, out);
            testAutocorrelation(data, 1, out);
            testAutocorrelation(data, 2, out);
            testAutocorrelation(data, 5, out);
            testChiSquare2D(data, out);
            testChiSquare3D(data, out);
        }

        out.append("\n");
    }

    /**
     * Generates one consecutive block of uniforms from stream.nextDouble().
     *
     * @param stream SSJ random stream
     * @param n number of uniforms to generate
     * @return array containing n uniforms
     */
    static double[] generateBlock(RandomStream stream, int n) {
        double[] data = new double[n];

        for (int i = 0; i < n; i++)
            data[i] = stream.nextDoubleNonzero();

        return data;
    }

    /**
     * Tests the basic moments of U(0,1).
     *
     * Expected values:
     * - mean = 1/2
     * - variance = 1/12
     *
     * The mean p-value is based on the normal approximation.
     *
     * @param data generated uniforms
     */
    static void testMoments(double[] data, StringBuilder out) {
        Tally tally = new Tally();
        tally.add(data, data.length);

        double mean = tally.average();
        double variance = tally.variance();

        double expectedMean = 0.5;
        double expectedVariance = 1.0 / 12.0;

        // For U(0,1), Var(mean) = (1/12) / n.
        double zMean = (mean - expectedMean)
                / Math.sqrt(expectedVariance / data.length);

        double pMean = twoSidedNormalPValue(zMean);

        System.out.printf("Mean: %.12f, z: %.6f, p-value: %.12g", ///////////////////////////////////
                mean, zMean, pMean);
        printPValueFlag(pMean, out);

        System.out.printf("Variance: %.12f, expected: %.12f%n",
                variance, expectedVariance);

        System.out.printf("Min: %.12f, Max: %.12f%n",
                tally.min(), tally.max());
    }

    /**
     * Performs a 1D chi-square test with equal-width bins on [0,1).
     *
     * @param data generated uniforms
     */
    static void testChiSquare1D(double[] data, StringBuilder out) {
        int[] count = new int[K1D];

        for (double u : data) {
            int b = binIndex(u, K1D);
            count[b]++;
        }

        double expected = (double) data.length / K1D;

        double chi2 = GofStat.chi2Equal(expected, count, 0, K1D - 1);
        int degreesFreedom = K1D - 1;
        double p = chiSquareUpperTail(degreesFreedom, chi2);

        System.out.printf("Chi-square 1D: %.6f, df: %d, p-value: %.12g",
                chi2, degreesFreedom, p);
        printPValueFlag(p, out);
    }

    /**
     * Performs the Kolmogorov-Smirnov test against U(0,1).
     *
     * SSJ returns:
     * - D+
     * - D-
     * - D
     * and the corresponding p-values.
     *
     * @param data generated uniforms
     */
    static void testKolmogorovSmirnov(double[] data,StringBuilder out) {
        double[] stat = new double[3];
        double[] pval = new double[3];

        GofStat.kolmogorovSmirnov(data, U01, stat, pval);

        System.out.printf("KS D+ : %.8f, p-value: %.12g",
                stat[0], pval[0]);
        printPValueFlag(pval[0], out);

        System.out.printf("KS D- : %.8f, p-value: %.12g",
                stat[1], pval[1]);
        printPValueFlag(pval[1], out);

        System.out.printf("KS D  : %.8f, p-value: %.12g",
                stat[2], pval[2]);
        printPValueFlag(pval[2], out);
    }

    /**
     * Performs the Anderson-Darling test against U(0,1).
     *
     * This test is useful because it gives more weight to the tails
     * near 0 and 1 than the standard KS test.
     *
     * @param data generated uniforms
     */
    static void testAndersonDarling(double[] data, StringBuilder out) {
        double[] result = GofStat.andersonDarling(data, U01);

        double statistic = result[0];
        double p = result[1];

        System.out.printf("Anderson-Darling: %.8f, p-value: %.12g",
                statistic, p);
        printPValueFlag(p, out);
    }

    /**
     * Computes a simple lag autocorrelation test.
     *
     * This is not a full independence battery. It is only a basic
     * dependence check for Phase 1.
     *
     * @param data generated uniforms
     * @param lag autocorrelation lag
     */
    static void testAutocorrelation(double[] data, int lag, StringBuilder out) {
        int n = data.length - lag;

        double mean = 0.5;
        double variance = 1.0 / 12.0;

        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            sum += (data[i] - mean) * (data[i + lag] - mean);
        }

        double corr = sum / (n * variance);

        // Under independence, corr is approximately N(0, 1/n).
        double z = corr * Math.sqrt(n);
        double p = twoSidedNormalPValue(z);

        System.out.printf("Autocorrelation lag %d: %.12f, z: %.6f, p-value: %.12g",
                lag, corr, z, p);
        printPValueFlag(p, out);
    }

    /**
     * Performs a simple 2D chi-square test on consecutive pairs:
     *
     * (u0, u1), (u2, u3), ...
     *
     * @param data generated uniforms
     */
    static void testChiSquare2D(double[] data, StringBuilder out) {
        int numberBins = K2D * K2D;
        int[] count = new int[numberBins];

        int pairs = data.length / 2;

        for (int i = 0; i < 2 * pairs; i += 2) {
            int x = binIndex(data[i], K2D);
            int y = binIndex(data[i + 1], K2D);

            count[x * K2D + y]++;
        }

        double expected = (double) pairs / numberBins;

        double chi2 = GofStat.chi2Equal(expected, count, 0, numberBins - 1);
        int degreesFreedom = numberBins - 1;
        double p = chiSquareUpperTail(degreesFreedom, chi2);

        System.out.printf("Chi-square 2D: %.6f, df: %d, p-value: %.12g",
                chi2, degreesFreedom, p);
        printPValueFlag(p, out);
    }

    /**
     * Performs a simple 3D chi-square test on consecutive triples:
     *
     * (u0, u1, u2), (u3, u4, u5), ...
     *
     * @param data generated uniforms
     */
    static void testChiSquare3D(double[] data, StringBuilder out) {
        int numberBins = K3D * K3D * K3D;
        int[] count = new int[numberBins];

        int triples = data.length / 3;

        for (int i = 0; i < 3 * triples; i += 3) {
            int x = binIndex(data[i], K3D);
            int y = binIndex(data[i + 1], K3D);
            int z = binIndex(data[i + 2], K3D);

            count[(x * K3D + y) * K3D + z]++;
        }

        double expected = (double) triples / numberBins;

        double chi2 = GofStat.chi2Equal(expected, count, 0, numberBins - 1);
        int degreesFreedom = numberBins - 1;
        double p = chiSquareUpperTail(degreesFreedom, chi2);

        System.out.printf("Chi-square 3D: %.6f, df: %d, p-value: %.12g",
                chi2, degreesFreedom, p);
        printPValueFlag(p, out);
    }

    /**
     * Maps a uniform u in [0,1] to a bin index from 0 to numberBins - 1.
     *
     * The last check protects against the rare case where u is exactly 1.0.
     *
     * @param u uniform value
     * @param numberBins number of bins
     * @return bin index
     */
    static int binIndex(double u, int numberBins) {
        int b = (int) (u * numberBins);

        if (b == numberBins)
            b = numberBins - 1;

        return b;
    }

    /**
     * Computes the upper-tail p-value of a chi-square statistic.
     *
     * @param degreesFreedom chi-square degrees of freedom
     * @param chi2 chi-square statistic
     * @return P[X >= chi2]
     */
    static double chiSquareUpperTail(int degreesFreedom, double chi2) {
        ChiSquareDist dist = new ChiSquareDist(degreesFreedom);
        return dist.barF(chi2);
    }

    /**
     * Computes a two-sided p-value from the standard normal approximation.
     *
     * @param z standard normal statistic
     * @return two-sided p-value
     */
    static double twoSidedNormalPValue(double z) {
        double p = 2.0 * NormalDist.barF01(Math.abs(z));

        if (p > 1.0)
            p = 1.0;

        return p;
    }

    /**
     * Prints a warning when a p-value is extremely close to 0 or 1.
     *
     * One flagged p-value is not enough to reject a generator.
     * Repeated flags across runs are what matter.
     *
     * @param p p-value to inspect
     */
    static void printPValueFlag(double p, StringBuilder out) {
        if (p < SUSPICIOUS_P || p > 1.0 - SUSPICIOUS_P)
            System.out.println("  <-- suspicious \n");
        else
            out.append("");
    }
}