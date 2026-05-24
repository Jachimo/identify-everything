package com.identify.everything.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Item item);

    @Update
    void update(Item item);

    @Query("SELECT * FROM items WHERE deleted = 0")
    LiveData<List<Item>> getAllItems();

    @Query("SELECT * FROM items WHERE guid = :guid LIMIT 1")
    Item getByGuid(String guid);

    @Query("SELECT * FROM items WHERE itemId = :itemId LIMIT 1")
    Item getById(String itemId);
}
