package com.identify.Everything.data;

import android.content.Context;
import androidx.room.*;
import com.identify.Everything.data.entities.Item;
import com.identify.Everything.data.entities.ItemVersion;

@Database(
    entities = {Item.class, ItemVersion.class, SyncQueue.class},
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "identify.db";

    private static volatile AppDatabase INSTANCE;

    public abstract ItemDao itemDao();
    public abstract ItemVersionDao itemVersionDao();
    public abstract SyncQueueDao syncQueueDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
