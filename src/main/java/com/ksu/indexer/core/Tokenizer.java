
package com.ksu.indexer.core;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {
    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String[] parts = text.toLowerCase().replaceAll("[^a-z0-9\s]", " ").split("\s+");
        for (String p : parts) {
            if (!p.isBlank()) out.add(p);
        }
        return out;
    }
}
