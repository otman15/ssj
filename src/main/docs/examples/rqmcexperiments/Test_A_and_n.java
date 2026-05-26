package rqmcexperiments;
import umontreal.ssj.hups64.Rank1Lattice;
import umontreal.ssj.rng.LFSR258;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.MWC32k2a;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.MWC64k3a2;
import umontreal.ssj.rng.RandomStream;





public class Test_A_and_n {
	

	public static void main(String[] args) {
		
		
		   RandomStream stream = new MWC64k2a2();
		   //RandomStream stream = new MRG32k3a();
		
		//TestRngOnly(stream);
		   testRandomPrimeAndVector(stream);

	}
	
//	private static void TestRngOnly(RandomStream stream) {
//		   int[] counts = new int[5];
//
//		   for (int i = 0; i < 100000000; i++) {
//		      int x = stream.nextInt(0, 4);
//		      counts[x]++;
//		   }
//
//		   System.out.println("Counts for stream.nextInt(0, 4):");
//		   for (int i = 0; i < counts.length; i++) {
//		      System.out.println(i + " appeared " + counts[i] + " times");
//		   }
//		}
	
	
	
	private static void testRandomPrimeAndVector(RandomStream stream ) {
		   int k = 5;
		   int s = 4;
		   int m = 100000;

		   int nmin = 1 << (k - 1); 
		   int nmax = 1 << k;       



		   Rank1Lattice pLat = new Rank1Lattice(s);

		   int[] primes = {17, 19, 23, 29, 31};
		   int[] counts = new int[primes.length];

		   System.out.println("Test random prime n and random vector a");
		   System.out.println("k = " + k + ", nmin = " + nmin + ", nmax = " + nmax);
		   System.out.println("s = " + s + ", randomizations = " + m);
		   System.out.println();

		   for (int r = 0; r < m; r++) {
		      pLat.setRandomAandn(nmin, nmax, stream);

		      int p = pLat.getNumPoints(); // get the prime number generated
		      int[] a = pLat.getAs();  // get the generating vector generated

		      
		      // Add this prime to the counts.
		      for (int i = 0; i < primes.length; i++) {
		         if (p == primes[i]) {
		            counts[i]++;
		            break;
		         }
		      }

		      System.out.print("sim: "+(r+1) +", p = " + p);
		      System.out.print(", a = [");

		      for (int j = 0; j < s; j++) {
		         System.out.print(a[j]);
		         if (j < s - 1)
		            System.out.print(", ");
				else
					System.out.println("]");
		      }

		      

		      // Check that p is one of the expected primes.
		      boolean expectedPrimes = false;
		      for (int q : primes) {
		         if (p == q) {
		        	 expectedPrimes = true;
		            break;
		         }
		      }

		      if (!expectedPrimes) {
		         throw new RuntimeException("Wrong p generated: p = " + p);
		      }

		      // Check that coordinates are valid.
		      for (int j = 0; j < s; j++) {
		         if (a[j] < 1 || a[j] >= p) {
		            throw new RuntimeException(
		               "Wrong a[" + j + "] = " + a[j] + " for p = " + p
		            );
		         }
		      }
		   }

		   System.out.println();
		   System.out.println("Counts for each prime:");

		   for (int i = 0; i < primes.length; i++) {
		      System.out.println("p = " + primes[i] + " appeared " + counts[i] + " times");
		   }
		}

}
