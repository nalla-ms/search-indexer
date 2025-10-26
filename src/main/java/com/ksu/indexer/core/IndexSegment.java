
package com.ksu.indexer.core;

import com.ksu.indexer.codec.VarByteCodec;
import com.ksu.indexer.structures.BloomFilter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class IndexSegment {
    private final Path dir;
    private final String segId;
    private final Map<String, List<Integer>> postings = new HashMap<>();
    private final Set<Integer> deletedDocs = new HashSet<>();
    private int maxDocId = 0;
    private BloomFilter bloom = new BloomFilter(1<<20, 7);

    public IndexSegment(Path dir, String segId) {
        this.dir = dir;
        this.segId = segId;
    }

    public String id(){ return segId; }

    public int addDoc(List<String> terms) {
        int docId = ++maxDocId;
        Set<String> seen = new HashSet<>();
        for (String t : terms) {
            if (!seen.add(t)) continue;
            postings.computeIfAbsent(t, k -> new ArrayList<>()).add(docId);
            bloom.add(t);
        }
        return docId;
    }

    public void deleteDoc(int docId) {
        deletedDocs.add(docId);
    }

    public boolean mightContainTerm(String term) {
        return bloom.mightContain(term);
    }

    public List<Integer> getPostings(String term) {
        List<Integer> p = postings.getOrDefault(term, Collections.emptyList());
        if (p.isEmpty()) return p;
        List<Integer> filtered = new ArrayList<>(p.size());
        for (int id : p) if (!deletedDocs.contains(id)) filtered.add(id);
        return filtered;
    }

    public List<Integer> getRawPostings(String term) {
        return postings.getOrDefault(term, Collections.emptyList());
    }

    public int sizeBytesEstimate() {
        int sum = 0;
        for (var e : postings.entrySet()) {
            sum += e.getKey().length();
            sum += e.getValue().size() * 4;
        }
        return sum;
    }

    public double deletedRatio() {
        if (maxDocId == 0) return 0.0;
        return (double) deletedDocs.size() / (double) maxDocId;
    }

    public void persist() throws IOException {
        Files.createDirectories(dir);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(dir.resolve(segId + ".seg"))))) {
            out.writeInt(maxDocId);
            out.writeInt(postings.size());
            for (var e : postings.entrySet()) {
                out.writeUTF(e.getKey());
                byte[] enc = VarByteCodec.intsToBytes(e.getValue());
                out.writeInt(enc.length);
                out.write(enc);
            }
            out.writeInt(deletedDocs.size());
            for (int d : deletedDocs) out.writeInt(d);
        }
    }

    public static IndexSegment load(Path dir, String segId) throws IOException {
        IndexSegment s = new IndexSegment(dir, segId);
        Path p = dir.resolve(segId + ".seg");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            s.maxDocId = in.readInt();
            int terms = in.readInt();
            for (int i=0;i<terms;i++) {
                String term = in.readUTF();
                int len = in.readInt();
                byte[] enc = in.readNBytes(len);
                List<Integer> postings = VarByteCodec.bytesToInts(enc);
                s.postings.put(term, new ArrayList<>(postings));
                s.bloom.add(term);
            }
            int dels = in.readInt();
            for (int i=0;i<dels;i++) s.deletedDocs.add(in.readInt());
        }
        return s;
    }

    public static IndexSegment merge(Path dir, String newId, List<IndexSegment> segs) throws IOException {
        IndexSegment out = new IndexSegment(dir, newId);
        Map<String, TreeSet<Integer>> agg = new HashMap<>();
        for (IndexSegment s : segs) {
            for (var e : s.postings.entrySet()) {
                agg.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).addAll(e.getValue());
            }
        }
        for (var e : agg.entrySet()) {
            out.postings.put(e.getKey(), new ArrayList<>(e.getValue()));
            out.bloom.add(e.getKey());
        }
        out.maxDocId = agg.values().stream().mapToInt(TreeSet::size).max().orElse(0);
        out.persist();
        return out;
    }

  /**
   * Merge that also returns a doc remap: for each NEW docId in the merged segment,
   * tells which (segId, docId) it originally came from.
   *
   * Assumes the existing `merge(dir, id, parts)` concatenates all docs from `parts`
   * in the same order they're provided, preserving each part's internal doc order.
   */
