package umontreal.ssj.hups64;

import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.util.Num;

public class NestedUniformScramblingExperimental implements PointSetRandomization {

   public enum Method {
      SSJ_NUS64_COPY,
      SCIML_JL_OWEN,
      SCIML_JL_OWEN_INCREMENTAL,
      SCIML_JL_OWEN_PACKED,
      FRIEDEL,
      FULL_OWEN,
      SSJ_NUS64_PRESORTED,
      SCIML_JL_OWEN_PACKED_CACHED
   }

   private RandomStream stream;
   private final Method method;
   private int numBits;

   private DigitalNetBase2 cachedScimlNet;
   private int cachedScimlPad;
   private int cachedScimlN;
   private int cachedScimlDim;
   private int cachedScimlM;

private byte[][][] cachedScimlOriginBits;
private byte[][][] cachedScimlRandomBits;
private int[][][] cachedScimlIndices;
private byte[][] cachedScimlPerms;

//nus presorted:
private DigitalNetBase2 cachedNusPresortedNet;
private int cachedNusPresortedNumPoints;
private int cachedNusPresortedDim;
private int cachedNusPresortedOutDigits;
private int cachedNusPresortedNumCols;

private long[][] cachedNusPresortedBv;
private int[][] cachedNusPresortedPos;

////////// Julia packed cashed:
private DigitalNetBase2 cachedScimlPackedCachedNet;
private int cachedScimlPackedCachedPad;
private int cachedScimlPackedCachedN;
private int cachedScimlPackedCachedDim;
private int cachedScimlPackedCachedM;
private int cachedScimlPackedCachedOutDigits;
private int cachedScimlPackedCachedNumCols;

private byte[] cachedScimlPackedCachedOriginalBits;
private int[] cachedScimlPackedCachedPermIndices;

//private byte[] cachedScimlPackedPerms;

   public NestedUniformScramblingExperimental(RandomStream stream, Method method) {
      this(stream, method, 0);
   }

   public NestedUniformScramblingExperimental(RandomStream stream, Method method, int numBits) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      if (method == null)
         throw new IllegalArgumentException("method must not be null");

