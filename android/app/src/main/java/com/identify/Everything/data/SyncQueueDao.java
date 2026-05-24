package com.identify.Everything.data;

import androidx.room.*;
import com.identify.Everything.data.entities.SyncQueue;

import java.util.List;

@Dao
public interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SyncQueue queue);

    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC LIMIT 10")
    LiveData<List<SyncQueue>> getPendingItems();

    @Update
    void update(SyncQueue queue);

    @Query("DELETE FROM sync_queue WHERE recordId = :recordId")
    void delete(String recordId);

    @Delete
    void delete(SyncQueue queue);

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    long pendingCount();

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT 50")
    LiveData<List<SyncQueue>> getAll();
}
