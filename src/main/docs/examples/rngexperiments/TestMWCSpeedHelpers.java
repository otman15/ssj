package rngexperiments;

import java.math.BigInteger;
import java.util.function.LongSupplier;
import java.io.PrintStream;
import java.io.FileNotFoundException;


/**
* Java mirror of the C++ TestMWCSpeed.cc benchmark. The goal is to reproduce the same generator recurrences
*  and benchmark structure as the C++ file.
* Java does not provide uint64_t or __uint128_t, so unsigned 64-bit values are stored in long variables,
*  and the simulated 128-bit product tau is represented by two 64-bit parts: tauHigh and tauLow.
*
* This version uses helper methods such as setTauMulAdd, setTauMulAdd2,
* setTauMulAdd3, and setTauMul128Add to compute tau. The helpers store the
* simulated 128-bit intermediate values in static temporary variables
* tauLow, tauHigh, pLow, pHigh, oldLow, sum3Low, and sum3High.
*
* Findings from the Java speed tests:
*
* 1. For carry propagation, the branchless form : high += condition ? 1L : 0L; 
*    was faster in these tests than  if (condition) high++;
* This is why carry updates use the ternary-add form.
*
* 2. Generators that require more calls to Math.unsignedMultiplyHigh are
* generally slower in Java, because Java must emulate the high 64 bits of
* an unsigned 128-bit product.
*
* 3. Some Vigna coefficients are unsigned 64-bit constants larger than
* Long.MAX_VALUE, for example constants starting with 0xff.... Java stores
* these as negative long values. Passing such values directly to
* Math.unsignedMultiplyHigh is slower, because the method must correct the
* signed high product to obtain the unsigned high product.
*
* For these cases, this version uses a special helper based on
* a = 2^64 - q  with q = -a in Java. It computes q*x and reconstructs
* (2^64 - q)*x + carry by subtraction. This keeps the same recurrence while
* avoiding the slower negative-coefficient path in Math.unsignedMultiplyHigh.
*
* This helper-based version is useful because it keeps the arithmetic logic
* compact and close to the structure of a reusable implementation.
* 
* To write results to a .res file uncomment the PrintStream in main.
 * */
public final class TestMWCSpeedHelpers {
    static long x, y, z, c; 
    static long x1, x2, x3;

    static long tauLow;//Low 64 bits of the simulated C++ __uint128_t variable tau.
    static long pLow; //  Low halves of second and third products(a2x2; a3x3)
    static long oldLow; // used as tmp to store taulow to see if it has overflowd after the addition: tauLow += carry;
    
    static long tauHigh;//High 64 bits of the simulated C++ __uint128_t variable tau.
    static long pHigh;//high halves of second and third products(a2x2; a3x3)
    
    
    static long sum3Low; // Low 64 bits of x1+x2 or x1+x2+x3 in equal-coefficient generators.
    static long sum3High; //High carry of x1+x2 or x1+x2+x3 in equal-coefficient generators. 
    static long sum;//Unsigned sum modulo 2^64 accumulated in integer speed tests. 

    static long block53;//emporary 53-bit block used by U(0,1) generators that reject zero.
    static long tmp; // for timing
    static long tottmp; // total time

    static final double twom53 = 0x1.0p-53; //1/2^53 equivalent to 1.0 / (double) ((uint64_t) 1 << 53) in c++
    static final double twom55 = 0x1.0p-55;
    static final double twom63 = 0x1.0p-63;
    static final double twom64 = 0x1.0p-64;

    private TestMWCSpeedHelpers() {}

    // print an unit128 in decimal
    static String uint128DecStr(long high, long low) {
        BigInteger hi = new BigInteger(Long.toUnsignedString(high));
        BigInteger lo = new BigInteger(Long.toUnsignedString(low));
        return hi.shiftLeft(64).add(lo).toString();
    }
    // Called at the beginning of a speed test.
    static void init() {
        x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        tmp = System.nanoTime();
    }

    static void printState() {
        System.out.println("sum = " + Long.toUnsignedString(sum));
        System.out.println("c   = " + Long.toUnsignedString(c));
        System.out.println("tau   = " + uint128DecStr(tauHigh, tauLow));
    }

    static void printResults(String rngName, long elapsedNanos, long sum) {
        System.out.printf("%16s%13.6f    %18s%n",
                rngName, elapsedNanos / 1.0e9, Long.toUnsignedString(sum));
    }

