//251RDB028 Reinis Delvers
//111RDB111 Aleksis Kaļetovs
//111RDB111 Raimonds Polis
//111RDB111 Izidors Vīķelis

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;


public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String choiseStr;
        String sourceFile, resultFile, firstFile, secondFile;

        encoderReset(); //Initialize starting probabilities

        loop: while (true) {

            System.out.println("Enter command: ");
            choiseStr = sc.next();

            switch (choiseStr) {
                case "comp":
                    System.out.print("source file name: ");
                    sourceFile = sc.next();
                    System.out.print("archive name: ");
                    resultFile = sc.next();
                    comp(sourceFile, resultFile);
                    break;
                case "decomp":
                    System.out.print("archive name: ");
                    sourceFile = sc.next();
                    System.out.print("file name: ");
                    resultFile = sc.next();
                    decomp(sourceFile, resultFile);
                    break;
                case "size":
                    System.out.print("file name: ");
                    sourceFile = sc.next();
                    size(sourceFile);
                    break;
                case "equal":
                    System.out.print("first file name: ");
                    firstFile = sc.next();
                    System.out.print("second file name: ");
                    secondFile = sc.next();
                    System.out.println(equal(firstFile, secondFile));
                    break;
                case "about":
                    about();
                    break;
                case "exit":
                    break loop;
            }
        }

        sc.close();
    }

    public static void comp(String sourceFile, String resultFile) {
        //TODO: implement this method

        //For testing purposes
        rangeEncoder(false, 0, 0, 'A');  //literal
        rangeEncoder(true, 3, 4, 0);     //match
        rangeEncoder(false, 0, 0, 'B');  //literal
        flush();
        System.out.println(outCompressedBytes);
    }

    private static long subRangeStart = 0L;   //Interval start
    private static long range = 0xFFFFFFFFL;    //set 32-bit max value
    private static final int PROBABILITY_INITIALIZER = 1024; //Probability of 0 in scale of 0-2048

    private static int matchProbability = PROBABILITY_INITIALIZER;  //1-bit tree
    private static int[] literalProbability = new int[256];   //8-bit tree
    private static int[] lengthProbability = new int[256];    //8-bit tree
    private static int[] distanceProbability = new int[256];  //8-bit tree

    private static ArrayList<Byte> outCompressedBytes = new ArrayList<>();

    //Resets global values for encoder
    public static void encoderReset() {
        outCompressedBytes.clear();
        subRangeStart = 0L;
        range = 0xFFFFFFFFL;
        matchProbability = PROBABILITY_INITIALIZER;
        Arrays.fill(literalProbability, PROBABILITY_INITIALIZER);   //Sets PROBABILITY_INITIALIZER as value to all array positions
        Arrays.fill(lengthProbability, PROBABILITY_INITIALIZER);    //Sets PROBABILITY_INITIALIZER as value to all array positions
        Arrays.fill(distanceProbability, PROBABILITY_INITIALIZER);  //Sets PROBABILITY_INITIALIZER as value to all array positions
    }

    //Main range encoder
    public static void rangeEncoder(boolean isMatch, int distance, int length, int value) {
        //Match ot literal bit encoding
        encodeFlag(isMatch ? 1 : 0); //literal = 0, match = 1

        //Encode depending on match or literal
        if (isMatch) {
            lengthEncoder(length);
            distanceEncoder(distance);
        } else {
            literalEncoder(value);
        }
    }

    //Match or literal flag encoder
    public static void encodeFlag(int bit) {
        int probability = matchProbability;
        long subRange = (range >>> 11) * probability;
        if (bit == 0) {
            range = subRange;
            matchProbability += (2048 - probability) >>> 5;
        } else {
            subRangeStart += subRange;
            range -= subRange;
            matchProbability -= probability >>> 5;
        }

        //Return stable bytes
        while ((range < 0x01000000)) {
            outCompressedBytes.add((byte)(subRangeStart >> 24));
            range <<= 8;
            subRangeStart <<= 8;
        }
    }

    //Literal value encoder
    public static void literalEncoder(int value) {
        int bitTreeIndex = 1; //bit tree position index root start is 1
        for (int i = 7; i >= 0; i--) {
            int bit = (value >>> i) & 1;
            int probability = literalProbability[bitTreeIndex];
            long subRange = (range >>> 11) * probability;

            if (bit == 0) {
                range = subRange;
                literalProbability[bitTreeIndex] += (2048 - probability) >>> 5;
                bitTreeIndex <<= 1;   //go left int bit tree
            } else {
                subRangeStart += subRange;
                range -= subRange;
                literalProbability[bitTreeIndex] -= probability >>> 5;
                bitTreeIndex = (bitTreeIndex << 1) | 1; //go right int bit tree
            }

            //Return stable bytes
            while ((range < 0x01000000)) {
                outCompressedBytes.add((byte)(subRangeStart >> 24));
                range <<= 8;
                subRangeStart <<= 8;
            }
        }
    }

    //Match length value encoder
    public static void lengthEncoder(int length) {
        int len = length - 2;   //min match length = 2 subRangeStarter numbers
        int bitTreeIndex = 1;   //bit tree position index root start is 1
        for (int i = 7; i >= 0; i--) {
            int bit = (len >>> i) & 1;
            int probability= lengthProbability[bitTreeIndex];
            long subRange = (range >>> 11) * probability;

            if (bit == 0) {
                range = subRange;
                lengthProbability[bitTreeIndex] += (2048 - probability) >>> 5;
                bitTreeIndex <<= 1;
            } else {
                subRangeStart += subRange;
                range -= subRange;
                lengthProbability[bitTreeIndex] -= probability >>> 5;
                bitTreeIndex = (bitTreeIndex << 1) | 1;
            }

            //Return stable bytes
            while ((range < 0x01000000)) {
                outCompressedBytes.add((byte)(subRangeStart >> 24));
                range <<= 8;
                subRangeStart <<= 8;
            }
        }
    }

    //Match distance value encoder
    public static void distanceEncoder(int distance) {
        int bitTreeIndex = 1;    //bit tree position index root start is 1
        for (int i = 7; i >= 0; i--) {
            int bit = (distance >>> i) & 1;
            int probability= distanceProbability[bitTreeIndex];
            long subRange = (range >>> 11) * probability;

            if (bit == 0) {
                range = subRange;
                distanceProbability[bitTreeIndex] += (2048 - probability) >>> 5;
                bitTreeIndex <<= 1;
            } else {
                subRangeStart += subRange;
                range -= subRange;
                distanceProbability[bitTreeIndex] -= probability >>> 5;
                bitTreeIndex = (bitTreeIndex << 1) | 1;
            }

            //Return stable bytes
            while ((range < 0x01000000)) {
                outCompressedBytes.add((byte)(subRangeStart >> 24));
                range <<= 8;
                subRangeStart <<= 8;
            }
        }
    }

    //After encoding everything taking the last bit value
    public static void flush() {
        for (int i = 0; i < 4; i++) {
            outCompressedBytes.add((byte)(subRangeStart >> 24));
            subRangeStart <<= 8;
        }
    }

    public static void decomp(String sourceFile, String resultFile) {
        //TODO: implement this method
    }

    public static void size(String sourceFile) {
        try {
            FileInputStream f = new FileInputStream(sourceFile);
            System.out.println("size: " + f.available());
            f.close();
        }
        catch (IOException ex) {
            System.out.println(ex.getMessage());
        }

    }

    public static boolean equal(String firstFile, String secondFile) {
        try {
            FileInputStream f1 = new FileInputStream(firstFile);
            FileInputStream f2 = new FileInputStream(secondFile);
            int k1, k2;
            byte[] buf1 = new byte[1000];
            byte[] buf2 = new byte[1000];
            do {
                k1 = f1.read(buf1);
                k2 = f2.read(buf2);
                if (k1 != k2) {
                    f1.close();
                    f2.close();
                    return false;
                }
                for (int i=0; i<k1; i++) {
                    if (buf1[i] != buf2[i]) {
                        f1.close();
                        f2.close();
                        return false;
                    }

                }
            } while (!(k1 == -1 && k2 == -1));
            f1.close();
            f2.close();
            return true;
        }
        catch (IOException ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

    public static void about() {
        //TODO insert information about authors
        System.out.println("251RDB028 Reinis Delvers");
        System.out.println("111RDB111 Aleksis Kaļetovs");
        System.out.println("111RDB111 Raimonds Polis");
        System.out.println("111RDB111 Izidors Vīķelis");

    }
}