// 251RDB028 Reinis Delvers
// 251RDB060 Aleksis Kaļetovs
// 251RDB213 Raimonds Polis


//imports
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;


public class Main {
    //
    // ALL Globals
    //
    // FOR DICTIONARY / MATCH FINDER
    private static final int DICT_SIZE = 65536;
    private static final int MAX_DISTANCE = 65535;
    private static final int MAX_MATCH_LEN = 511;
    private static byte[] dictionary = new byte[DICT_SIZE];
    private static int dictPos = 0;
    private static int dictFill = 0;
    private static int[] btLeft;
    private static int[] btRight;
    private static int btRoot;
    private static final int BT_MAX_SEARCH_DEPTH = 64;

    // FOR DECOMP / COMP
    private static ArrayList<Byte> outBytes = new ArrayList<>();

    // FOR COMP
    private static ArrayList<int[]> allMatches = new ArrayList<>();
    private static int bitCache = 0;
    private static int bitCount = 0;

    // FOR DECOMP
    private static int inBitPos = 0;
    private static byte[] inBytes;
    private static int lastDistance = 0;


    //
    // MAIN
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
                case "about":
                    about();
                    break;
                case "exit":
                    break loop;
                default:
                    System.out.println("Unknown command. Available: comp, decomp, size, equal, test, about, exit");
            }
        }
        sc.close();
    }


    //
    // COMP
    //
    // main comp method
    public static void comp(String sourceFile, String resultFile) {
        try {
            System.out.println("Started compression");
            resetComp();
            resetDictionary();

            byte[] input = readAllBytes(sourceFile);
            if (input == null) return;

            writeByBits(input.length, 32);
            compInput(input);
            flushBits();

            writeToFile(resultFile);
            System.out.println("Compression complete");
        } catch (OutOfMemoryError e) {
            System.out.println("File is too big while compressing, out of memory");
        }
    }
    
    // Resets comp variables
    private static void resetComp() {
        outBytes.clear();
        allMatches.clear();
        bitCache = 0;
        bitCount = 0;
    }
    
    // Encodes literal
    private static void literalComp(int b) {
        writeByBits(0, 1);
        writeByBits(b & 0xFF, 8);
        dictPut((byte) b);
    }
    
    // Encodes match
    private static void matchComp(byte[] input, int pos, int length, int distance) {
        writeByBits(1, 1);

        if (length < 32) {
            writeByBits(0, 1);
            writeByBits(length & 0x1F, 5);
        } else {
            writeByBits(1, 1);
            writeByBits(length & 0xFF, 8);
            writeByBits((length >>> 8) & 0x01, 1);
        }

        if (distance < 128) {
            writeByBits(0, 1);
            writeByBits(0, 1);
            writeByBits(distance & 0x7F, 7);
        } else if (distance < 1024) {
            writeByBits(1, 1);
            writeByBits(0, 1);
            writeByBits(distance & 0xFF, 8);
            writeByBits((distance >>> 8) & 0x03, 2);
        } else if (distance < 8192) {
            writeByBits(0, 1);
            writeByBits(1, 1);
            writeByBits(distance & 0xFF, 8);
            writeByBits((distance >>> 8) & 0x1F, 5);
        } else {
            writeByBits(1, 1);
            writeByBits(1, 1);
            writeByBits(distance & 0xFF, 8);
            writeByBits((distance >>> 8) & 0xFF, 8);
        }
        
        for (int k = 0; k < length; k++) {
            dictPut(input[pos + k]);
        }
    }


    // Fills up each byte before saving it
    private static void writeByBits(int b, int bitNumber) {
        for (int i = bitNumber - 1; i >= 0; i--) {
            int bit = (b >> i) & 1;
            bitCache = (bitCache << 1) | bit;
            bitCount++;

            if (bitCount == 8) {
                outBytes.add((byte) (bitCache & 0xFF));
                bitCache = 0;
                bitCount = 0;
            }
        }
    }
    
    // Saves remaining data from last byte
    private static void flushBits() {
        if (bitCount > 0) {
            bitCache <<= (8 - bitCount);
            outBytes.add((byte) (bitCache & 0xFF));
            bitCache = 0;
            bitCount = 0;
        }
    }
    
    // Write compressed bytes to file
    private static void writeToFile(String resultFile) {
        try (FileOutputStream out = new FileOutputStream(resultFile)) {
            for (byte b : outBytes) out.write(b & 0xFF);
        } catch (IOException ex) {
            System.out.println("Error writing file: " + ex.getMessage());
        }
    }


    //
    // DICTIONARY / MATCH FINDER
    //
    // Adds encoded bytes to dictionary
    private static void dictPut(byte b) {
        dictionary[dictPos] = b;
        dictPos = (dictPos + 1) % DICT_SIZE;
        if (dictFill < DICT_SIZE) dictFill++;
    }

    // Resets directory variables
    private static void resetDictionary() {
        Arrays.fill(dictionary, (byte) 0);
        dictPos = 0;
        dictFill = 0;
    }

    // Adds position to the tree
    private static void insertPos(byte[] input, int pos) {
        if (pos < 0 || pos >= input.length) return;
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

    // Adds the next byte to length if equal
    private static int calcMatchLen(byte[] input, int pos1, int pos2, int n) {
        if (pos1 < 0 || pos2 < 0 || pos1 >= n || pos2 >= n) return 0;
        int maxLen = Math.min(MAX_MATCH_LEN, n - Math.max(pos1, pos2));
        int len = 0;
        while (len < maxLen && input[pos1 + len] == input[pos2 + len]) {
            len++;
        }
        return len;
    }

    // Compares two positions
    private static int compareForTree(byte[] input, int pos1, int pos2, int n) {
        if (pos1 == pos2) return 0;
        int maxLen = Math.min(MAX_MATCH_LEN, n - Math.max(pos1, pos2));
        int i = 0;
        while (i < maxLen) {
            int b1 = input[pos1 + i] & 0xFF;
            int b2 = input[pos2 + i] & 0xFF;
            if (b1 != b2) return b1 - b2;
            i++;
        }
        return pos1 - pos2;
    }

    // Reads all bytes from provided file
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

    // Forwards data to comp for encoding
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
                literalComp(input[i] & 0xFF);
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
                literalComp(input[i] & 0xFF);
                insertPos(input, i);
                i++;
            }
        }
    }

    // Finds best match based on encoded bit length
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

            if (dist > 1 && dist <= dictFill) {
                int matchLen = calcMatchLen(input, pos, curPos, n);

                if (matchLen > 1) {
                    allMatches.add(new int[]{matchLen, dist});
                }
            }

            int cmp = compareForTree(input, pos, curPos, n);
            if (cmp < 0) cur = btLeft[cur];
            else cur = btRight[cur];
        }

        int lowestByteCount = Integer.MAX_VALUE;

        for (int[] m : allMatches) {
            int matchLen = m[0];
            int dist = m[1];

            int tempByteCount = 1; // The match flag bit
            if (matchLen < 32) {
                tempByteCount += 6; // 1 length variant bit + 5 data bits
            } else {
                tempByteCount += 10;// 1 length variant bit + 9 data bits
            }

            if (dist < 128) {
                tempByteCount += 9; // 2 distance variant bits + 7 data bits
            } else if (dist < 1024) {
                tempByteCount += 12; // 2 distance variant bits + 10 data bits
            } else if (dist < 8192) {
                tempByteCount += 15; // 2 distance variant bits + 13 data bits
            } else {
                tempByteCount += 18; // 2 distance variant bits + 16 data bits
            }

            // Check if encoding as literal is better
            if (tempByteCount < (9 * matchLen)) { // 1 literal flag bit + 8 data bits
                if (tempByteCount < lowestByteCount) {
                    bestLen = matchLen;
                    bestDist = dist;
                    lowestByteCount = tempByteCount;
                }
            }
        }
        return new int[]{bestLen, bestDist};
    }


    //
    // DECOMP
    //
    // main decomp method
    public static void decomp(String sourceFile, String resultFile) {
        try {
            System.out.println("Started decompression");
            resetDecomp();
            resetDictionary();

            byte[] input = readAllBytes(sourceFile);
            if (input == null) return;

            inBytes = input;
            inBitPos = 0;

            int originalSize = readBits(32);
            if (originalSize < 0) {
                throw new RuntimeException("Invalid file compressed file size");
            }
            decompInput(originalSize);

            writeToFile(resultFile); // outBytes now holds decompressed bytes
            System.out.println("Decompression complete");
        } catch (OutOfMemoryError e) {
            System.out.println("File is too big while decompresing, out of memory");
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        }
    }

    // Resets decomp variables
    private static void resetDecomp() {
        outBytes.clear();
        lastDistance = 0;
        inBitPos = 0;
    }

    // Reads byte bit by bit
    private static int readBit() {
        int bytePos = inBitPos >> 3;
        if (inBytes == null || bytePos >= inBytes.length) return -1;
        int bitPos = 7 - (inBitPos & 7);
        inBitPos++;
        return (inBytes[bytePos] >> bitPos) & 1;
    }

    // Reads certain amount of bits
    private static int readBits(int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            int b = readBit();
            if (b < 0) return -1; // signal EOF
            v = (v << 1) | b;
        }
        return v;
    }
    
    // decompreses the file
    private static void decompInput(int originalSize) {

        outBytes.clear();
        while (outBytes.size() < originalSize) {

            int flag = readBit();
            if (flag < 0) break;

            if (flag == 0) {
                int b = readBits(8);
                if (b < 0) break;
                literalDecomp(b);

            } else {
                int lenHeader = readBit();
                if (lenHeader < 0) break;
                int length;

                if (lenHeader == 0) {
                    int v = readBits(5);
                    if (v < 0) break;
                    length = v;
                } else {
                    int v1 = readBits(8);
                    int v2 = readBit();
                    if (v1 < 0 || v2 < 0) break;
                    length = v1 | (v2 << 8);
                }

                int c1 = readBit();
                int c2 = readBit();
                if (c1 < 0 || c2 < 0) break;
                int distance = 0;

                if (c1 == 0 && c2 == 0) {
                    int v = readBits(7);
                    if (v < 0) break;
                    distance = v;
                } else if (c1 == 1 && c2 == 0) {
                    int low = readBits(8);
                    int high2 = readBits(2);
                    if (low < 0 || high2 < 0) break;
                    distance = low | (high2 << 8);
                } else if (c1 == 0 && c2 == 1) {
                    int low = readBits(8);
                    int high5 = readBits(5);
                    if (low < 0 || high5 < 0) break;
                    distance = low | (high5 << 8);
                } else {
                    int low = readBits(8);
                    int high = readBits(8);
                    if (low < 0 || high < 0) break;
                    distance = low | (high << 8);
                }

                if (distance < 2 || distance > dictFill) {
                    throw new RuntimeException("Decomp got invalid distance: " + distance);
                }

                if (length < 2 || length > MAX_MATCH_LEN) {
                    throw new RuntimeException("Decomp got invalid length: " + length);
                }

                distanceDecomp(distance);
                lengthDecomp(length);
            }
        }
    }
    
    // Decompreses literals
    private static void literalDecomp(int value) {
        byte b = (byte) value;
        outBytes.add(b);
        dictPut(b);
    }

    // Decompreses distance
    private static void distanceDecomp(int distance) {
        lastDistance = distance;
    }

    // Decompreses length
    private static void lengthDecomp(int length) {
        if (length <= 0) return;
        for (int i = 0; i < length; i++) {
            int src = (dictPos - lastDistance + DICT_SIZE) % DICT_SIZE;
            byte b = dictionary[src];
            outBytes.add(b);
            dictPut(b);
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
        } catch (IOException ex) {
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
                if (k1 > 0) {
                    for (int i = 0; i < k1; i++) {
                        if (buf1[i] != buf2[i]) {
                            f1.close();
                            f2.close();
                            return false;
                        }
                    }
                }
            } while (!(k1 == -1 && k2 == -1));
            
            f1.close();
            f2.close();
            return true;
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

    //
    // ABOUT
    //
    public static void about() {
        System.out.println("251RDB028 Reinis Delvers");
        System.out.println("251RDB060 Aleksis Kaļetovs");
        System.out.println("251RDB213 Raimonds Polis");
    }
}