    static void printResultsDouble(String rngName, long elapsedNanos, double average) {
        System.out.printf("%16s%13.8f  %12.8f%n",
                rngName, elapsedNanos / 1.0e9, average);
    }

/**
 * Computes tau = a1*x1 as an unsigned 128-bit value.
 * First stores a1*x1 in the global pair (tauHigh, tauLow).
 * Then adds the carry to tauLow and
 * updates tauHigh if the addition overflows tauLow.
 */
    static void setTauMulAdd(long a1, long x1Value, long carry) {
        tauLow = a1 * x1Value;
        tauHigh = Math.unsignedMultiplyHigh(a1, x1Value);
        oldLow = tauLow;
        tauLow += carry;
        tauHigh += Long.compareUnsigned(tauLow, oldLow)  < 0 ? 1L : 0L;
    }
    
    
static void setTauNegMulAddFromA(long a, long xValue, long carry) {
    // a is the original 0xff... coefficient.
    // Since unsigned a = 2^64 - q, Java wraparound gives:
    // q = -a
    long q = -a;

    // Compute q * xValue
    pLow = q * xValue;
    pHigh = Math.unsignedMultiplyHigh(q, xValue);

    // Compute:
    // tau = (2^64 - q) * xValue + carry
    //     = xValue * 2^64 - q * xValue + carry
    tauLow = carry - pLow;

    long borrow = Long.compareUnsigned(carry, pLow) < 0 ? 1L : 0L;

    tauHigh = xValue - pHigh - borrow;
}

    /**
     * Computes tau = a1*x1 then p = a2*x2 , add p to tau, 
     * Stores the result in the global pair (tauHigh, tauLow).
     *  adds the carry and
     *  updates tauHigh if any addition overflows .
     */
    static void setTauMulAdd2(long a1, long x1Value, long a2, long x2Value, long carry) {
        tauLow = a1 * x1Value;
        tauHigh = Math.unsignedMultiplyHigh(a1, x1Value);
        pLow = a2 * x2Value;
        pHigh = Math.unsignedMultiplyHigh(a2, x2Value);
        // Previous low half, used to detect carry after adding pLow.
        oldLow = tauLow;
        tauLow += pLow;
        tauHigh += pHigh + (Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L);
        oldLow = tauLow;
        tauLow += carry;
        tauHigh += Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L;
    }

    /**
     * Computes tau = a1*x1Value + a2*x2Value + a3*x3Value + carry
     * as an unsigned 128-bit value. Stores the result in (tauHigh, tauLow), update tauhigh if additions overflow
     */
    static void setTauMulAdd3(long a1, long x1Value, long a2, long x2Value, long a3, long x3Value, long carry) {
        tauLow = a1 * x1Value;
        tauHigh = Math.unsignedMultiplyHigh(a1, x1Value);
        
        pLow = a2 * x2Value;
        pHigh = Math.unsignedMultiplyHigh(a2, x2Value);
        oldLow = tauLow;
        tauLow += pLow;
        tauHigh += pHigh + (Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L);
        
        pLow = a3 * x3Value;
        pHigh = Math.unsignedMultiplyHigh(a3, x3Value);
        oldLow = tauLow;
        tauLow += pLow;
        tauHigh += pHigh + (Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L);
        
        oldLow = tauLow;
        tauLow += carry;
        tauHigh +=  Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L;
    }


    /**
     * Computes tau = coefficient*(sumHigh*2^64 + sumLow) + carry.
     * Used when equal coefficients require multiplying a 128-bit state sum.
     */
    static void setTauMul128Add(long coefficient, long sumLow, long sumHigh, long carry) {
        tauLow = coefficient * sumLow;
        tauHigh = Math.unsignedMultiplyHigh(coefficient, sumLow) + coefficient * sumHigh;
        oldLow = tauLow;
        tauLow += carry;
        tauHigh +=  Long.compareUnsigned(tauLow, oldLow) < 0 ? 1L : 0L;
    }

    /**
     * Returns the high 64 bits of tau - coefficient*stateValue.
     * Used by general-a0 generators where the new carry is derived this way. See mwc64k2a..gk,
     */
    static long highOfTauMinusProduct(long coefficient, long stateValue) {
        // Low and high halves of the unsigned 128-bit product being subtracted.
        long pLow = coefficient * stateValue;
        long pHigh = Math.unsignedMultiplyHigh(coefficient, stateValue);

        // Borrow from the low half subtraction.
        long borrow = Long.compareUnsigned(tauLow, pLow) < 0 ? 1L : 0L;
        return tauHigh - pHigh - borrow;
    }


