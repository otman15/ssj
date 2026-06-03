import java.util.function.LongSupplier;

/**
 * Java mirror of TestMWCSpeed.cc with local 128-bit temporaries.
 *
 * Generator state remains static, as in the original benchmark, but the
 * simulated uint128 intermediates are local variables inside each generator.
 * No setTau... arithmetic helpers are used in the hot generator methods.
 */
public final class TestMWCSpeed_2_Local {
    static long x, y, z, c;
    static long x1, x2, x3;
    static long sum;
    static long tmp;
    static long tottmp;

    static final double twom53 = 0x1.0p-53;
    static final double twom55 = 0x1.0p-55;
    static final double twom63 = 0x1.0p-63;
    static final double twom64 = 0x1.0p-64;

    static final long GMWC_MINUSA0 = 0x54c3da46afb70fL;
    static final long GMWC_A0INV = 0xbbf397e9a69da811L;
    static final long GMWC_A3 = 0xff963a86efd088a2L;

    private TestMWCSpeed_2_Local() {}

    static void printResults(String rngName, long elapsedNanos, long sum) {
        System.out.printf("%16s%13.6f    %18s%n",
                rngName, elapsedNanos / 1.0e9, Long.toUnsignedString(sum));
    }

    static void printResultsDouble(String rngName, long elapsedNanos, double average) {
        System.out.printf("%16s%13.8f  %12.8f%n",
                rngName, elapsedNanos / 1.0e9, average);
    }

    static double unsignedToDouble(long v) {
        if (v >= 0L)
            return (double) v;
        return (double) (v & Long.MAX_VALUE) + 0x1.0p63;
    }

    // *******   k = 1  **********************************************

    // From Vigna 2021. Coefficient is 0xff..., so use a = 2^64 - q, q = -a.
    static long MWC128() {
        final long result = x;
        final long q = -0xffebb71d94fcdaf9L;

        long pl = q * x;
        long ph = Math.unsignedMultiplyHigh(q, x);
        long tl = c - pl;
        long borrow = Long.compareUnsigned(c, pl) < 0 ? 1L : 0L;
        long th = x - ph - borrow;

        x = tl;
        c = th;
        return result;
    }

    // k = 1, a_0 = -1.
    static long mwc64k1() {
        final long out = x1;

        long tl = 0x87eac24b8adc9L * x1;
        long th = Math.unsignedMultiplyHigh(0x87eac24b8adc9L, x1);
        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x2 = out;
        x1 = tl;
        c = th;
        return out;
    }

    // *******   k = 2  **********************************************

    // From Vigna 2021. Coefficient is 0xff..., so use a = 2^64 - q, q = -a.
    static long MWC192() {
        final long result = y;
        final long q = -0xffa04e67b3c95d86L;

        long pl = q * x;
        long ph = Math.unsignedMultiplyHigh(q, x);
        long tl = c - pl;
        long borrow = Long.compareUnsigned(c, pl) < 0 ? 1L : 0L;
        long th = x - ph - borrow;

        x = y;
        y = tl;
        c = th;
        return result;
    }

    // From MWC1k2-0-62.res. Here, a_0=-1 and a_1=0.
    static long mwc64k2a1() {
        final long out = x1;

        long tl = 0x4b740f53265dL * x2;
        long th = Math.unsignedMultiplyHigh(0x4b740f53265dL, x2);
        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = out;
        x2 = out;
        x1 = tl;
        c = th;
        return out;
    }