      this.stream = stream;
      this.method = method;
      this.numBits = numBits;
   }

   @Override
   public void randomize(PointSet p) {
      if (!(p instanceof CachedPointSet))
         throw new IllegalArgumentException(
               "NestedUniformScramblingExperimental requires a CachedPointSet");

      CachedPointSet cp = (CachedPointSet) p;
      PointSet parent = cp.getParentPointSet();

      if (!(parent instanceof DigitalNetBase2))
         throw new IllegalArgumentException(
               "NestedUniformScramblingExperimental requires a CachedPointSet of a DigitalNetBase2");

      DigitalNetBase2 net = (DigitalNetBase2) parent;

      switch (method) {
      case SSJ_NUS64_COPY:
         nestedUniformScramble64Copy(net, cp.getArray(), numBits);
         break;
      case SCIML_JL_OWEN:
         scimlJlOwen(net, cp.getArray(), numBits);
         break;
      case SCIML_JL_OWEN_INCREMENTAL:
        scimlJlOwenIncremental(net, cp.getArray(), numBits);
        break;
      case SCIML_JL_OWEN_PACKED:
        scimlJlOwenPacked(net, cp.getArray(), numBits);
        break;

      case SSJ_NUS64_PRESORTED:
         nestedUniformScramble64Presorted(net, cp.getArray(), numBits);
         break;
      case SCIML_JL_OWEN_PACKED_CACHED:
         scimlJlOwenPackedCached(net, cp.getArray(), numBits);
         break;

      default:
         throw new UnsupportedOperationException("Method not implemented yet: " + method);
      }
   }

   @Override
   public void setStream(RandomStream stream) {
      if (stream == null)
         throw new IllegalArgumentException("stream must not be null");
      this.stream = stream;
   }

   @Override
   public RandomStream getStream() {
      return stream;
   }

   public void setNumBits(int numBits) {
      this.numBits = numBits;
   }

   public int getNumBits() {
      return numBits;
   }

   // ssj Nus: Keller and Friedel: add doc later
   private void nestedUniformScramble64Copy(DigitalNetBase2 net, double[][] output, int numBits) {
      assert output.length == net.numPoints;
      assert output.length > 0;
      assert output[0].length == net.dim;
      assert net.outDigits >= 31;
      assert net.outDigits <= 62;

      if (numBits == 0)
         numBits = net.outDigits;

      double localNormFactor = 1.0 / Math.abs((double) (1L << (net.outDigits)));

      int[] poslist = new int[2 * net.numPoints];
      long[] bvlist = new long[2 * net.numPoints];
      int[] counts = new int[256];
      int[] binpos = new int[256];

      for (int j = 0; j < net.dim; ++j) {
         bvlist[0] = 0;
         poslist[0] = 0;

         for (int i = 1; i < net.numPoints; i++) {
            // int pos = 0;
            // int bv = 1;
            // while ((i & bv) == 0) {
            //    pos++;
            //    bv <<= 1;
            // }
            int pos = Integer.numberOfTrailingZeros(i);

            bvlist[i] = bvlist[i - 1] ^ ((net.genMat[j * net.numCols + pos]) >>> (net.outDigits - 31));
            poslist[i] = i;
         }

         for (int b = 0; b < 4; b++) {
            for (int i = 0; i < 256; i++)
               counts[i] = 0;

            int m = (b % 2) * net.numPoints;
            int bb = 8 * b;
            long bv = 0xff << bb;

            for (int i = 0; i < net.numPoints; i++)
               counts[(int) ((bvlist[m + i] & bv)) >>> bb]++;

            binpos[0] = (1 - b % 2) * net.numPoints;

            for (int i = 0; i < 255; i++)
               binpos[i + 1] = binpos[i] + counts[i];

            for (int i = 0; i < net.numPoints; i++) {
               int pos = (int) ((bvlist[m + i] & bv) >>> bb);
               int k = binpos[pos]++;
               bvlist[k] = bvlist[m + i];
               poslist[k] = poslist[m + i];
            }
         }

         long bv = (stream.nextLong(0, (1L << numBits) - 1) << (net.outDigits - numBits));
         long bvlistL = bvlist[0] << (net.outDigits - 31);
         output[poslist[0]][j] = (bvlistL ^ bv) * localNormFactor + net.EpsilonHalf;

         for (int i = 1; i < net.numPoints; i++) {
            long bv2 = (bvlist[i - 1]) << (net.outDigits - 31);
            bvlistL = (bvlist[i]) << (net.outDigits - 31);
            bv2 ^= bvlistL;

            bv2 = (stream.nextLong(0, (1L << numBits) - 1) << (net.outDigits - numBits))
                  & ((1L << (long) Num.log2((double) bv2)) - 1);

            bv ^= bv2;
            output[poslist[i]][j] = (bvlistL ^ bv) * localNormFactor + net.EpsilonHalf;
         }
      }
   }

   /////////////////////nus presorted:
   
/**
 * Applies the SSJ NUS64 algorithm using precomputed sorted point bits.
 * The sorted bvlist and poslist are computed once for the same digital net,
 * then reused across randomizations.
 *
 * @param net the original base-2 digital net
 * @param output the array receiving the scrambled points
 * @param numBits the number of bits randomized; 0 means outDigits
 */
