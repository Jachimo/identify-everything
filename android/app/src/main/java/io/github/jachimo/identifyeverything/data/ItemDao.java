package io.github.jachimo.identifyeverything.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemDao {

    @Query("SELECT * FROM items WHERE guid = :guid")
    LiveData<Item> getByGuid(String guid);

    @Query("SELECT * FROM items WHERE guid = :guid")
    Item getByGuidSync(String guid);

    @Query("SELECT * FROM items WHERE synced = 0 AND deleted = 0 ORDER BY updatedAt ASC")
    List<Item> getUnsyncedItems();

    @Query("SELECT * FROM items WHERE deleted = 0 ORDER BY updatedAt DESC")
    LiveData<List<Item>> getAllItems();

    @Query("SELECT * FROM items WHERE deleted = 0 AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR guid LIKE '%' || :query || '%')")
    LiveData<List<Item>> searchItems(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Item item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Item> items);

    @Update
    void update(Item item);

    @Delete
    void delete(Item item);

    @Query("DELETE FROM items WHERE guid = :guid")
    void deleteByGuid(String guid);

    @Query("UPDATE items SET synced = 1 WHERE guid = :guid")
    void markSynced(String guid);
}