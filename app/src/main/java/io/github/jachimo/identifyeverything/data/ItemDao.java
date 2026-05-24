package io.github.jachimo.identifyeverything.data;

import androidx.room.*;
import androidx.lifecycle.LiveData;
import io.github.jachimo.identifyeverything.data.entities.Item;
import io.github.jachimo.identifyeverything.data.entities.ItemVersion;

import java.util.Date;
import java.util.List;

@Dao
public interface ItemDao {

    @Query("SELECT * FROM local_items WHERE guid = :guid AND deleted = 0 LIMIT 1")
    LiveData<Item> getGuid(String guid);

    @Query("SELECT * FROM local_items WHERE item_id = :itemId AND deleted = 0 LIMIT 1")
    LiveData<Item> getItemById(String itemId);

    @Query("SELECT * FROM local_items WHERE deleted = 0 ORDER BY createdAt DESC")
    LiveData<List<Item>> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Item item);

    @Update
    void update(Item item);

    @Query("UPDATE local_items SET deleted = 1, deleted_at = :deletedAt WHERE guid = :guid")
    void softDelete(String guid, Date deletedAt);

    @Query("SELECT COUNT(*) FROM local_items WHERE deleted = 0")
    long count();
}