    /**
     * Returns the high 64 bits of tau + coefficient*stateValue.
     * Used by GMWC256 for its carry update.
     */
    static long highOfTauPlusProduct(long coefficient, long stateValue) {
        // Low and high halves of the unsigned 128-bit product being added.
        long pLow = coefficient * stateValue;
        long pHigh = Math.unsignedMultiplyHigh(coefficient, stateValue);

        // Low half of tau + product, used to detect carry.
        long low = tauLow + pLow;
        long carry = Long.compareUnsigned(low, tauLow) < 0 ? 1L : 0L;
        return tauHigh + pHigh + carry;
    }


    /**
     * Converts a Java long interpreted as unsigned uint64_t into a nonnegative double.
     * Needed for full 64-bit scaling by 2^-64.
     */
    static double unsignedToDouble(long v) {
        if (v >= 0L)
            return (double) v;
        return (double) (v & Long.MAX_VALUE) + 0x1.0p63;
        
    }

    // *******   k = 1  ********************************************** 

    // From Vigna 2021

    static long MWC128() {
        final long result = x;
        //setTauMulAdd(0x87eac24b8adc9L, x, c);
        setTauNegMulAddFromA(0xffebb71d94fcdaf9L, x, c); 
        x = tauLow;
        c = tauHigh;
        return result;
    }

    // k = 1, a_0 = -1.
    static long mwc64k1() {
        x2 = x1;
        setTauMulAdd(0x87eac24b8adc9L, x1, c);
        c = tauHigh;
        x1 = tauLow;
        return x2;
    }

    // *******   k = 2  **********************************************

    // From Vigna 2021. Here, a_0=-1 and a_1=0.

    static long MWC192() {
        final long result = y;
        //setTauMulAdd(0x4b740f53265dL, x, c);
        setTauNegMulAddFromA(0xffa04e67b3c95d86L, x, c);
        x = y;
        y = tauLow;
        c = tauHigh;
        return result;
    }

    // From MWC1k2-0-62.res Here, a_0=-1 and a_1=0.

    static long mwc64k2a1() {
        x3 = x1;
        setTauMulAdd(0x4b740f53265dL, x2, c);
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return x3;
    }

    // From MWC1k2-62-58.res a_0=-1

