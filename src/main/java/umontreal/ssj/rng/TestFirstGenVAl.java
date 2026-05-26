package umontreal.ssj.rng;


public class TestFirstGenVAl {
	
	private static final long[] SEED = { 12345L, 12345L, 12345L };

	public static void main(String[] args)  {
    	MWC64k2a2 rng = new MWC64k2a2();
    	rng.setSeed(SEED);

        String[] expected = {
                "12345",
                "10696559420953515965",
                "15833093041707857722",
                "13008444907648650382",
                "8340608399441939748",
                "724505734081829545",
                "17005365746286648484",
                "12481504552091035962",
                "8934609514004007026",
                "9680141491856332893"
        };
        String raw;
        
        boolean goodgen = true;

        for (String e : expected) {
        	raw = Long.toUnsignedString(rng.nextRaw());
//        	System.out.println("e  " +e + "  raw" + raw);
//        	
//            System.out.println(e.equals(raw));
            if(!e.equals(raw)) goodgen = false;
        }
        System.out.println("test for first 10 vals is : " + goodgen );
/////////////////////////////////////////////////////
        System.out.println("-------------------------");
        rng.resetStartStream();

        long sum = 0L;
        int n = 1_000_000;

        for (int i = 0; i < n; i++) {
            sum += rng.nextRaw(); 
        }
        String res = Long.toUnsignedString(sum);
        String expectedSum = "6667913625287060059";  

        System.out.println("the test for the sum of first " +n + " vals is : "+
                expectedSum.equals(res));
        System.out.println("e  " +expectedSum + "  raw  " + res);
///////////////////////////////////////////////////////
        System.out.println("------------------------------");
        rng.resetStartStream();

        n = 1_000_000;
        double dsum = 0.0;

        for (int i = 0; i < n; i++) {
            double u = rng.nextDouble();

            if(u < 0.0) {
            	System.out.print("u is < 0, u = " + u);
            	break;
            };
            if(u >= 1.0) {
            	System.out.print("u is >= 1, u = " + u);
            	break;
            };

            dsum += u;
        }

        double avg = dsum / n;

        if(Math.abs(avg - 0.5) < 0.002) System.out.print("avg is good avg =  " + avg);
        else System.out.print("avg >> 0.5, avg =  " + avg);
    }
}