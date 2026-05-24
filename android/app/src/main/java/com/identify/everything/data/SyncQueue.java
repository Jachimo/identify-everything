package com.identify.everything.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "sync_queue")
public class SyncQueue {
    @PrimaryKey
    @NonNull
    public String recordId;
    public String itemId;
    public String versionId;
    public String operation;
    public String payload;
    public long createdAt;
    public String status;
    public int retryCount;

    public SyncQueue(@NonNull String recordId, String itemId, String operation, String payload) {
        this.recordId = recordId;
        this.itemId = itemId;
        this.operation = operation;
        this.payload = payload;
        this.createdAt = System.currentTimeMillis();
        this.status = "pending";
        this.retryCount = 0;
    }
}
