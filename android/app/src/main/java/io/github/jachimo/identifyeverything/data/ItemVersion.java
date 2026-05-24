package io.github.jachimo.identifyeverything.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a version of an item.
 */
@Entity(
        tableName = "item_versions",
        foreignKeys = @ForeignKey(
                entity = Item.class,
                parentColumns = "guid",
                childColumns = "itemGuid",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("itemGuid")}
)
public class ItemVersion {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String itemGuid;
    private String versionId;
    private String data;
    private String changeSummary;
    private String parentVersionId;
    private long createdAt;
    private boolean isCanonical;

    public ItemVersion() {
    }

    public ItemVersion(String itemGuid, String versionId, String data, String changeSummary) {
        this.itemGuid = itemGuid;
        this.versionId = versionId;
        this.data = data;
        this.changeSummary = changeSummary;
        this.createdAt = System.currentTimeMillis();
        this.isCanonical = true;
    }

    // Getters and setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getItemGuid() {
        return itemGuid;
    }

    public void setItemGuid(String itemGuid) {
        this.itemGuid = itemGuid;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getParentVersionId() {
        return parentVersionId;
    }

    public void setParentVersionId(String parentVersionId) {
        this.parentVersionId = parentVersionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isCanonical() {
        return isCanonical;
    }

    public void setCanonical(boolean canonical) {
        isCanonical = canonical;
    }
}