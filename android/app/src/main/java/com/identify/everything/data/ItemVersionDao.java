package com.identify.everything.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ItemVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ItemVersion version);

    @Query("SELECT * FROM item_versions WHERE itemId = :itemId ORDER BY createdAt DESC")
    List<ItemVersion> getVersionsForItem(String itemId);

    @Query("SELECT * FROM item_versions WHERE itemId = :itemId AND isCanonical = 1 LIMIT 1")
    ItemVersion getCanonicalVersion(String itemId);
}
