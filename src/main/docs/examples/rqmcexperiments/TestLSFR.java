package rqmcexperiments;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.util.Chrono;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;


public class TestLSFR {
	
	public static void main(String[] args) {
		
		
//		
//		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
//
//		long start = bean.getCurrentThreadCpuTime();
		   int m=100000;
		   int n=100;
		   System.out.println("With long,  " + "n = " + n + ", m = " + m);
		Chrono timer = new Chrono();

		TestRngOnly(new LFSR258(), n, m);
		System.out.println(
	            "\nTotal time for Int: " + timer.format() + "\n=========================================== \n");
	   }
	

//		long end = bean.getCurrentThreadCpuTime();
//
//		System.out.println("CPU time = " + ((end - start) / 1.0e9) + " seconds");
//		System.out.println("With MRG32k3a");
//		TestRngOnly(new MRG32k3a());
//CPU time = 241.487985706 seconds nextInt(0, n-1) for LFSR258
//CPU time = 241.035011479 seconds intL}
//	
//	With <<32,  n = 100000, m = 1000000000
//
//			Total time for Int: 0:0:20.87
//			=========================================== 
//	With <<32,  n = 100000, m = 1000000000
//
//			Total time for Int: 0:0:20.87
//			=========================================== 
	
	
	
	private static void TestRngOnly(RandomStream stream, int n, int m) {
		   int[] counts = new int[n];

		   for (int i= 0; i < m; i++) {
		      int x = stream.nextInt(0, n-1);
		      counts[x]++;
		   }

//		   System.out.println("Counts for stream.nextInt(0, n):");
//		   for (int i = 0; i < counts.length; i++) {
//		      System.out.println(i + " appeared " + counts[i] + " times");
//		   }
		}

}
