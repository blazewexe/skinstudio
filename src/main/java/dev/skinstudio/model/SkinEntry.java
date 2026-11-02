package dev.skinstudio.model;

import java.time.Instant;

public class SkinEntry {
    public String id;
    public String displayName;
    public String sourceType;
    public String sourceReference;
    public String cacheFile;
    public String model;
    public int width;
    public int height;
    public long createdAt;
    public long lastUsedAt;
    public boolean favorite;

    public SkinEntry() {}

    public SkinEntry(String displayName, String sourceType, String sourceReference,
                     String cacheFile, SkinModel model, int width, int height) {
        this.id = cacheFile;
        this.displayName = displayName;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.cacheFile = cacheFile;
        this.model = model.name();
        this.width = width;
        this.height = height;
        this.createdAt = Instant.now().toEpochMilli();
        this.lastUsedAt = createdAt;
    }

    public SkinModel skinModel() {
        try { return SkinModel.valueOf(model); }
        catch (RuntimeException ignored) { return SkinModel.UNKNOWN; }
    }
}