/*  public static MergedResult mergeWithRemap(java.nio.file.Path dir, String id, java.util.List<IndexSegment> parts)
      throws java.io.IOException {

    // 1) Precompute the remap by concatenation order:
    // newDocId 0..(N-1) maps to each part's docs in order.
    java.util.ArrayList<DocPointer> remap = new java.util.ArrayList<>();
    for (IndexSegment p : parts) {
      int n = p.maxDocId;              // <-- If your API differs, replace this call.
      String srcSegId = p.id();
      for (int d = 0; d < n; d++) {
        remap.add(new DocPointer(srcSegId, d));
      }
    }

    // 2) Use your existing merge implementation to actually write/build the merged segment.
    IndexSegment merged = merge(dir, id, parts);

    // 3) Return both the merged segment and the precomputed remap.
    return new MergedResult(merged, remap);
  }*/


  /**
   * Merge segments and also return a doc remap:
   * - For each NEW docId in the merged segment (0..N-1),
   *   tell which (segId, docId) it originally came from.
   *
   * This method REMAPS doc IDs so that the merged segment uses a contiguous
   * 0..N-1 space. It also rebuilds postings and bloom accordingly.
   */
  public static MergedResult mergeWithRemap(Path dir, String newId, java.util.List<IndexSegment> parts)
      throws IOException {

    // 0) Collect live (non-deleted) docs per source segment
    //    We'll discover live docs by unioning all term postings (filtered).
    java.util.Map<IndexSegment, java.util.SortedSet<Integer>> livePerSeg = new java.util.HashMap<>();
    for (IndexSegment s : parts) {
      java.util.SortedSet<Integer> live = new java.util.TreeSet<>();
      for (var e : s.postings.entrySet()) {
        // use filtered postings (skips deleted doc IDs)
        java.util.List<Integer> p = s.getPostings(e.getKey());
        live.addAll(p);
      }
      livePerSeg.put(s, live);
    }

    // 1) Assign new contiguous doc IDs (by segment order, then by old docId)
    //    and build remap[newDocId] -> (srcSegId, oldDocId).
    java.util.ArrayList<DocPointer> remap = new java.util.ArrayList<>();
    java.util.Map<IndexSegment, java.util.Map<Integer,Integer>> oldToNew = new java.util.HashMap<>();
    int nextNewId = 0;
    for (IndexSegment s : parts) {
      java.util.Map<Integer,Integer> m = new java.util.HashMap<>();
      for (int oldId : livePerSeg.get(s)) {
        m.put(oldId, nextNewId);
        remap.add(new DocPointer(s.id(), oldId));
        nextNewId++;
      }
      oldToNew.put(s, m);
    }

    // 2) Build remapped postings: union by term, but IDs are NEW global IDs
    java.util.Map<String, java.util.SortedSet<Integer>> agg = new java.util.HashMap<>();
    for (IndexSegment s : parts) {
      java.util.Map<Integer,Integer> map = oldToNew.get(s);
      for (var e : s.postings.entrySet()) {
        String term = e.getKey();
        java.util.List<Integer> srcPost = s.getPostings(term); // filtered
        if (srcPost.isEmpty()) continue;
        java.util.SortedSet<Integer> dst = agg.computeIfAbsent(term, k -> new java.util.TreeSet<>());
        for (int oldId : srcPost) {
          int remappedId = map.get(oldId);
          if (newId != null) dst.add(remappedId);
        }
      }
    }

    // 3) Create merged segment with remapped postings
    IndexSegment out = new IndexSegment(dir, newId);

// Replace postings with remapped lists
    for (var e : agg.entrySet()) {
      out.postings.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
    }

// No deletions after merge
    out.deletedDocs.clear();

// New contiguous doc id space size
    out.maxDocId = nextNewId;

// Rebuild bloom
    out.bloom = new BloomFilter(1<<20, 7);
    for (String term : agg.keySet()) out.bloom.add(term);

// 4) Persist using the segment's own format
    out.persist();

// 5) Either return the in-memory object…
    return new MergedResult(out, remap);

// …or, if you prefer normalization via load():
// IndexSegment merged = IndexSegment.load(dir, newId);
// return new MergedResult(merged, remap);

  }



  /** Identifies a source doc by (segmentId, docId). */
  public static final class DocPointer {
    public final String segId;
    public final int docId;
    public DocPointer(String segId, int docId) { this.segId = segId; this.docId = docId; }
  }

  /** Result of a merge: the new segment + mapping newDocId -> source (segId,docId). */
  public static final class MergedResult {
    public final IndexSegment segment;
    public final java.util.List<DocPointer> remap;
    public MergedResult(IndexSegment segment, java.util.List<DocPointer> remap) {
      this.segment = segment; this.remap = remap;
    }
  }


}
