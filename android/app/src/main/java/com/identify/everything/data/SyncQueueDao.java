package com.identify.everything.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SyncQueue record);

    @Update
    void update(SyncQueue record);

    @Delete
    void delete(SyncQueue record);

    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC")
    List<SyncQueue> getPendingItems();

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    List<SyncQueue> getAll();
}