private void nestedUniformScramble64Presorted(DigitalNetBase2 net,
                                              double[][] output,
                                              int numBits) {
   assert output.length == net.numPoints;
   assert output.length > 0;
   assert output[0].length == net.dim;
   assert net.outDigits >= 31;
   assert net.outDigits <= 62;

   if (numBits == 0)
      numBits = net.outDigits;

   ensureNusPresortedCache(net);

   double localNormFactor = 1.0 / Math.abs((double) (1L << net.outDigits));

   for (int j = 0; j < net.dim; ++j) {
      long[] bvlist = cachedNusPresortedBv[j];
      int[] poslist = cachedNusPresortedPos[j];

      long bv = (stream.nextLong(0, (1L << numBits) - 1)
                 << (net.outDigits - numBits));

      long bvlistL = bvlist[0] << (net.outDigits - 31);
      output[poslist[0]][j] = (bvlistL ^ bv) * localNormFactor + + net.EpsilonHalf;

      for (int i = 1; i < net.numPoints; i++) {
         long bv2 = bvlist[i - 1] << (net.outDigits - 31);
         bvlistL = bvlist[i] << (net.outDigits - 31);
         bv2 ^= bvlistL;

         bv2 = (stream.nextLong(0, (1L << numBits) - 1)
                << (net.outDigits - numBits))
               & ((1L << (long) Num.log2((double) bv2)) - 1);

         bv ^= bv2;

         output[poslist[i]][j] = (bvlistL ^ bv) * localNormFactor + + net.EpsilonHalf;
      }
   }
}


/**
 * Computes the sorted bvlist and poslist used by the SSJ NUS64 algorithm.
 * This part is deterministic and does not depend on the random stream.
 *
 * @param net the original base-2 digital net
 */
private void ensureNusPresortedCache(DigitalNetBase2 net) {
   if (cachedNusPresortedNet == net &&
       cachedNusPresortedNumPoints == net.numPoints &&
       cachedNusPresortedDim == net.dim &&
       cachedNusPresortedOutDigits == net.outDigits &&
       cachedNusPresortedNumCols == net.numCols &&
       cachedNusPresortedBv != null &&
       cachedNusPresortedPos != null)
      return;

   cachedNusPresortedNet = net;
   cachedNusPresortedNumPoints = net.numPoints;
   cachedNusPresortedDim = net.dim;
   cachedNusPresortedOutDigits = net.outDigits;
   cachedNusPresortedNumCols = net.numCols;

   cachedNusPresortedBv = new long[net.dim][net.numPoints];
   cachedNusPresortedPos = new int[net.dim][net.numPoints];

   int[] poslist = new int[2 * net.numPoints];
   long[] bvlist = new long[2 * net.numPoints];
   int[] counts = new int[256];
   int[] binpos = new int[256];

   for (int j = 0; j < net.dim; ++j) {
      bvlist[0] = 0;
      poslist[0] = 0;

      for (int i = 1; i < net.numPoints; i++) {
         int pos = Integer.numberOfTrailingZeros(i);
         bvlist[i] = bvlist[i - 1]
               ^ (net.genMat[j * net.numCols + pos] >>> (net.outDigits - 31));
         poslist[i] = i;
      }

      for (int b = 0; b < 4; b++) {
         for (int i = 0; i < 256; i++)
            counts[i] = 0;

         int m = (b % 2) * net.numPoints;
         int bb = 8 * b;
         long bv = 0xffL << bb;

         for (int i = 0; i < net.numPoints; i++)
            counts[(int) ((bvlist[m + i] & bv) >>> bb)]++;

         binpos[0] = (1 - b % 2) * net.numPoints;

         for (int i = 0; i < 255; i++)
            binpos[i + 1] = binpos[i] + counts[i];

         for (int i = 0; i < net.numPoints; i++) {
            int pos = (int) ((bvlist[m + i] & bv) >>> bb);
            int k = binpos[pos]++;
            bvlist[k] = bvlist[m + i];
            poslist[k] = poslist[m + i];
         }
      }

      for (int i = 0; i < net.numPoints; i++) {
         cachedNusPresortedBv[j][i] = bvlist[i];
         cachedNusPresortedPos[j][i] = poslist[i];
      }
   }
}


////////////////////SciML cashed and packed
/// 
/**
 * Applies the SciML QuasiMonteCarlo.jl Owen scrambling rule in base 2,
 * using a Java-optimized packed implementation with cached deterministic data.
 * The original bit and the permutation index used at each Owen level are
 * precomputed once for the same digital net.
 *
 * @param net the original base-2 digital net
 * @param output the array receiving the scrambled points
 * @param pad the number of binary digits used in the scrambling
 */
