package com.identify.everything.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "items")
public class Item {
    @PrimaryKey
    @NonNull
    public String itemId;
    public String guid;
    public String url;
    public String domain;
    public String schemaType;
    public long createdAt;
    public boolean deleted;
    public long deletedAt;

    public Item(@NonNull String itemId, String guid, String url, String domain) {
        this.itemId = itemId;
        this.guid = guid;
        this.url = url;
        this.domain = domain;
        this.schemaType = "generic";
        this.createdAt = System.currentTimeMillis();
        this.deleted = false;
    }
}
