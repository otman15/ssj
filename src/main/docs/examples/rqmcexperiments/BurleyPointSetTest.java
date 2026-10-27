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

import umontreal.ssj.hups64.BurleyPointSet;
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

/**
 * Correctness tests in the test source directory configured by build.gradle.
 * No performance measurements are performed.
 */
public class BurleyPointSetTest {
   private static final long MASK32 = 0xffffffffL;
   private static final int[] SEEDS = { 0, 1, 12345, -1, 0x80000000, 0xdeadbeef };
   private static final int[] INDICES = {
      0, 1, 2, 3, 7, 8, 31, 255, 65535, 0x1fffffff, 0x3fffffff
   };

   /*
    * Integer outputs of the unmodified shuffled_scrambled_sobol4d(index, seed, X)
    * from https://jcgt.org/published/0009/04/01/suppl.zip, sobol.h and sobol.cpp,
    * compiled as C++11. The archive SHA-256 is
    * d5e80d69bfce311d7be7451b5f3cf4df9be018a174e80858aa528ebf7807c81a.
    * Outer rows follow SEEDS; each consecutive quadruple follows INDICES.
    * These use Listing 3's seed directly, without genpoints.cpp's initial hash.
    */
   private static final int[][] REFERENCE_BITS = {
      {
         0x00000000, 0xa66de0ec, 0x5336f076, 0xeb8e6301,
         0xa66de0ec, 0x5336f076, 0xeb8e6301, 0x299b783b,
         0x5336f076, 0x299b783b, 0x8bb03ceb, 0x75c73180,
         0xeb8e6301, 0xeb8e6301, 0x299b783b, 0x8bb03ceb,
         0x75c73180, 0xc6ffbf8e, 0xc6ffbf8e, 0x45d81e75,
         0x45d81e75, 0x9d08b649, 0x3ae398c0, 0x3ae398c0,
         0xf248fed6, 0x95768758, 0x691a101b, 0x4e845b24,
         0x967bcd1d, 0xa97249e3, 0x9eb33231, 0xdbbeb412,
         0xf981fe45, 0x6c742bde, 0x0971214f, 0x86b8182d,
         0x378f64d0, 0x567905d9, 0x9549416e, 0x71105b50,
         0xb8b41364, 0xec6a2fe4, 0x8fe3109c, 0x1d9da32e
      },
      {
         0x98930182, 0x9bdfee08, 0xde26b895, 0x7e67daf7,
         0x66cbfd18, 0x3d3d4842, 0x02010296, 0xce2c3cdd,
         0xd6787859, 0x65458e57, 0x517524c9, 0xbc86773e,
         0x0e182029, 0xd5f069f0, 0xa477aef3, 0x1f826485,
         0xfd64bfe6, 0xfe45e43a, 0x3417532a, 0x835ac712,
         0x11f8e43c, 0x06dfb594, 0x4a20b676, 0x6bc3860a,
         0x798f35fe, 0x5c5b4f97, 0x7041d4b8, 0x516023aa,
         0x64ebe96a, 0x77a21bf5, 0x5daf14eb, 0xf185d5f1,
         0xe5a2f4bc, 0xf5073f7f, 0xc1071489, 0xfa4dce74,
         0xfd5fb008, 0xa3b053dd, 0x36441ef7, 0x2bdaef6a,
         0xb77a15eb, 0xea5cf3be, 0x93b80ef5, 0xe5d638a0
      },
      {
         0x8df3fe29, 0x47696416, 0xe3acb5a2, 0xbfe852ef,
         0x7253a54a, 0xfb8d8ffb, 0x201379ba, 0x1c6b7acb,
         0xecd19c1a, 0x9f463cdc, 0x58422b8f, 0x4fb641b3,
         0x2f50f4ae, 0x38f11659, 0xaecab470, 0xf3578ab2,
         0x07988422, 0xb6e17065, 0xcee7f4f2, 0xdbfc1824,
         0x33f1a1d8, 0xd045f7ac, 0x47a2e350, 0xa2f19382,
         0x1345f1bf, 0xe92a1e58, 0xbc4082f9, 0x44f7bb16,
         0x9c59a2d6, 0xb72424e1, 0x8a7267f2, 0x65db5f4c,
         0x2c69b8c5, 0x25a8bcd9, 0xe3536330, 0xf0b57ef8,
         0xb31b9be9, 0x4d0d61ae, 0xedd6253b, 0x7cb9c61f,
         0x9218c3cb, 0x9217b1f0, 0x2fb805be, 0x2d6d6b65
      },
      {
         0xc2ea9c62, 0xc2cba4e1, 0xbb985d97, 0x731ab15d,
         0x70e04337, 0x11431aee, 0x1866d51a, 0x8cc575e4,
         0x2d1fc882, 0xb0385ace, 0xc97f5b0d, 0x1209b052,
         0x8fef2144, 0x40aed351, 0x79bcd382, 0xc148afcd,
         0xee873ad4, 0x3e768fcf, 0xf7c37838, 0x43883b0f,
         0xb01c6f4c, 0x0503603d, 0x848081dc, 0xa0306fed,
         0x0eac33dd, 0x904cb4a3, 0x705a548f, 0x9b9c7625,
         0x356c461d, 0x473455db, 0x5248e9c6, 0x26993968,
         0x01d7da90, 0x1120c340, 0x071f060a, 0x828d78ae,
         0x24f79093, 0x3fa0b7a6, 0x9653f97d, 0x776b9221,
         0x31479fb4, 0xe4d39935, 0x7359bbbb, 0x335e00a3
      },
      {
         0xa66de0e8, 0x5336f072, 0xeb8e6304, 0x299b783f,
         0x00000004, 0xa66de0e8, 0x5336f072, 0xeb8e6304,
         0xeb8e6304, 0xeb8e6304, 0x299b783f, 0x8bb03cee,
         0x5336f072, 0x299b783f, 0x8bb03cee, 0x75c73185,
         0xc6ffbf8b, 0x14cdbc18, 0x75c73185, 0xb43e8c5b,
         0xf915a226, 0x637fdfc3, 0x9d08b64c, 0xf915a226,
         0x4e845b20, 0x691a101f, 0xdaf82ed6, 0xbe922af3,
         0x32766b2f, 0x5e078bd9, 0x39552bde, 0x045d81e2,
         0x45572168, 0x91776294, 0xaf200f6a, 0x7b002272,
         0x9383c00c, 0xa39532b6, 0x318f64f7, 0x8e2ffa59,
         0x1bc7b26f, 0x2fe40917, 0x2d1195ab, 0xcc3ba79a
      },
      {
         0xf8c8b927, 0x11a4f294, 0xa76d76ef, 0xd236c45d,
         0x442241c4, 0xc23a209e, 0x0105c178, 0x0a64d1eb,
         0x1505fcd4, 0x4055191c, 0xdcc33b63, 0x9d0adb7c,
         0xb5d8d54d, 0xb0ffaa84, 0x6f167562, 0x637d50af,
         0x8a69a116, 0x70373635, 0x36fc7c6b, 0x45da34f3,
         0x3b0ffbdd, 0x0e52845e, 0x25157c1e, 0xeb8c8309,
         0x1ad9e140, 0xbe490151, 0x3be135ef, 0x24d0afbe,
         0x7d919315, 0x3f2991ee, 0xb1de47a5, 0xcea38cd0,
         0x48dc5c80, 0x3a05827c, 0xf09050e9, 0x1c9ce7f9,
         0x3d648374, 0xf92e5593, 0x6713be7a, 0x0f45d3df,
         0x02475d32, 0x932e5cf8, 0x78c48757, 0x27f0d2ad
      }
   };

