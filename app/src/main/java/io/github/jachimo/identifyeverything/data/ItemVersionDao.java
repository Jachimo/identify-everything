package io.github.jachimo.identifyeverything.data;

import androidx.room.*;
import androidx.lifecycle.LiveData;
import io.github.jachimo.identifyeverything.data.entities.ItemVersion;

import java.util.Date;
import java.util.List;

@Dao
public interface ItemVersionDao {

    @Query("SELECT * FROM local_item_versions WHERE version_id = :versionId LIMIT 1")
    LiveData<ItemVersion> getVersion(String versionId);

    @Query("SELECT * FROM local_item_versions WHERE item_id = :itemId ORDER BY createdAt DESC LIMIT 3")
    LiveData<List<ItemVersion>> getLatestVersions(String itemId);

    @Query("SELECT * FROM local_item_versions WHERE item_id = :itemId ORDER BY createdAt DESC")
    LiveData<List<ItemVersion>> getAllVersions(String itemId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ItemVersion version);

    @Update
    void update(ItemVersion version);

    @Query("SELECT COUNT(*) FROM local_item_versions")
    long count();
}
