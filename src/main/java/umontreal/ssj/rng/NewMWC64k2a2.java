package umontreal.ssj.rng;

/**
 * Object generator version of TestMWCSpeed.mwc64k2a2.
 *
 * This class keeps only the mwc64k2a2 recurrence and its U(0,1)
 * conversions. The recurrence computation is written directly inside
 * nextNumber(), with the same unsigned-128 simulation logic used in
 * TestMWCSpeed_1.setTauMulAdd2(...), but without helper calls.
 */
public final class NewMWC64k2a2 {
    /** First benchmark coefficient from TestMWCSpeed.mwc64k2a2(). */
    private static final long A1 = 193154555888013165L;

    /** Second benchmark coefficient from TestMWCSpeed.mwc64k2a2(). */
    private static final long A2 = 0x6fcce264fcc37L;

    /** 2^-53, used by the 53-bit U(0,1) conversions. */
    private static final double TWOM53 = 0x1.0p-53;

    /** 2^-55, used by the shifted 53-bit U(0,1) conversion. */
    private static final double TWOM55 = 0x1.0p-55;

    /** 2^-63, used by the 63-bit U(0,1) conversion. */
    private static final double TWOM63 = 0x1.0p-63;

    /** 2^-64, used by the full unsigned 64-bit U(0,1) conversion. */
    private static final double TWOM64 = 0x1.0p-64;

    /** Current state x1, same role as the benchmark variable x1. */
    private long x1;

    /** Current state x2, same role as the benchmark variable x2. */
    private long x2;

    /** Current carry c, same role as the benchmark variable c. */
    private long c;

    /** Creates the generator with the benchmark seed x1 = x2 = c = 12345. */
    public NewMWC64k2a2() {
        this(12345L, 12345L, 12345L);
    }

    /** Creates the generator with a benchmark-style state: x1, x2, c. */
    public NewMWC64k2a2(long x1, long x2, long c) {
        this.x1 = x1;
        this.x2 = x2;
        this.c = c;
    }

    /** Resets the generator state. State order is x1, x2, c. */
    public void setState(long x1, long x2, long c) {
        this.x1 = x1;
        this.x2 = x2;
        this.c = c;
    }

    /** Resets to the benchmark seed x1 = x2 = c = 12345. */
    public void resetBenchmarkSeed() {
        x1 = x2 = c = 12345L;
    }

    /** Returns the current state in order {x1, x2, c}. */
    public long[] getState() {
        return new long[] { x1, x2, c };
    }

    /**
     * Raw mwc64k2a2 generator.
     *
     * Mirrors TestMWCSpeed.mwc64k2a2():
     *
     *     out = old x1
     *     tau = A1*x1 + A2*x2 + c
     *     x2 = old x1
     *     x1 = low 64 bits of tau
     *     c  = high 64 bits of tau
     *     return out
     *
     * The unsigned 128-bit tau is represented locally by tauHigh and tauLow.
     */
    public long nextNumber() {
        final long out = x1;

        long tauLow = A1 * x1;
        long tauHigh = Math.unsignedMultiplyHigh(A1, x1);

        long pLow = A2 * x2;
        long pHigh = Math.unsignedMultiplyHigh(A2, x2);

        long oldLow = tauLow;
        tauLow += pLow;
        tauHigh += pHigh + (Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L);

        oldLow = tauLow;
        tauLow += c;
        if (Long.compareUnsigned(tauLow, oldLow) < 0)
            tauHigh++;

        x2 = x1;
        x1 = tauLow;
        c = tauHigh;

        return out;
    }

    /** Same as nextDouble01(): 53-bit U(0,1) conversion, may return 0. */
    public double nextValue() {
        return (nextNumber() >>> 11) * TWOM53;
    }

    /** Mirrors TestMWCSpeed.mwc64k2a2U01(): 53-bit conversion, may return 0. */
    public double nextDouble01() {
        return (nextNumber() >>> 11) * TWOM53;
    }

    /** Mirrors TestMWCSpeed.mwc64k2a2U01w(): 53-bit conversion, rejects 0 with while. */
    public double nextDouble01w() {
        long block53 = nextNumber() >>> 11;
        while (block53 == 0L)
            block53 = nextNumber() >>> 11;
        return block53 * TWOM53;
    }

    /** Mirrors TestMWCSpeed.mwc64k2a2U01i(): 53-bit conversion, rejects 0 recursively. */
    public double nextDouble01i() {
        long block53 = nextNumber() >>> 11;
        if (block53 == 0L)
            return nextDouble01i();
        return block53 * TWOM53;
    }

    /** Mirrors the benchmark expression: (mwc64k2a2() >>> 1) * 2^-63. */
    public double nextDouble01_63() {
        return (nextNumber() >>> 1) * TWOM63;
    }

    /**
     * Mirrors the benchmark full 64-bit U(0,1) conversion.
     *
     * Java long is signed, so a negative long must be interpreted as an
     * unsigned 64-bit value before multiplying by 2^-64.
     */
    public double nextDouble01_64() {
        long v = nextNumber();
        return (v >= 0L ? (double) v : (double) (v & Long.MAX_VALUE) + 0x1.0p63) * TWOM64;
    }

    /** Mirrors the benchmark expression: (mwc64k2a2() >>> 11) * 2^-53 + 2^-55. */
    public double nextDouble01Plus55() {
        return (nextNumber() >>> 11) * TWOM53 + TWOM55;
    }
}