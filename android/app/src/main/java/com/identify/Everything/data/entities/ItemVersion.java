package com.identify.Everything.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Local SQLite table for version history
 */
@Entity(tableName = "local_item_versions")
public class ItemVersion {

    @PrimaryKey
    public String versionId;  // UUID

    public String itemId;     // GUID

    public String device_id;  // Local device identifier

    public String data;       // JSONB: {title, description, location_lat, location_lon, etc.}

    public String changeSummary;

    public String parentVersionId;  // Previous version for chain

    public Date createdAt;
    public Boolean isCanonical = false;  // Latest version per device

    /**
     * Create new version for an item
     */
    public static ItemVersion newVersion(
        String itemId,
        String deviceId,
        String data,
        String parentVersionId
    ) {
        ItemVersion version = new ItemVersion();
        version.versionId = java.util.UUID.randomUUID().toString();
        version.itemId = itemId;
        version.device_id = deviceId;
        version.data = data;
        version.parentVersionId = parentVersionId;
        version.createdAt = new Date();
        return version;
    }
}
