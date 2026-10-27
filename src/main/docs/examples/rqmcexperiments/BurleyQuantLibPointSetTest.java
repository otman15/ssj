/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rqmcexperiments;

import static org.junit.Assert.*;

import org.junit.Test;

import umontreal.ssj.hups64.BurleyQuantLibPointSet;
import umontreal.ssj.hups64.PointSetIterator;
import umontreal.ssj.hups64.PointSetRandomization;
import umontreal.ssj.hups64.RQMCPointSet;
import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment64;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.rng.RandomStreamBase;
import umontreal.ssj.stat.TallyStore;

/** Correctness checks for the QuantLib port; no performance measurements. */
public class BurleyQuantLibPointSetTest {
   private static final int[] INDICES = { 0, 1, 7, 255, 65535, 0x3fffffff };

   /*
    * Fixtures from the local, unmodified QuantLib 1.43 C++ library:
    * Burley2020SobolRsg(dim, sobolSeed, SobolRsg::Jaeckel, scrambleSeed).skipTo(i).
    * The C++ oracle also checked sequential nextInt32Sequence() against skipTo()
    * for indices 0 through 15 in every configuration.
    *
    * Source SHA-256:
    * burley2020sobolrsg.cpp
    * 329a5cd35264179aefead809053ab84048d38f00fe9477be978fafd295201596
    * sobolrsg.cpp
    * b12c48dec3672583a184114ce30ce346814f10de73324218816632dc5cb0c2d4
    * mt19937uniformrng.cpp
    * a9171816567baf59c5fc6f6d500428d65a5f122c27a9a76dfc0503c65446e85f
    *
    * Parameters: dimension, Sobol seed, scramble seed. 0x100000000 is a
    * nonzero 64-bit seed whose MT19937 initialization word is zero.
    */
   private static final long[][] CPP_PARAMETERS = {
      { 40L, 42L, 43L },
      { 33L, 7L, 3735928559L },
      { 10L, 4294967295L, 4294967295L },
      { 33L, 4294967296L, 4294967296L },
      { 1L, 42L, 1L },
      { 32L, 42L, 43L },
      { 64L, 7L, 43L },
      { 360L, 42L, 43L },
      { 2500L, 42L, 43L },
      { 21200L, 42L, 43L }
   };
   // For every point in INDICES, visit every dimension in ascending order:
   // digest = (digest XOR uint32Coordinate) * 0x100000001b3 (mod 2^64),
   // starting at 0xcbf29ce484222325. No floating-point hashing is involved.
   private static final long[] CPP_DIGESTS = {
      0xb78afdd43e5160fdL,
      0x8ed185899124d819L,
      0x92f5654e79cde6bcL,
      0x939c24551eaad32aL,
      0x2219bc440a8342dbL,
      0xdbc918643fd89c8dL,
      0xaef50f16285ee770L,
      0x4bf87a301d8b0778L,
      0x33186b1ba1a0637dL,
      0x47896cf33abddeb1L
   };
   private static final int[][] FIXTURE_DIMENSIONS = {
      { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 31, 32, 39 },
      { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 31, 32 },
      { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 },
      { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 31, 32 }
   };
   // Integer coordinates for the first four CPP_PARAMETERS configurations.
   private static final int[][][] CPP_COORDINATES = {
      {
         { 0xeb0bfbab, 0x5257f29a, 0x515d1053, 0xf2668ede, 0x4f2ac596, 0x16837872, 0x9fcf8f4c, 0x885110f8, 0xca56da16, 0xfac55c3e, 0x0579e295, 0xaa137046, 0x97328705 },
         { 0x53a29909, 0xf5952942, 0x6ddafa14, 0x1d524dea, 0x3054f155, 0x47f9504d, 0x08a1fb7c, 0xa5ebd90e, 0x4906d3d2, 0xb7aaf86e, 0x31f21c00, 0xd49beb06, 0xa1c47ad8 },
         { 0x290b9e62, 0xa70508bd, 0xa45deb36, 0x31983e91, 0xf3d119d9, 0xb64b7c8a, 0x38613643, 0x2a1df0d2, 0x7aa73fc3, 0x39dd6c27, 0xdaa93cbc, 0x5d5513be, 0x6b658531 },
         { 0x5772a79b, 0xc4ddda9a, 0xb2c97162, 0x22cf1568, 0x6909f032, 0xdbbb1340, 0x1e968532, 0x658a8c38, 0xbf5085cb, 0x1d39b36f, 0x77f77bcd, 0x700b57f1, 0x4d2d2f94 },
         { 0x8c9c876b, 0x3eedd733, 0x6863d8aa, 0x01e61464, 0xa5e4bb4f, 0xabd3d079, 0xd01f4c8f, 0x8ef1337e, 0xa6c31517, 0x9ecef292, 0x1224967a, 0x6cdeb209, 0xa8e93b91 },
         { 0x6e1de40e, 0x4526d81d, 0x60f7e1ec, 0x6f0b08d1, 0xdea1c94e, 0xd6970665, 0x18c5b1f4, 0x5cdae804, 0xd4c50ded, 0xa8b45bde, 0x3bc2ad49, 0x0bb53f64, 0x7fc1f206 }
      },
      {
         { 0xb036bd23, 0x198e06e9, 0xe988c554, 0x9ac09192, 0x8b22f5e3, 0x28cedb2d, 0x082cc066, 0x4f877d7a, 0x6106b105, 0xc082c71b, 0x204b2684, 0x11d9ffc7 },
         { 0x40a11364, 0xe40ef514, 0xc4e99410, 0x133d1ceb, 0xeb2784c7, 0x6f7ead22, 0x9f763da2, 0x7dec49bd, 0xe381e9a0, 0xa19731eb, 0x08b8f675, 0x98b67b00 },
         { 0x9813cd95, 0x8530e7bf, 0x1690b4a0, 0x42a17a8c, 0x538db07a, 0x0172f590, 0x47590188, 0x23933c2e, 0x83b0543a, 0x54561c78, 0xd0cf75ba, 0x4034afd1 },
         { 0xb18dbe56, 0xffd3e833, 0x863a3b16, 0x273f2305, 0x7b2541eb, 0x4980f437, 0x91e120ab, 0x76687e0c, 0x7df651ae, 0xddbcf275, 0x2d0ca663, 0x49f798b0 },
         { 0x92f91269, 0xb41e3a8e, 0x4659a655, 0x379d0efe, 0xe753c3a9, 0x44e73299, 0xe090111f, 0x88cb3532, 0x953e4e68, 0xb2fd42d2, 0x78957aeb, 0xb21da0d8 },
         { 0xf7f46ec7, 0xc0e17687, 0x85dda482, 0xd29bb7fa, 0xe2f34658, 0x5ca4e976, 0xe0268d33, 0x21f71fa6, 0x20958f50, 0x6f205560, 0xf74ceb1e, 0xe83b0b8f }
      },
      {
         { 0x991b0154, 0x655f42fc, 0xf2a7b543, 0x3706ac78, 0xf87dcc58, 0x3b283003, 0x76a39118, 0xbd46ccd2, 0x7acf92a5, 0x8a689a5a },
         { 0x670c17a7, 0x3d20aba4, 0x1da22a6c, 0x49f9b742, 0x15b801f0, 0x62fffee4, 0xb783ba45, 0xf12b5f84, 0x1b4cda42, 0x2870c3a7 },
         { 0xe454a692, 0x0dfdcd66, 0x69c52a4c, 0x00788d93, 0x28c4c4c9, 0xadd80fb0, 0x460f36e0, 0x21286433, 0x49752da6, 0x01d251fc },
         { 0x5a4ce4d1, 0x1f19ca83, 0xef3a551c, 0x8d8d0d31, 0x06c0b788, 0xaa59d8de, 0x3709b2b1, 0x331fb713, 0x4204e04a, 0xa22609df },
         { 0x144c903f, 0x65dd7b36, 0x90a9c043, 0x73fb7e6d, 0xde73b9ba, 0xec97dead, 0x4af2284b, 0xd1bfa1d1, 0x19f0983b, 0xae994828 },
         { 0x927ecea1, 0xe74c1bc6, 0x018e0957, 0x76cfae3a, 0x8cac3d5a, 0x3e703822, 0xab806df0, 0x3b879449, 0x36c0b7fa, 0x2a98be8c }
      },
      {
         { 0xde202754, 0xe5e61086, 0x304ee027, 0x0e126cff, 0x927fc5b9, 0x7c62e3e7, 0x41d11a56, 0x04c7e750, 0xe1461d86, 0x37e373d2, 0x8f6df7dd, 0x5664e530 },
         { 0x6df7d2b8, 0xa8ab3d3a, 0xdb48facd, 0x5fd98d59, 0x6fec6430, 0x22162776, 0xb16cc4cf, 0x68d890d3, 0xac866cd1, 0xddb855d9, 0x11e0e6ed, 0x05480e03 },
         { 0x2b821405, 0xc96cb16f, 0x57b3e0da, 0x7987156f, 0xb9f5a965, 0xf286ac11, 0x996f1d77, 0xf39c3388, 0x81bfbc86, 0x00fb3f74, 0xee264726, 0x8f829341 },
         { 0xb2712370, 0xe83c8159, 0x0a64ecf3, 0x2c0e7822, 0xfcbf0979, 0x0f719119, 0xe239a239, 0x984e006d, 0x41fdc776, 0x2ddcc054, 0xe0d86e19, 0xbf1be261 },
         { 0xb54a6261, 0x7e2d8619, 0xbf48c531, 0x50b82413, 0xd740a68e, 0x59b9b6e8, 0x6edb2898, 0xf1d8712e, 0x671197d3, 0x365dc4d3, 0x1dd0f90c, 0x039ded1b },
         { 0x2a1a418c, 0x05ebdad6, 0xfab6cb85, 0xed1aa518, 0xa1e2689e, 0x44f59ef6, 0x68d74816, 0x6272d951, 0x330642ed, 0xeec43b61, 0xa171d9c8, 0x948122f7 }
      }
   };

