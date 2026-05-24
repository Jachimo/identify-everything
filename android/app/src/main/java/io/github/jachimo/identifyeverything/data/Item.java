package io.github.jachimo.identifyeverything.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing an item locally.
 * Uses the Base26 GUID as the local primary key.
 * The server-assigned UUID is stored in {@code itemId} for API calls.
 */
@Entity(tableName = "items")
public class Item {

    @PrimaryKey
    private String guid;

    private String itemId;
    private String url;
    private String domain;
    private String schemaType;
    private String title;
    private String description;
    private long createdAt;
    private long updatedAt;
    private boolean deleted;
    private boolean synced;

    public Item() {
    }

    public Item(String guid, String itemId, String url, String domain) {
        this.guid = guid;
        this.itemId = itemId;
        this.url = url;
        this.domain = domain;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.deleted = false;
        this.synced = false;
    }

    // Getters and setters

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSchemaType() {
        return schemaType;
    }

    public void setSchemaType(String schemaType) {
        this.schemaType = schemaType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}