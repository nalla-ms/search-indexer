
package com.ksu.indexer.model;

import java.time.Instant;
import java.util.Map;

public class FileEvent {
    public enum Type { ADD, UPDATE, DELETE }

    private String fileId;
    private Type type;
    private String text;
    private Map<String,String> metadata;
    private Instant ts;

    public FileEvent(){}

    public FileEvent(String fileId, Type type, String text, Map<String,String> metadata, Instant ts) {
        this.fileId = fileId;
        this.type = type;
        this.text = text;
        this.metadata = metadata;
        this.ts = ts;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
}
