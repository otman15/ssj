package rngexperiments;
import umontreal.ssj.rng.MWC64k2a2;
import umontreal.ssj.rng.NewMWC64k2a2;

public class TestMWC64k2a2 {
    static long tmp;
    static long tottmp;
    static long sum;
    
    /*
     * old:     MWC64k2a2      26.314957    5495659462671017987
  NewMWC64k2a2      34.697047    5495659462671017987
     */

	public static void main(String[] args) {
		long n = 10L * 1000L * 1000L * 1000L; // One billion
		//long n = 1000_000_000;
		
        long[] w = new long[5000000];
        for (int i = 0; i < 5000000; i++) {
            w[i] = i*i + 5*i;
        }
		
        sum = 0L;
        MWC64k2a2 stream = new MWC64k2a2();
        
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += stream.nextRaw(); // turn ro public to execute code 
        }
        tmp = System.nanoTime() - tmp;
        //System.out.println("MWC64k2a2  " +  tmp/10000.0 + "  sum:" + sum);
        System.out.printf("%16s%13.6f    %18s%n",
        		"MWC64k2a2  ", tmp / 1.0e9, Long.toUnsignedString(sum));
        
        
        NewMWC64k2a2 stream2 = new NewMWC64k2a2();
        
        sum = 0L;
  
        tmp = System.nanoTime();
        for (long i = 0; i < n; i++) {
            sum += stream2.nextNumber();
        }
        tmp = System.nanoTime() - tmp;
 //       System.out.println("NewMWC64k2a2  " +  tmp/10000.0 + "  sum:" + sum);
        System.out.printf("%16s%13.6f    %18s%n",
        		"NewMWC64k2a2  " , tmp / 1.0e9, Long.toUnsignedString(sum));

	}

}
