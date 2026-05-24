package com.identify.Everything.data.entities;

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

    public String url;   // https://{domain}/objects/v1/{guid}

    public String schemaType;  // e.g., "generic", "book", "clothing"

    public Boolean deleted = false;

    public Date createdAt;
    public Date deletedAt;
    public Date lastLocalSync;

    /**
     * Get non-deleted items sorted by creation date
     */
    public static Item newItem(String guid, String url, String schemaType) {
        Item item = new Item();
        item.guid = guid;
        item.url = url;
        item.schemaType = schemaType;
        item.createdAt = new Date();
        item.deleted = false;
        return item;
    }
}
