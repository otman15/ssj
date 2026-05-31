package rngexperiments;
import java.math.BigInteger;
import java.math.BigDecimal;

public class MWCconstatns {

	

	public static void main(String[] args) {
		  /** First coefficient a1. */
		   long A1 = 193154555888013165L;
		  /** Second coefficient a2. */
		   long A2 = 1966812196490295L;
		   int STREAM_ADVANCE_EXPONENT = 113;
		    int SUBSTREAM_ADVANCE_EXPONENT = 62;
	    System.out.println("For MWCk2a2");
		printFixedJumpConstants( A1,  A2,  STREAM_ADVANCE_EXPONENT,  SUBSTREAM_ADVANCE_EXPONENT);
		
		
		System.out.println("------------For MWCk2a3------------------");
		    A2 = 184698970548483715L;
		   /** Third coefficient a3. */
		    long A3 = 6028691832887L;
		     STREAM_ADVANCE_EXPONENT = 169;
		     SUBSTREAM_ADVANCE_EXPONENT= 118;
		    
		    printFixedJumpConstantsMWCk3( A2,  A3,  STREAM_ADVANCE_EXPONENT,  SUBSTREAM_ADVANCE_EXPONENT);

	}
	private static void printFixedJumpConstants(long A1, long A2, int STREAM_ADVANCE_EXPONENT, int SUBSTREAM_ADVANCE_EXPONENT) {
		   BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
		    BigInteger BI_A1 = BigInteger.valueOf(A1);
		    BigInteger BI_A2 = BigInteger.valueOf(A2); 
		    BigInteger BI_M = BI_A2.multiply(BI_B).add(BI_A1).multiply(BI_B).subtract(BigInteger.ONE); // m = a2*b^2 + a1*b - 1
		    
			   System.out.println("");
			   System.out.println("m = " + BI_M);
			   System.out.println("");

			   BigInteger rho = BI_M.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2));
			   System.out.println("rhho "+  rho);
			// Calculate log2(rho)
			double log2 = (rho.bitLength() - 62) + Math.log(rho.shiftRight(rho.bitLength() - 62).doubleValue()) / Math.log(2);

			System.out.printf("rho = 2^%.2f%n", log2);

			   System.out.println("");
			   
		   BigInteger BI_B_INV = BI_B.modInverse(BI_M); // b^(-1) mod m
		    BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); // b^2
		    BigInteger BI_MAP_X2 = BigInteger.ONE.subtract(BI_A1.multiply(BI_B)); // 1 - a1*b

		    BigInteger STREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT), BI_M); // J = (b^(-1))^(2^STREAM_JUMP_EXPONENT) mod m
		    BigInteger SUBSTREAM_JUMP_MULTIPLIER = BI_B_INV.modPow(BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT), BI_M); // J = (b^(-1))^(2^SUBSTREAM_JUMP_EXPONENT) mod m
		    BigInteger STREAM_K_X2 = STREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X2).mod(BI_M);; // K_x2 = J*(1 - a1*b) mod m
		    BigInteger STREAM_K_X1 = STREAM_JUMP_MULTIPLIER.multiply(BI_B).mod(BI_M); // K_x1 = J*b mod m
		    BigInteger STREAM_K_C  = STREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M); // K_c = J*b^2 mod m
		    BigInteger SUBSTREAM_K_X2 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X2).mod(BI_M); // K_x2 = J*(1 - a1*b) mod m
		    BigInteger SUBSTREAM_K_X1 = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B).mod(BI_M); // K_x1 = J*b mod m
		   BigInteger SUBSTREAM_K_C  = SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M);// K_c = J*b^2 mod m

		   System.out.println("STREAM_K_X2 = " + STREAM_K_X2);
		   System.out.println("STREAM_K_X1 = " + STREAM_K_X1);
		   System.out.println("STREAM_K_C  = " + STREAM_K_C);

		   System.out.println("SUBSTREAM_K_X2 = " + SUBSTREAM_K_X2);
		   System.out.println("SUBSTREAM_K_X1 = " + SUBSTREAM_K_X1);
		   System.out.println("SUBSTREAM_K_C  = " + SUBSTREAM_K_C);
		}
	
	private static void printFixedJumpConstantsMWCk3(long A2, long A3, int STREAM_ADVANCE_EXPONENT, int SUBSTREAM_ADVANCE_EXPONENT) {
		
		    BigInteger BI_B = BigInteger.ONE.shiftLeft(64); // b = 2^64
		    BigInteger BI_B2 = BigInteger.ONE.shiftLeft(128); // b^2
		    BigInteger BI_B3 = BigInteger.ONE.shiftLeft(192); // b^3
		    BigInteger BI_A2 = BigInteger.valueOf(A2);
		    BigInteger BI_A3 = BigInteger.valueOf(A3);
		   BigInteger BI_M =
		         BI_A3.multiply(BI_B).add(BI_A2).multiply(BI_B).multiply(BI_B)
		               .subtract(BigInteger.ONE); // m = a3*b^3 + a2*b^2 - 1
		   
		   System.out.println("");
		   System.out.println("m = " + BI_M);
		   System.out.println("");

		   BigInteger rho = BI_M.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2));
		   System.out.println("rhho "+  rho);
		// Calculate log2(rho)
		double log2 = (rho.bitLength() - 62) + Math.log(rho.shiftRight(rho.bitLength() - 62).doubleValue()) / Math.log(2);

		System.out.printf("rho = 2^%.2f%n", log2);

		   System.out.println("");
		   
		   BigInteger BI_B_INV = BI_B.modInverse(BI_M); // b^(-1) mod m
		    BigInteger BI_MAP_X3 =
		         BigInteger.ONE.subtract(BI_A2.multiply(BI_B2)); // 1 - a2*b^2
		    BigInteger STREAM_JUMP_MULTIPLIER =
		         BI_B_INV.modPow(BigInteger.ONE.shiftLeft(STREAM_ADVANCE_EXPONENT), BI_M);
		    BigInteger SUBSTREAM_JUMP_MULTIPLIER =
		         BI_B_INV.modPow(BigInteger.ONE.shiftLeft(SUBSTREAM_ADVANCE_EXPONENT), BI_M);
		    BigInteger STREAM_K_X3 =
		         STREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X3).mod(BI_M); // K_x3 = J*(1 - a2*b^2) mod m
		   BigInteger STREAM_K_X2 =
		         STREAM_JUMP_MULTIPLIER.multiply(BI_B).mod(BI_M); // K_x2 = J*b mod m
		    BigInteger STREAM_K_X1 =
		         STREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M); // K_x1 = J*b^2 mod m
		   BigInteger STREAM_K_C =
		         STREAM_JUMP_MULTIPLIER.multiply(BI_B3).mod(BI_M); // K_c = J*b^3 mod m
		    BigInteger SUBSTREAM_K_X3 =
		         SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_MAP_X3).mod(BI_M); // K_x3 = J*(1 - a2*b^2) mod m
		    BigInteger SUBSTREAM_K_X2 =
		         SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B).mod(BI_M); // K_x2 = J*b mod m
		    BigInteger SUBSTREAM_K_X1 =
		         SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B2).mod(BI_M); // K_x1 = J*b^2 mod m
		   BigInteger SUBSTREAM_K_C =
		         SUBSTREAM_JUMP_MULTIPLIER.multiply(BI_B3).mod(BI_M); // K_c = J*b^3 mod m

		   System.out.println("STREAM_K_X3 = " + STREAM_K_X3);
		   System.out.println("STREAM_K_X2 = " + STREAM_K_X2);
		   System.out.println("STREAM_K_X1 = " + STREAM_K_X1);
		   System.out.println("STREAM_K_C  = " + STREAM_K_C);
		   
		   System.out.println("SUBSTREAM_K_X3 = " + SUBSTREAM_K_X3);
		   System.out.println("SUBSTREAM_K_X2 = " + SUBSTREAM_K_X2);
		   System.out.println("SUBSTREAM_K_X1 = " + SUBSTREAM_K_X1);
		   System.out.println("SUBSTREAM_K_C  = " + SUBSTREAM_K_C);
		}

}