   @Test
   public void matchesCppIntegerCoordinatesAndMidpointConversion() {
      for (int c = 0; c < CPP_COORDINATES.length; c++) {
         FixedSeedStream stream = new FixedSeedStream();
         BurleyQuantLibPointSet points = configured(c, stream);
         assertEquals(0, stream.draws);
         PointSetIterator iterator = points.iterator();
         double[] row = new double[points.getDimension()];
         for (int p = 0; p < INDICES.length; p++) {
            iterator.setCurPointIndex(INDICES[p]);
            iterator.nextPoint(row, row.length);
            iterator.setCurPointIndex(INDICES[p]);
            for (int j = 0; j < row.length; j++) {
               double scalar = iterator.nextDouble();
               assertEquals(row[j], scalar, 0.0);
               assertEquals(points.getCoordinate(INDICES[p], j), scalar, 0.0);
            }
            for (int j = 0; j < FIXTURE_DIMENSIONS[c].length; j++) {
               int dimension = FIXTURE_DIMENSIONS[c][j];
               long expected = Integer.toUnsignedLong(CPP_COORDINATES[c][p][j]);
               assertEquals(expected, integerCoordinate(row[dimension]));
               assertEquals((expected + 0.5) * 0x1.0p-32, row[dimension], 0.0);
            }
         }
         assertEquals(0, stream.draws);
      }
   }