   @Test
   public void referenceMatchesOriginalCppIntegers() {
      for (int s = 0; s < SEEDS.length; s++) {
         FixedSeedStream stream = new FixedSeedStream(SEEDS[s]);
         // A billion-point set also checks that construction does not cache points.
         BurleyPointSet points = new BurleyPointSet(30, 4, stream);
         BurleyPointSet explicitReference = new BurleyPointSet(30, 4,
               new FixedSeedStream(SEEDS[s]), false);
         assertEquals(0, stream.draws);
         points.randomize();
         explicitReference.randomize();
         assertEquals(1 << 30, points.getNumPoints());
         PointSetIterator iterator = points.iterator();
         double[] row = new double[4];
         for (int p = 0; p < INDICES.length; p++) {
            iterator.setCurPointIndex(INDICES[p]);
            iterator.nextPoint(row, 4);
            iterator.setCurPointIndex(INDICES[p]);
            for (int j = 0; j < 4; j++) {
               long expected = REFERENCE_BITS[s][4 * p + j] & MASK32;
               assertCoordinate(expected, points, INDICES[p], j);
               assertCoordinate(expected, explicitReference, INDICES[p], j);
               assertEquals(expected * 0x1.0p-32 + 0x1.0p-55, row[j], 0.0);
               assertEquals(row[j], iterator.nextDouble(), 0.0);
            }
         }
         assertEquals(1, stream.draws);
      }
      // Reference evaluation uses 32 columns even for a one-point set.
      BurleyPointSet onePoint = new BurleyPointSet(0, 4, new FixedSeedStream(12345));
      onePoint.randomize();
      for (int j = 0; j < 4; j++)
         assertCoordinate(REFERENCE_BITS[2][j] & MASK32, onePoint, 0, j);
   }

