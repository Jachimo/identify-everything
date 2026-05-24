package com.identify.Everything.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Tracks pending sync operations for offline-first
 */
@Entity(tableName = "sync_queue")
public class SyncQueue {

    @PrimaryKey
    public String recordId;  // Unique ID for this sync operation

    public String itemId;    // GUID of item being synced

    public String operation;  // "create", "update", "delete"

    public String payload;    // JSON payload for the sync request

    public Date createdAt;   // When this sync was queued

    public String status;    // "pending", "sent", "failed"

    public Integer retryCount = 0;

    /**
     * Create a sync queue entry
     */
    public static SyncQueue newOperation(String itemId, String operation, String payload) {
        SyncQueue queue = new SyncQueue();
        queue.recordId = java.util.UUID.randomUUID().toString();
        queue.itemId = itemId;
        queue.operation = operation;
        queue.payload = payload;
        queue.createdAt = new Date();
        queue.status = "pending";
        return queue;
    }
}
