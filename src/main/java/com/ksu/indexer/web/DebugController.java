
package com.ksu.indexer.web;

import com.ksu.indexer.core.IndexSegment;
import com.ksu.indexer.model.FileEvent;
import com.ksu.indexer.service.IndexService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
    private final IndexService indexService;

    public DebugController(IndexService indexService) {
        this.indexService = indexService;
    }

    @GetMapping("/segments")
    public List<Map<String,Object>> segments() {
        List<Map<String,Object>> out = new ArrayList<>();
        for (IndexSegment s : indexService.currentSegments()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("segId", s.id());
            row.put("sizeBytesEstimate", s.sizeBytesEstimate());
            row.put("deletedRatio", s.deletedRatio());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/load")
    public Map<String,Object> generateLoad(@RequestParam(defaultValue = "50") int docs) {
        Random r = new Random(42);
        for (int i=0;i<docs;i++) {
            String text = "doc " + i + " quick fox " + (r.nextBoolean() ? "latency" : "freshness");
            FileEvent e = new FileEvent("ld-"+i, FileEvent.Type.ADD, text, Map.of(), java.time.Instant.now());
            indexService.applyEvent(e);
        }
        return Map.of("status","ok","docsIngested",docs,"hint","Run GET /api/search?q=quick fox to fill histograms");
    }
}
