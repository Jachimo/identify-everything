package io.github.jachimo.identifyeverything.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "local_item_versions")
public class ItemVersion {

    @PrimaryKey
    public String versionId;

    public String itemId;
    public String device_id;
    public String data;
    public String changeSummary;
    public String parentVersionId;
    public Date createdAt;
    public Boolean isCanonical = false;

    public static ItemVersion newVersion(String itemId, String deviceId, String data, String parentVersionId) {
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
