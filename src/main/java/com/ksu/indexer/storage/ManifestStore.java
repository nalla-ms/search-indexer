
package com.ksu.indexer.storage;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManifestStore {
    private final JdbcTemplate jdbc;

    public ManifestStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS segments(id VARCHAR(128) PRIMARY KEY, path VARCHAR(512), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS docmap(seg_id VARCHAR(128), doc_id INT, file_id VARCHAR(256), PRIMARY KEY(seg_id, doc_id))");
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS file_tombstones(\n"
            + "  file_id VARCHAR(256) PRIMARY KEY,\n"
            + "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n"
            + ");");
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS tombstones(seg_id VARCHAR(128), doc_id INT, PRIMARY KEY(seg_id, doc_id))");
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS file_versions(\n"
            + "  file_id VARCHAR(256) NOT NULL,\n"
            + "  seg_id  VARCHAR(128) NOT NULL,\n"
            + "  doc_id  INT NOT NULL,\n"
            + "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n"
            + ");");
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS file_heads(\n"
            + "  file_id VARCHAR(256) PRIMARY KEY,\n"
            + "  seg_id  VARCHAR(128) NOT NULL,\n"
            + "  doc_id  INT NOT NULL,\n"
            + "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n"
            + ");");
        this.jdbc.execute("CREATE INDEX IF NOT EXISTS idx_docmap_file_id ON docmap(file_id)");
    }



  public void upsert(String id, String path) {
    // Column list lets H2 apply DEFAULT for created_at
    jdbc.update("MERGE INTO segments (id, path) KEY(id) VALUES(?, ?)", id, path);
  }

  public List<String> listIds() {
    return jdbc.query("SELECT id FROM segments", (rs, i) -> rs.getString(1));
  }

  public void remove(String id) {
    jdbc.update("DELETE FROM segments WHERE id = ?", id);
  }
    public void mapDoc(String segId, int docId, String fileId) {
        jdbc.update("MERGE INTO docmap KEY(seg_id, doc_id) VALUES(?, ?, ?)", segId, docId, fileId);
    }

    public List<Map<String,Object>> findDocsByFileId(String fileId) {
        return jdbc.queryForList("SELECT seg_id, doc_id FROM docmap WHERE file_id=?", fileId);
    }

    public String resolveFileId(String segId, int docId) {
        List<String> ids = jdbc.query("SELECT file_id FROM docmap WHERE seg_id=? AND doc_id=?", ps->{
            ps.setString(1, segId);
            ps.setInt(2, docId);
        }, (rs,i)->rs.getString(1));
        return ids.isEmpty()? null : ids.get(0);
    }

    public void addTombstone(String segId, int docId) {
        jdbc.update("MERGE INTO tombstones KEY(seg_id, doc_id) VALUES(?, ?)", segId, docId);
    }

    public boolean isTombstoned(String segId, int docId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tombstones WHERE seg_id=? AND doc_id=?", Integer.class, segId, docId);
        return n != null && n > 0;
    }

  public void addTombstoneByFileId(String fileId) {
    jdbc.update("MERGE INTO file_tombstones (file_id) KEY(file_id) VALUES (?)", fileId);
  }

  public boolean isTombstonedFileId(String fileId) {
    Integer n = jdbc.queryForObject(
        "SELECT COUNT(*) FROM file_tombstones WHERE file_id = ?",
        Integer.class, fileId
    );
    return n != null && n > 0;
  }

  /** Map (segId, docId) -> fileId (idempotent). */
  public void upsertDocmap(String segId, int docId, String fileId) {
    jdbc.update(
        "MERGE INTO docmap (seg_id, doc_id, file_id) KEY (seg_id, doc_id) VALUES (?,?,?)",
        segId, docId, fileId
    );
  }

  /** Remove all (segId, *) rows after a merge removes that segment. */
  public void deleteDocmapBySegment(String segId) {
    jdbc.update("DELETE FROM docmap WHERE seg_id = ?", segId);
  }



}