private void scimlJlOwenPackedCached(DigitalNetBase2 net,
                                     double[][] output,
                                     int pad) {
   assert output.length == net.numPoints;
   assert output.length > 0;
   assert output[0].length == net.dim;

   if (pad == 0)
      pad = net.outDigits;

   int n = net.numPoints;
   int dim = net.dim;
   int m = log2ExactSciml(n);

   assert pad >= m;
   assert pad <= net.outDigits;
   assert pad <= 62;

   ensureScimlPackedCachedData(net, pad, n, dim, m);

   int totalPerms = n - 1;
   int tailBits = pad - m;
   byte[] perms = new byte[totalPerms];

   double normFactor = Math.scalb(1.0, -pad);

   final int randomBlockBits = 62;
   long randomBlock = 0L;
   int bitsLeft = 0;

   for (int j = 0; j < dim; j++) {
      for (int t = 0; t < totalPerms; t++) {
         if (bitsLeft == 0) {
            randomBlock = stream.nextBitsLong(randomBlockBits);
            bitsLeft = randomBlockBits;
         }

         perms[t] = (byte) (randomBlock & 1L);
         randomBlock >>>= 1;
         bitsLeft--;
      }

      for (int i = 0; i < n; i++) {
         long y = 0L;
         int base = (j * n + i) * m;

         for (int level = 0; level < m; level++) {
            int index = base + level;

            int scrambledBit =
                  cachedScimlPackedCachedOriginalBits[index]
                  ^ perms[cachedScimlPackedCachedPermIndices[index]];

            y = (y << 1) | scrambledBit;
         }

         if (tailBits > 0) {
            long tail = stream.nextBitsLong(tailBits);
            y = (y << tailBits) | tail;
         }

         output[i][j] = y * normFactor;
      }
   }
}


/**
 * Computes the deterministic data used by the cached packed SciML Owen
 * implementation. For each dimension, point, and Owen level, it stores the
 * original bit and the flat permutation index offset + prefix.
 *
 * @param net the original base-2 digital net
 * @param pad the number of binary digits used in the scrambling
 * @param n the number of points
 * @param dim the dimension
 * @param m the number of Owen levels, equal to log2(numPoints)
 */