   @Test
   public void matchesCppDigestsThrough21200Dimensions() {
      for (int c = 0; c < CPP_PARAMETERS.length; c++) {
         BurleyQuantLibPointSet points = configured(c, new FixedSeedStream());
         PointSetIterator iterator = points.iterator();
         double[] row = new double[points.getDimension()];
         long digest = 0xcbf29ce484222325L;
         for (int index : INDICES) {
            iterator.setCurPointIndex(index);
            iterator.nextPoint(row, row.length);
            for (int j = 0; j < row.length; j++) {
               assertEquals(points.getCoordinate(index, j), row[j], 0.0);
               digest = (digest ^ integerCoordinate(row[j])) * 0x100000001b3L;
            }
         }
         assertEquals("QuantLib fixture " + c, CPP_DIGESTS[c], digest);
      }
   }

   @Test
   public void pointCountKeepsFullColumnsAndSobolSeedAffectsOnlyHigherDimensions() {
      for (int k : new int[] { 0, 4, 30 }) {
         BurleyQuantLibPointSet points = new BurleyQuantLibPointSet(k, 40, new FixedSeedStream());
         assertEquals(1 << k, points.getNumPoints());
         points.setScrambleSeed(43);
         for (int j = 0; j < 10; j++)
            assertEquals(Integer.toUnsignedLong(CPP_COORDINATES[0][0][j]),
                  integerCoordinate(points.getCoordinate(0, j)));
      }
      BurleyQuantLibPointSet original = new BurleyQuantLibPointSet(4, 40, 42, new FixedSeedStream());
      BurleyQuantLibPointSet different = new BurleyQuantLibPointSet(4, 40, 7, new FixedSeedStream());
      original.setScrambleSeed(43);
      different.setScrambleSeed(43);
      for (int i = 0; i < 16; i++)
         for (int j = 0; j < 32; j++)
            assertEquals(original.getCoordinate(i, j), different.getCoordinate(i, j), 0.0);
      assertEquals(0xaa137046L, integerCoordinate(original.getCoordinate(0, 32)));
      assertEquals(0xe664c496L, integerCoordinate(different.getCoordinate(0, 32)));
   }

