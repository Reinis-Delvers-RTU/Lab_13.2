//251RDB028 Reinis Delvers
//111RDB111 Aleksis Kaļetovs
//111RDB111 Raimonds Polis

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    //
    //MAIN
    //
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String choiseStr;
        String sourceFile, resultFile, firstFile, secondFile;

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
                case "test":
                    comp("test.txt", "t.txt");
                    decomp("t.txt", "tt.txt");
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

    //
    //COMP MATCH FINDER
    //
    private static final int DICT_SIZE = 1024;

    private static final int MAX_DISTANCE = 255;
    private static final int MAX_MATCH_LEN = 255;
    // Encoder dictionary
    private static byte[] dictionary = new byte[DICT_SIZE];
    private static int dictPos = 0;
    private static int dictFill = 0;

    private static void dictPut(byte b) {
        dictionary[dictPos] = b;
        dictPos = (dictPos + 1) % DICT_SIZE;
        if (dictFill < DICT_SIZE) {
            dictFill++;
        }
    }

    private static void resetDictionary() {
        Arrays.fill(dictionary, (byte) 0);
        dictPos = 0;
        dictFill = 0;
    }

    // Reads the entire source file into a byte array.
    private static byte[] readAllBytes(String sourceFile) {
        try (FileInputStream in = new FileInputStream(sourceFile);
            ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buf = new byte[4096];
            int k;
            while ((k = in.read(buf)) != -1) {
                baos.write(buf, 0, k);
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            System.out.println("Error reading source file: " + ex.getMessage());
            return null;
        }
    }

    // Walks through all input bytes and encodes them as either match or literal
    private static void encodeInput(byte[] input) {
        int n = input.length;
        int i = 0;

        while (i < n) {
            if (i == n - 1) {
                encodeLiteral(input[i]);
                i++;
                continue;
            }

            int[] match = findBestMatch(input, i);
            int bestLen = match[0];
            int bestDist = match[1];

            if (bestLen >= 2) {
                encodeMatch(input, i, bestLen, bestDist);
                i += bestLen;
            } else {
                encodeLiteral(input[i]);
                i++;
            }
        }
    }

    // Tries to find the best (longest) match starting at position 'pos' in 'input'.
    private static int[] findBestMatch(byte[] input, int pos) {
        int n = input.length;
        int bestLen = 0;
        int bestDist = 0;

        if (pos + 1 >= n) {
            return new int[]{0, 0};
        }

        byte b0 = input[pos];
        byte b1 = input[pos + 1];

        int maxBack = Math.min(MAX_DISTANCE, pos);

        for (int dist = 1; dist <= maxBack; dist++) {
            int j = pos - dist;
            if (j < 0 || j + 1 >= n) continue;

            if (input[j] != b0 || input[j + 1] != b1) continue;

            int len = 2;
            while (len < MAX_MATCH_LEN &&
                pos + len < n &&
                j + len < n &&
                input[pos + len] == input[j + len]) {
                len++;
            }

            if (len > bestLen) {
                bestLen = len;
                bestDist = dist;
                if (bestLen == MAX_MATCH_LEN) break;
            }
        }

        return new int[]{bestLen, bestDist};
    }

    // Encodes a single byte as a literal using rangeEncoder.
    private static void encodeLiteral(byte b) {
        rangeEncoder(false, 0, 0, b & 0xFF);
        dictPut(b);
    }

    // Encodes a match given by (distance, length), starting at 'pos' in 'input'. Calls rangeEncoder as a match and then pushes every matched byte into the dictionary so that future matches can refer to it.
    private static void encodeMatch(byte[] input, int pos, int length, int distance) {
        rangeEncoder(true, distance, length, 0);
        for (int k = 0; k < length; k++) {
            dictPut(input[pos + k]);
        }
    }

    // Writes all bytes collected in outCompressedBytes to the given file.
    private static void writeCompressed(String resultFile) {
        try (FileOutputStream out = new FileOutputStream(resultFile)) {
            for (byte b : outCompressedBytes) {
                out.write(b);
            }
        } catch (IOException ex) {
            System.out.println("Error writing compressed file: " + ex.getMessage());
        }
    }

    //
    //COMP
    //
    public static void comp(String sourceFile, String resultFile) {
        System.out.print("Compresion started");
        encoderReset();
        resetDictionary();

        byte[] input = readAllBytes(sourceFile);
        if (input == null) return;

        encodeInput(input);
        flush();
        writeCompressed(resultFile);
        System.out.print("Compresion complete");
    }


    //
    //COMP RANGE ENCODER
    //
    private static long subRangeStart = 0L;   //Interval start
    private static long range = 0xFFFFFFFFL;    //set 32-bit max value
    private static final int PROBABILITY_INITIALIZER = 1024; //Probability of 0 in scale of 0-2048

    private static int matchProbability = PROBABILITY_INITIALIZER;  //1-bit tree
    private static int[] literalProbability = new int[512];   //8-bit tree
    private static int[] lengthProbability = new int[512];    //8-bit tree
    private static int[] distanceProbability = new int[512];  //8-bit tree

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
            normalizeEncoder();
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
            normalizeEncoder();
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
            normalizeEncoder();
        }
    }

    public static void normalizeEncoder() {
        //Return stable bytes
        while ((range < 0x01000000)) {
            outCompressedBytes.add((byte)(subRangeStart >> 24));
            range <<= 8;
            subRangeStart <<= 8;
        }
    }

    //After encoding everything taking the last bit value
    public static void flush() {
        for (int i = 0; i < 4; i++) {
            outCompressedBytes.add((byte)(subRangeStart >> 24));
            subRangeStart <<= 8;
        }
    }

    //
    //DECOMP RANGE DECODER
    //
    public static void decomp(String sourceFile, String resultFile) {
        try {
            // read compressed data
            byte[] input = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(sourceFile));

            // output buffer
            ArrayList<Byte> out = new ArrayList<>();

            // reset the coder state
            encoderReset();

            class Decoder {
                int inPos = 0;
                long code = 0;

                Decoder() {
                    // load first 4 bytes into code
                    for (int i = 0; i < 4; i++) {
                        code = (code << 8) | (input[inPos++] & 0xFF);
                    }
                }

                void normalize() {
                    while (range < 0x01000000) {
                        if (inPos < input.length) {
                            code = ((code << 8) | (input[inPos++] & 0xFF)) & 0xFFFFFFFFL;
                        } else {
                            code = (code << 8) & 0xFFFFFFFFL;
                        }
                        range <<= 8;
                        subRangeStart <<= 8;
                    }
                }

                int decodeFlag() {
                    int p = matchProbability;
                    long bound = (range >>> 11) * p;
                    int bit;

                    if ((code - subRangeStart) < bound) {
                        range = bound;
                        matchProbability += (2048 - p) >>> 5;
                        bit = 0;
                    } else {
                        subRangeStart += bound;
                        range -= bound;
                        matchProbability -= p >>> 5;
                        bit = 1;
                    }

                    normalize();
                    return bit;
                }

                int decodeBit(int[] prob, int idx) {
                    int p = prob[idx];
                    long bound = (range >>> 11) * p;
                    int bit;

                    if ((code - subRangeStart) < bound) {
                        range = bound;
                        prob[idx] += (2048 - p) >>> 5;
                        bit = 0;
                    } else {
                        subRangeStart += bound;
                        range -= bound;
                        prob[idx] -= p >>> 5;
                        bit = 1;
                    }

                    normalize();
                    return bit;
                }

                int decodeLiteral() {
                    int idx = 1;
                    int value = 0;
                    for (int i = 7; i >= 0; i--) {
                        int b = decodeBit(literalProbability, idx);
                        value |= b << i;
                        idx = (idx << 1) | b;
                    }
                    return value;
                }
                //mind = controlled
                int decodeLength() {
                    int idx = 1;
                    int value = 0;
                    for (int i = 7; i >= 0; i--) {
                        int b = decodeBit(lengthProbability, idx);
                        value |= b << i;
                        idx = (idx << 1) | b;
                    }
                    return value + 2;
                }

                int decodeDistance() {
                    int idx = 1;
                    int value = 0;
                    for (int i = 7; i >= 0; i--) {
                        int b = decodeBit(distanceProbability, idx);
                        value |= b << i;
                        idx = (idx << 1) | b;
                    }
                    return value;
                }
            }

            // create decoder instance
            Decoder d = new Decoder();

            // --- MAIN LOOP ---
            while (d.inPos < input.length) {
                int isMatch = d.decodeFlag();

                if (isMatch == 0) {
                    int lit = d.decodeLiteral();
                    out.add((byte) lit);
                } else {
                    int len = d.decodeLength();
                    int dist = d.decodeDistance();

                    for (int i = 0; i < len; i++) {
                        byte b = out.get(out.size() - dist);
                        out.add(b);
                    }
                }
            }

            // write decompressed file
            byte[] outputBytes = new byte[out.size()];
            for (int i = 0; i < out.size(); i++) outputBytes[i] = out.get(i);

            java.nio.file.Files.write(java.nio.file.Paths.get(resultFile), outputBytes);
            System.out.println("Decompression complete.");

        } catch (Exception ex) {
            System.out.println("Decompression error: " + ex.getMessage());
        }
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

    }
}