private void ensureScimlPackedCachedData(DigitalNetBase2 net,
                                         int pad,
                                         int n,
                                         int dim,
                                         int m) {
   if (cachedScimlPackedCachedNet == net &&
       cachedScimlPackedCachedPad == pad &&
       cachedScimlPackedCachedN == n &&
       cachedScimlPackedCachedDim == dim &&
       cachedScimlPackedCachedM == m &&
       cachedScimlPackedCachedOutDigits == net.outDigits &&
       cachedScimlPackedCachedNumCols == net.numCols &&
       cachedScimlPackedCachedOriginalBits != null &&
       cachedScimlPackedCachedPermIndices != null)
      return;

   cachedScimlPackedCachedNet = net;
   cachedScimlPackedCachedPad = pad;
   cachedScimlPackedCachedN = n;
   cachedScimlPackedCachedDim = dim;
   cachedScimlPackedCachedM = m;
   cachedScimlPackedCachedOutDigits = net.outDigits;
   cachedScimlPackedCachedNumCols = net.numCols;

   int size = dim * n * m;

   cachedScimlPackedCachedOriginalBits = new byte[size];
   cachedScimlPackedCachedPermIndices = new int[size];

   for (int j = 0; j < dim; j++) {
      long x = 0L;

      for (int i = 0; i < n; i++) {
         if (i > 0) {
            int pos = Integer.numberOfTrailingZeros(i);
            x ^= net.genMat[j * net.numCols + pos];
         }

         int prefix = 0;
         int offset = 0;
         int base = (j * n + i) * m;

         for (int level = 0; level < m; level++) {
            int index = base + level;

            byte originalBit =
                  (byte) ((x >>> (net.outDigits - 1 - level)) & 1L);

            cachedScimlPackedCachedOriginalBits[index] = originalBit;
            cachedScimlPackedCachedPermIndices[index] = offset + prefix;

            prefix |= originalBit << level;
            offset += 1 << level;
         }
      }
   }
}

   ////////////////////////SciML 
    /**
     * Applies the SciML QuasiMonteCarlo.jl Owen scrambling structure in base 2.
     * The original bits are read directly from the digital net generating matrices,
     * not from double coordinates. The original bits and prefix indices are cached
     * because they do not depend on the random stream.
     *
     * @param net the original base-2 digital net
     * @param output the array receiving the scrambled points
     * @param pad the number of binary digits used in the scrambling
     */
    private void scimlJlOwen(DigitalNetBase2 net, double[][] output, int pad) {
    assert output.length == net.numPoints;
    assert output.length > 0;
    assert output[0].length == net.dim;

    if (pad == 0)
        pad = net.outDigits;

    int n = net.numPoints;
    int dim = net.dim;
    int m = log2ExactSciml(n);

    if (pad < m)
        throw new IllegalArgumentException("pad must be >= log2(numPoints)");
    if (pad > net.outDigits)
        throw new IllegalArgumentException("pad must be <= outDigits");

    ensureScimlCache(net, pad, n, dim, m);

    randomizeBitsSciml(cachedScimlRandomBits, cachedScimlOriginBits,
                        cachedScimlIndices, cachedScimlPerms,
                        pad, m, n, dim);
    double normFactor = Math.scalb(1.0, -pad);
    for (int j = 0; j < dim; j++)
        for (int i = 0; i < n; i++)
            output[i][j] = bits2UnifSciml(cachedScimlRandomBits, pad, i, j, normFactor);
    }

    /**
     * Computes and stores the SciML original bits and prefix indices when needed.
     * These arrays are reused across randomizations of the same net and pad.
     *
     * @param net the original base-2 digital net
     * @param pad the number of binary digits used in the scrambling
     * @param n the number of points
     * @param dim the dimension
     * @param m the number of scrambled levels, equal to log2(numPoints)
     */
    private void ensureScimlCache(DigitalNetBase2 net, int pad, int n, int dim, int m) {
    if (cachedScimlNet == net &&
        cachedScimlPad == pad &&
        cachedScimlN == n &&
        cachedScimlDim == dim &&
        cachedScimlM == m &&
        cachedScimlOriginBits != null &&
        cachedScimlIndices != null &&
        cachedScimlRandomBits != null &&
        cachedScimlPerms != null)
        return;

    cachedScimlNet = net;
    cachedScimlPad = pad;
    cachedScimlN = n;
    cachedScimlDim = dim;
    cachedScimlM = m;

cachedScimlOriginBits = new byte[pad][n][dim];
cachedScimlIndices = new int[m][n][dim];
cachedScimlRandomBits = new byte[pad][n][dim];

int maxPrefixes = (m == 0) ? 0 : (1 << (m - 1));
cachedScimlPerms = new byte[m][maxPrefixes];

    fillOriginBitsSciml(net, cachedScimlOriginBits, pad);
    whichPermutationSciml(cachedScimlOriginBits, cachedScimlIndices, m, n, dim);
    }
    /**
     * Builds the binary digit array of the original digital net points.
     * The bits are generated directly from the Gray-code recurrence and the
     * generating matrices.
     *
     * @param net the original base-2 digital net
     * @param originBits the destination array for the original bits
     * @param pad the number of binary digits to store
     */
    private void fillOriginBitsSciml(DigitalNetBase2 net, byte[][][] originBits, int pad) {
        for (int j = 0; j < net.dim; j++) {
            long x = 0L;

            for (int i = 0; i < net.numPoints; i++) {
                if (i > 0) {
                    int pos = 0;
                    int bv = 1;
                    while ((i & bv) == 0) {
                    pos++;
                    bv <<= 1;
                    }
                    x ^= net.genMat[j * net.numCols + pos];
                }

                for (int bit = 0; bit < pad; bit++)
                    originBits[bit][i][j] =
                        (byte) ((x >>> (net.outDigits - 1 - bit)) & 1L);
            }
        }
    }

    /**
     * Computes the prefix index that selects the permutation used at each level.
     *
     * @param bits the original binary digits
     * @param indices the destination prefix-index array
     * @param m the number of scrambled levels, equal to log2(numPoints)
     * @param n the number of points
     * @param dim the dimension
     */
    private void whichPermutationSciml(byte[][][] bits, int[][][] indices,
                                    int m, int n, int dim) {
        for (int j = 0; j < dim; j++) {
            for (int i = 0; i < n; i++) {
                
                indices[0][i][j] = 0;

                for (int level = 1; level < m; level++)
                    indices[level][i][j] = bits2IntSciml(bits, level, i, j);
            }
        }
    }

    /**
     * Converts the first {@code prefixLength} bits of one coordinate to an integer.
     *
     * @param bits the binary digit array
     * @param prefixLength the number of prefix bits to convert
     * @param point the point index
     * @param dim the dimension index
     * @return the integer represented by the prefix bits
     */
    private int bits2IntSciml(byte[][][] bits, int prefixLength, int point, int dim) {
        int y = 0;

        for (int bit = prefixLength - 1; bit >= 0; bit--)
            y = 2 * y + bits[bit][point][dim];

        return y;
    }

    /**
     * Applies the base-2 SciML Owen scrambling rule.
     * For the first {@code m} bits, the selected permutation is applied by XOR.
     * For the remaining bits, independent random bits are generated directly.
     *
     * @param randomBits the destination array for the scrambled bits
     * @param originBits the original binary digits
     * @param indices the prefix-index array
     * @param pad the number of binary digits
     * @param m the number of scrambled levels, equal to log2(numPoints)
     * @param n the number of points
     * @param dim the dimension
     */
    private void randomizeBitsSciml(byte[][][] randomBits, byte[][][] originBits,
                                int[][][] indices, byte[][] perms,
                                int pad, int m, int n, int dim) {
    for (int j = 0; j < dim; j++) {
        fillPermSetSciml(perms, m);

        for (int level = 0; level < m; level++) {
            for (int i = 0; i < n; i++) {
                randomBits[level][i][j] = (byte) (originBits[level][i][j] ^ perms[level][indices[level][i][j]]);
            }
        }
    }

    long x = 0L;
    int bitsLeft = 0;

    for (int j = 0; j < dim; j++) {
        for (int i = 0; i < n; i++) {
            for (int bit = m; bit < pad; bit++) {
                if (bitsLeft == 0) {
                    x = stream.nextLong(0, (1L << 62) - 1);
                    bitsLeft = 62;
                }

                randomBits[bit][i][j] = (byte) (x & 1L);
                x >>>= 1;
                bitsLeft--;
            }
        }
    }
    }

    /**
     * Generates the random permutation bits for all prefix-tree nodes.
     * In base 2, each permutation is represented by one bit: 0 keeps the digit,
     * and 1 flips it.
     *
     * @param m the number of scrambled levels
     * @return the permutation bits indexed by level and prefix
     */
    private void fillPermSetSciml(byte[][] perms, int m) {
    long x = 0L;
    int bitsLeft = 0;

    for (int level = 0; level < m; level++) {
        int nLevel = 1 << level;

        for (int i = 0; i < nLevel; i++) {
            if (bitsLeft == 0) {
                x = stream.nextLong(0, (1L << 62) - 1);
                bitsLeft = 62;
            }

            perms[level][i] = (byte) (x & 1L);
            x >>>= 1;
            bitsLeft--;
        }
    }
    }

