
package com.ksu.indexer.planner;

import com.ksu.indexer.core.IndexSegment;

import java.util.ArrayList;
import java.util.List;

public class DPMergePlanner {

    public static class Candidate {
        public final IndexSegment seg;
        public final int cost;     // e.g., size bytes
        public final int benefit;  // e.g., size * (1 + deletedRatio*100)

        public Candidate(IndexSegment seg, int cost, int benefit) {
            this.seg = seg;
            this.cost = cost;
            this.benefit = benefit;
        }
    }

    public List<IndexSegment> plan(List<IndexSegment> segments, int budget) {
        List<Candidate> items = new ArrayList<>();
        for (IndexSegment s : segments) {
            int cost = Math.max(1, s.sizeBytesEstimate());
            int benefit = (int)(cost * (1 + s.deletedRatio() * 10));
            items.add(new Candidate(s, cost, benefit));
        }
        int n = items.size();
        int[][] dp = new int[n+1][budget+1];
        boolean[][] take = new boolean[n+1][budget+1];
        for (int i=1;i<=n;i++) {
            Candidate it = items.get(i-1);
            for (int w=0; w<=budget; w++) {
                dp[i][w] = dp[i-1][w];
                if (it.cost <= w) {
                    int v = dp[i-1][w - it.cost] + it.benefit;
                    if (v > dp[i][w]) {
                        dp[i][w] = v;
                        take[i][w] = true;
                    }
                }
            }
        }
        int w = budget;
        List<IndexSegment> chosen = new ArrayList<>();
        for (int i=n;i>=1;i--) {
            if (take[i][w]) {
                Candidate it = items.get(i-1);
                chosen.add(it.seg);
                w -= it.cost;
            }
        }
        return chosen;
    }
}
