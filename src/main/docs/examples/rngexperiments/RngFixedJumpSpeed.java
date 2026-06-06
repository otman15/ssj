package rngexperiments;

import java.io.FileWriter;
import java.io.IOException;

import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.MWC64k2a2new;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;

/**
 * Speed test for fixed stream and substream jumps in several SSJ random streams.
 *
 * This class compares:
 *
 * 1. Stream jumps:
 *    Creating a new stream object repeatedly. In SSJ, constructing a new stream
 *    advances the package-level seed to the next stream.
 *
 * 2. Substream jumps:
 *    Calling resetNextSubstream() repeatedly on an existing stream.
 *
 * Each generator is tested for N runs. Run 1 is treated as a warm-up run because
 * the JIT compiler may not have optimized the code yet. Therefore, Run 1 is
 * printed but excluded from the reported average. The average is computed only
 * over runs 2 to N.
 */
public class RngFixedJumpSpeed {

    /** Number of jumps performed in each run. */
    static final int M = 1_000_000;

    /** Number of runs. Run 1 is warm-up and is not included in the average. */
    static final int N = 6;

    /**
     * Sink variable used to prevent the JVM from removing object creation
     * as dead code in the stream-jump tests.
     */
    static RandomStream streamSink;

    /**
     * Runs the stream-jump and substream-jump speed tests.
     */
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();

        out.append("Jump speed test\n");
        out.append("M = ").append(M).append(" jumps per run\n");
        out.append("N = ").append(N).append(" runs\n");
        out.append("Run 1 is a warm-up run and is not included in the average.\n");
        out.append("Average is computed over runs 2 to ").append(N).append(".\n\n");

        testStreamJump(out);
        testSubstreamJump(out);

        System.out.println(out);

//        FileWriter writer = new FileWriter("/home/otman/Documents/GitHub/Data/o-MWC-test/MRGJumpSpeedTest.res");
//        writer.write(out.toString());
//        writer.close();
    }

    /**
     * Tests stream jumps by repeatedly creating new stream objects.
     */
    static void testStreamJump(StringBuilder out) {
        out.append("===== Stream jump: new object =====\n\n");

        runStreamJumpMRG32k3a(out);
        runStreamJumpLFSR258(out);
        runStreamJumpMWC64k2a2(out);
        runStreamJumpMWC64k3a2(out);

        out.append("\n");
    }

    /**
     * Tests substream jumps by repeatedly calling resetNextSubstream().
     */
    static void testSubstreamJump(StringBuilder out) {
        out.append("===== Substream jump: resetNextSubstream() =====\n\n");

        runSubstreamJump("MRG32k3a", new MRG32k3a(), out);
        runSubstreamJump("LFSR258", new LFSR258(), out);
        runSubstreamJump("MWC64k2a2", new MWC64k2a2(), out);
        runSubstreamJump("MWC64k2a2new", new MWC64k2a2new(), out);

        out.append("\n");
    }

    /**
     * Measures stream jumps for MRG32k3a by repeatedly creating new objects.
     */
    static void runStreamJumpMRG32k3a(StringBuilder out) {
        out.append("MRG32k3a\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new MRG32k3a();

            double time = (System.nanoTime() - start) / 1_000_000.0;

            if (rep > 1)
                total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms");

            if (rep == 1)
                out.append("  (warm-up, not included in average)");

            out.append("\n");
        }

        out.append("Average over runs 2 to ").append(N).append(": ")
           .append(total / (N - 1)).append(" ms\n\n");
    }

    /**
     * Measures stream jumps for MWC64k2a2 by repeatedly creating new objects.
     */
    static void runStreamJumpMWC64k2a2(StringBuilder out) {
        out.append("MWC64k2a2\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new MWC64k2a2();

            double time = (System.nanoTime() - start) / 1_000_000.0;

            if (rep > 1)
                total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms");

            if (rep == 1)
                out.append("  (warm-up, not included in average)");

            out.append("\n");
        }

        out.append("Average over runs 2 to ").append(N).append(": ")
           .append(total / (N - 1)).append(" ms\n\n");
    }

    /**
     * Measures stream jumps for MWC64k3a2 by repeatedly creating new objects.
     */
    static void runStreamJumpMWC64k3a2(StringBuilder out) {
        out.append("MWC64k2a2new\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new MWC64k2a2new();

            double time = (System.nanoTime() - start) / 1_000_000.0;

            if (rep > 1)
                total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms");

            if (rep == 1)
                out.append("  (warm-up, not included in average)");

            out.append("\n");
        }

        out.append("Average over runs 2 to ").append(N).append(": ")
           .append(total / (N - 1)).append(" ms\n\n");
    }

    /**
     * Measures stream jumps for LFSR258 by repeatedly creating new objects.
     */
    static void runStreamJumpLFSR258(StringBuilder out) {
        out.append("LFSR258\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new LFSR258();

            double time = (System.nanoTime() - start) / 1_000_000.0;

            if (rep > 1)
                total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms");

            if (rep == 1)
                out.append("  (warm-up, not included in average)");

            out.append("\n");
        }

        out.append("Average over runs 2 to ").append(N).append(": ")
           .append(total / (N - 1)).append(" ms\n\n");
    }

    /**
     * Measures substream jumps for a given RandomStream by repeatedly calling
     * resetNextSubstream() on the same stream object.
     *
     * @param name generator name printed in the result file
     * @param stream stream object used for the test
     * @param out output buffer
     */
    static void runSubstreamJump(String name, RandomStream stream, StringBuilder out) {
        out.append(name).append("\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                stream.resetNextSubstream();

            double time = (System.nanoTime() - start) / 1_000_000.0;

            if (rep > 1)
                total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms");

            if (rep == 1)
                out.append("  (warm-up, not included in average)");

            out.append("\n");
        }

        out.append("Average over runs 2 to ").append(N).append(": ")
           .append(total / (N - 1)).append(" ms\n\n");
    }
}