   @Test
   public void adapterSelectsReproducibleSeedsAndDelegatesStreamAccess() {
      FixedSeedStream stream = new FixedSeedStream(0, 43, 1, 0, 0xdeadbeef);
      BurleyQuantLibPointSet points = new BurleyQuantLibPointSet(4, 40, stream);
      PointSetRandomization adapter = points.getRandomization();
      assertSame(adapter, points.getRandomization());
      assertSame(stream, adapter.getStream());
      assertEquals(0, points.getScrambleSeed());
      assertEquals(42, points.getSobolSeed());
      assertEquals(0, stream.draws);

      points.randomize(adapter);
      assertEquals(43, points.getScrambleSeed());
      assertEquals(2, stream.draws);
      double first = points.getCoordinate(0, 0);
      points.setScrambleSeed(43);
      assertEquals(first, points.getCoordinate(0, 0), 0.0);
      assertEquals(2, stream.draws);
      points.randomize();
      assertEquals(1, points.getScrambleSeed());
      assertNotEquals(first, points.getCoordinate(0, 0), 0.0);
      assertEquals(3, stream.draws);
      points.setScrambleSeed(0);
      assertEquals(0xdeadbeefL, points.getScrambleSeed());
      assertEquals(5, stream.draws);

      stream.resetStartStream();
      adapter.randomize(points);
      assertEquals(43, points.getScrambleSeed());
      assertEquals(first, points.getCoordinate(0, 0), 0.0);
      FixedSeedStream replacement = new FixedSeedStream(7);
      adapter.setStream(replacement);
      assertSame(replacement, points.getStream());
      assertEquals(first, points.getCoordinate(0, 0), 0.0);
      adapter.randomize(points);
      assertEquals(7, points.getScrambleSeed());
      assertEquals(1, replacement.draws);
      points.setStream(stream);
      assertSame(stream, adapter.getStream());
      assertThrows(IllegalArgumentException.class,
            () -> adapter.randomize(new SobolSequence(4, 32, 40)));
   }

   @Test
   public void automaticSobolSeedIsSelectedOnlyForHigherDimensions() {
      FixedSeedStream unused = new FixedSeedStream();
      BurleyQuantLibPointSet low = new BurleyQuantLibPointSet(3, 32, 0, unused);
      low.setScrambleSeed(43);
      assertEquals(0, unused.draws);
      assertEquals(0xeb0bfbabL, integerCoordinate(low.getCoordinate(0, 0)));

      FixedSeedStream automatic = new FixedSeedStream(0, 7);
      BurleyQuantLibPointSet selected = new BurleyQuantLibPointSet(3, 33, 0, automatic);
      assertEquals(7, selected.getSobolSeed());
      assertEquals(2, automatic.draws);
      selected.setScrambleSeed(43);
      assertEquals(0xe664c496L, integerCoordinate(selected.getCoordinate(0, 32)));
      BurleyQuantLibPointSet explicit = new BurleyQuantLibPointSet(3, 33, 7, new FixedSeedStream());
      explicit.setScrambleSeed(43);
      for (int i = 0; i < 8; i++)
         for (int j = 0; j < 33; j++)
            assertEquals(explicit.getCoordinate(i, j), selected.getCoordinate(i, j), 0.0);
      assertEquals(2, automatic.draws);
   }

   @Test
   public void signedLongSeedsRetainTheirUnsignedLow32Bits() {
      FixedSeedStream unused = new FixedSeedStream();
      BurleyQuantLibPointSet signed = new BurleyQuantLibPointSet(4, 40, -1L, unused);
      BurleyQuantLibPointSet unsigned = new BurleyQuantLibPointSet(4, 40, 0xffffffffL, unused);
      signed.setScrambleSeed(-1L);
      unsigned.setScrambleSeed(0xffffffffL);
      assertEquals(-1L, signed.getScrambleSeed());
      assertEquals(0xffffffffL, unsigned.getScrambleSeed());
      for (int i = 0; i < 16; i++)
         for (int j = 0; j < 40; j++)
            assertEquals(unsigned.getCoordinate(i, j), signed.getCoordinate(i, j), 0.0);
      assertEquals(0, unused.draws);
   }

