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
                    System.out.println(equal("test.txt", "tt.txt")); // Compare original vs decompressed
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
    // Binary tree for match finding
    private static int[] btLeft;
    private static int[] btRight;
    private static int btRoot;
    private static final int BT_MAX_SEARCH_DEPTH = 64;


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

    // Inserts position 'pos' into the binary search tree used for match finding.
    private static void insertPos(byte[] input, int pos) {
        if (btRoot == -1) {
            btRoot = pos;
            return;
        }

        int n = input.length;
        int cur = btRoot;

        while (true) {
            int cmp = compareForTree(input, pos, cur, n);

            if (cmp < 0) {
                if (btLeft[cur] == -1) {
                    btLeft[cur] = pos;
                    return;
                }
                cur = btLeft[cur];
            } else {
                if (btRight[cur] == -1) {
                    btRight[cur] = pos;
                    return;
                }
                cur = btRight[cur];
            }
        }
    }

    // Computes how many bytes match starting at pos1 and pos2, limited by MAX_MATCH_LEN and the end of the input.
    private static int calcMatchLen(byte[] input, int pos1, int pos2, int n) {
        int maxLen = Math.min(MAX_MATCH_LEN, n - Math.max(pos1, pos2));
        int len = 0;
        while (len < maxLen && input[pos1 + len] == input[pos2 + len]) {
            len++;
        }
        return len;
    }

    // Compares the sequences starting at pos1 and pos2.
    private static int compareForTree(byte[] input, int pos1, int pos2, int n) {
        if (pos1 == pos2) return 0;

        int maxLen = Math.min(MAX_MATCH_LEN, n - Math.max(pos1, pos2));
        int i = 0;
        while (i < maxLen) {
            int b1 = input[pos1 + i] & 0xFF;
            int b2 = input[pos2 + i] & 0xFF;
            if (b1 != b2) {
                return b1 - b2;
            }
            i++;
        }

        return pos1 - pos2;
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

    // Walks through all input bytes and encodes them as either literals or matches.
    private static void encodeInput(byte[] input) {
        int n = input.length;
        int i = 0;

        btLeft = new int[n];
        btRight = new int[n];
        Arrays.fill(btLeft, -1);
        Arrays.fill(btRight, -1);
        btRoot = -1;

        while (i < n) {
            if (i == n - 1) {
                encodeLiteral(input[i]);
                insertPos(input, i);
                i++;
                continue;
            }

            int[] match = findBestMatch(input, i);
            int bestLen = match[0];
            int bestDist = match[1];

            if (bestLen >= 2) {
                encodeMatch(input, i, bestLen, bestDist);

                for (int p = 0; p < bestLen; p++) {
                    insertPos(input, i + p);
                }

                i += bestLen;
            } else {
                encodeLiteral(input[i]);
                insertPos(input, i);
                i++;
            }
        }
    }


    // Searches the binary tree for the best (longest) match starting at 'pos'.
    private static int[] findBestMatch(byte[] input, int pos) {
        int n = input.length;
        int bestLen = 0;
        int bestDist = 0;

        int cur = btRoot;
        int depth = 0;

        while (cur != -1 && depth++ < BT_MAX_SEARCH_DEPTH) {
            int curPos = cur;
            int dist = pos - curPos;

            if (dist > 0 && dist <= MAX_DISTANCE) {
                int matchLen = calcMatchLen(input, pos, curPos, n);

                if (matchLen >= 2 && matchLen > bestLen) {
                    bestLen = matchLen;
                    bestDist = dist;

                    if (bestLen == MAX_MATCH_LEN) {
                        break;
                    }
                }
            }

            int cmp = compareForTree(input, pos, curPos, n);
            if (cmp < 0) {
                cur = btLeft[cur];
            } else {
                cur = btRight[cur];
            }
        }

        return new int[]{bestLen, bestDist};
    }


    // Encodes a single byte as a literal using rangeEncoder.
    private static void encodeLiteral(byte b) {
        System.out.println("value: " + b);
        rangeEncoder(false, 0, 0, b & 0xFF);
        dictPut(b);
    }

    // Encodes a match given by (distance, length), starting at 'pos' in 'input'. Calls rangeEncoder as a match and then pushes every matched byte into the dictionary so that future matches can refer to it.
    private static void encodeMatch(byte[] input, int pos, int length, int distance) {
        System.out.println("distance: " + distance + "length: " + length);
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
        System.out.println("started compresing");
        resetEncoder();
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
    private static long encoderSubRangeStart = 0L;   //Interval start
    private static long encoderRange = 0xFFFFFFFFL;    //set 32-bit max value
    private static final int PROBABILITY_INITIALIZER = 1024; //Probability of 0 in scale of 0-2048

    private static int encoderMatchProbability = PROBABILITY_INITIALIZER;  //1-bit tree
    private static int[] encoderLiteralProbability = new int[512];   //8-bit tree
    private static int[] encoderLengthProbability = new int[512];    //8-bit tree
    private static int[] encoderDistanceProbability = new int[512];  //8-bit tree

    private static ArrayList<Byte> outCompressedBytes = new ArrayList<>();

    //Resets global values for encoder
    public static void resetEncoder() {
        outCompressedBytes.clear();
        encoderSubRangeStart = 0L;
        encoderRange = 0xFFFFFFFFL;
        encoderMatchProbability = PROBABILITY_INITIALIZER;
        Arrays.fill(encoderLiteralProbability, PROBABILITY_INITIALIZER);   //Sets PROBABILITY_INITIALIZER as value to all array positions
        Arrays.fill(encoderLengthProbability, PROBABILITY_INITIALIZER);    //Sets PROBABILITY_INITIALIZER as value to all array positions
        Arrays.fill(encoderDistanceProbability, PROBABILITY_INITIALIZER);  //Sets PROBABILITY_INITIALIZER as value to all array positions
    }

    //Main range encoder
    public static void rangeEncoder(boolean isMatch, int distance, int length, int value) {
        //Match ot literal bit encoding
        flagEncoder(isMatch ? 1 : 0); //literal = 0, match = 1

        //Encode depending on match or literal
        if (isMatch) {
            lengthEncoder(length);
            distanceEncoder(distance);
        } else {
            literalEncoder(value);
        }
    }

    //Match or literal flag encoder
    public static void flagEncoder(int bit) {
        int probability = encoderMatchProbability;
        long subRange = (encoderRange >>> 11) * probability;
        if (bit == 0) {
            encoderRange = subRange;
            encoderMatchProbability += (2048 - probability) >>> 5;
        } else {
            encoderSubRangeStart += subRange;
            encoderRange -= subRange;
            encoderMatchProbability -= probability >>> 5;
        }

        //Return stable bytes
        while ((encoderRange < 0x01000000)) {
            outCompressedBytes.add((byte)(encoderSubRangeStart >> 24));
            encoderRange <<= 8;
            encoderSubRangeStart <<= 8;
        }
    }

    //Literal value encoder
    public static void literalEncoder(int value) {
        int bitTreeIndex = 1; //bit tree position index root start is 1
        for (int i = 7; i >= 0; i--) {
            int bit = (value >>> i) & 1;
            int probability = encoderLiteralProbability[bitTreeIndex];
            long subRange = (encoderRange >>> 11) * probability;

            if (bit == 0) {
                encoderRange = subRange;
                encoderLiteralProbability[bitTreeIndex] += (2048 - probability) >>> 5;
                bitTreeIndex <<= 1;   //go left int bit tree
            } else {
                encoderSubRangeStart += subRange;
                encoderRange -= subRange;
                encoderLiteralProbability[bitTreeIndex] -= probability >>> 5;
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
            int probability= encoderLengthProbability[bitTreeIndex];
            long subRange = (encoderRange >>> 11) * probability;

            if (bit == 0) {
                encoderRange = subRange;
                encoderLengthProbability[bitTreeIndex] += (2048 - probability) >>> 5;
                bitTreeIndex <<= 1;
            } else {
                encoderSubRangeStart += subRange;
                encoderRange -= subRange;
                encoderLengthProbability[bitTreeIndex] -= probability >>> 5;
                bitTreeIndex = (bitTreeIndex << 1) | 1;
            }
            normalizeEncoder();
        }
    }

    //Match distance value encoder
    public static void distanceEncoder(int distance) {
        int dist = distance - 1; // minimum distance =1
        int bitTreeIndex = 1;    // bit tree position index root start is 1
        for (int i = 7; i >= 0; i--) {
            int bit = (dist >>> i) & 1;
            int probability= encoderDistanceProbability[bitTreeIndex];
            long subRange = (encoderRange >>> 11) * probability;

            if (bit == 0) {
                encoderRange = subRange;
                encoderDistanceProbability[bitTreeIndex] += (2048 - probability) >>> 5;
                bitTreeIndex <<= 1;
            } else {
                encoderSubRangeStart += subRange;
                encoderRange -= subRange;
                encoderDistanceProbability[bitTreeIndex] -= probability >>> 5;
                bitTreeIndex = (bitTreeIndex << 1) | 1;
            }
            normalizeEncoder();
        }
    }

    public static void normalizeEncoder() {
        //Return stable bytes
        while ((encoderRange < 0x01000000)) {
            outCompressedBytes.add((byte)(encoderSubRangeStart >> 24));
            encoderRange <<= 8;
            encoderSubRangeStart <<= 8;
        }
    }

    //After encoding everything taking the last bit value
    public static void flush() {
        for (int i = 0; i < 4; i++) {
            outCompressedBytes.add((byte)(encoderSubRangeStart >> 24));
            encoderSubRangeStart <<= 8;
        }
    }

    //
    //DECOMP RANGE DECODER
    //

    private static long decoderSubRangeStart = 0L; // current code
    private static long decoderRange = 0xFFFFFFFFL;

    private static int decoderMatchProbability = PROBABILITY_INITIALIZER;
    private static int[] decoderLiteralProbability = new int[512];
    private static int[] decoderLengthProbability = new int[512];
    private static int[] decoderDistanceProbability = new int[512];
    private static byte[] inCompressedBytes;
    private static int inputPos = 0;
    public static void decomp(String sourceFile, String resultFile) {
        try {
            // read compressed data
            byte[] input = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(sourceFile));

            resetEncoder();
            resetDictionary();


            // Decoder class for range decoding
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
                    while (decoderRange < 0x01000000) {
                        if (inPos < input.length) {
                            code = ((code << 8) | (input[inPos++] & 0xFF)) & 0xFFFFFFFFL;
                        } else {
                            code = (code << 8) & 0xFFFFFFFFL;
                        }
                        decoderRange <<= 8;
                        decoderSubRangeStart <<= 8;
                    }
                }

                int decodeFlag() {
                    int p = decoderMatchProbability;
                    long bound = (decoderRange >>> 11) * p;
                    int bit;

                    if ((code - decoderSubRangeStart) < bound) {
                        decoderRange = bound;
                        decoderMatchProbability += (2048 - p) >>> 5;
                        bit = 0;
                    } else {
                        decoderSubRangeStart += bound;
                        decoderRange -= bound;
                        decoderMatchProbability -= p >>> 5;
                        bit = 1;
                    }

                    normalize();
                    return bit;
                }

                int decodeBit(int[] prob, int idx) {
                    int p = prob[idx];
                    long bound = (decoderRange >>> 11) * p;
                    int bit;

                    if ((code - decoderSubRangeStart) < bound) {
                        decoderRange = bound;
                        prob[idx] += (2048 - p) >>> 5;
                        bit = 0;
                    } else {
                        decoderSubRangeStart += bound;
                        decoderRange -= bound;
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
                        int b = decodeBit(decoderLiteralProbability, idx);
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
                        int b = decodeBit(decoderLengthProbability, idx);
                        value |= b << i;
                        idx = (idx << 1) | b;
                    }
                    return value + 2;
                }

                int decodeDistance() {
                    int idx = 1;
                    int value = 0;
                    for (int i = 7; i >= 0; i--) {
                        int b = decodeBit(decoderDistanceProbability, idx);
                        value |= b << i;
                        idx = (idx << 1) | b;
                    }
                    return value + 1;
                }
            }

            // create decoder instance
            Decoder d = new Decoder();

            // --- MAIN LOOP ---
            ArrayList<Byte> out = new ArrayList<>();
            for (int s = 0; s < out.size(); s++) {
                int isMatch = d.decodeFlag();

                if (isMatch == 0) {
                    int lit = d.decodeLiteral();
                    byte b = (byte) lit;
                    out.add(b);
                    dictPut(b); // push literal to global dictionary
                } else {
                    int len = d.decodeLength();
                    int dist = d.decodeDistance();

                    if (dist <= 0 || dist > dictFill) {
                        throw new RuntimeException("Invalid distance: " + dist + " at position " + out.size());
                    }

                    for (int i = 0; i < len; i++) {
                        // take byte from dictionary sliding window
                        byte b = dictionary[(dictPos - dist + DICT_SIZE) % DICT_SIZE];
                        out.add(b);
                        dictPut(b); // update dictionary
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


    //
    // SIZE
    //
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

    //
    // EQUAL
    //
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

    //
    // ABOUT
    //
    public static void about() {
        //TODO insert information about authors
        System.out.println("251RDB028 Reinis Delvers");
        System.out.println("111RDB111 Aleksis Kaļetovs");
        System.out.println("111RDB111 Raimonds Polis");

    }
}