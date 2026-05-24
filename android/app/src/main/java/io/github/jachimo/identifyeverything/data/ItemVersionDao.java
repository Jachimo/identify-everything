package io.github.jachimo.identifyeverything.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemVersionDao {

    @Query("SELECT * FROM item_versions WHERE itemGuid = :itemGuid ORDER BY createdAt DESC")
    List<ItemVersion> getVersionsByItemGuid(String itemGuid);

    @Query("SELECT * FROM item_versions WHERE itemGuid = :itemGuid AND isCanonical = 1 LIMIT 1")
    ItemVersion getCanonicalVersion(String itemGuid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ItemVersion version);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ItemVersion> versions);

    @Update
    void update(ItemVersion version);

    @Query("UPDATE item_versions SET isCanonical = 0 WHERE itemGuid = :itemGuid AND isCanonical = 1")
    void clearCanonical(String itemGuid);

    @Query("DELETE FROM item_versions WHERE itemGuid = :itemGuid")
    void deleteByItemGuid(String itemGuid);
}