/**
 * Converts the scrambled binary digits of one coordinate to a double.
 *
 * @param bits the scrambled binary digit array
 * @param pad the number of binary digits
 * @param point the point index
 * @param dim the dimension index
 * @param normFactor the value 2^(-pad)
 * @return the coordinate value in [0, 1)
 */
private double bits2UnifSciml(byte[][][] bits, int pad, int point, int dim,
                              double normFactor) {
   long y = 0L;

   for (int bit = 0; bit < pad; bit++)
      y = (y << 1) | bits[bit][point][dim];

   return y * normFactor;
}

    /**
     * Returns log2(n) when n is a power of two.
     *
     * @param n the input integer
     * @return log2(n)
     * @throws IllegalArgumentException if {@code n} is not a power of two
     */
    private static int log2ExactSciml(int n) {
        if (n < 1 || (n & (n - 1)) != 0)
            throw new IllegalArgumentException("numPoints must be a power of 2");

        return Integer.numberOfTrailingZeros(n);
    }


    ///////////////////SCIML_JL_OWEN_INCREMENTAL.///////////////////////////
    /// 
    /**
 * Applies the SciML QuasiMonteCarlo.jl Owen scrambling structure in base 2,
 * without storing the prefix-index array. The prefix index is updated
 * incrementally for each point while scrambling the first m bits.
 *
 * @param net the original base-2 digital net
 * @param output the array receiving the scrambled points
 * @param pad the number of binary digits used in the scrambling
 */
