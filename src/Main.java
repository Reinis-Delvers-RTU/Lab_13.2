//251RDB028 Reinis Delvers
//111RDB111 Aleksis Kaļetovs
//111RDB111 Raimonds Polis

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    //
    //MAIN
    //
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String choiseStr;
        String sourceFile, resultFile, firstFile, secondFile;
        boolean hasrun = false;
        loop: while (true) {
            if (hasrun == false) {
                choiseStr = "test";
                hasrun = true;
            }
            else {
                System.out.println("Enter command: ");
                choiseStr = sc.next();
            }

            switch (choiseStr) {
                case "comp":
                    System.out.println("source file name: ");
                    sourceFile = sc.next();
                    System.out.println("archive name: ");
                    resultFile = sc.next();
                    comp(sourceFile, resultFile);
                    break;
                case "decomp":
                    System.out.println("archive name: ");
                    sourceFile = sc.next();
                    System.out.println("file name: ");
                    resultFile = sc.next();
                    decomp(sourceFile, resultFile);
                    break;
                case "size":
                    System.out.println("file name: ");
                    sourceFile = sc.next();
                    size(sourceFile);
                    break;
                case "equal":
                    System.out.println("first file name: ");
                    firstFile = sc.next();
                    System.out.println("second file name: ");
                    secondFile = sc.next();
                    System.out.println(equal(firstFile, secondFile));
                    break;
                case "test":
                    System.out.println("---------------------------------------");
                    for (int i = 1; i < 5; i++) {
                        System.out.println("File: " + i);
                        comp("File" + i + ".html", "t.txt");
                        decomp("t.txt", "tt.txt");
                        System.out.print("Are files equal: ");
                        System.out.println(equal("File" + i + ".html", "tt.txt")); // Compare original vs decompressed
                        int originalFileSize = size("File" + i + ".html");
                        int compFileSize = size("t.txt");
                        size("tt.txt");
                        System.out.println("ratio: " + (float) originalFileSize / compFileSize );
                        System.out.println("---------------------------------------");
                    }
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
    private static final int DICT_SIZE = 65536;
    private static final int MAX_DISTANCE = 65535;
    private static final int MAX_MATCH_LEN = 511;
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
    private static void compInput(byte[] input) {
        int n = input.length;
        int i = 0;

        btLeft = new int[n];
        btRight = new int[n];
        Arrays.fill(btLeft, -1);
        Arrays.fill(btRight, -1);
        btRoot = -1;

        while (i < n) {
            if (i == n - 1) {
                literalComp(input[i]);
                insertPos(input, i);
                i++;
                continue;
            }

            int[] match = findBestMatch(input, i);
            int bestLen = match[0];
            int bestDist = match[1];

            if (bestLen >= 3) {
                matchComp(input, i, bestLen, bestDist);

                for (int p = 0; p < bestLen; p++) {
                    insertPos(input, i + p);
                }

                i += bestLen;
            } else {
                literalComp(input[i]);
                insertPos(input, i);
                i++;
            }
        }
    }

    private static ArrayList<int[]> allMatches = new ArrayList<>();

    // Searches the binary tree for the best (longest) match starting at 'pos'.
    private static int[] findBestMatch(byte[] input, int pos) {
        allMatches.clear();
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

                if (matchLen >= 2) {

                    allMatches.add(new int[] {matchLen, dist});
                }
            }

            int cmp = compareForTree(input, pos, curPos, n);
            if (cmp < 0) {
                cur = btLeft[cur];
            } else {
                cur = btRight[cur];
            }
        }

        int lowestByteCount = Integer.MAX_VALUE;

        for (int[] i : allMatches) {
            int tempByteCount = 1;
            if (i[0] < 32) {
                tempByteCount += 6;
            } else {
                tempByteCount += 10;
            }

            if (i[1] < 128) {
                tempByteCount += 9;
            } else if (i[1] < 1024) {
                tempByteCount += 12;
            } else if (i[1] < 8192) {
                tempByteCount += 15;
            } else {
                tempByteCount += 18;
            }

            if (tempByteCount< (9 * i[0])) {
                if (tempByteCount < lowestByteCount) {
                    bestLen = i[0];
                    bestDist = i[1];
                    lowestByteCount = tempByteCount;
                }

            }
        }

        return new int[]{bestLen, bestDist};
    }


    //
    //COMP
    //
    private static ArrayList<Byte> outCompBytes = new ArrayList<>();

    public static void comp(String sourceFile, String resultFile) {
        System.out.println("started compresing");
        resetComp();
        resetDictionary();

        byte[] input = readAllBytes(sourceFile);
        if (input == null) return;

        compInput(input);
        flushBits();

        writeCompressed(resultFile);
        System.out.println("Compresion complete");
    }

    //Resets global values for encoder
    public static void resetComp() {
        outCompBytes.clear();
    }

    // Encodes a single byte as a literal using rangeEncoder.
    private static void literalComp(int b) {
//        System.out.println("value: " + b);
        writeByBits((byte) 0, 1);
        writeByBits((byte) b, 8);
        dictPut((byte) b);
    }

    private static int bitCache = 0;
    private static int bitCount = 0;

    private static void writeByBits(byte b, int bitNumber) {
        for (int i = bitNumber - 1; i >= 0; i--) {
            int bit = (b >> i) & 1;
            bitCache = (bitCache << 1) | bit;
            bitCount++;

            if (bitCount == 8) {
                outCompBytes.add((byte) bitCache);
                bitCache = 0;
                bitCount = 0;
            }
        }
    }

    private static void  flushBits() {
        if (bitCount > 0) {
            bitCache <<= (8 - bitCount);
            outCompBytes.add((byte) bitCache);
            bitCache = 0;
            bitCount = 0;
        }
    }

    // Encodes a match given by (distance, length), starting at 'pos' in 'input'. Calls rangeEncoder as a match and then pushes every matched byte into the dictionary so that future matches can refer to it.
    private static void matchComp(byte[] input, int pos, int length, int distance) {
//        System.out.println("length: " + length + "   distance: " + distance );
        writeByBits((byte) 1, 1);
        if (length < 32) {
            writeByBits((byte) 0, 1);
            writeByBits((byte) (length & 0x1F), 5);
        } else {
            writeByBits((byte) 1, 1);
            writeByBits((byte) (length & 0xFF), 8);
            writeByBits((byte) ((length >>> 8) & 0x01), 1);
        }

        if (distance < 128) {
            writeByBits((byte) 0, 1);
            writeByBits((byte) 0, 1);
            writeByBits((byte) (distance & 0x7F), 7);
        } else if (distance < 1024) {
            writeByBits((byte) 1, 1);
            writeByBits((byte) 0, 1);
            writeByBits((byte) (distance & 0xFF), 8);
            writeByBits((byte) ((distance >>> 8) & 0x03), 2);
        } else if (distance < 8192) {
            writeByBits((byte) 0, 1);
            writeByBits((byte) 1, 1);
            writeByBits((byte) (distance & 0xFF), 8);
            writeByBits((byte) ((distance >>> 8) & 0x1F), 5);
        } else {
            writeByBits((byte) 1, 1);
            writeByBits((byte) 1, 1);
            writeByBits((byte) (distance & 0xFF), 8);
            writeByBits((byte) ((distance >>> 8) & 0xFF), 8);
        }

        for (int k = 0; k < length; k++) {
            dictPut(input[pos + k]);
        }
    }

    // Writes all bytes collected in outCompressedBytes to the given file.
    private static void writeCompressed(String resultFile) {
        try (FileOutputStream out = new FileOutputStream(resultFile)) {
            for (byte b : outCompBytes) {
                out.write(b);
            }
        } catch (IOException ex) {
            System.out.println("Error writing compressed file: " + ex.getMessage());
        }
    }


    //
    //DECOMP
    //
    public static void decomp(String sourceFile, String resultFile) {
        System.out.println("started Decompresing");
        resetDecomp();
        resetDictionary();

        byte[] input = readAllBytes(sourceFile);
        if (input == null) return;

        decompInput(input);

        writeCompressed(resultFile);
        System.out.println("Decompresion complete");
    }

    public static void decompInput(byte[] input) {

    }

    //Resets global values for decomp
    public static void resetDecomp() {

    }

    //Match or literal flag decomp
    public static void flagDecomp(int bit) {

    }

    //Literal value decomp
    public static void literalDecomp(int value) {

    }

    //Match length value decomp
    public static void lengthDecomp(int length) {

    }

    //Match distance value decomp
    public static void distanceDecomp(int distance) {

    }

    //
    // SIZE
    //
    public static int size(String sourceFile) {
        int size = 0;
        try {
            FileInputStream f = new FileInputStream(sourceFile);
            System.out.println("size: " + f.available());
            size = f.available();
            f.close();
        }
        catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        return size;
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