   @Test
   public void referencePadsInGroupsOfFourWithIndependentSeeds() {
      FixedSeedStream stream = new FixedSeedStream(12345, 0xdeadbeef, 0x80000000);
      BurleyPointSet points = new BurleyPointSet(8, 10, stream);
      points.randomize();
      int[] fixtureRows = { 2, 5, 4 };
      for (int p = 0; p < 8; p++)
         for (int j = 0; j < 10; j++)
            assertCoordinate(REFERENCE_BITS[fixtureRows[j / 4]][4 * p + j % 4] & MASK32,
                  points, INDICES[p], j);
      assertEquals(3, stream.draws);

      // Identical group seeds must reproduce identical groups, including local
      // dimension seed numbering; this also covers a partial final group.
      BurleyPointSet sameSeeds = new BurleyPointSet(8, 10, new FixedSeedStream(1, 1, 1));
      sameSeeds.randomize();
      for (int i = 0; i < 256; i++)
         for (int j = 4; j < 10; j++)
            assertEquals(sameSeeds.getCoordinate(i, j % 4), sameSeeds.getCoordinate(i, j), 0.0);
   }

   @Test
   public void ssjUsesAll32ColumnsInBinaryOrder() {
      long[] columns = fullSsjColumns();
      int[] sizes = { 0, 1, 2, 4, 7, 12, 20, 30 };
      for (int k : sizes) {
         for (int seed : SEEDS) {
            int[] seeds = { seed, seed ^ 0x9e3779b9, ~seed };
            FixedSeedStream stream = new FixedSeedStream(seeds);
            BurleyPointSet points = new BurleyPointSet(k, 10, stream, true);
            assertEquals(0, stream.draws);
            points.randomize();
            int n = 1 << k;
            for (int p = 0; p < Math.min(n, 128) + 2; p++) {
               int i = p < Math.min(n, 128) ? p : (p == Math.min(n, 128) ? n - 1 : n / 2);
               for (int j = 0; j < 10; j++)
                  assertCoordinate(ssjBits(columns, i, j % 4, seeds[j / 4]), points, i, j);
            }
            assertEquals(3, stream.draws);
         }
      }
   }

   @Test
   public void fullIndexRetainsHighPrefixAndPermutesLowBits() {
      for (int k = 0; k <= 12; k++) {
         int n = 1 << k;
         for (int seed : SEEDS) {
            BurleyPointSet points = new BurleyPointSet(k, 1, new FixedSeedStream(seed), true);
            points.randomize();
            boolean[] seen = new boolean[n];
            long highMask = MASK32 ^ (n - 1L);
            long prefix = scramble32(0, seed & MASK32) & highMask;
            for (int i = 0; i < n; i++) {
               long fullIndex = scramble32(i, seed & MASK32);
               assertEquals(prefix, fullIndex & highMask);
               int restricted = (int) (fullIndex & (n - 1L));
               assertFalse(seen[restricted]);
               seen[restricted] = true;
               // The first Sobol coordinate is reverse(fullIndex), including
               // its high prefix, before the coordinate scramble is applied.
               assertCoordinate(scramble32(reverse32(fullIndex), combine32(seed, 0)), points, i, 0);
            }
            for (boolean visited : seen)
               assertTrue(visited);
         }
      }
   }

