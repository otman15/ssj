package rqmcexperiments;

import umontreal.ssj.util.Num;


public class SillyTest {
    public static void main(String args[]){
        int k = 10;
        System.out.println("k : " + k);
        System.out.println("exp : " + (32-k));
        double x = 5*Math.pow(2, 32- k); //6, 7, 12, 13, 14, 24,26, 28,
        System.out.println(x);
  
        long logexp = (long) Num.log2(x);
        long logMask = (1L << logexp) - 1L;
        System.out.println(Num.log2(x));
        System.out.println(logMask);
        System.out.println(Long.toBinaryString(logMask));
        long withlong = Long.highestOneBit((long)(x)) - 1;
        System.out.println(Long.toBinaryString(withlong));
        System.out.println(withlong);
    }
}