private void scimlJlOwenIncremental(DigitalNetBase2 net, double[][] output, int pad) {
   assert output.length == net.numPoints;
   assert output.length > 0;
   assert output[0].length == net.dim;

   if (pad == 0)
      pad = net.outDigits;

   int n = net.numPoints;
   int dim = net.dim;
   int m = log2ExactSciml(n);

   if (pad < m)
      throw new IllegalArgumentException("pad must be >= log2(numPoints)");
   if (pad > net.outDigits)
      throw new IllegalArgumentException("pad must be <= outDigits");

   ensureScimlIncrementalCache(net, pad, n, dim, m);

   randomizeBitsScimlIncremental(cachedScimlRandomBits, cachedScimlOriginBits,
                                 cachedScimlPerms, pad, m, n, dim);

   double normFactor = Math.scalb(1.0, -pad);

   for (int j = 0; j < dim; j++)
      for (int i = 0; i < n; i++)
         output[i][j] = bits2UnifSciml(cachedScimlRandomBits, pad, i, j, normFactor);
}

/**
 * Computes and stores the SciML original bits when needed for the incremental
 * implementation. No prefix-index array is built.
 *
 * @param net the original base-2 digital net
 * @param pad the number of binary digits used in the scrambling
 * @param n the number of points
 * @param dim the dimension
 * @param m the number of scrambled levels, equal to log2(numPoints)
 */
private void ensureScimlIncrementalCache(DigitalNetBase2 net, int pad,
                                         int n, int dim, int m) {
   if (cachedScimlNet == net &&
       cachedScimlPad == pad &&
       cachedScimlN == n &&
       cachedScimlDim == dim &&
       cachedScimlM == m &&
       cachedScimlOriginBits != null &&
       cachedScimlRandomBits != null &&
       cachedScimlPerms != null)
      return;

   cachedScimlNet = net;
   cachedScimlPad = pad;
   cachedScimlN = n;
   cachedScimlDim = dim;
   cachedScimlM = m;

   cachedScimlOriginBits = new byte[pad][n][dim];
   cachedScimlRandomBits = new byte[pad][n][dim];

   int maxPrefixes = 1 << (m - 1);
   cachedScimlPerms = new byte[m][maxPrefixes];

   cachedScimlIndices = null;

   fillOriginBitsSciml(net, cachedScimlOriginBits, pad);
}

/**
 * Applies the base-2 SciML Owen scrambling rule without a stored index array.
 * The prefix index is updated from the original bits as the levels are scanned.
 *
 * @param randomBits the destination array for the scrambled bits
 * @param originBits the original binary digits
 * @param perms the permutation bits reused for each dimension
 * @param pad the number of binary digits
 * @param m the number of scrambled levels, equal to log2(numPoints)
 * @param n the number of points
 * @param dim the dimension
 */
private void randomizeBitsScimlIncremental(byte[][][] randomBits,
                                           byte[][][] originBits,
                                           byte[][] perms,
                                           int pad, int m, int n, int dim) {
   for (int j = 0; j < dim; j++) {
      fillPermSetSciml(perms, m);

      for (int i = 0; i < n; i++) {
         int prefix = 0;

         for (int level = 0; level < m; level++) {
            randomBits[level][i][j] =
                  (byte) (originBits[level][i][j] ^ perms[level][prefix]);

            prefix |= originBits[level][i][j] << level;
         }
      }
   }

   long x = 0L;
   int bitsLeft = 0;

   for (int j = 0; j < dim; j++) {
      for (int i = 0; i < n; i++) {
         for (int bit = m; bit < pad; bit++) {
            if (bitsLeft == 0) {
               x = stream.nextLong(0, (1L << 62) - 1);
               bitsLeft = 62;
            }

            randomBits[bit][i][j] = (byte) (x & 1L);
            x >>>= 1;
            bitsLeft--;
         }
      }
   }
}

