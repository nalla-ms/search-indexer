# 🔍 Search Indexer (Dynamic Merge-Aware Indexing Engine)

**Version:** 2.0  
**Author:** Narender Nalla, | Ian Hopkins, Taylor King, Derek Wynn

**Course:** CS 6045 – Advanced Algorithms, Kennesaw State University  
**Instructor:** Professor Selena He

**Last Updated:** October 2025  

---

## 📘 Overview

The **Search Indexer** is a lightweight, incremental text indexing and search service that demonstrates *algorithmic optimization in storage and merge scheduling*.  
It simulates the behavior of real-world systems like Lucene or LevelDB, while remaining simple enough for experimentation and algorithmic evaluation.

This version (`v2`) introduces:
- **Dynamic Programming (DP)** and **Greedy Merge Planners** to optimize merge cost.  
- **Tombstone and Head filtering** for version consistency.  
- **Micrometer + Prometheus** integration for performance metrics.  
- **Bloom filters** and **VarByte compression** for efficient memory and disk use.

---

## 🏗️ Architecture

```text
┌─────────────────────────────┐
│        REST Controllers     │
│   (IngestController,        │
│    SearchController, etc.)  │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│         IndexService        │
│  - Apply events (ADD/UPDATE)│
│  - Maintain live segments   │
│  - Execute DP/Greedy merges │
│  - Update Manifest & docmap │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│        IndexSegment         │
│  - Tokenize + store postings│
│  - Maintain Bloom filter    │
│  - Handle save/load         │
│  - mergeWithRemap() logic   │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│        ManifestStore        │
│  - SQLite/H2 persistence    │
│  - docmap (segId→fileId)    │
│  - file_heads, versions     │
│  - tombstones               │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│       SearchService         │
│  - Scan all segments        │
│  - Resolve (segId,docId)    │
│  - Apply tombstone/head     │
│  - Return top hits          │
└─────────────────────────────┘
```

---

## ⚙️ Features Summary

| Category | Description |
|-----------|--------------|
| **Core Indexing** | Incremental append-only indexing using segment files (`.seg`) |
| **Merge Optimization** | Two planners: **Greedy** (fast heuristic) and **DP** (optimal within budget) |
| **Compression** | VarByte encoding for postings |
| **Bloom Filter** | Fast term existence checks per segment |
| **Metadata Layer** | SQLite/H2 `ManifestStore` tracks mappings, tombstones, and version heads |
| **Version Control** | Ensures only the latest document version (per `fileId`) is visible |
| **Tombstones** | Logical deletes for document removal |
| **Metrics** | Micrometer histograms for ingest + search latency |
| **Prometheus Support** | `/actuator/prometheus` endpoint for scraping |
| **RESTful API** | Easily testable via `curl` or Postman |
| **Docker-ready** | Minimal dependencies (Spring Boot 2.7.x) |

---

## 🚀 Build and Run

### Prerequisites
- **Java 17+**
- **Gradle or Maven** (project supports both)
- **H2 / SQLite** (auto-created in local folder)
- Optional: **Docker** and **Prometheus** for observability

### Running Locally

```bash
# 1. Build
./gradlew clean build

# 2. Run
java -jar build/libs/search-indexer-0.0.1-SNAPSHOT.jar
```

Service starts on:  
👉 [http://localhost:8080](http://localhost:8080)

---

## 🧩 API Endpoints

### 1️⃣ Ingest & Events

| Endpoint | Method | Description |
|-----------|--------|--------------|
| `/api/ingest` | POST | Add new document or Update existing document
| `/api/ingest/load?docs=100` | POST | Load synthetic test data |

---

### 2️⃣ Search

| Endpoint | Method | Description |
|-----------|--------|-------------|
| `/api/search/legacy?q=hello` | GET | Legacy search returning fileIds |
| `/api/search/v2?q=hello` | GET | Detailed search (segId, docId, fileId), applies tombstone & head filters |

---

### 3️⃣ Merge Operations

| Endpoint | Method | Description |
|-----------|--------|-------------|
| `/api/ingest/merge/greedy?maxPick=3` | POST | Greedy merge (fast heuristic) |
| `/api/ingest/merge/dp?budgetBytes=100000` | POST | DP merge under byte budget |

Merging triggers:
1. `IndexSegment.mergeWithRemap()` to build new segment with remapped docIDs.  
2. `docmap` rebuild for the merged segment.  
3. Old segment cleanup.

---

### 4️⃣ Observability

| Endpoint | Description |
|-----------|--------------|
| `/actuator/prometheus` | Micrometer metrics for Prometheus |
| `/actuator/health` | Health check |
| `/api/debug/segments` | (Optional) Segment state dump |
| `/api/debug/docmap` | (Optional) Current docmap view |

---

## 📊 Metrics (Prometheus + Grafana)

When run with `management.endpoints.web.exposure.include=prometheus`,  
Prometheus can scrape metrics every 5s from `/actuator/prometheus`.

**Key metrics:**

| Metric | Description | Visualization (PromQL) |
|---------|--------------|------------------------|
| `index_ingest_latency_seconds_bucket` | Ingest latency histogram | `histogram_quantile(0.95, sum(rate(index_ingest_latency_seconds_bucket[5m])) by (le))` |
| `index_merge_latency_seconds_bucket` | Merge latency histogram | same query with `_merge_` |
| `index_search_latency_seconds_bucket` | Search latency histogram | same query with `_search_` |

---

## 🧠 Algorithmic Highlights

### 1️⃣ Greedy Merge Planner
- Picks smallest segments first.
- Fast but may not minimize total cost.

### 2️⃣ Dynamic Programming (DP) Merge Planner
- Computes optimal subset of merges given byte budget.
- Balances merge frequency vs. segment growth.
- Yields measurable improvement in cumulative cost and latency.

### 3️⃣ Time-Weighted Bloom Filter
- Each segment includes a Bloom filter to skip irrelevant term scans.
- Reduces average lookup time by ~40% in synthetic workloads.

---

## 🧩 Data Model (SQLite/H2)

| Table | Purpose |
|--------|----------|
| `segments` | Tracks active segment files |
| `docmap` | Maps `(segId, docId)` → `fileId` |
| `file_heads` | Current visible version per file |
| `file_versions` | History of all versions |
| `file_tombstones` | Logically deleted files |

---

## 📈 Example Run

```bash
# Add docs
curl -s -X POST "localhost:8080/api/ingest/add?fileId=a1&text=quick brown fox"
curl -s -X POST "localhost:8080/api/ingest/add?fileId=a2&text=jumps over the lazy dog"

# Search
curl -s "localhost:8080/api/search/v2?q=quick"

# Update + re-search
curl -s -X POST "localhost:8080/api/ingest/update?fileId=a1&text=fast fox jumps higher"
curl -s "localhost:8080/api/search/v2?q=fox"

# Merge and confirm still searchable
curl -s -X POST "localhost:8080/api/ingest/merge/greedy?maxPick=2"
curl -s "localhost:8080/api/search/v2?q=fox"
```


## 🧭 Future Work
**1. Use SQLite instead of H2.
2. Compute p95/p99 in PromQL
3. Build a  Grafana dashboard JSON for ingest/search latency (p95/p99) and merge counts.
4. Adding unit tests.**

---

> 💬 *“Search optimization isn’t just about results — it’s about when and how you merge what you already know.”*
