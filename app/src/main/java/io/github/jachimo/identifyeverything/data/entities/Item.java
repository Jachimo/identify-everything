package io.github.jachimo.identifyeverything.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Local SQLite table for item storage (offline-first)
 */
@Entity(tableName = "local_items")
public class Item {

    @PrimaryKey
    public String guid;  // Base26-encoded identifier

    public String itemId;   // UUID (assigned by backend after first sync)

    public String url;   // https://{domain}/objects/v1/{guid}

    public String schemaType;  // e.g., "generic", "book", "clothing"

    public Boolean deleted = false;

    public Date createdAt;
    public Date deletedAt;
    public Date lastLocalSync;
    public Date lastRemoteUpdated;

    /**
     * Create new item locally (guid assigned by user/QR)
     */
    public static Item newLocalItem(String guid, String url, String schemaType) {
        Item item = new Item();
        item.guid = guid;
        item.url = url;
        item.schemaType = schemaType;
        item.createdAt = new Date();
        item.deleted = false;
        return item;
    }

    /**
     * Create item after receiving from backend
     */
    public static Item newRemoteItem(
        String guid,
        String itemId,
        String url,
        String schemaType
    ) {
        Item item = new Item();
        item.guid = guid;
        item.itemId = itemId;
        item.url = url;
        item.schemaType = schemaType;
        item.createdAt = new Date();
        item.deleted = false;
        item.lastRemoteUpdated = new Date();
        return item;
    }
}
