package com.identify.everything.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "item_versions")
public class ItemVersion {
    @PrimaryKey
    @NonNull
    public String versionId;
    public String itemId;
    public String deviceId;
    public String data;
    public String changeSummary;
    public String parentVersionId;
    public long createdAt;
    public boolean isCanonical;

    public ItemVersion(@NonNull String versionId, String itemId, String deviceId, String data) {
        this.versionId = versionId;
        this.itemId = itemId;
        this.deviceId = deviceId;
        this.data = data;
        this.createdAt = System.currentTimeMillis();
        this.isCanonical = true;
    }
}
