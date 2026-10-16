package rqmcexperiments;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import com.sun.management.ThreadMXBean;

import umontreal.ssj.hups64.CachedPointSet;
import umontreal.ssj.hups64.DigitalNetBase2;
import umontreal.ssj.hups64.NestedUniformScrambling;
import umontreal.ssj.hups64.RQMCCachedNUS;
import umontreal.ssj.hups64.RQMCDigitalNet2FK02;
import umontreal.ssj.hups64.RQMCDigitalNet2HCK;
import umontreal.ssj.hups64.RQMCPointSet;
import umontreal.ssj.hups64.SobolSequence;
import umontreal.ssj.rng.MWC64k3a2;

/** Compares retained heap and allocated bytes for the five NUS methods. */
public class CompareNUSMemory {
   private static ThreadMXBean allocationBean;

   private static long usedHeap() {
      return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
   }

   private static long allocatedBytes() {
      return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
   }

   private static MWC64k3a2 newStream(long[] seed) {
      MWC64k3a2 stream = new MWC64k3a2();
      stream.setSeed(seed);
      return stream;
   }

   private static void warmUp(int s, int k, int w, long[] seed) {
      DigitalNetBase2 net = new SobolSequence(k, w, s);
      new NestedUniformScrambling(newStream(seed), w)
            .randomize(new CachedPointSet(net, false));

      net = new SobolSequence(k, w, s);
      new RQMCDigitalNet2FK02(net, new CachedPointSet(net, false),
            newStream(seed), w).randomize();

      CachedPointSet cache = new CachedPointSet(new SobolSequence(k, w, s));
      new RQMCCachedNUS(cache, cache, newStream(seed), w).randomize();

      net = new SobolSequence(k, w, s);
      new RQMCCachedNUS(net, new CachedPointSet(net, false),
            newStream(seed)).randomize();

      net = new SobolSequence(k, w, s);
      new RQMCDigitalNet2HCK(net, new CachedPointSet(net, false),
            newStream(seed), w).randomize();
   }

   private static void report(String name, RQMCPointSet points, long baselineHeap,
         long preparationBytes, int m) {
      System.gc();
      long retainedBytes = usedHeap() - baselineHeap;

      long before = allocatedBytes();
      for (int rep = 0; rep < m; rep++)
         points.randomize();
      long scramblingBytes = allocatedBytes() - before;

      // Print after measuring allocations, while this method still owns the point set.
      System.out.printf(Locale.ROOT, "%-26s %20.2f %20.2f %24.2f%n", name,
            retainedBytes / 1024.0, preparationBytes / 1024.0,
            scramblingBytes / (double) m);
   }

   private static void measureLegacy(int s, int k, int w, int m, long[] seed) {
      System.gc();
      long baselineHeap = usedHeap();
      long before = allocatedBytes();
      MWC64k3a2 stream = newStream(seed);
      DigitalNetBase2 net = new SobolSequence(k, w, s);
      RQMCPointSet points = new RQMCPointSet(new CachedPointSet(net, false),
            new NestedUniformScrambling(stream, w));
      long preparationBytes = allocatedBytes() - before;
      report("NestedUniformScrambling", points, baselineHeap, preparationBytes, m);
   }

   private static void measureFK02(int s, int k, int w, int m, long[] seed) {
      System.gc();
      long baselineHeap = usedHeap();
      long before = allocatedBytes();
      MWC64k3a2 stream = newStream(seed);
      DigitalNetBase2 net = new SobolSequence(k, w, s);
      RQMCDigitalNet2FK02 points = new RQMCDigitalNet2FK02(net,
            new CachedPointSet(net, false), stream, w);
      long preparationBytes = allocatedBytes() - before;
      report("RQMCDigitalNet2FK02", points, baselineHeap, preparationBytes, m);
   }

   private static void measureCached(int s, int k, int w, int m, long[] seed) {
      System.gc();
      long baselineHeap = usedHeap();
      long before = allocatedBytes();
      MWC64k3a2 stream = newStream(seed);
      CachedPointSet cache = new CachedPointSet(new SobolSequence(k, w, s));
      RQMCCachedNUS points = new RQMCCachedNUS(cache, cache, stream, w);
      long preparationBytes = allocatedBytes() - before;
      report("RQMCCachedNUS (cache)", points, baselineHeap, preparationBytes, m);
   }

   private static void measureDigital(int s, int k, int w, int m, long[] seed) {
      System.gc();
      long baselineHeap = usedHeap();
      long before = allocatedBytes();
      MWC64k3a2 stream = newStream(seed);
      DigitalNetBase2 net = new SobolSequence(k, w, s);
      RQMCCachedNUS points = new RQMCCachedNUS(net,
            new CachedPointSet(net, false), stream);
      long preparationBytes = allocatedBytes() - before;
      report("RQMCCachedNUS (digital)", points, baselineHeap, preparationBytes, m);
   }

   private static void measureHCK(int s, int k, int w, int m, long[] seed) {
      System.gc();
      long baselineHeap = usedHeap();
      long before = allocatedBytes();
      MWC64k3a2 stream = newStream(seed);
      DigitalNetBase2 net = new SobolSequence(k, w, s);
      RQMCDigitalNet2HCK points = new RQMCDigitalNet2HCK(net,
            new CachedPointSet(net, false), stream, w);
      long preparationBytes = allocatedBytes() - before;
      report("RQMCDigitalNet2HCK", points, baselineHeap, preparationBytes, m);
   }

   public static void main(String[] args) {
      int s = 10;
      int k = 22;
      int w = 32;
      int m = 2;
      long[] seed = { 1L, 3L, 4L, 5L };

      java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      if (!(bean instanceof ThreadMXBean)
            || !((ThreadMXBean) bean).isThreadAllocatedMemorySupported())
         throw new IllegalStateException("This JVM does not support thread allocation counts");
      allocationBean = (ThreadMXBean) bean;
      if (!allocationBean.isThreadAllocatedMemoryEnabled())
         allocationBean.setThreadAllocatedMemoryEnabled(true);

      warmUp(s, k, w, seed);

      System.out.printf("s=%d, n=%d, outputBits=%d, m=%d%n", s, 1 << k, w, m);
      System.out.println("Retained heap is approximate; allocated bytes include temporary objects.");
      System.out.printf("%-26s %20s %20s %24s%n", "Method", "Retained heap (KiB)",
            "Prep alloc (KiB)", "Scramble alloc (B/rep)");
      measureLegacy(s, k, w, m, seed);
      measureFK02(s, k, w, m, seed);
      measureCached(s, k, w, m, seed);
      measureDigital(s, k, w, m, seed);
      measureHCK(s, k, w, m, seed);
   }
}