    // From MWC1k2-62-58.res. a_0=-1.
    static long mwc64k2a2() {
        final long out = x1;

        long tl = 0x2ae390b92740f6dL * x1;
        long th = Math.unsignedMultiplyHigh(0x2ae390b92740f6dL, x1);

        long pl = 0x6fcce264fcc37L * x2;
        long ph = Math.unsignedMultiplyHigh(0x6fcce264fcc37L, x2);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Same recurrence as mwc64k2a2, but returns x1 xor x2.
    static long mwc64k2a2Xor() {
        final long out = x1 ^ x2;

        long tl = 0x2ae390b92740f6dL * x1;
        long th = Math.unsignedMultiplyHigh(0x2ae390b92740f6dL, x1);

        long pl = 0x6fcce264fcc37L * x2;
        long ph = Math.unsignedMultiplyHigh(0x6fcce264fcc37L, x2);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // a_0=-1. Equal coefficients on x1 and x2.
    static long mwc64k2a2eq() {
        final long out = x1;

        long sLow = x1 + x2;
        long sHigh = Long.compareUnsigned(sLow, x1) < 0 ? 1L : 0L;

        long tl = 0xc45ec2462f86L * sLow;
        long th = Math.unsignedMultiplyHigh(0xc45ec2462f86L, sLow)
                + 0xc45ec2462f86L * sHigh;

        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Same as mwc64k2a2eq, kept to mirror the C++ test.
    static long mwc64k2a2eq2() {
        final long out = x1;

        long sLow = x1 + x2;
        long sHigh = Long.compareUnsigned(sLow, x1) < 0 ? 1L : 0L;

        long tl = 0xc45ec2462f86L * sLow;
        long th = Math.unsignedMultiplyHigh(0xc45ec2462f86L, sLow)
                + 0xc45ec2462f86L * sHigh;

        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // From MWCa0k2-0-62-58.res. a_0 general, a_1=0.
    static long mwc64k2a1gk() {
        long tl = 0x6595a0b6737eL * x2;
        long th = Math.unsignedMultiplyHigh(0x6595a0b6737eL, x2);
        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        long out = x1;
        long newX1 = 0xba1485699989776dL * tl;

        long ql = 0xe4e1b5e445c2a65L * newX1;
        long qh = Math.unsignedMultiplyHigh(0xe4e1b5e445c2a65L, newX1);
        long borrow = Long.compareUnsigned(tl, ql) < 0 ? 1L : 0L;

        x2 = out;
        x1 = newX1;
        c = th - qh - borrow;
        return out;
    }

    // From MWCa0k2-60-58.res. a_0 general.
    static long mwc64k2a2gk() {
        final long out = x1;

        long tl = 0x22ddf8545f51c3dL * x1;
        long th = Math.unsignedMultiplyHigh(0x22ddf8545f51c3dL, x1);

        long pl = 0x3b4ba2cc0eb83d39L * x2;
        long ph = Math.unsignedMultiplyHigh(0x3b4ba2cc0eb83d39L, x2);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        long newX1 = 0x5a9b2e257b3e1d95L * tl;

        long ql = 0x0f0cdf98a7bf45bdL * newX1;
        long qh = Math.unsignedMultiplyHigh(0x0f0cdf98a7bf45bdL, newX1);
        long borrow = Long.compareUnsigned(tl, ql) < 0 ? 1L : 0L;

        x2 = x1;
        x1 = newX1;
        c = th - qh - borrow;
        return out;
    }

    // *******   k = 3  **********************************************

    // From Vigna 2021. Coefficient is 0xff..., so use a = 2^64 - q, q = -a.
    static long MWC256() {
        final long result = z;
        final long q = -0xfff62cf2ccc0cdafL;

        long pl = q * x;
        long ph = Math.unsignedMultiplyHigh(q, x);
        long tl = c - pl;
        long borrow = Long.compareUnsigned(c, pl) < 0 ? 1L : 0L;
        long th = x - ph - borrow;

        x = y;
        y = z;
        z = tl;
        c = th;
        return result;
    }

    // From MWC1k3-00-62.res. a_0=-1, a_1=a_2=0.
    static long mwc64k3a1() {
        final long out = x1;

        long tl = 0x14fabef33841dL * x3;
        long th = Math.unsignedMultiplyHigh(0x14fabef33841dL, x3);
        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // From MWC1k3-0-60-58.res. a_0=-1, a_1=0.
    static long mwc64k3a2() {
        final long out = x1;

        long tl = 0x2902ebc31ec3683L * x2;
        long th = Math.unsignedMultiplyHigh(0x2902ebc31ec3683L, x2);

        long pl = 0x57baa090037L * x3;
        long ph = Math.unsignedMultiplyHigh(0x57baa090037L, x3);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // From MWC1k3-60-58.res. a_0=-1.
    static long mwc64k3a3() {
        final long out = x1;

        long tl = 0x979f3660cbaca4L * x1;
        long th = Math.unsignedMultiplyHigh(0x979f3660cbaca4L, x1);

        long pl = 0x31dcb74b96510a8L * x2;
        long ph = Math.unsignedMultiplyHigh(0x31dcb74b96510a8L, x2);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        pl = 0x5194d4649dcdL * x3;
        ph = Math.unsignedMultiplyHigh(0x5194d4649dcdL, x3);
        old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Two coefficients are equal. a_0=-1, a_2=a_3.
    static long mwc64k3a2eq() {
        final long out = x1;

        long sLow = x2 + x3;
        long sHigh = Long.compareUnsigned(sLow, x2) < 0 ? 1L : 0L;

        long tl = 0x1d0710107d5dL * sLow;
        long th = Math.unsignedMultiplyHigh(0x1d0710107d5dL, sLow)
                + 0x1d0710107d5dL * sHigh;

        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Three coefficients are equal. a_0=-1, a_1=a_2=a_3.
    static long mwc64k3a3eq() {
        final long out = x1;

        long sLow = x1 + x2;
        long sHigh = Long.compareUnsigned(sLow, x1) < 0 ? 1L : 0L;
        long old = sLow;
        sLow += x3;
        sHigh += Long.compareUnsigned(sLow, old) < 0 ? 1L : 0L;

        long tl = 0x1d495210185cL * sLow;
        long th = Math.unsignedMultiplyHigh(0x1d495210185cL, sLow)
                + 0x1d495210185cL * sHigh;

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Same recurrence as mwc64k3a1, but returns x1 xor x3.
    static long mwc64k3a1Xor() {
        final long out = x1 ^ x3;

        long tl = 0x14fabef33841dL * x3;
        long th = Math.unsignedMultiplyHigh(0x14fabef33841dL, x3);
        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Same recurrence as mwc64k3a2, but returns x1 xor x3.
    static long mwc64k3a2Xor() {
        final long out = x1 ^ x3;

        long tl = 0x2902ebc31ec3683L * x2;
        long th = Math.unsignedMultiplyHigh(0x2902ebc31ec3683L, x2);

        long pl = 0x57baa090037L * x3;
        long ph = Math.unsignedMultiplyHigh(0x57baa090037L, x3);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // Same recurrence as mwc64k3a3, but returns x1 xor x2.
    static long mwc64k3a3Xor() {
        final long out = x1 ^ x2;

        long tl = 0x979f3660cbaca4L * x1;
        long th = Math.unsignedMultiplyHigh(0x979f3660cbaca4L, x1);

        long pl = 0x31dcb74b96510a8L * x2;
        long ph = Math.unsignedMultiplyHigh(0x31dcb74b96510a8L, x2);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        pl = 0x5194d4649dcdL * x3;
        ph = Math.unsignedMultiplyHigh(0x5194d4649dcdL, x3);
        old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = tl;
        c = th;
        return out;
    }

    // This version never returns 0, uses a while.
    static long mwc64k3a2No0w() {
        long out = mwc64k3a2();
        while (out == 0L)
            out = mwc64k3a2();
        return out;
    }

    // This version never returns 0, uses a if.
    static long mwc64k3a2No0i() {
        long out = mwc64k3a2();
        if (out == 0L) out = mwc64k3a2No0i();
        return out;
    }

    // Mirrors C++: out += (out == 0) * mwc64k3a2(); the second draw is always done.
    static long mwc64k3a2No0a() {
        long out = mwc64k3a2();
        long extra = mwc64k3a2();
        if (out == 0L) out += extra;
        return out;
    }

    // This version never returns 0, uses a while.
    static long mwc64k3a3No0w() {
        long out = mwc64k3a3();
        while (out == 0L)
            out = mwc64k3a3();
        return out;
    }

    // This version never returns 0, uses a if.
    static long mwc64k3a3No0i() {
        long out = mwc64k3a3();
        if (out == 0L) out = mwc64k3a3No0i();
        return out;
    }

    // Mirrors C++: out += (out == 0) * mwc64k3a3(); the second draw is always done.
    static long mwc64k3a3No0a() {
        long out = mwc64k3a3();
        long extra = mwc64k3a3();
        if (out == 0L) out += extra;
        return out;
    }

    // From Vigna 2021. a_0 general, a_1=a_2=0.
    static long GMWC256() {
        final long q = -GMWC_A3;

        long pl = q * x;
        long ph = Math.unsignedMultiplyHigh(q, x);
        long tl = c - pl;
        long borrow = Long.compareUnsigned(c, pl) < 0 ? 1L : 0L;
        long th = x - ph - borrow;

        x = y;
        y = z;
        long newZ = GMWC_A0INV * tl;

        long ql = GMWC_MINUSA0 * newZ;
        long qh = Math.unsignedMultiplyHigh(GMWC_MINUSA0, newZ);
        long low = tl + ql;
        long carryAdd = Long.compareUnsigned(low, tl) < 0 ? 1L : 0L;

        z = newZ;
        c = th + qh + carryAdd;
        return z;
    }

    // From MWCa0k3-00-62.res. a_0 general, a_1=a_2=0.
    static long mwc64k3a1gk() {
        final long out = x1;

        long tl = 0xf53334560dL * x3;
        long th = Math.unsignedMultiplyHigh(0xf53334560dL, x3);
        long old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        long newX1 = 0xa55ad7c337410603L * tl;

        long ql = 0x02ce8eef308854abL * newX1;
        long qh = Math.unsignedMultiplyHigh(0x02ce8eef308854abL, newX1);
        long borrow = Long.compareUnsigned(tl, ql) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = newX1;
        c = th - qh - borrow;
        return out;
    }

    // From MWCa0k3-0-60-58.res. a_0 general, a_1=0.
    static long mwc64k3a2gk() {
        final long out = x1;

        long tl = 0x1a43e76662f692cL * x2;
        long th = Math.unsignedMultiplyHigh(0x1a43e76662f692cL, x2);

        long pl = 0x5a888e5764193L * x3;
        long ph = Math.unsignedMultiplyHigh(0x5a888e5764193L, x3);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        long newX1 = 0x9772ec11e3b050c5L * tl;

        long ql = 0x0e6f81c49aeeae0dL * newX1;
        long qh = Math.unsignedMultiplyHigh(0x0e6f81c49aeeae0dL, newX1);
        long borrow = Long.compareUnsigned(tl, ql) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = newX1;
        c = th - qh - borrow;
        return out;
    }

    // From MWCa0k3-60-58.res. a_0 general.
    static long mwc64k3a3gk() {
        final long out = x1;

        long tl = 0x1427e7f2aedfa9cL * x1;
        long th = Math.unsignedMultiplyHigh(0x1427e7f2aedfa9cL, x1);

        long pl = 0x373ad6944cfb8d0L * x2;
        long ph = Math.unsignedMultiplyHigh(0x373ad6944cfb8d0L, x2);
        long old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        pl = 0x0f2125fd7e3997L * x3;
        ph = Math.unsignedMultiplyHigh(0x0f2125fd7e3997L, x3);
        old = tl;
        tl += pl;
        th += ph + (Long.compareUnsigned(tl, old) < 0 ? 1L : 0L);

        old = tl;
        tl += c;
        th += Long.compareUnsigned(tl, old) < 0 ? 1L : 0L;

        long newX1 = 0xdd3034dd040dab6bL * tl;

        long ql = 0x0ea08673f5e82943L * newX1;
        long qh = Math.unsignedMultiplyHigh(0x0ea08673f5e82943L, newX1);
        long borrow = Long.compareUnsigned(tl, ql) < 0 ? 1L : 0L;

        x3 = x2;
        x2 = x1;
        x1 = newX1;
        c = th - qh - borrow;
        return out;
    }

    // ****************************************************************
    // U(0,1) generators

    static double mwc64k2a2U01() {
        return (mwc64k2a2() >>> 11) * twom53;
    }

    static double mwc64k2a2U01w() {
        long block53 = mwc64k2a2() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k2a2() >>> 11;
        return block53 * twom53;
    }

    static double mwc64k2a2U01i() {
        long block53 = mwc64k2a2() >>> 11;
        if (block53 == 0L) return mwc64k2a2U01i();
        return block53 * twom53;
    }

    static double mwc64k2a2XorU01() {
        return (mwc64k2a2Xor() >>> 11) * twom53;
    }

    static double mwc64k2a2XorU01i() {
        long block53 = mwc64k2a2Xor() >>> 11;
        if (block53 == 0L) return mwc64k2a2XorU01i();
        return block53 * twom53;
    }

    static double mwc64k3a1U01() {
        return (mwc64k3a1() >>> 11) * twom53;
    }

    static double mwc64k3a1U01w() {
        long block53 = mwc64k3a1() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k3a1() >>> 11;
        return block53 * twom53;
    }

    static double mwc64k3a1U01i() {
        long block53 = mwc64k3a1() >>> 11;
        if (block53 == 0L) return mwc64k3a1U01i();
        return block53 * twom53;
    }

    static double mwc64k3a1XorU01() {
        return (mwc64k3a1Xor() >>> 11) * twom53;
    }

    static double mwc64k3a1XorU01i() {
        long block53 = mwc64k3a1Xor() >>> 11;
        if (block53 == 0L) return mwc64k3a1XorU01i();
        return block53 * twom53;
    }

    static double mwc64k3a2U01() {
        return (mwc64k3a2() >>> 11) * twom53;
    }

    static double mwc64k3a2U01w() {
        long block53 = mwc64k3a2() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k3a2() >>> 11;
        return block53 * twom53;
    }

    static double mwc64k3a2U01i() {
        long block53 = mwc64k3a2() >>> 11;
        if (block53 == 0L) return mwc64k3a2U01i();
        return block53 * twom53;
    }

    static double mwc64k3a2XorU01() {
        return (mwc64k3a2Xor() >>> 11) * twom53;
    }

    static double mwc64k3a2XorU01i() {
        long block53 = mwc64k3a2Xor() >>> 11;
        if (block53 == 0L) return mwc64k3a2XorU01i();
        return block53 * twom53;
    }

    static double mwc64k3a3U01w() {
        long block53 = mwc64k3a3() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k3a3() >>> 11;
        return block53 * twom53;
    }

    static double mwc64k3a3U01i() {
        long block53 = mwc64k3a3() >>> 11;
        if (block53 == 0L) return mwc64k3a3U01i();
        return block53 * twom53;
    }

    // *************************************************************************

    static void testLoop(String rngName, LongSupplier func, long n) {
        x = y = z = x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        long t = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += func.getAsLong();
        }
        t = System.nanoTime() - t;
        printResults(rngName, t, sum);
    }

    // *************************************************************************

    public static void main(String[] args) {
        // long n = 4;
        // long n = 1000L * 1000L; // One million
         long n = 1000L * 1000L * 1000L; // One billion
        //long n = 1000L * 1000L * 10000L; // Ten billions

        System.out.println("\n=========JAVA LOCAL TEMPS========");
        System.out.printf("Time to generate n = %d = %.6e numbers.%n", n, (double) n);
        System.out.println("    Generator     Time (seconds)      Sum mod 2^{64} ");
        tottmp = System.nanoTime();

        // *******   k = 1  *********************************************
        System.out.println("k = 1 ");

        testLoop("MWC128 given as a parameter to testLoop    ", TestMWCSpeed_2_Local::MWC128, n);
        testLoop("mwc64k1 given as a parameter to testLoop   ", TestMWCSpeed_2_Local::mwc64k1, n);
        testLoop("mwc64k3a2 given as a parameter to testLoop ", TestMWCSpeed_2_Local::mwc64k3a2, n);
        System.out.println();

        x = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += MWC128();
        }
        tmp = System.nanoTime() - tmp;
        printResults("MWC128", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k1();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k1", tmp, sum);

        // *******   k = 2  *********************************************
        System.out.println("k = 2 ");

        x = y = z = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += MWC192();
        }
        tmp = System.nanoTime() - tmp;
        printResults("MWC192", tmp, sum);

        x1 = x2 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k2a1();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k2a1", tmp, sum);

        x1 = x2 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k2a2();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k2a2", tmp, sum);

        x1 = x2 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k2a2eq();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k2a2eq", tmp, sum);

        x1 = x2 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k2a2eq2();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k2a2eq2", tmp, sum);

        x1 = x2 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k2a1gk();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k2a1gk", tmp, sum);

        x1 = x2 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k2a2gk();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k2a2gk", tmp, sum);

        // *******   k = 3  *********************************************
        System.out.println("k = 3 ");

        x = y = z = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += MWC256();
        }
        tmp = System.nanoTime() - tmp;
        printResults("MWC256", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a1();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a1", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a2();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a2", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a3();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a3", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a2eq();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a2eq", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a3eq();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a3eq", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a1Xor();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a1Xor", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a2Xor();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a2Xor", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a3Xor();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a3Xor", tmp, sum);
        System.out.println();

        // ************************************************************
        // a_0 < -1

        x = y = z = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += GMWC256();
        }
        tmp = System.nanoTime() - tmp;
        printResults("GMWC256", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a1gk();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a1gk", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a2gk();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a2gk", tmp, sum);

        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += mwc64k3a3gk();
        }
        tmp = System.nanoTime() - tmp;
        printResults("mwc64k3a3gk", tmp, sum);

        // ******************************************************
        System.out.println("\nUniform over (0,1) ");
        System.out.printf("standard dev. for average = (4n)^{-1/2} =    %.8f%n",
                1.0 / Math.sqrt(2.0 * n));
        System.out.println("      Generator              Time (seconds)    Average ");
        double dsum = 0.0;

        // k = 2

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k2a2U01();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2U01    53           ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k2a2() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2 U(0,1) 63          ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k2a2() >>> 11) * twom53 + twom55;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2 U(0,1) 53, + twom55", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k2a2U01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2U01i U(0,1) 53      ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k2a2XorU01();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2XorU01 U(0,1) 53    ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k2a2XorU01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2XorU01i U(0,1) 53   ", tmp, dsum / n);

        System.out.println();

        // k = 3

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a1() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1 U(0,1) 53          ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a1U01();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1U01 U(0,1) 53       ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a1U01w();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1U01w U(0,1) 53      ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a1U01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1U01i U(0,1) 53      ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a1XorU01();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1XorU01 U(0,1) 53    ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a1XorU01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1XorU01i U(0,1) 53   ", tmp, dsum / n);

        System.out.println();

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a2() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2 U(0,1) 53          ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a2U01();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2U01 U(0,1) 53       ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a2U01w();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2U01w U(0,1) 53      ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a2U01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2U01i U(0,1) 53      ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a2XorU01();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2XorU01 U(0,1) 53    ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a2XorU01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2XorU01i U(0,1) 53   ", tmp, dsum / n);

        System.out.println();

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a3() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a3 U(0,1) 53          ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a3U01w();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a3U01w U(0,1) 53      ", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += mwc64k3a3U01i();
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a3U01i U(0,1) 53      ", tmp, dsum / n);

        // ******************************************************************
        // Extra tests that appear after return 0 in the C++ file.

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k2a2()) * twom64;
        }
        printResultsDouble("mwc64k2a2 U(0,1) 64", System.nanoTime() - tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k2a2Xor() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2Xor U(0,1) 53", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k2a2Xor() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2Xor U(0,1) 63", tmp, dsum / n);

        x1 = x2 = x3 = c = 12345L;
        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k2a2Xor()) * twom64;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k2a2Xor U(0,1) 64", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a1() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1 U(0,1) 53", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a1() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1 U(0,1) 63", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k3a1()) * twom64;
        }
        printResultsDouble("mwc64k3a1 U(0,1) 64", System.nanoTime() - tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a1Xor() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1Xor U(0,1)", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a1Xor() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1Xor U(0,1) 63", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k3a1Xor()) * twom64;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a1Xor U(0,1) 64", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a2() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2 U(0,1)   ", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a2() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2 U(0,1) 63", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k3a2()) * twom64;
        }
        printResultsDouble("mwc64k3a2 U(0,1) 64", System.nanoTime() - tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a2Xor() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2Xor U(0,1) 53", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a2Xor() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2Xor U(0,1) 63", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k3a2Xor()) * twom64;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a2Xor U(0,1) 64", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a3() >>> 11) * twom53;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a3 U(0,1) 53 ", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += (mwc64k3a3() >>> 1) * twom63;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a3 U(0,1) 63 ", tmp, dsum / n);

        dsum = 0.0;
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            dsum += unsignedToDouble(mwc64k3a3()) * twom64;
        }
        tmp = System.nanoTime() - tmp;
        printResultsDouble("mwc64k3a3 U(0,1) 64 ", tmp, dsum / n);

        System.out.println();
        printResultsDouble("Total computing time: ", System.nanoTime() - tottmp, 0);
    }
}
