package umontreal.ssj.hups64;

import umontreal.ssj.rng.RandomStream;
//import umontreal.ssj.util.Num;

public class NestedUniformScramblingExperimental implements PointSetRandomization {

   public enum Method {
      SSJ_NUS64,
      SSJ_NUS64_PRESORTED,
      SCIML_JL_OWEN_PACKED_CACHED,
      BURLEY_OWEN_SSJ_DIR

      // SCIML_JL_OWEN,
      // SCIML_JL_OWEN_INCREMENTAL,
      // SCIML_JL_OWEN_PACKED,
      // FULL_OWEN,

   }

   private RandomStream stream;
   private final Method method;
   private int numBits;

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

   ///////////////Original from julia code/////////////////
   // private DigitalNetBase2 cachedScimlNet;
   // private int cachedScimlPad;
   // private int cachedScimlN;
   // private int cachedScimlDim;
   // private int cachedScimlM;

   // private byte[][][] cachedScimlOriginBits;
   // private byte[][][] cachedScimlRandomBits;
   // private int[][][] cachedScimlIndices;
   // private byte[][] cachedScimlPerms;

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
      case SSJ_NUS64:
         nestedUniformScramble64Copy(net, cp.getArray(), numBits);
         break;

      case SSJ_NUS64_PRESORTED:
         nestedUniformScramble64Presorted(net, cp.getArray(), numBits);
         break;
      case SCIML_JL_OWEN_PACKED_CACHED:
         scimlJlOwenPackedCached(net, cp.getArray(), numBits);
         break;

      case BURLEY_OWEN_SSJ_DIR:
         burleyOwenSsjDirections(net, cp.getArray(), numBits);
         break;

      // case SCIML_JL_OWEN:
      //    scimlJlOwen(net, cp.getArray(), numBits);
      //    break;
      // case SCIML_JL_OWEN_INCREMENTAL:
      //   scimlJlOwenIncremental(net, cp.getArray(), numBits);
      //   break;
      // case SCIML_JL_OWEN_PACKED:
      //   scimlJlOwenPacked(net, cp.getArray(), numBits);
      //   break;

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

