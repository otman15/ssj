package rngexperiments;

import java.io.FileWriter;
import java.io.IOException;

import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.RandomStream;


public class MRGJumpSpeedTest {

    static final int M = 1_000_000;
    static final int N = 5;

    static RandomStream streamSink;

    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();

        out.append("Jump speed test\n");
        out.append("M = ").append(M).append(" jumps per run\n");
        out.append("N = ").append(N).append(" runs\n\n");

        testStreamJump(out);
        testSubstreamJump(out);

        System.out.println(out);

//        FileWriter writer = new FileWriter("/home/otman/Documents/GitHub/Data/o-MWC-test/MRGJumpSpeedTest.res");
//        writer.write(out.toString());
//        writer.close();
    }

    static void testStreamJump(StringBuilder out) {
        out.append("===== Stream jump: new object =====\n\n");

        runStreamJumpMRG32k3a(out);      
        runStreamJumpLFSR258(out);
        runStreamJumpMWC64k2a2(out);
        runStreamJumpMWC64k3a2(out);

        out.append("\n");
    }

    static void testSubstreamJump(StringBuilder out) {
        out.append("===== Substream jump: resetNextSubstream() =====\n\n");

        runSubstreamJump("MRG32k3a", new MRG32k3a(), out);
        runSubstreamJump("LFSR258", new LFSR258(), out);
        runSubstreamJump("MWC64k2a2", new MWC64k2a2(), out);
        runSubstreamJump("MWC64k3a2", new MWC64k3a2(), out);
        

        out.append("\n");
    }

    static void runStreamJumpMRG32k3a(StringBuilder out) {
        out.append("MRG32k3a\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new MRG32k3a();

            double time = (System.nanoTime() - start) / 1_000_000.0;
            total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms\n");
        }

        out.append("Average: ").append(total / N).append(" ms\n\n");
    }

    static void runStreamJumpMWC64k2a2(StringBuilder out) {
        out.append("MWC64k2a2\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new MWC64k2a2(); 

            double time = (System.nanoTime() - start) / 1_000_000.0;
            total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms\n");
        }

        out.append("Average: ").append(total / N).append(" ms\n\n");
    }
    
    static void runStreamJumpMWC64k3a2(StringBuilder out) {
        out.append("MWC64k3a2\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new MWC64k3a2(); 

            double time = (System.nanoTime() - start) / 1_000_000.0;
            total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms\n");
        }

        out.append("Average: ").append(total / N).append(" ms\n\n");
    }

    static void runStreamJumpLFSR258(StringBuilder out) {
        out.append("LFSR258\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                streamSink = new LFSR258();

            double time = (System.nanoTime() - start) / 1_000_000.0;
            total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms\n");
        }

        out.append("Average: ").append(total / N).append(" ms\n\n");
    }

    static void runSubstreamJump(String name, RandomStream stream, StringBuilder out) {
        out.append(name).append("\n");

        double total = 0.0;

        for (int rep = 1; rep <= N; rep++) {
            long start = System.nanoTime();

            for (int i = 0; i < M; i++)
                stream.resetNextSubstream();

            double time = (System.nanoTime() - start) / 1_000_000.0;
            total += time;

            out.append("Run ").append(rep).append(": ").append(time).append(" ms\n");
        }

        out.append("Average: ").append(total / N).append(" ms\n\n");
    }
}
