
package com.ksu.indexer.service;

import com.ksu.indexer.core.IndexSegment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class SearchService_backup {
    private final IndexService indexService;
    private final Timer searchLatency;
    public SearchService_backup(IndexService indexService, MeterRegistry registry) {
        this.indexService = indexService;
        this.searchLatency = Timer.builder("index.search_latency")
                .publishPercentiles(0.95, 0.99)
                .distributionStatisticExpiry(Duration.ofMinutes(10))
                .register(registry);
    }

  // SearchService.java (legacy search)
  public List<Integer> search(String query) {
    long start = System.nanoTime();
    try {
      String[] terms = query.toLowerCase().split("\\s+");

      // segId -> docId set for current result
      Map<String, Set<Integer>> segToDocs = null;

      for (String t : terms) {
        Map<String, Set<Integer>> local = new HashMap<>();
        for (IndexSegment s : indexService.currentSegments()) {
          if (s.mightContainTerm(t)) {
            for (int id : s.getPostings(t)) {
              local.computeIfAbsent(s.id(), k -> new HashSet<>()).add(id);
            }
          }
        }
        if (segToDocs == null) segToDocs = local;
        else {
          for (String seg : new HashSet<>(segToDocs.keySet())) {
            Set<Integer> cur = segToDocs.get(seg);
            Set<Integer> nxt = local.getOrDefault(seg, Set.of());
            cur.retainAll(nxt);
            if (cur.isEmpty()) segToDocs.remove(seg);
          }
        }
      }
      if (segToDocs == null) return List.of();

      // Turn (segId, docId) into unique ints so the legacy signature stays the same
      List<Integer> out = new ArrayList<>();
      for (var e : segToDocs.entrySet()) {
        for (int d : e.getValue()) {
          out.add(Objects.hash(e.getKey(), d)); // unique per seg+doc
        }
      }
      Collections.sort(out);
      return out;
    } finally {
      searchLatency.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

}