   // ssj Nus: Keller and Friedel:
   /**
    * Generates the points defined by {@code net}, applies Owen's nested uniform
    * scrambling in base 2, and writes the resulting points into {@code output}.
    * The points are generated from the digital-net parameters stored in
    * {@code net}, including its generator matrices, number of points, dimension,
    * and output precision. The digital net itself is not modified.
    *
    * <p>The implementation follows the base-2 algorithm described by
    * I. Friedel and A. Keller in
    * <i>Fast Generation of Randomized Low Discrepancy Point Sets</i>.
    * It processes the point set one coordinate at a time in three phases:
    *
    * <ol>
    *   <li>
    *     <b>Point generation.</b> All coordinates of the current dimension are
    *     generated as binary integers from the generator matrix using the
    *     Gray-code recurrence. The original index of each generated point is
    *     stored in parallel.
    *   </li>
    *   <li>
    *     <b>Radix sort.</b> The generated integer coordinates are sorted using a
    *     stable base-256 radix sort. This places coordinates that share long
    *     binary prefixes next to each other, so that the leaves of the implicit
    *     Owen permutation tree can be traversed efficiently. The original point
    *     indices are moved with the coordinates.
    *   </li>
    *   <li>
    *     <b>Nested uniform scrambling.</b> The sorted coordinates are traversed
    *     from left to right. Consecutive coordinates reuse the random
    *     permutations associated with their common binary prefix, while new
    *     random permutations are generated after the first digit at which their
    *     paths differ. Each scrambled coordinate is converted to a value in
    *     {@code [0, 1)} and written to {@code output} at its original point
    *     position.
    *   </li>
    * </ol>
    *
    * <p>This method returns no value; the generated and scrambled point set is
    * written directly into {@code output}.
    *
    * @param net
    *        the base-2 digital net containing the parameters and generator
    *        matrices used to generate the points
    * @param output
    *        the array receiving the generated and scrambled points; it must have
    *        {@code net.numPoints} rows and {@code net.dim} columns
    * @param numBits
    *        the number of most significant binary digits to scramble; a value of
    *        {@code 0} means that all {@code net.outDigits} output digits are
    *        scrambled
    */
   private void nestedUniformScramble64Copy(DigitalNetBase2 net, double[][] output, int numBits) {
      assert output.length == net.numPoints; // Verify that the output array has one row per point.
      assert output.length > 0; // Check that the array is nonempty before accessing output[0].
      assert output[0].length == net.dim; // Verify that each row has one entry per dimension.
      assert net.outDigits >= 31;
      assert net.outDigits <= 62;

      if (numBits == 0)
         numBits = net.outDigits; // A value of zero means that all output bits must be scrambled.

      double localNormFactor = 1.0 / Math.abs((double) (1L << (net.outDigits)));
      // The radix sort alternates between two buffers of numPoints elements (size 2*n).
      // bvlist stores the integer coordinates, while poslist stores their original
      // point indices so that the initial point order can be restored after sorting.
      int[] poslist = new int[2 * net.numPoints];
      long[] bvlist = new long[2 * net.numPoints];
      // Auxiliary arrays for the base-256 radix sort.
      int[] counts = new int[256]; // Number of coordinates in each byte-value group,
      int[] binpos = new int[256]; // Write positions of these groups.

      for (int j = 0; j < net.dim; ++j) {
         bvlist[0] = 0; // The first point of a digital net is zero before randomization.
         poslist[0] = 0;
         // Generate all coordinates in dimension j in Gray-code order.
         for (int i = 1; i < net.numPoints; i++) {
            // Find the position of the least significant 1-bit of i.
            // This is the bit that changes between the Gray codes of i - 1 and i,
            // and therefore identifies the generator-matrix column to apply.
            int pos = Integer.numberOfTrailingZeros(i);
            // Obtain the next coordinate by XORing the previous coordinate with
            // the generator-matrix column corresponding to the changed Gray-code bit.
            bvlist[i] = bvlist[i - 1] ^ ((net.genMat[j * net.numCols + pos]) >>> (net.outDigits - 31));
            poslist[i] = i; // Record the coordinate's original point index before the radix sort.
         }
         // Sort the coordinates so that values sharing common binary prefixes are adjacent,
         // which allows the NUS step to traverse the implicit permutation tree efficiently.
         // This sorts the coordinates by their 32-bit integer representations using a
         // base-256 radix sort.
         //
         // The radix sort processes one byte at a time, starting with the least
         // significant byte. Each pass is stable, so after four passes the values
         // are sorted by all 32 bits.
         for (int b = 0; b < 4; b++) {
            // counts[d] will contain the number of values whose current byte is d,
            // where d is between 0 and 255.
            for (int i = 0; i < 256; i++)
               counts[i] = 0;
            // bvlist and poslist each contain two buffers of numPoints elements.
            // The source and destination buffers alternate at each radix-sort pass:
            //
            // even b: source starts at 0, destination starts at numPoints;
            // odd  b: source starts at numPoints, destination starts at 0
            int m = (b % 2) * net.numPoints;

            // Select the byte processed during this pass.
            // b = 0: bits  0-7; // b = 1: bits  8-15
            // b = 2: bits 16-23 ; // b = 3: bits 24-31
            int bb = 8 * b;
            long bv = 0xff << bb;

            // Count how many values belong to each of the 256 possible byte values.
            //
            // The expression extracts the current byte:
            //   1. '& bv' keeps only the selected byte;
            //   2. '>>> bb' moves it to the least significant position.(between 0 and 256)
            for (int i = 0; i < net.numPoints; i++)
               counts[(int) ((bvlist[m + i] & bv)) >>> bb]++;
            // binpos[0] is the beginning of the destination buffer.
            binpos[0] = (1 - b % 2) * net.numPoints;

            // Compute the boundaries of the 256 byte-value groups.
            //
            // Before this loop, binpos[0] is the first index of the destination buffer.
            // counts[i] is the number of values whose selected byte is i.
            //
            // After this loop, group i occupies:
            //
            //   [binpos[i], binpos[i + 1])
            //
            // Therefore, binpos[i] is the first destination index of group i, and
            // binpos[i + 1] is the index immediately after the end of that group.

            for (int i = 0; i < 255; i++)
               binpos[i + 1] = binpos[i] + counts[i];
            // Distribute the values into the destination buffer.
            //
            // Since the source is scanned from left to right and each group's write
            // position is incremented after use, values belonging to the same group
            // preserve their relative order. The radix-sort pass is therefore stable.
            for (int i = 0; i < net.numPoints; i++) {
               // Byte value of the current coordinate; this is its group number.
               int pos = (int) ((bvlist[m + i] & bv) >>> bb);
               // binpos[pos] is the next free position in this group.
               //
               // Therefore, k is the exact destination index for the current value,
               // and binpos[pos] is advanced for the next value in the same group.
               int k = binpos[pos]++;
               // Move the coordinate and its original point position together
               bvlist[k] = bvlist[m + i];
               poslist[k] = poslist[m + i];
            }
         }

         /////////////// Scrambling part:

         // Start the NUS traversal for dimension j with the first coordinate in
         // sorted order. This coordinate represents the first root-to-leaf path
         // visited in the implicit Owen tree.
         //
         // Generate one random bit for each of the numBits digits to scramble.
         // The shift places these random bits in the most significant positions,
         // where the binary digits of the coordinate are stored.
         long bv = (stream.nextLong(0, (1L << numBits) - 1) << (net.outDigits - numBits));
         // bvlist stores each sorted coordinate using 31 bits.
         // Shift the first sorted coordinate so that these 31 bits occupy the
         // most significant positions of the outDigits-bit representation.
         // The remaining outDigits - 31 least significant bits are set to zero.
         long bvlistL = bvlist[0] << (net.outDigits - 31);
         // Apply the scrambling vector to the first point sorted coordinate.
         // poslist[0] gives the original point index, before the radix sort.
         output[poslist[0]][j] = (bvlistL ^ bv) * localNormFactor + net.EpsilonHalf;

         for (int i = 1; i < net.numPoints; i++) {
            // Convert the preceding sorted coordinate from its 31-bit representation
            // to the outDigits-bit representation
            long bv2 = (bvlist[i - 1]) << (net.outDigits - 31);
             // Convert the current sorted coordinate in the same way.
            bvlistL = (bvlist[i]) << (net.outDigits - 31);
            // XOR the previous and current sorted coordinates. A 1-bit marks each
            // binary digit where the two coordinates differ.
            bv2 ^= bvlistL; 
               // Generate a new random scrambling vector.
               //
               // The mask keeps only the bits less significant than the first
               // differing bit. These positions correspond to the digits after the
               // two paths have separated and therefore belong to a new subtree.
               // Num.log2(bv2)/Long.highestOneBit( locates the most significant 1-bit of bv2.
               //
               // The scrambling bits for the common path, including the permutation
               // of the first differing digit, are not changed.          
            bv2 = (stream.nextLong(0, (1L << numBits) - 1) << (net.outDigits - numBits))
                   & (Long.highestOneBit(bv2) - 1L);// highestOneBit is faster than log2
            
            // bv2 = (stream.nextLong(0, (1L << numBits) - 1) << (net.outDigits - numBits))
            //       & ((1L << (long) Num.log2((double) bv2)) - 1); // ..1000 -> ..0111
               // Preserve the scrambling decisions for the common path and replace
               // only those associated with the new subtree.
            bv ^= bv2;
            // Scramble the current coordinate and restore its original point index.
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
         bv2 = (stream.nextLong(0, (1L << numBits) - 1) << (net.outDigits - numBits))
               & (Long.highestOneBit(bv2) - 1L);// faster thatn log2
         // bv2 = (stream.nextLong(0, (1L << numBits) - 1)
         //        << (net.outDigits - numBits))
         //       & ((1L << (long) Num.log2((double) bv2)) - 1);

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


////////////////////SciML cashed and packed//////////////////////////////////

/**
 * Generates the points defined by {@code net}, applies 
 * a packed base-2 Owen scrambling implementation adapted from SciML's QuasiMonteCarlo.jl
 * and writes the resulting randomized points into {@code output}.
 * The digital net itself is not modified.
 *
 * <p>The method applies a nested, prefix-dependent scrambling. For each
 * binary digit of a coordinate, the permutation to apply depends on the
 * original binary digits that precede it. In base 2, each permutation is
 * represented by one random bit:
 *
 * <ul>
 *   <li>{@code 0} keeps the binary digit unchanged;</li>
 *   <li>{@code 1} exchanges {@code 0} and {@code 1}.</li>
 * </ul>
 *
 * <p>For {@code n = 2^m} points, the first {@code m} binary digits come from
 * the digital net. The remaining digits, from position {@code m} up to
 * {@code pad - 1}, are generated randomly. This preserves the randomized-net
 * construction while extending each coordinate to {@code pad} output bits.
 *
 * <p>To avoid repeating the deterministic part of the computation at every
 * randomization, the method precomputes and caches the deterministic data for the first
 *  m = log2(n) levels. In particular, it caches:
 *
 * <ul>
 *   <li>the original binary digits generated from the digital net;</li>
 *   <li>the permutation-tree node index associated with each digit, as
 *       determined by its preceding original digits.</li>
 * </ul>
 *
 * <p>The cached data is reused while the same net configuration and padding
 * are used. For each new randomization, only the random permutation bits and
 * the random trailing digits are regenerated. The scrambled digits are then
 * packed into an integer representation, converted to values in
 * {@code [0, 1)}, and written into {@code output}.
 *
 * <p>The method proceeds in three main phases:
 *
 * <ol>
 *   <li>
 *     <b>Cache preparation.</b> If the current net configuration or
 *     {@code pad} differs from the cached configuration, generate the
 *     deterministic binary digits of the digital net and compute the
 *     permutation-tree node index used by every point, dimension, and
 *     scrambled digit. Store this information for subsequent calls.
 *   </li>
 *   <li>
 *     <b>Random scrambling.</b> Generate one random base-2 permutation for every
 *     node of the complete binary tree of depth m of the Owen permutation tree. For each
 *     net digit, retrieve its cached node index and apply the corresponding
 *     random permutation. Generate independent random digits for the
 *     remaining positions up to {@code pad}.
 *   </li>
 *   <li>
 *     <b>Packing and conversion.</b> Pack the scrambled binary digits into
 *     the integer representation of each coordinate, scale the result to
 *     {@code [0, 1)}, and store it in {@code output}.
 *   </li>
 * </ol>
 *
 * <p>This method returns no value; the generated and scrambled point set is
 * written directly into {@code output}.
 *
 * @param net
 *        the base-2 digital net containing the generator matrices and the
 *        parameters required to generate the original point set
 * @param output
 *        the array receiving the generated and scrambled points; it must have
 *        {@code net.numPoints} rows and {@code net.dim} columns
 * @param pad
 *        the number of binary digits generated for each output coordinate;
 *        a value of {@code 0} uses {@code net.outDigits}
 */
private void scimlJlOwenPackedCached(DigitalNetBase2 net,
                                     double[][] output,
                                     int pad) {
   assert output.length == net.numPoints;
   assert output.length > 0;
   assert output[0].length == net.dim;
   assert pad <= net.outDigits;
   assert pad <= 62;
    
   if (pad == 0)  pad = net.outDigits;

   int n = net.numPoints;
   assert n > 0 && (n & (n - 1)) == 0 : "n must be a positive power of two";
   
   int m = Integer.numberOfTrailingZeros(n);
   int dim = net.dim;
   assert pad >= m;

   // For every dimension, point, and level from 0 to m - 1, precompute:
   //
   //   cachedScimlPackedCachedOriginalBits:
   //      the original bit extracted from the generated digital-net
   //      coordinate;
   //
   //   cachedScimlPackedCachedPermIndices:
   //      the value offset + prefix that will later be used directly as
   //      an index into perms.
   //
   // These two arrays are reused when the cached configuration still
   // matches the current net and parameters.
   ensureScimlPackedCachedData(net, pad, n, dim, m);

   int totalPerms = n - 1; // There are 1 + 2 + 4 + ... + 2^(m - 1) = 2^m - 1 = n - 1
   int tailBits = pad - m;  // The remaining pad - m bits are generated directly at random.
   byte[] perms = new byte[totalPerms];

   double normFactor = Math.scalb(1.0, -pad);// // Multiplying the packed integer by 2^-pad converts it to [0, 1).
   // Request random permutation bits in blocks of 62 instead of making
   // one random-stream call for every entry of perms.
   final int randomBlockBits = 62;
   long randomBlock = 0L;// Stores the current block of random bits.
   int bitsLeft = 0; // Number of unused bits remaining in randomBlock.

   for (int j = 0; j < dim; j++) {
       // Generate the n - 1 random permutation bits used for dimension j.
      for (int t = 0; t < totalPerms; t++) {
         if (bitsLeft == 0) {
            randomBlock = stream.nextBitsLong(randomBlockBits);
            bitsLeft = randomBlockBits;
         }
         // Store the least significant unused bit in perms[t].
         perms[t] = (byte) (randomBlock & 1L);
         randomBlock >>>= 1;
         bitsLeft--;
      }
// Generate the randomized coordinate of every point in dimension j.
      for (int i = 0; i < n; i++) {
         long y = 0L;
         int base = (j * n + i) * m;  // base is the array position corresponding to level 0 of
         // point i in dimension j.
 // Generate and scramble the first m bits.
         for (int level = 0; level < m; level++) {
            int index = base + level;

            // Get the original digital-net bit stored for this
            // dimension, point, and level.
            //
            // Get the precomputed value offset + prefix, use it as an
            // index into perms, and XOR the corresponding random
            // permutation bit with the original bit.
            int scrambledBit =
                  cachedScimlPackedCachedOriginalBits[index]
                  ^ perms[cachedScimlPackedCachedPermIndices[index]];

            y = (y << 1) | scrambledBit;  // Append scrambledBit to the right of the bits already in y.
         }

         if (tailBits > 0) {
            long tail = stream.nextBitsLong(tailBits);
            // Shift the first m bits to make room for the random tail,
            // then append the tail.
            y = (y << tailBits) | tail;
         }

         output[i][j] = y * normFactor;
      }
   }
}


/**
 * Computes and caches the deterministic values used by
 * {@code scimlJlOwenPackedCached}. For each dimension, point, and level, it
 * stores the original digital-net bit and the value {@code offset + prefix}
 * that will later be used directly as an index into the random permutation
 * array.
 *
 * @param net the base-2 digital net containing the generator matrices
 * @param pad the total number of output bits
 * @param n the number of points
 * @param dim the dimension
 * @param m the number of levels, equal to {@code log2(n)}
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

   // Store one original bit and one permutation-array index for every
   // dimension, point, and level.
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
         // prefix encodes the original bits preceding the current level.
         //
         // The original bit from level 0 is stored in bit 0 of prefix,
         // the original bit from level 1 in bit 1, and so on.
         int prefix = 0;
         int offset = 0;  // offset is the first index in perms assigned to the current level:
         int base = (j * n + i) * m; // First cached-array position for point i in dimension j.

         for (int level = 0; level < m; level++) {
            int index = base + level;
            // Extract the original bit at this level, starting with the
            // most significant bit of the coordinate
            byte originalBit = (byte) ((x >>> (net.outDigits - 1 - level)) & 1L);

            cachedScimlPackedCachedOriginalBits[index] = originalBit;
            // Store the exact index that will later be used in:
            // perms[cachedScimlPackedCachedPermIndices[index]]
            // offset gives the first perms index for the current level.
            // prefix selects one index within that level according to the
            // original bits preceding the current level.
            cachedScimlPackedCachedPermIndices[index] = offset + prefix;

            prefix |= originalBit << level; // Add the current original bit to the prefix used at the next level.
            offset += 1 << level; // Move offset to the first perms index of the next level.
         }
      }
   }
} 

///////////////////////////////////Burley method//////////////////////
/// 


/**
 * Applies Burley's shuffled Owen scrambling using SSJ Sobol directions.
 *
 * For dimension j:
 * group = j / 4 and localDim = j % 4.
 * Each group reuses the SSJ Sobol directions of local dimensions 0, 1, 2, 3.
 *
 * @param net the original base-2 digital net
 * @param output the array receiving the scrambled points
 * @param numBits the number of leading bits scrambled; 0 means min(outDigits, 32)
 */
private void burleyOwenSsjDirections(DigitalNetBase2 net,
                                     double[][] output,
                                     int numBits) {
   assert output.length == net.numPoints;
   assert output.length > 0;
   assert output[0].length == net.dim;
   assert net.outDigits <= 62;

   if (numBits == 0)
      numBits = Math.min(net.outDigits, 32);

   assert numBits >= 0;
   assert numBits <= net.outDigits;
   assert numBits <= 32;

   int n = net.numPoints;
   int dim = net.dim;

   int seed = BurleyOwenUtils.hash((int) stream.nextBitsLong(32));
   double normFactor = Math.scalb(1.0, -net.outDigits);

   for (int j = 0; j < dim; j++) {
      int group = j / 4;
      int localDim = j % 4;

      int seedGroup = group == 0
            ? seed
            : BurleyOwenUtils.hash(BurleyOwenUtils.hashCombine(seed, group));

      int coordSeed = BurleyOwenUtils.hashCombine(seedGroup, localDim);

      for (int i = 0; i < n; i++) {
         int shuffledIndex =
               BurleyOwenUtils.nestedUniformScramble(i, seedGroup);

         long bits = sobolBitsFromSsj4D(net, shuffledIndex, localDim);

         long scrambledBits =
               burleyScrambleLeadingBits(bits, coordSeed,
                                         numBits, net.outDigits);

         output[i][j] = scrambledBits * normFactor;
      }
   }
}

/**
 * Computes one Sobol coordinate using SSJ directions local 0, 1, 2, or 3.
 *
 * @param net the original base-2 digital net
 * @param index the shuffled Burley index
 * @param localDim the local Sobol dimension, from 0 to 3
 * @return the Sobol coordinate bits stored in the lowest outDigits bits
 */
private long sobolBitsFromSsj4D(DigitalNetBase2 net,
                                int index,
                                int localDim) {
   assert localDim >= 0;
   assert localDim < 4;
   assert localDim < net.dim;

   long x = 0L;
   int maxCols = Math.min(net.numCols, 32);

   for (int bit = 0; bit < maxCols; bit++) {
      if (((index >>> bit) & 1) != 0)
         x ^= net.genMat[localDim * net.numCols + bit];
   }

   return x;
}

/**
 * Scrambles the first numBits leading bits of a Sobol coordinate.
 *
 * @param bits the original Sobol coordinate bits
 * @param seed the Burley coordinate seed
 * @param numBits the number of leading bits to scramble
 * @param outDigits the number of output bits stored in bits
 * @return the coordinate bits after scrambling
 */
private long burleyScrambleLeadingBits(long bits,
                                       int seed,
                                       int numBits,
                                       int outDigits) {
   assert numBits >= 0;
   assert numBits <= outDigits;
   assert numBits <= 32;
   assert outDigits <= 62;

   if (numBits == 0)
      return bits;

   int tailBits = outDigits - numBits;

   long tailMask = tailBits == 0 ? 0L : (1L << tailBits) - 1L;
   long tail = bits & tailMask;

   int prefix = (int) (bits >>> tailBits);
   int shiftedPrefix = prefix << (32 - numBits);

   long prefixMask = numBits == 32
         ? 0xffffffffL
         : (1L << numBits) - 1L;

   long scrambledPrefix =
         (BurleyOwenUtils.nestedUniformScramble(shiftedPrefix, seed)
          >>> (32 - numBits)) & prefixMask;

   return (scrambledPrefix << tailBits) | tail;
}

   @Override
   public String toString() {
      return "Experimental nested uniform scrambling: " + method;
   }

   //  /**
   //   * Returns log2(n) when n is a power of two.
   //   *
   //   * @param n the input integer
   //   * @return log2(n)
   //   * @throws IllegalArgumentException if {@code n} is not a power of two
   //   */
   //  private static int log2ExactSciml(int n) {
   //      if (n < 1 || (n & (n - 1)) != 0)
   //          throw new IllegalArgumentException("numPoints must be a power of 2");

   //      return Integer.numberOfTrailingZeros(n);
   //  }

//////////////////////////////////////// old code///////////////////////////////////////////
/// /////////////////////////////////////////////////////////////////////////////////////////////
/// 
/// 
/// 

//////////////////case SCIML_JL_OWEN_PACKED://////////////////////
/// //////////////////////////////////////////////////////////////////////
// /**
//  * Applies the SciML QuasiMonteCarlo.jl Owen scrambling rule in base 2,
//  * using a Java-optimized packed implementation.
//  * The scrambled coordinate is built directly as an integer and then converted
//  * to a double. No originBits, randomBits, or indices arrays are stored.
//  *
//  * @param net the original base-2 digital net
//  * @param output the array receiving the scrambled points
//  * @param pad the number of binary digits used in the scrambling
//  */
// private void scimlJlOwenPacked(DigitalNetBase2 net, double[][] output, int pad) {
//    assert output.length == net.numPoints;
//    assert output.length > 0;
//    assert output[0].length == net.dim;
//    assert pad <=  net.outDigits;
//    assert pad <= 62;

//    if (pad == 0)
//       pad = net.outDigits;

//    int n = net.numPoints;
//    int dim = net.dim;
//    int m = log2ExactSciml(n);
//    assert m >= pad;



//    int totalPerms = n - 1;
// //    if (cachedScimlPackedPerms == null ||
// //        cachedScimlPackedPerms.length < totalPerms)
// //       cachedScimlPackedPerms = new byte[totalPerms];

//    byte[] perms = new byte[totalPerms];// we need 1+2+...+2^k = 2^k-1=n-1 permutations up to level k.
//    double normFactor = Math.scalb(1.0, -pad);

//    final int randomBlockBits = 62;
//   // final long randomBlockMask = (1L << randomBlockBits) - 1L;

//    long randomBlock = 0L;
//    int bitsLeft = 0;
//    int tailBits = pad - m;
    
//    for (int j = 0; j < dim; j++) {
//       // Generate all permuations for this dim: a block of size totalPerms (01010....)
//       for (int t = 0; t < totalPerms; t++) {
//         // genarete a block of randomBlockBits size each time it is all consumed
//          if (bitsLeft == 0) {
//             randomBlock = stream.nextBitsLong(randomBlockBits); 
//             bitsLeft = randomBlockBits;
//          }

//          perms[t] = (byte) (randomBlock & 1L);
//          randomBlock >>>= 1;
//          bitsLeft--;
//       }

//       long x = 0L;

//       for (int i = 0; i < n; i++) {
//             if (i > 0) {
//                 int pos = Integer.numberOfTrailingZeros(i);
//                 x ^= net.genMat[j * net.numCols + pos];
//             }

//             long y = 0L;
//             int prefix = 0;
//             int offset = 0;
//             int originalBit;
//             int scrambledBit;
            
//             for (int level = 0; level < m; level++) {
//                 originalBit = (int) ((x >>> (net.outDigits - 1 - level)) & 1L);

//                 scrambledBit = originalBit ^ perms[offset + prefix];

//                 y = (y << 1) | scrambledBit;// construct the scrambeled bit bit by bit

//                 prefix |= originalBit << level;// find the node using the prefix: append original bit
//                 offset += 1 << level;// find the level in a flat array ; +2^level   
//             }

//             if (tailBits > 0) {
//                long tail = stream.nextBitsLong(tailBits);
//                y = (y << tailBits) | tail;
//             }

//             output[i][j] = y * normFactor;
//       }
//    }
// }

   ////////////////////////SciML orginal structure from Julia code/////////////////////////////////////////////////
   /// ////////////////////////////////////////////////////////////////////////////////
//     /**
//      * Applies the SciML QuasiMonteCarlo.jl Owen scrambling structure in base 2.
//      * The original bits are read directly from the digital net generating matrices,
//      * not from double coordinates. The original bits and prefix indices are cached
//      * because they do not depend on the random stream.
//      *
//      * @param net the original base-2 digital net
//      * @param output the array receiving the scrambled points
//      * @param pad the number of binary digits used in the scrambling
//      */
//     private void scimlJlOwen(DigitalNetBase2 net, double[][] output, int pad) {
//     assert output.length == net.numPoints;
//     assert output.length > 0;
//     assert output[0].length == net.dim;

//     if (pad == 0)
//         pad = net.outDigits;

//     int n = net.numPoints;
//     int dim = net.dim;
//     int m = log2ExactSciml(n);

//     if (pad < m)
//         throw new IllegalArgumentException("pad must be >= log2(numPoints)");
//     if (pad > net.outDigits)
//         throw new IllegalArgumentException("pad must be <= outDigits");

//     ensureScimlCache(net, pad, n, dim, m);

//     randomizeBitsSciml(cachedScimlRandomBits, cachedScimlOriginBits,
//                         cachedScimlIndices, cachedScimlPerms,
//                         pad, m, n, dim);
//     double normFactor = Math.scalb(1.0, -pad);
//     for (int j = 0; j < dim; j++)
//         for (int i = 0; i < n; i++)
//             output[i][j] = bits2UnifSciml(cachedScimlRandomBits, pad, i, j, normFactor);
//     }

//     /**
//      * Computes and stores the SciML original bits and prefix indices when needed.
//      * These arrays are reused across randomizations of the same net and pad.
//      *
//      * @param net the original base-2 digital net
//      * @param pad the number of binary digits used in the scrambling
//      * @param n the number of points
//      * @param dim the dimension
//      * @param m the number of scrambled levels, equal to log2(numPoints)
//      */
//     private void ensureScimlCache(DigitalNetBase2 net, int pad, int n, int dim, int m) {
//     if (cachedScimlNet == net &&
//         cachedScimlPad == pad &&
//         cachedScimlN == n &&
//         cachedScimlDim == dim &&
//         cachedScimlM == m &&
//         cachedScimlOriginBits != null &&
//         cachedScimlIndices != null &&
//         cachedScimlRandomBits != null &&
//         cachedScimlPerms != null)
//         return;

//     cachedScimlNet = net;
//     cachedScimlPad = pad;
//     cachedScimlN = n;
//     cachedScimlDim = dim;
//     cachedScimlM = m;

// cachedScimlOriginBits = new byte[pad][n][dim];
// cachedScimlIndices = new int[m][n][dim];
// cachedScimlRandomBits = new byte[pad][n][dim];

// int maxPrefixes = (m == 0) ? 0 : (1 << (m - 1));
// cachedScimlPerms = new byte[m][maxPrefixes];

//     fillOriginBitsSciml(net, cachedScimlOriginBits, pad);
//     whichPermutationSciml(cachedScimlOriginBits, cachedScimlIndices, m, n, dim);
//     }
//     /**
//      * Builds the binary digit array of the original digital net points.
//      * The bits are generated directly from the Gray-code recurrence and the
//      * generating matrices.
//      *
//      * @param net the original base-2 digital net
//      * @param originBits the destination array for the original bits
//      * @param pad the number of binary digits to store
//      */
//     private void fillOriginBitsSciml(DigitalNetBase2 net, byte[][][] originBits, int pad) {
//         for (int j = 0; j < net.dim; j++) {
//             long x = 0L;

//             for (int i = 0; i < net.numPoints; i++) {
//                 if (i > 0) {
//                     int pos = 0;
//                     int bv = 1;
//                     while ((i & bv) == 0) {
//                     pos++;
//                     bv <<= 1;
//                     }
//                     x ^= net.genMat[j * net.numCols + pos];
//                 }

//                 for (int bit = 0; bit < pad; bit++)
//                     originBits[bit][i][j] =
//                         (byte) ((x >>> (net.outDigits - 1 - bit)) & 1L);
//             }
//         }
//     }

//     /**
//      * Computes the prefix index that selects the permutation used at each level.
//      *
//      * @param bits the original binary digits
//      * @param indices the destination prefix-index array
//      * @param m the number of scrambled levels, equal to log2(numPoints)
//      * @param n the number of points
//      * @param dim the dimension
//      */
//     private void whichPermutationSciml(byte[][][] bits, int[][][] indices,
//                                     int m, int n, int dim) {
//         for (int j = 0; j < dim; j++) {
//             for (int i = 0; i < n; i++) {
                
//                 indices[0][i][j] = 0;

//                 for (int level = 1; level < m; level++)
//                     indices[level][i][j] = bits2IntSciml(bits, level, i, j);
//             }
//         }
//     }

//     /**
//      * Converts the first {@code prefixLength} bits of one coordinate to an integer.
//      *
//      * @param bits the binary digit array
//      * @param prefixLength the number of prefix bits to convert
//      * @param point the point index
//      * @param dim the dimension index
//      * @return the integer represented by the prefix bits
//      */
//     private int bits2IntSciml(byte[][][] bits, int prefixLength, int point, int dim) {
//         int y = 0;

//         for (int bit = prefixLength - 1; bit >= 0; bit--)
//             y = 2 * y + bits[bit][point][dim];

//         return y;
//     }

//     /**
//      * Applies the base-2 SciML Owen scrambling rule.
//      * For the first {@code m} bits, the selected permutation is applied by XOR.
//      * For the remaining bits, independent random bits are generated directly.
//      *
//      * @param randomBits the destination array for the scrambled bits
//      * @param originBits the original binary digits
//      * @param indices the prefix-index array
//      * @param pad the number of binary digits
//      * @param m the number of scrambled levels, equal to log2(numPoints)
//      * @param n the number of points
//      * @param dim the dimension
//      */
//     private void randomizeBitsSciml(byte[][][] randomBits, byte[][][] originBits,
//                                 int[][][] indices, byte[][] perms,
//                                 int pad, int m, int n, int dim) {
//     for (int j = 0; j < dim; j++) {
//         fillPermSetSciml(perms, m);

//         for (int level = 0; level < m; level++) {
//             for (int i = 0; i < n; i++) {
//                 randomBits[level][i][j] = (byte) (originBits[level][i][j] ^ perms[level][indices[level][i][j]]);
//             }
//         }
//     }

//     long x = 0L;
//     int bitsLeft = 0;

//     for (int j = 0; j < dim; j++) {
//         for (int i = 0; i < n; i++) {
//             for (int bit = m; bit < pad; bit++) {
//                 if (bitsLeft == 0) {
//                     x = stream.nextLong(0, (1L << 62) - 1);
//                     bitsLeft = 62;
//                 }

//                 randomBits[bit][i][j] = (byte) (x & 1L);
//                 x >>>= 1;
//                 bitsLeft--;
//             }
//         }
//     }
//     }

//     /**
//      * Generates the random permutation bits for all prefix-tree nodes.
//      * In base 2, each permutation is represented by one bit: 0 keeps the digit,
//      * and 1 flips it.
//      *
//      * @param m the number of scrambled levels
//      * @return the permutation bits indexed by level and prefix
//      */
//     private void fillPermSetSciml(byte[][] perms, int m) {
//     long x = 0L;
//     int bitsLeft = 0;

//     for (int level = 0; level < m; level++) {
//         int nLevel = 1 << level;

//         for (int i = 0; i < nLevel; i++) {
//             if (bitsLeft == 0) {
//                 x = stream.nextLong(0, (1L << 62) - 1);
//                 bitsLeft = 62;
//             }

//             perms[level][i] = (byte) (x & 1L);
//             x >>>= 1;
//             bitsLeft--;
//         }
//     }
//     }

// /**
//  * Converts the scrambled binary digits of one coordinate to a double.
//  *
//  * @param bits the scrambled binary digit array
//  * @param pad the number of binary digits
//  * @param point the point index
//  * @param dim the dimension index
//  * @param normFactor the value 2^(-pad)
//  * @return the coordinate value in [0, 1)
//  */
// private double bits2UnifSciml(byte[][][] bits, int pad, int point, int dim,
//                               double normFactor) {
//    long y = 0L;

//    for (int bit = 0; bit < pad; bit++)
//       y = (y << 1) | bits[bit][point][dim];

//    return y * normFactor;
// }

//     /**
//      * Returns log2(n) when n is a power of two.
//      *
//      * @param n the input integer
//      * @return log2(n)
//      * @throws IllegalArgumentException if {@code n} is not a power of two
//      */
//     private static int log2ExactSciml(int n) {
//         if (n < 1 || (n & (n - 1)) != 0)
//             throw new IllegalArgumentException("numPoints must be a power of 2");

//         return Integer.numberOfTrailingZeros(n);
//     }

 ////////////////////////////////////////////////////////////////////////////
 /// ///////////////////////////////////////////////////////////////////////// 

/////////////////////////////////////////////////////////////////////////////////////
    ///////////////////SCIML_JL_OWEN_INCREMENTAL.///////////////////////////
    /// 
//  /**
//  * Applies the SciML QuasiMonteCarlo.jl Owen scrambling structure in base 2,
//  * without storing the prefix-index array. The prefix index is updated
//  * incrementally for each point while scrambling the first m bits.
//  *
//  * @param net the original base-2 digital net
//  * @param output the array receiving the scrambled points
//  * @param pad the number of binary digits used in the scrambling
//  */
// private void scimlJlOwenIncremental(DigitalNetBase2 net, double[][] output, int pad) {
//    assert output.length == net.numPoints;
//    assert output.length > 0;
//    assert output[0].length == net.dim;

//    if (pad == 0)
//       pad = net.outDigits;

//    int n = net.numPoints;
//    int dim = net.dim;
//    int m = log2ExactSciml(n);

//    if (pad < m)
//       throw new IllegalArgumentException("pad must be >= log2(numPoints)");
//    if (pad > net.outDigits)
//       throw new IllegalArgumentException("pad must be <= outDigits");

//    ensureScimlIncrementalCache(net, pad, n, dim, m);

//    randomizeBitsScimlIncremental(cachedScimlRandomBits, cachedScimlOriginBits,
//                                  cachedScimlPerms, pad, m, n, dim);

//    double normFactor = Math.scalb(1.0, -pad);

//    for (int j = 0; j < dim; j++)
//       for (int i = 0; i < n; i++)
//          output[i][j] = bits2UnifSciml(cachedScimlRandomBits, pad, i, j, normFactor);
// }

// /**
//  * Computes and stores the SciML original bits when needed for the incremental
//  * implementation. No prefix-index array is built.
//  *
//  * @param net the original base-2 digital net
//  * @param pad the number of binary digits used in the scrambling
//  * @param n the number of points
//  * @param dim the dimension
//  * @param m the number of scrambled levels, equal to log2(numPoints)
//  */
// private void ensureScimlIncrementalCache(DigitalNetBase2 net, int pad,
//                                          int n, int dim, int m) {
//    if (cachedScimlNet == net &&
//        cachedScimlPad == pad &&
//        cachedScimlN == n &&
//        cachedScimlDim == dim &&
//        cachedScimlM == m &&
//        cachedScimlOriginBits != null &&
//        cachedScimlRandomBits != null &&
//        cachedScimlPerms != null)
//       return;

//    cachedScimlNet = net;
//    cachedScimlPad = pad;
//    cachedScimlN = n;
//    cachedScimlDim = dim;
//    cachedScimlM = m;

//    cachedScimlOriginBits = new byte[pad][n][dim];
//    cachedScimlRandomBits = new byte[pad][n][dim];

//    int maxPrefixes = 1 << (m - 1);
//    cachedScimlPerms = new byte[m][maxPrefixes];

//    cachedScimlIndices = null;

//    fillOriginBitsSciml(net, cachedScimlOriginBits, pad);
// }

// /**
//  * Applies the base-2 SciML Owen scrambling rule without a stored index array.
//  * The prefix index is updated from the original bits as the levels are scanned.
//  *
//  * @param randomBits the destination array for the scrambled bits
//  * @param originBits the original binary digits
//  * @param perms the permutation bits reused for each dimension
//  * @param pad the number of binary digits
//  * @param m the number of scrambled levels, equal to log2(numPoints)
//  * @param n the number of points
//  * @param dim the dimension
//  */
// private void randomizeBitsScimlIncremental(byte[][][] randomBits,
//                                            byte[][][] originBits,
//                                            byte[][] perms,
//                                            int pad, int m, int n, int dim) {
//    for (int j = 0; j < dim; j++) {
//       fillPermSetSciml(perms, m);

//       for (int i = 0; i < n; i++) {
//          int prefix = 0;

//          for (int level = 0; level < m; level++) {
//             randomBits[level][i][j] =
//                   (byte) (originBits[level][i][j] ^ perms[level][prefix]);

//             prefix |= originBits[level][i][j] << level;
//          }
//       }
//    }

//    long x = 0L;
//    int bitsLeft = 0;

//    for (int j = 0; j < dim; j++) {
//       for (int i = 0; i < n; i++) {
//          for (int bit = m; bit < pad; bit++) {
//             if (bitsLeft == 0) {
//                x = stream.nextLong(0, (1L << 62) - 1);
//                bitsLeft = 62;
//             }

//             randomBits[bit][i][j] = (byte) (x & 1L);
//             x >>>= 1;
//             bitsLeft--;
//          }
//       }
//    }
// }
////////////////////////////////////////////////////////////////////////////////////
/// //////////////////////////////////////////////////////////////////////////////////
/// 
}