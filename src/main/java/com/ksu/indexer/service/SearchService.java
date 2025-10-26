
package com.ksu.indexer.service;

import com.ksu.indexer.core.IndexSegment;
import com.ksu.indexer.storage.ManifestStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private final IndexService indexService;
    private final ManifestStore manifest;
    private final Timer searchLatency;
    public SearchService(IndexService indexService, ManifestStore manifest, MeterRegistry registry) {
        this.indexService = indexService;
        this.manifest = manifest;
        this.searchLatency = Timer.builder("index.search_latency")
                .publishPercentiles(0.95, 0.99)
                .distributionStatisticExpiry(Duration.ofMinutes(10))
                .register(registry);
    }

  // SearchService.java
  public List<String> searchFileIdsLegacy(String q) {
    long start = System.nanoTime();
    try {
      String[] terms = q.toLowerCase().trim().split("\\s+");
      Map<String, Set<Integer>> segToDocs = null;

      // boolean AND across terms
      for (String t : terms) {
        if (t.isBlank()) continue;
        Map<String, Set<Integer>> local = new HashMap<>();
        for (IndexSegment seg : indexService.currentSegments()) {
          if (!seg.mightContainTerm(t)) continue;
          Set<Integer> docs = new HashSet<>(seg.getRawPostings(t));
          if (!docs.isEmpty()) local.put(seg.id(), new HashSet<>(docs));
        }
        if (segToDocs == null) {
          segToDocs = local;
        } else {
          // intersect per-segment
          segToDocs.keySet().retainAll(local.keySet());
          for (var e : new ArrayList<>(segToDocs.entrySet())) {
            Set<Integer> keep = local.getOrDefault(e.getKey(), Set.of());
            e.getValue().retainAll(keep);
            if (e.getValue().isEmpty()) segToDocs.remove(e.getKey());
          }
        }
        if (segToDocs == null || segToDocs.isEmpty()) return List.of();
      }

      // map (segId, docId) -> fileId and de-dup
      java.util.LinkedHashSet<String> fileIds = new java.util.LinkedHashSet<>();
      for (var e : segToDocs.entrySet()) {
        String seg = e.getKey();
        for (int docId : e.getValue()) {
          String fid = manifest.resolveFileId(seg, docId);
          if (fid != null) fileIds.add(fid);
        }
      }
      return new ArrayList<>(fileIds);
    } finally {
      // keep your timer logic here
    }
  }


    public List<Map<String,Object>> searchV2(String query) {
        long start = System.nanoTime();
        try {
            String[] terms = query.toLowerCase().split("\s+");
            Map<String, Set<Integer>> segToDocSet = new HashMap<>();
            for (String t : terms) {
                Map<String, Set<Integer>> local = new HashMap<>();
                for (IndexSegment s : indexService.currentSegments()) {
                    if (s.mightContainTerm(t)) {
                        for (int id : s.getRawPostings(t)) {
                            local.computeIfAbsent(s.id(), k->new HashSet<>()).add(id);
                        }
                    }
                }
                if (segToDocSet.isEmpty()) segToDocSet = local;
                else {
                    for (String seg : new HashSet<>(segToDocSet.keySet())) {
                        Set<Integer> current = segToDocSet.get(seg);
                        Set<Integer> next = local.getOrDefault(seg, Set.of());
                        current.retainAll(next);
                        if (current.isEmpty()) segToDocSet.remove(seg);
                    }
                }
            }
          // SearchService.java (inside searchV2)
          List<Map<String,Object>> hits = new ArrayList<>();
          for (var e : segToDocSet.entrySet()) {
            String seg = e.getKey();
            for (int docId : e.getValue()) {
              // resolve fileId first
              String fileId = manifest.resolveFileId(seg, docId);
              if (fileId == null) {
                // either skip or log; better to ensure docmap is complete at ingest
                continue;
              }
              // filter by file-level tombstone
              if (manifest.isTombstonedFileId(fileId)) continue;

              // (Optional) also filter by legacy seg/doc tombstones if you keep them:
              // if (manifest.isTombstoned(seg, docId)) continue;

              Map<String, Object> row = new LinkedHashMap<>();
              row.put("segId", seg);
              row.put("docId", docId);
              row.put("fileId", fileId);
              hits.add(row);
            }
          }
          return hits;

        } finally {
            searchLatency.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