    static long mwc64k2a2() {
        final long out = x1;
        setTauMulAdd2(0x2ae390b92740f6dL, x1, 0x6fcce264fcc37L, x2, c);
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWC1k2-62-58.res a_0=-1

    /**
     * Same recurrence as mwc64k2a2, but returns x1 xor x2 as the output.
     */
    static long mwc64k2a2Xor() {
        final long out = x1 ^ x2;
        setTauMulAdd2(0x2ae390b92740f6dL, x1, 0x6fcce264fcc37L, x2, c);
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // a_0=-1

    /**
     * k=2 MWC64 generator where the two coefficients are equal.
     * Computes coefficient*(x1+x2)+c using a 128-bit state sum.
     */
    static long mwc64k2a2eq() {
        final long out = x1;
        sum3Low = x1 + x2;
        sum3High = Long.compareUnsigned(sum3Low, x1) < 0 ? 1L : 0L; // get the high if overflow
        setTauMul128Add(0xc45ec2462f86L, sum3Low, sum3High, c);// compute tau: taulow = coef * sumlow; tau high = coef * tauhigh + overflow of taulow
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    /**
     * The is the same, ....... i m keeping it to mirror c++ code and results?
     */
    static long mwc64k2a2eq2() {
        final long out = x1;      
        sum3Low = x1 + x2;
        sum3High = Long.compareUnsigned(sum3Low, x1) < 0 ? 1L : 0L;
        setTauMul128Add(0xc45ec2462f86L, sum3Low, sum3High, c);
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWCa0k2-0-62-58.res a_0 general, a_1=0

    static long mwc64k2a1gk() {
        setTauMulAdd(0x6595a0b6737eL, x2, c);
        x2 = x1;
        x1 = 0xba1485699989776dL * tauLow;
        c = highOfTauMinusProduct(0xe4e1b5e445c2a65L, x1);
        return x2;
    }

    // From MWCa0k2-60-58.res a_0 general
    static long mwc64k2a2gk() {
        final long out = x1;
        setTauMulAdd2(0x22ddf8545f51c3dL, x1, 0x3b4ba2cc0eb83d39L, x2, c);
        x2 = x1;
        x1 = 0x5a9b2e257b3e1d95L * tauLow;
        c = highOfTauMinusProduct(0x0f0cdf98a7bf45bdL, x1);
        return out;
    }

    // *******   k = 3  *********************************************

    // From Vigna 2021. a_0=-1, a_1 = a_2 = 0.

    /**
     * Vigna 2021 MWC256 raw generator. Returns z and advances (x,y,z,c).
     */
    static long MWC256() {
        final long result = z;
       // setTauMulAdd(0x14fabef33841dL, x, c);
       setTauNegMulAddFromA(0xfff62cf2ccc0cdafL, x, c); ///
        x = y;
        y = z;
        z = tauLow;
        c = tauHigh;
        return result;
    }

    // From MWC1k3-00-62.res a_0 = -1, a_1 = a_2 = 0.

    /**0x14fabef33841dL
     * k=3 MWC64 generator with a0=-1 and only the last coefficient nonzero.
     */
    static long mwc64k3a1() {
        final long out = x1;
        setTauMulAdd(0x14fabef33841dL, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWC1k3-0-60-58.res a_0 = -1, a_1 = 0.

    /**
     * k=3 MWC64 generator with a0=-1 and two nonzero coefficients.
     */
    static long mwc64k3a2() {
        final long out = x1;
        setTauMulAdd2(0x2902ebc31ec3683L, x2, 0x57baa090037L, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWC1k3-60-58.res a_0 = -1.

    /**
     * k=3 MWC64 generator with a0=-1 and three nonzero coefficients.
     */
    static long mwc64k3a3() {
        final long out = x1;
        setTauMulAdd3(0x979f3660cbaca4L, x1, 0x31dcb74b96510a8L, x2,
                0x5194d4649dcdL, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // Two coefficients are equal. a_0 =-1, a_2 = a_3.

    /**
     * k=3 generator with two equal coefficients on x2 and x3.
     */
    static long mwc64k3a2eq() {
        final long out = x1;
        sum3Low = x2 + x3;
        sum3High = Long.compareUnsigned(sum3Low, x2) < 0 ? 1L : 0L;
        setTauMul128Add(0x1d0710107d5dL, sum3Low, sum3High, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // Three coefficients are equal. a_0 =-1, a_1 = a_2 = a_3.

    /**
     * k=3 generator with three equal coefficients on x1, x2, and x3.
     */
    static long mwc64k3a3eq() {
        final long out = x1;
        // Low half of x1+x2+x3.
        long t = x1 + x2;

        // High carry of the 128-bit sum x1+x2+x3.
        long h = Long.compareUnsigned(t, x1) < 0 ? 1L : 0L;

        // Previous low half, used to detect carry when adding x3.
        long old = t;
        t += x3;
        h += Long.compareUnsigned(t, old) < 0 ? 1L : 0L;
        sum3Low = t;
        sum3High = h;
        setTauMul128Add(0x1d495210185cL, sum3Low, sum3High, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWC1k3-0-60-58.res a_0 = -1, a_1 = a_2 = 0.

    /**
     * Same recurrence as mwc64k3a1, but returns x1 xor x3.
     */
    static long mwc64k3a1Xor() {
        final long out = x1 ^ x3;
        setTauMulAdd(0x14fabef33841dL, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWC1k3-0-60-58.res a_0 = -1, a_1 = 0.

    /**
     * Same recurrence as mwc64k3a2, but returns x1 xor x3.
     */
    static long mwc64k3a2Xor() {
        final long out = x1 ^ x3;
        setTauMulAdd2(0x2902ebc31ec3683L, x2, 0x57baa090037L, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // From MWC1k3-60-58.res a_0 = -1.

    /**
     * Same recurrence as mwc64k3a3, but returns x1 xor x2.
     */
    static long mwc64k3a3Xor() {
        final long out = x1 ^ x2;
        setTauMulAdd3(0x979f3660cbaca4L, x1, 0x31dcb74b96510a8L, x2,
                0x5194d4649dcdL, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = tauLow;
        c = tauHigh;
        return out;
    }

    // This version never returns 0, uses a while

    /**
     * Nonzero-output version of mwc64k3a2 using a while loop to reject zero.
     */
    static long mwc64k3a2No0w() {
        long out = mwc64k3a2();
        while (out == 0L)
            out = mwc64k3a2();
        return out;
    }

    // This version never returns 0, uses a if.

    /**
     * Nonzero-output version of mwc64k3a2 using recursive retry on zero.
     */
    static long mwc64k3a2No0i() {
        long out = mwc64k3a2();
        if (out == 0L) out = mwc64k3a2No0i();
        return out;
    }

    // This version never returns 0

    /**
     * Nonzero-output version of mwc64k3a2 using an extra draw added on zero.
     */
    static long mwc64k3a2No0a() {
        long out = mwc64k3a2();
        if (out == 0L) out += mwc64k3a2();
        return out;
    }

    // This version never returns 0, uses while.

    static long mwc64k3a3No0w() {
        long out = mwc64k3a3();
        while (out == 0L)
            out = mwc64k3a3();
        return out;
    }

    // This version never returns 0.

    /**
     * Nonzero-output version of mwc64k3a3 using recursive retry on zero.
     */
    static long mwc64k3a3No0i() {
        long out = mwc64k3a3();
        if (out == 0L) out = mwc64k3a3No0i();
        return out;
    }

    // This version never returns 0.
    static long mwc64k3a3No0a() {
        long out = mwc64k3a3();
        if (out == 0L) out += mwc64k3a3();
        return out;
    }

    // Parameters from Vigna 2021.
    static final long GMWC_MINUSA0 = 0x54c3da46afb70fL;
    static final long GMWC_A0INV = 0xbbf397e9a69da811L;
    static final long GMWC_A3 = 0xff963a86efd088a2L;
    
    //static final long GMWC_MINUSA0 = 0xf53334560dL;
    //static final long GMWC_A0INV = 0xa55ad7c337410603L;
    //static final long GMWC_A3 = 0x02ce8eef308854abL;

    // From Vigna 2021. a_0 general, a_1 = a_2 = 0.
    static long GMWC256() {
        setTauNegMulAddFromA(GMWC_A3, x, c);
        x = y;
        y = z;
        z = GMWC_A0INV * tauLow;
        c = highOfTauPlusProduct(GMWC_MINUSA0, z);
        return z;
    }

    // From MWCa0k3-00-62.res a_0 general, a_1 = a_2 = 0.

    static long mwc64k3a1gk() {
        final long out = x1;
        setTauMulAdd(0xf53334560dL, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = 0xa55ad7c337410603L * tauLow;
        c = highOfTauMinusProduct(0x02ce8eef308854abL, x1);
        return out;
    }

    // From MWCa0k3-0-60-58.res a_0 general, a_1 = 0.
    static long mwc64k3a2gk() {
        final long out = x1;
        setTauMulAdd2(0x1a43e76662f692cL, x2, 0x5a888e5764193L, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = 0x9772ec11e3b050c5L * tauLow;
        c = highOfTauMinusProduct(0x0e6f81c49aeeae0dL, x1);
        return out;
    }

    // From MWCa0k3-60-58.res a_0 general.

    /**
     * k=3 general-a0 MWC64 generator with three nonzero coefficients.
     */
    static long mwc64k3a3gk() {
        final long out = x1;
        setTauMulAdd3(0x1427e7f2aedfa9cL, x1, 0x373ad6944cfb8d0L, x2,
                0x0f2125fd7e3997L, x3, c);
        x3 = x2;
        x2 = x1;
        x1 = 0xdd3034dd040dab6bL * tauLow;
        c = highOfTauMinusProduct(0x0ea08673f5e82943L, x1);
        return out;
    }

    // ****************************************************************
    // U(0,1) generators

    // This version may return 0
    static double mwc64k2a2U01() {
        return (mwc64k2a2() >>> 11) * twom53;
    }

    // This version never returns 0, uses a while
    static double mwc64k2a2U01w() {
        block53 = mwc64k2a2() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k2a2() >>> 11;
        return block53 * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k2a2U01i() {
        block53 = mwc64k2a2() >>> 11;
        if (block53 == 0L) return mwc64k2a2U01i();
        return block53 * twom53;
    }


    /**
     * May return zero.
     */
    static double mwc64k2a2XorU01() {
        return (mwc64k2a2Xor() >>> 11) * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k2a2XorU01i() {
        block53 = mwc64k2a2Xor() >>> 11;
        if (block53 == 0L) return mwc64k2a2XorU01i();
        return block53 * twom53;
    }

    // This version may return 0

    static double mwc64k3a1U01() {
        return (mwc64k3a1() >>> 11) * twom53;
    }

    // This version never returns 0, uses a while

    static double mwc64k3a1U01w() {
        block53 = mwc64k3a1() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k3a1() >>> 11;
        return block53 * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k3a1U01i() {
        block53 = mwc64k3a1() >>> 11;
        if (block53 == 0L) return mwc64k3a1U01i();
        return block53 * twom53;
    }


    /**
     * Converts mwc64k3a1Xor output to U(0,1) using the top 53 bits. May return zero.
     */
    static double mwc64k3a1XorU01() {
        return (mwc64k3a1Xor() >>> 11) * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k3a1XorU01i() {
        block53 = mwc64k3a1Xor() >>> 11;
        if (block53 == 0L) return mwc64k3a1XorU01i();
        return block53 * twom53;
    }

    // This version may return 0

    static double mwc64k3a2U01() {
        return (mwc64k3a2() >>> 11) * twom53;
    }

    // This version never returns 0, uses a while

    static double mwc64k3a2U01w() {
        block53 = mwc64k3a2() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k3a2() >>> 11;
        return block53 * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k3a2U01i() {
        block53 = mwc64k3a2() >>> 11;
        if (block53 == 0L) return mwc64k3a2U01i();
        return block53 * twom53;
    }


    /**
     * Converts mwc64k3a2Xor output to U(0,1) using the top 53 bits. May return zero.
     */
    static double mwc64k3a2XorU01() {
        return (mwc64k3a2Xor() >>> 11) * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k3a2XorU01i() {
        block53 = mwc64k3a2Xor() >>> 11;
        if (block53 == 0L) return mwc64k3a2XorU01i();
        return block53 * twom53;
    }

    // This version never returns 0, uses a while

    static double mwc64k3a3U01w() {
        block53 = mwc64k3a3() >>> 11;
        while (block53 == 0L)
            block53 = mwc64k3a3() >>> 11;
        return block53 * twom53;
    }

    // This version never returns 0, uses a if.

    static double mwc64k3a3U01i() {
        block53 = mwc64k3a3() >>> 11;
        if (block53 == 0L) return mwc64k3a3U01i();
        return block53 * twom53;
    }

    // *************************************************************************


    /**
     * Runs a benchmark where the generator is passed as a LongSupplier.
     * This intentionally measures the extra cost of indirect calls.
     */
    static void testLoop(String rngName, LongSupplier func, long n) {
        x = y = z = x1 = x2 = x3 = c = 12345L;
        sum = 0L;
        long tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += func.getAsLong();
        }
        tmp = System.nanoTime() - tmp;
        printResults(rngName, tmp, sum);
    }

    // *************************************************************************


    /**
     * Runs the same sections as the cc file.
     */
    public static void main(String[] args) throws FileNotFoundException {
    	
//    	PrintStream out = new PrintStream("C:/Users/cherrato/Documents/GitHub/Data/o-MWC-test/MWCSpeed10JavUdpVigNoIf.res");
//    	System.setOut(out); // to write to res file uncomment these two lines
    	
         //long n = 4;
         //long n = 1000L * 1000L; // One million
        //long n = 1000L * 1000L * 1000L; // Number of generated values per benchmark: one billion
         long n = 1000L * 1000L * 10000L; // Ten billions
        System.out.println("\n=========JAVA========updated helper for vigna + never use if inside generetors ==========");
        System.out.printf("Time to generate n = %d = %.6e numbers.%n", n, (double) n);
        System.out.println("    Generator     Time (seconds)      Sum mod 2^{64} ");
        tottmp = System.nanoTime();

        // *******   k = 1  *********************************************
        System.out.println("k = 1 ");

        testLoop("MWC128 given as a parameter to testLoop    ", TestMWCSpeedHelpers::MWC128, n);
        testLoop("mwc64k1 given as a parameter to testLoop   ", TestMWCSpeedHelpers::mwc64k1, n);
        testLoop("mwc64k3a2 given as a parameter to testLoop ", TestMWCSpeedHelpers::mwc64k3a2, n);
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
        double dsum = 0.0; // Accumulated sum of U(0,1) values for average tests

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
        // The following blocks mirror the C++ code that appears after
        //     return 0;  // ******************************************************************
        // in TestMWCSpeed.cc. They are included here because they are real test cases,
        // even though they are unreachable in the original C++ file unless that return is removed.

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
