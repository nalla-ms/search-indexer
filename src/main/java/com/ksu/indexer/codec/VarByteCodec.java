
package com.ksu.indexer.codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VarByteCodec {

    public static byte[] encode(List<Integer> numbers) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int n : numbers) {
            while (true) {
                int b = n & 0x7F;
                n >>>= 7;
                if (n == 0) {
                    baos.write(b | 0x80);
                    break;
                } else {
                    baos.write(b);
                }
            }
        }
        return baos.toByteArray();
    }

    public static List<Integer> decode(byte[] bytes) {
        List<Integer> out = new ArrayList<>();
        int n = 0;
        int shift = 0;
        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            if ((b & 0x80) != 0) {
                n |= ((b & 0x7F) << shift);
                out.add(n);
                n = 0;
                shift = 0;
            } else {
                n |= (b << shift);
                shift += 7;
            }
        }
        return out;
    }

    public static byte[] intsToBytes(List<Integer> ints) {
        return encode(ints);
    }

    public static List<Integer> bytesToInts(byte[] bytes) {
        return decode(bytes);
    }
}
