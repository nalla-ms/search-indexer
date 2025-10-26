
package com.ksu.indexer.structures;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class BloomFilter {
    private final BitSet bits;
    private final int m;
    private final int k;

    public BloomFilter(int m, int k) {
        this.m = m;
        this.k = k;
        this.bits = new BitSet(m);
    }

    private int hash(byte[] data, int seed) {
        int h = seed;
        for (byte b : data) {
            h = h * 31 + b;
        }
        return (h & 0x7fffffff) % m;
    }

    public void add(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < k; i++) {
            int idx = hash(data, 1337 + i * 101);
            bits.set(idx);
        }
    }

    public boolean mightContain(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < k; i++) {
            int idx = hash(data, 1337 + i * 101);
            if (!bits.get(idx)) return false;
        }
        return true;
    }
}
