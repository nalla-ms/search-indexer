
# Title Page
**Project:** Near-Real-Time, Memory-Lean File Search Indexer  
**Team:** Team ID - 1  
**Roles:**  
- Lead/Backend: Index core, merge planners  
- Data & Evaluation: Workloads, metrics, dashboards  
- DevOps: Build, run, Micrometer/Prometheus

---
# Abstract (~100 words)
We designed and implemented a Spring Boot service that builds an inverted index with near‑real‑time deltas and background merges. The system encodes postings via VarByte, uses per‑segment Bloom filters for fast skipping, and supports two merge strategies: a dynamic‑programming (0/1 knapsack) planner with IO budget and a greedy, size/deletion‑aware planner. A manifest persisted in H2/SQLite tracks segments. We instrumented ingest→visible and search latency with Micrometer histograms to capture p95/p99 under load. To date, CRUD ingest, persistence, DP/greedy planning, and a Boolean AND query path work. Next we’ll add ACLs, filters/facets, snapshots, and richer ranking.

---
# Background & Motivation
We target fresh, responsive, and resource‑efficient file search under bursty updates and strict RAM/IO budgets. The proposal emphasized near‑real‑time readers, immutable delta segments, time‑boxed merges, and succinct encodings. Our design keeps deltas small and merges controlled to bound tail latencies while limiting write‑amplification.

---
# Progress Report
**Done**
- Delta segment write path with tokenization and VarByte postings
- Per‑segment Bloom filters to skip misses
- Persistent manifest (H2/SQLite) and on‑disk `.seg` format
- DP merge planner (0/1 knapsack on benefit/cost) and greedy planner
- Boolean AND search across live segments with skip via Bloom filter
- Micrometer histograms for ingest→visible and search p95/p99
- Actuator + Prometheus endpoint

**Demo Hints**
- `./gradlew bootRun`
- Ingest a few docs; query `GET /api/search?q=term1 term2`
- Trigger DP merge with `/api/ingest/merge/dp?budgetBytes=50000`

**Time Complexity (current core)**
- Tokenization: O(L) per document text length
- Index add (unique term set): O(U) inserts; postings append O(1) amortized; overall O(L)
- Query AND across S segments: O(Σ |postings(term)|) with Bloom negative skip ~O(S) checks
- DP merge planning: O(N * B) where N=segments, B=budget (bytes units). Greedy: O(N log N).

**Problems & Mitigations**
- Mapping fileId→docId for deletes not implemented → plan: maintain KV map in manifest DB.
- Ranking/BM25 not implemented → plan: add term frequencies and BM25; keep Boolean path as fallback.
- Write‑amplification control → tune budget windows; opportunistic greedy merges between DP runs.

**Revised Timeline**
- Week 1–2: Complete delete map, TF storage, BM25 ranking, AND/OR
- Week 3: Snapshots & crash‑recovery polish, test suite, benchmarks
- Week 4: Ablations & final report

---
# Self‑Evaluation
Slightly behind on ranking and delete maps but on track for core milestones. Make‑up plan allocates an extra iteration for ranking + tests while keeping merges and metrics stable.

---
# Prognosis & Future Work
Add BM25 ranking, OR/phrase queries, document‑level metadata filters, ACL evaluation, and segment compaction policies with cost models. Extend metrics (QPS, RAM/posting, write‑amp). Produce ablation graphs comparing brute‑force vs. delta+merge; DP vs. greedy; Bloom on/off.

---
# References
- Levitin, A. *Introduction to the Design and Analysis of Algorithms* (3rd ed.). 2012.
- Cormen, T. H., et al. *Introduction to Algorithms* (3rd ed.). 2009.
- Whoosh (BM25F) as baseline for inverted index comparisons.