   @Test
   public void iteratorSupportsBulkAccessJumpsAndRandomizationWithoutReset() {
      for (int k : new int[] { 0, 1, 4, 12, 30 })
         for (int dim : new int[] { 1, 2, 3, 4, 5, 9, 10, 31, 32, 33, 65 })
            checkIterator(new BurleyQuantLibPointSet(k, dim, new FixedSeedStream(43, 1)));
   }

   @Test
   public void integratesWithRqmcExperiments() {
      MWC64k3a2 stream = new MWC64k3a2();
      stream.setSeed(new long[] { 1, 2, 3, 4 });
      BurleyQuantLibPointSet points = new BurleyQuantLibPointSet(4, 10, stream);
      MonteCarloModelDouble model = new MonteCarloModelDouble() {
         private double value;

         @Override
         public void simulate(RandomStream random) {
            value = 0;
            for (int j = 0; j < 10; j++)
               value += (j + 1) * random.nextDouble();
         }

         @Override
         public double getPerformance() {
            return value;
         }
      };
      TallyStore estimates = new TallyStore(3);
      RQMCExperiment64.simulReplicatesRQMC(model,
            new RQMCPointSet(points, points.getRandomization()), 3, estimates);
      assertEquals(3, estimates.numberObs());
      stream.resetStartStream();
      for (int rep = 0; rep < 3; rep++) {
         points.randomize();
         double sum = 0;
         for (int i = 0; i < 16; i++) {
            double value = 0;
            for (int j = 0; j < 10; j++)
               value += (j + 1) * points.getCoordinate(i, j);
            sum += value;
         }
         assertEquals(sum / 16, estimates.getArray()[rep], 4 * Math.ulp(sum / 16));
      }
   }

