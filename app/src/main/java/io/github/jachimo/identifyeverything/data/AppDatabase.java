package io.github.jachimo.identifyeverything.data;

import android.content.Context;
import androidx.room.*;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewModelScope;
import io.github.jachimo.identifyeverything.data.entities.Item;
import io.github.jachimo.identifyeverything.data.entities.ItemVersion;
import io.github.jachimo.identifyeverything.data.entities.SyncQueue;

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

    public static class DatabaseRepository extends ViewModel {
        public final AppDatabase database;
        public DatabaseRepository(Context context) {
            this.database = getDatabase(context);
        }
    }

    public static class DatabaseRepositoryFactory implements ViewModelProvider.Factory {
        private final Context context;
        public DatabaseRepositoryFactory(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(DatabaseRepository.class)) {
                return (T) new DatabaseRepository(context);
            }
            throw new IllegalArgumentException("Unknown ViewModel");
        }
    }
}