//////////////////case SCIML_JL_OWEN_PACKED://////////////////////
/// 
/**
 * Applies the SciML QuasiMonteCarlo.jl Owen scrambling rule in base 2,
 * using a Java-optimized packed implementation.
 * The scrambled coordinate is built directly as an integer and then converted
 * to a double. No originBits, randomBits, or indices arrays are stored.
 *
 * @param net the original base-2 digital net
 * @param output the array receiving the scrambled points
 * @param pad the number of binary digits used in the scrambling
 */
private void scimlJlOwenPacked(DigitalNetBase2 net, double[][] output, int pad) {
   assert output.length == net.numPoints;
   assert output.length > 0;
   assert output[0].length == net.dim;
   assert pad <=  net.outDigits;
   assert pad <= 62;

   if (pad == 0)
      pad = net.outDigits;

   int n = net.numPoints;
   int dim = net.dim;
   int m = log2ExactSciml(n);
   assert m >= pad;



   int totalPerms = n - 1;
//    if (cachedScimlPackedPerms == null ||
//        cachedScimlPackedPerms.length < totalPerms)
//       cachedScimlPackedPerms = new byte[totalPerms];

   byte[] perms = new byte[totalPerms];// we need 1+2+...+2^k = 2^k-1=n-1 permutations up to level k.
   double normFactor = Math.scalb(1.0, -pad);

   final int randomBlockBits = 62;
  // final long randomBlockMask = (1L << randomBlockBits) - 1L;

   long randomBlock = 0L;
   int bitsLeft = 0;
   int tailBits = pad - m;
    
   for (int j = 0; j < dim; j++) {
      // Generate all permuations for this dim: a block of size totalPerms (01010....)
      for (int t = 0; t < totalPerms; t++) {
        // genarete a block of randomBlockBits size each time it is all consumed
         if (bitsLeft == 0) {
            randomBlock = stream.nextBitsLong(randomBlockBits); 
            bitsLeft = randomBlockBits;
         }

         perms[t] = (byte) (randomBlock & 1L);
         randomBlock >>>= 1;
         bitsLeft--;
      }

      long x = 0L;

      for (int i = 0; i < n; i++) {
            if (i > 0) {
                int pos = Integer.numberOfTrailingZeros(i);
                x ^= net.genMat[j * net.numCols + pos];
            }

            long y = 0L;
            int prefix = 0;
            int offset = 0;
            int originalBit;
            int scrambledBit;
            
            for (int level = 0; level < m; level++) {
                originalBit = (int) ((x >>> (net.outDigits - 1 - level)) & 1L);

                scrambledBit = originalBit ^ perms[offset + prefix];

                y = (y << 1) | scrambledBit;// construct the scrambeled bit bit by bit

                prefix |= originalBit << level;// find the node using the prefix: append original bit
                offset += 1 << level;// find the level in a flat array ; +2^level   
            }

            // for (int bit = m; bit < pad; bit++) {
            //     if (bitsLeft == 0) {
            //         randomBlock = stream.nextBitsLong(randomBlockBits); 
            //         bitsLeft = randomBlockBits;
            //     }

            //     y = (y << 1) | (randomBlock & 1L);
            //     randomBlock >>>= 1;
            //     bitsLeft--;
            // }

            if (tailBits > 0) {
               long tail = stream.nextBitsLong(tailBits);
               y = (y << tailBits) | tail;
            }

            output[i][j] = y * normFactor;
      }
   }
}

   @Override
   public String toString() {
      return "Experimental nested uniform scrambling: " + method;
   }
}