   @Test
   public void validatesArgumentsInitializationAndBounds() {
      RandomStream unused = new FixedSeedStream();
      assertThrows(IllegalArgumentException.class, () -> new BurleyQuantLibPointSet(-1, 1, unused));
      assertThrows(IllegalArgumentException.class, () -> new BurleyQuantLibPointSet(31, 1, unused));
      assertThrows(IllegalArgumentException.class, () -> new BurleyQuantLibPointSet(1, 0, unused));
      assertThrows(IllegalArgumentException.class, () -> new BurleyQuantLibPointSet(1, 21201, unused));
      assertThrows(IllegalArgumentException.class, () -> new BurleyQuantLibPointSet(1, 1, null));
      BurleyQuantLibPointSet points = new BurleyQuantLibPointSet(0, 1, unused);
      PointSetIterator iterator = points.iterator();
      assertThrows(IllegalStateException.class, () -> points.getCoordinate(0, 0));
      assertThrows(IllegalStateException.class, () -> iterator.nextCoordinate());
      points.setScrambleSeed(43);
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(-1, 0));
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(1, 0));
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(0, -1));
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(0, 1));
      assertThrows(IllegalArgumentException.class, () -> points.setStream(null));
      assertThrows(IllegalArgumentException.class, () -> points.getRandomization().setStream(null));
      assertThrows(UnsupportedOperationException.class, () -> points.addRandomShift(unused));
      iterator.setCurPointIndex(1);
      assertThrows(java.util.NoSuchElementException.class, () -> iterator.nextDouble());
      iterator.nextCoordinates(new double[0], 0);
   }

   private static BurleyQuantLibPointSet configured(int c, RandomStream stream) {
      long[] parameters = CPP_PARAMETERS[c];
      BurleyQuantLibPointSet points =
            new BurleyQuantLibPointSet(30, (int) parameters[0], parameters[1], stream);
      points.setScrambleSeed(parameters[2]);
      return points;
   }

   private static long integerCoordinate(double coordinate) {
      assertTrue(coordinate > 0.0 && coordinate < 1.0);
      long bits = (long) (Math.scalb(coordinate, 32) - 0.5);
      assertEquals((bits + 0.5) * 0x1.0p-32, coordinate, 0.0);
      return bits;
   }

   private static void sameNext(BurleyQuantLibPointSet points, PointSetIterator iterator) {
      double expected = points.getCoordinate(iterator.getCurPointIndex(), iterator.getCurCoordIndex());
      assertEquals(expected, iterator.nextDouble(), 0.0);
   }

   private static void checkIterator(BurleyQuantLibPointSet points) {
      PointSetIterator first = points.iterator();
      PointSetIterator second = points.iterator();
      points.randomize();
      int n = points.getNumPoints();
      int dim = points.getDimension();
      int count = Math.min(n, 32);
      double[] row = new double[dim];
      for (int i = 0; i < count; i++) {
         assertEquals(i + 1, first.nextPoint(row, dim));
         assertEquals(0, first.getCurCoordIndex());
         for (int j = 0; j < dim; j++)
            assertEquals(points.getCoordinate(i, j), row[j], 0.0);
      }
      first.resetStartStream();
      for (int i = 0; i < count; i++) {
         for (int j = 0; j < dim; j++)
            sameNext(points, first);
         first.resetNextSubstream();
      }
      for (int i : new int[] { n - 1, 0, n / 2 }) {
         first.setCurPointIndex(i);
         second.setCurPointIndex(n - 1 - i);
         for (int j = dim - 1; j >= 0; j--) {
            first.setCurCoordIndex(j);
            second.setCurCoordIndex(j);
            sameNext(points, first);
            sameNext(points, second);
         }
      }
      int from = Math.min(3, dim - 1);
      first.setCurPointIndex(n - 1);
      first.setCurCoordIndex(from);
      first.nextCoordinates(row, dim - from);
      for (int j = from; j < dim; j++)
         assertEquals(points.getCoordinate(n - 1, j), row[j - from], 0.0);
      assertThrows(java.util.NoSuchElementException.class, () -> first.nextDouble());
      first.setCurPointIndex(n - 1);
      assertEquals(n, first.nextPoint(row, from, dim - from));
      for (int j = from; j < dim; j++)
         assertEquals(points.getCoordinate(n - 1, j), row[j - from], 0.0);
      assertThrows(java.util.NoSuchElementException.class, () -> first.nextDouble());
      first.nextCoordinates(row, 0);

      first.setCurPointIndex(0);
      first.setCurCoordIndex(dim - 1);
      assertThrows(java.util.NoSuchElementException.class, () -> first.nextCoordinates(row, 2));
      assertEquals(dim - 1, first.getCurCoordIndex());
      first.resetStartSubstream();
      int length = Math.min(dim, 6);
      double[] array = new double[length + 2];
      array[0] = -3;
      array[length + 1] = -7;
      first.nextArrayOfDouble(array, 1, length);
      assertEquals(-3, array[0], 0.0);
      assertEquals(-7, array[length + 1], 0.0);
      for (int j = 0; j < length; j++)
         assertEquals(points.getCoordinate(0, j), array[j + 1], 0.0);

      first.setCurPointIndex(0);
      second.setCurPointIndex(n - 1);
      sameNext(points, first);
      sameNext(points, second);
      points.randomize();
      if (dim == 1) {
         first.setCurCoordIndex(0);
         second.setCurCoordIndex(0);
      }
      sameNext(points, first);
      sameNext(points, second);
      points.setScrambleSeed(43);
      first.resetCurCoordIndex();
      second.resetCurCoordIndex();
      for (int j = 0; j < dim; j++) {
         sameNext(points, first);
         sameNext(points, second);
      }
      first.setCurPointIndex(-1);
      assertThrows(IndexOutOfBoundsException.class, () -> first.nextCoordinate());
      first.setCurPointIndex(0);
      first.setCurCoordIndex(-1);
      assertThrows(IndexOutOfBoundsException.class, () -> first.nextCoordinates(row, 1));
   }

   private static final class FixedSeedStream extends RandomStreamBase {
      private static final long serialVersionUID = 1L;
      private final int[] seeds;
      private int draws;

      FixedSeedStream(int... seeds) {
         this.seeds = seeds.clone();
      }

      @Override
      public long nextBitsLong(int bits) {
         assertEquals(32, bits);
         assertTrue("Unexpected seed draw", draws < seeds.length);
         return Integer.toUnsignedLong(seeds[draws++]);
      }

      @Override
      protected double nextValue() {
         throw new AssertionError("Seed selection must use nextBitsLong(32)");
      }

      @Override
      public void resetStartStream() {
         draws = 0;
      }

      @Override
      public void resetStartSubstream() {
         resetStartStream();
      }

      @Override
      public void resetNextSubstream() {
         throw new UnsupportedOperationException();
      }

      @Override
      public String toString() {
         return "Fixed QuantLib scramble seeds";
      }
   }
}
