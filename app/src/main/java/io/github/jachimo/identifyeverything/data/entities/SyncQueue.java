package io.github.jachimo.identifyeverything.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "sync_queue")
public class SyncQueue {

    @PrimaryKey
    public String recordId;

    public String itemId;
    public String operation;
    public String payload;
    public Date createdAt;
    public String status;
    public Integer retryCount = 0;

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
