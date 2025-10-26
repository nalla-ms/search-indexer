
package com.ksu.indexer.web;

import com.ksu.indexer.model.FileEvent;
import com.ksu.indexer.service.IndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {
    private final IndexService indexService;

    public IngestController(IndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody FileEvent event) {
        indexService.applyEvent(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/merge/dp")
    public ResponseEntity<?> mergeDP(@RequestParam(defaultValue = "50000") int budgetBytes) throws Exception {
        int merged = indexService.mergeWithDPBudget(budgetBytes);
        return ResponseEntity.ok().body("Merged " + merged + " segments by DP within budget=" + budgetBytes);
    }

    @PostMapping("/merge/greedy")
    public ResponseEntity<?> mergeGreedy(@RequestParam(defaultValue = "3") int maxPick) throws Exception {
        int merged = indexService.mergeGreedy(maxPick);
        return ResponseEntity.ok().body("Merged " + merged + " segments by greedy with maxPick=" + maxPick);
    }
}
