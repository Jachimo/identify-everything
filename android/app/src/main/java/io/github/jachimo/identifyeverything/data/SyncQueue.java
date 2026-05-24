package io.github.jachimo.identifyeverything.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a pending sync operation for offline-first support.
 */
@Entity(tableName = "sync_queue")
public class SyncQueue {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String itemGuid;
    private String operation;
    private String payload;
    private int retryCount;
    private long createdAt;
    private String lastError;

    public SyncQueue() {
    }

    public SyncQueue(String itemGuid, String operation, String payload) {
        this.itemGuid = itemGuid;
        this.operation = operation;
        this.payload = payload;
        this.retryCount = 0;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getItemGuid() {
        return itemGuid;
    }

    public void setItemGuid(String itemGuid) {
        this.itemGuid = itemGuid;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}