   @Test
   public void allSsjColumnsMatchSsjAndItsRecurrence() {
      long[] columns = fullSsjColumns();
      for (int c = 0; c < 32; c++) {
         long index = 1L << c;
         int seed = seedForFirstIndex(index);
         assertEquals(index, scramble32(0, seed & MASK32));
         // Force each individual column to contribute, even in a one-point
         // set. This checks all 128 constants, including columns 31 and 32.
         FixedSeedStream stream = new FixedSeedStream(seed);
         BurleyPointSet points = new BurleyPointSet(0, 4, stream, true);
         points.randomize();
         PointSetIterator iterator = points.iterator();
         double[] row = new double[4];
         iterator.nextPoint(row, 4);
         for (int j = 0; j < 4; j++) {
            long expected = scramble32(columns[j * 32 + c], combine32(seed, j));
            assertCoordinate(expected, points, 0, j);
            assertEquals(expected * 0x1.0p-32 + 0x1.0p-55, row[j], 0.0);
         }
         assertEquals(1, stream.draws);
      }
   }

   @Test
   public void randomizationAndIteratorRemainConsistent() {
      for (boolean useSsjDirections : new boolean[] { false, true }) {
         FixedSeedStream stream = new FixedSeedStream(12345, 0xdeadbeef, 0x80000000, 1, -1, 0);
         BurleyPointSet points = new BurleyPointSet(3, 9, stream, useSsjDirections);
         PointSetRandomization adapter = points.getRandomization();
         assertSame(adapter, points.getRandomization());
         assertSame(stream, adapter.getStream());
         PointSetIterator iterator = points.iterator();
         points.randomize(adapter);
         double[][] before = new double[8][9];
         double[] row = new double[9];
         for (int i = 0; i < 8; i++) {
            iterator.nextPoint(row, 9);
            for (int j = 0; j < 9; j++) {
               before[i][j] = points.getCoordinate(i, j);
               assertEquals(before[i][j], row[j], 0.0);
            }
         }
         assertEquals(3, stream.draws);
         assertThrows(java.util.NoSuchElementException.class, () -> iterator.nextCoordinate());
         for (int i = 7; i >= 0; i--)
            for (int j = 8; j >= 0; j--)
               assertEquals(before[i][j], points.getCoordinate(i, j), 0.0);
         assertEquals(3, stream.draws);

         points.randomize();
         assertEquals(6, stream.draws);
         assertNotEquals(before[0][0], points.getCoordinate(0, 0), 0.0);
         iterator.resetStartStream();
         assertEquals(points.getCoordinate(0, 0), iterator.nextDouble(), 0.0);
         stream.resetStartStream();
         adapter.randomize(points);
         for (int i = 0; i < 8; i++)
            for (int j = 0; j < 9; j++)
               assertEquals(before[i][j], points.getCoordinate(i, j), 0.0);

         FixedSeedStream replacement = new FixedSeedStream(1, -1, 0);
         adapter.setStream(replacement);
         assertSame(replacement, points.getStream());
         assertEquals(before[0][0], points.getCoordinate(0, 0), 0.0);
         adapter.randomize(points);
         assertEquals(3, replacement.draws);
         assertNotEquals(before[0][0], points.getCoordinate(0, 0), 0.0);
         points.setStream(stream);
         assertSame(stream, adapter.getStream());
         assertThrows(IllegalArgumentException.class,
               () -> adapter.randomize(new SobolSequence(3, 32, 4)));
      }
   }

