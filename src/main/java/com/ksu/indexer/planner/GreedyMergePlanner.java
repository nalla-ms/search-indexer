
package com.ksu.indexer.planner;

import com.ksu.indexer.core.IndexSegment;
import java.util.*;

public class GreedyMergePlanner {
    public List<IndexSegment> plan(List<IndexSegment> segments, int maxToPick) {
        // Pick segments with highest deleted ratio first, as a simple heuristic
        return segments.stream()
                .sorted(Comparator.comparingDouble(IndexSegment::deletedRatio).reversed())
                .limit(maxToPick)
                .toList();
    }
}
