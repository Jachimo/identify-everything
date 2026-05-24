package io.github.jachimo.identifyeverything.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    List<SyncQueue> getAllPending();

    @Query("SELECT * FROM sync_queue WHERE retryCount < 3 ORDER BY createdAt ASC")
    List<SyncQueue> getPendingRetry();

    @Insert
    void insert(SyncQueue syncQueue);

    @Delete
    void delete(SyncQueue syncQueue);

    @Query("DELETE FROM sync_queue WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    void incrementRetry(long id, String error);

    @Query("SELECT COUNT(*) FROM sync_queue WHERE retryCount < 3")
    int getPendingCount();
}