   @Test
   public void iteratorMatchesDirectAccessForAllTraversalModes() {
      for (int k : new int[] { 0, 1, 4, 12, 30 }) {
         for (int dim : new int[] { 1, 2, 3, 4, 5, 7, 8, 9, 10, 13 }) {
            for (boolean useSsjDirections : new boolean[] { false, true }) {
               MWC64k3a2 stream = new MWC64k3a2();
               stream.setSeed(new long[] { 1, 2, 3, 4 });
               BurleyPointSet points = new BurleyPointSet(k, dim, stream, useSsjDirections);
               assertIteratorTraversal(points);
            }
         }
      }
   }

   @Test
   public void iteratorRefreshesIndexWithoutResetAfterRandomization() {
      for (int dim : new int[] { 1, 2, 3, 4, 5, 8, 9, 10 }) {
         int groups = (dim + 3) / 4;
         int[] seeds = new int[4 * groups];
         for (int i = 0; i < seeds.length; i++)
            seeds[i] = SEEDS[i % SEEDS.length] ^ (0x9e3779b9 * i);
         for (boolean useSsjDirections : new boolean[] { false, true }) {
            FixedSeedStream stream = new FixedSeedStream(seeds);
            BurleyPointSet points = new BurleyPointSet(4, dim, stream, useSsjDirections);
            PointSetIterator first = points.iterator();
            PointSetIterator second = points.iterator();
            points.randomize();
            for (int rep = 1; rep < 4; rep++) {
               int lastGroupStart = ((dim - 1) / 4) * 4;
               first.setCurPointIndex(15);
               first.setCurCoordIndex(lastGroupStart);
               second.setCurPointIndex(0);
               sameNextCoordinate(points, first);
               sameNextCoordinate(points, second);

               points.randomize();
               // Continue within a cached group, without resetting either
               // iterator's point. Single-coordinate groups are revisited.
               if (first.getCurCoordIndex() == dim)
                  first.setCurCoordIndex(lastGroupStart);
               if (second.getCurCoordIndex() == dim)
                  second.setCurCoordIndex(0);
               sameNextCoordinate(points, first);
               sameNextCoordinate(points, second);
               first.resetStartSubstream();
               second.resetStartSubstream();
               for (int j = 0; j < dim; j++) {
                  sameNextCoordinate(points, first);
                  sameNextCoordinate(points, second);
               }
               assertEquals((rep + 1) * groups, stream.draws);
            }
         }
      }
   }

   @Test
   public void worksWithExistingRqmcExperiment() {
      for (boolean useSsjDirections : new boolean[] { false, true }) {
         MWC64k3a2 stream = new MWC64k3a2();
         stream.setSeed(new long[] { 1, 2, 3, 4 });
         BurleyPointSet points = new BurleyPointSet(4, 9, stream, useSsjDirections);
         MonteCarloModelDouble model = new MonteCarloModelDouble() {
            private double value;

            @Override
            public void simulate(RandomStream random) {
               value = 0;
               for (int j = 0; j < 9; j++)
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
               for (int j = 0; j < 9; j++)
                  value += (j + 1) * points.getCoordinate(i, j);
               sum += value;
            }
            // Tally uses an incremental mean, which can round differently from
            // this direct sum even when every model observation is identical.
            assertEquals(sum / 16, estimates.getArray()[rep], 4 * Math.ulp(sum / 16));
         }
      }
   }

