
package com.ksu.indexer.service;

import com.ksu.indexer.core.IndexSegment;
import com.ksu.indexer.core.Tokenizer;
import com.ksu.indexer.model.FileEvent;
import com.ksu.indexer.planner.DPMergePlanner;
import com.ksu.indexer.planner.GreedyMergePlanner;
import com.ksu.indexer.storage.ManifestStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IndexService {
    private final Path segDir;
    private final ManifestStore manifestStore;
    private final MeterRegistry registry;

    private final List<IndexSegment> liveSegments = new CopyOnWriteArrayList<>();
    private final AtomicInteger seq = new AtomicInteger();

    private final Timer ingestToVisible;
    public IndexService(@Value("${index.dir:segments}") String dir, ManifestStore manifestStore, MeterRegistry registry) throws IOException {
        this.segDir = Path.of(dir);
        Files.createDirectories(segDir);
        this.manifestStore = manifestStore;
        this.registry = registry;
        this.ingestToVisible = Timer.builder("index.ingest_visible")
                .publishPercentiles(0.95, 0.99)
                .distributionStatisticExpiry(Duration.ofMinutes(10))
                .register(registry);
        reloadFromManifest();
    }

    private void reloadFromManifest() {
        for (String id : manifestStore.listIds()) {
            try {
                liveSegments.add(IndexSegment.load(segDir, id));
            } catch (Exception e) {
                // ignore for brevity
            }
        }
    }

    public void applyEvent(FileEvent e) {
        Instant start = Instant.now();
        IndexSegment delta = new IndexSegment(segDir, "delta-" + seq.incrementAndGet());

        // tombstone any existing docs for this fileId
        if (e.getFileId() != null) {
            var previous = manifestStore.findDocsByFileId(e.getFileId());
            for (var row : previous) {
                String seg = (String) row.get("SEG_ID");
                int doc = ((Number) row.get("DOC_ID")).intValue();
                manifestStore.addTombstone(seg, doc);
            }
        }

        if (e.getType() != FileEvent.Type.DELETE) {
            var terms = Tokenizer.tokenize(e.getText());
            int docId = delta.addDoc(terms);
            if (e.getFileId() != null) {
                manifestStore.mapDoc(delta.id(), docId, e.getFileId());
            }
        }
        try {
            delta.persist();
            liveSegments.add(delta);
            manifestStore.upsert(delta.id(), segDir.resolve(delta.id() + ".seg").toString());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            ingestToVisible.record(Duration.between(start, Instant.now()));
        }
    }

    public List<IndexSegment> currentSegments() {
        return new ArrayList<>(liveSegments);
    }

    public int mergeWithDPBudget(int budgetBytes) throws IOException {
        DPMergePlanner dp = new DPMergePlanner();
        List<IndexSegment> choice = dp.plan(currentSegments(), budgetBytes);
        if (choice.isEmpty()) return 0;
        String id = "merge-" + System.currentTimeMillis();
      var result  = IndexSegment.mergeWithRemap(segDir, id, choice);
      IndexSegment merged = result.segment;
      // rebuild docmap for the merged segment
      for (int newDocId = 0; newDocId < result.remap.size(); newDocId++) {
        var src = result.remap.get(newDocId);
        String fileId = manifestStore.resolveFileId(src.segId, src.docId);
        if (fileId != null) {
          manifestStore.upsertDocmap(merged.id(), newDocId, fileId);
        }
      }
        for (IndexSegment s : choice) {
            liveSegments.remove(s);
            manifestStore.remove(s.id());
            manifestStore.deleteDocmapBySegment(s.id()); // <--- important
            Files.deleteIfExists(segDir.resolve(s.id()+".seg"));
        }
        liveSegments.add(merged);
        manifestStore.upsert(merged.id(), segDir.resolve(merged.id()+".seg").toString());
        return choice.size();
    }

    public int mergeGreedy(int maxPick) throws IOException {
        GreedyMergePlanner g = new GreedyMergePlanner();
        List<IndexSegment> choice = g.plan(currentSegments(), maxPick);
        if (choice.isEmpty()) return 0;
        String id = "merge-" + System.currentTimeMillis();
      var result  = IndexSegment.mergeWithRemap(segDir, id, choice);
      IndexSegment merged = result.segment;
      // rebuild docmap for the merged segment
      for (int newDocId = 0; newDocId < result.remap.size(); newDocId++) {
        var src = result.remap.get(newDocId);
        String fileId = manifestStore.resolveFileId(src.segId, src.docId);
        if (fileId != null) {
          manifestStore.upsertDocmap(merged.id(), newDocId, fileId);
        }
      }
        for (IndexSegment s : choice) {
            liveSegments.remove(s);
            manifestStore.remove(s.id());
           manifestStore.deleteDocmapBySegment(s.id());
            Files.deleteIfExists(segDir.resolve(s.id()+".seg"));
        }
        liveSegments.add(merged);
        manifestStore.upsert(merged.id(), segDir.resolve(merged.id()+".seg").toString());
        return choice.size();
    }
}