   @Test
   public void validatesInputsAndCoordinateBounds() {
      RandomStream stream = new FixedSeedStream(1);
      assertThrows(IllegalArgumentException.class, () -> new BurleyPointSet(-1, 4, stream));
      assertThrows(IllegalArgumentException.class, () -> new BurleyPointSet(31, 4, stream));
      assertThrows(IllegalArgumentException.class, () -> new BurleyPointSet(32, 4, stream));
      assertThrows(IllegalArgumentException.class, () -> new BurleyPointSet(1, 0, stream));
      assertThrows(IllegalArgumentException.class, () -> new BurleyPointSet(1, 4, null));
      for (boolean useSsjDirections : new boolean[] { false, true }) {
         assertThrows(IllegalArgumentException.class,
               () -> new BurleyPointSet(-1, 4, stream, useSsjDirections));
         assertThrows(IllegalArgumentException.class,
               () -> new BurleyPointSet(31, 4, stream, useSsjDirections));
         assertThrows(IllegalArgumentException.class,
               () -> new BurleyPointSet(1, 0, stream, useSsjDirections));
         assertThrows(IllegalArgumentException.class,
               () -> new BurleyPointSet(1, 4, null, useSsjDirections));
      }

      BurleyPointSet points = new BurleyPointSet(1, 4, stream);
      points.randomize();
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(-1, 0));
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(2, 0));
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(0, -1));
      assertThrows(IndexOutOfBoundsException.class, () -> points.getCoordinate(0, 4));
      assertThrows(IllegalArgumentException.class, () -> points.setStream(null));
      assertThrows(IllegalArgumentException.class, () -> points.getRandomization().setStream(null));
      assertThrows(UnsupportedOperationException.class, () -> points.addRandomShift(stream));
   }

   private static void sameNextCoordinate(BurleyPointSet points, PointSetIterator iterator) {
      double expected = points.getCoordinate(iterator.getCurPointIndex(), iterator.getCurCoordIndex());
      assertEquals(expected, iterator.nextDouble(), 0.0);
   }

   private static void assertIteratorTraversal(BurleyPointSet points) {
      points.randomize();
      int n = points.getNumPoints();
      int dim = points.getDimension();
      int limit = Math.min(n, 32);
      PointSetIterator iterator = points.iterator();
      PointSetIterator other = points.iterator();
      double[] row = new double[dim];
      for (int i = 0; i < limit; i++) {
         assertEquals(i + 1, iterator.nextPoint(row, dim));
         assertEquals(0, iterator.getCurCoordIndex());
         for (int j = 0; j < dim; j++)
            assertEquals(points.getCoordinate(i, j), row[j], 0.0);
      }
      iterator.resetStartStream();
      for (int i = 0; i < limit; i++) {
         for (int j = 0; j < dim; j++)
            sameNextCoordinate(points, iterator);
         iterator.resetNextSubstream();
      }

      for (int i : new int[] { n - 1, 0, n / 2 }) {
         iterator.setCurPointIndex(i);
         other.setCurPointIndex(n - 1 - i);
         for (int j = dim - 1; j >= 0; j--) {
            iterator.setCurCoordIndex(j);
            other.setCurCoordIndex(j);
            sameNextCoordinate(points, iterator);
            sameNextCoordinate(points, other);
         }
      }

      int from = Math.min(3, dim - 1);
      int count = dim - from;
      iterator.setCurPointIndex(n - 1);
      iterator.setCurCoordIndex(from);
      iterator.nextCoordinates(row, count);
      assertEquals(dim, iterator.getCurCoordIndex());
      for (int j = 0; j < count; j++)
         assertEquals(points.getCoordinate(n - 1, from + j), row[j], 0.0);
      assertThrows(java.util.NoSuchElementException.class, () -> iterator.nextCoordinate());
      iterator.nextCoordinates(row, 0);

      iterator.setCurPointIndex(n - 1);
      assertEquals(n, iterator.nextPoint(row, from, count));
      for (int j = 0; j < count; j++)
         assertEquals(points.getCoordinate(n - 1, from + j), row[j], 0.0);
      assertThrows(java.util.NoSuchElementException.class, () -> iterator.nextCoordinate());
      iterator.nextCoordinates(row, 0);

      iterator.setCurPointIndex(0);
      iterator.setCurCoordIndex(dim - 1);
      assertThrows(java.util.NoSuchElementException.class, () -> iterator.nextCoordinates(row, 2));
      assertEquals(dim - 1, iterator.getCurCoordIndex());
      iterator.resetCurCoordIndex();
      int length = Math.min(6, dim);
      double[] array = new double[length + 2];
      array[0] = -3;
      array[length + 1] = -7;
      iterator.nextArrayOfDouble(array, 1, length);
      assertEquals(-3, array[0], 0.0);
      assertEquals(-7, array[length + 1], 0.0);
      for (int j = 0; j < length; j++)
         assertEquals(points.getCoordinate(0, j), array[j + 1], 0.0);

      iterator.setCurPointIndex(-1);
      assertThrows(IndexOutOfBoundsException.class, () -> iterator.nextCoordinate());
      iterator.setCurPointIndex(0);
      iterator.setCurCoordIndex(-1);
      assertThrows(IndexOutOfBoundsException.class, () -> iterator.nextCoordinates(row, 1));
   }

   private static void assertCoordinate(long expected, BurleyPointSet points, int i, int j) {
      double coordinate = points.getCoordinate(i, j);
      // Integer recovery is exact at 32-bit precision, including SSJ's epsilon.
      assertEquals(expected, (long) Math.scalb(coordinate, 32));
      assertEquals(expected * 0x1.0p-32 + 0x1.0p-55, coordinate, 0.0);
      assertTrue(coordinate > 0.0 && coordinate < 1.0);
   }

   // Independent unsigned-long arithmetic for the full-index test oracle.
   private static long reverse32(long x) {
      long reversed = 0;
      for (int b = 0; b < 32; b++, x >>>= 1)
         reversed = (reversed << 1) | (x & 1);
      return reversed;
   }

   private static long scramble32(long x, long seed) {
      x = (reverse32(x) + seed) & MASK32;
      x ^= (x * 0x6c50b47cL) & MASK32;
      x ^= (x * 0xb82f1e52L) & MASK32;
      x ^= (x * 0xc7afe638L) & MASK32;
      x ^= (x * 0x8d22f6e6L) & MASK32;
      return reverse32(x);
   }

   private static long combine32(int seed, int dimension) {
      long unsignedSeed = seed & MASK32;
      return (unsignedSeed ^ (dimension + (unsignedSeed << 6) + (unsignedSeed >>> 2))) & MASK32;
   }

   private static long ssjBits(long[] columns, int i, int localDimension, int seed) {
      long index = scramble32(i, seed & MASK32);
      long coordinate = 0;
      for (int c = 0; c < 32; c++)
         if (((index >>> c) & 1) != 0)
            coordinate ^= columns[localDimension * 32 + c];
      return scramble32(coordinate, combine32(seed, localDimension));
   }

   private static long[] fullSsjColumns() {
      long[] prefix = new SobolSequence(30, 32, 4).getGeneratorMatricesColumns();
      long[] columns = new long[4 * 32];
      // SSJ's built-in polynomials for the four base dimensions.
      int[] polynomials = { 1, 3, 7, 11 };
      for (int j = 0; j < 4; j++) {
         int start = j * 32;
         System.arraycopy(prefix, j * 30, columns, start, 30);
         if (j == 0) {
            columns[start + 30] = 2;
            columns[start + 31] = 1;
         } else {
            int polynomial = polynomials[j];
            int degree = 31 - Integer.numberOfLeadingZeros(polynomial);
            for (int c = 30; c < 32; c++) {
               long value = columns[start + c - degree] >>> degree;
               for (int b = 0; b < degree; b++)
                  if (((polynomial >>> b) & 1) != 0)
                     value ^= columns[start + c - degree + b];
               columns[start + c] = value;
            }
         }
      }
      return columns;
   }

   private static int seedForFirstIndex(long index) {
      long value = reverse32(index);
      // Invert x -> x XOR (x * multiplier), reversing the order of the four
      // Laine-Karras stages. Even multipliers make the low-bit dependency
      // triangular; 32 substitution steps recover the complete unsigned word.
      long[] multipliers = { 0x8d22f6e6L, 0xc7afe638L, 0xb82f1e52L, 0x6c50b47cL };
      for (long multiplier : multipliers) {
         long inverse = value;
         for (int b = 0; b < 32; b++)
            inverse = value ^ ((inverse * multiplier) & MASK32);
         value = inverse;
      }
      return (int) value;
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
         return seeds[draws++] & MASK32;
      }

      @Override
      protected double nextValue() {
         throw new AssertionError("Seed generation must use nextBitsLong(32)");
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
         return "Fixed Burley group seeds";
      }
   }
}
