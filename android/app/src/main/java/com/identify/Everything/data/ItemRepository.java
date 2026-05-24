package com.identify.Everything.data;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.identify.Everything.R;
import com.identify.Everything.util.GuidGenerator;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Repository for managing item data, offline-first sync, and HTTP operations
 */
public class ItemRepository extends ViewModel {

    private final AppDatabase database;
    private final String deviceId;  // Persistent local device identifier

    // Shared device ID storage file
    private static final String DEVICE_ID_FILE = "device_id.txt";

    private final MutableLiveData<Boolean> syncInProgress = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncError = new MutableLiveData<>(null);

    public ItemRepository(Context context) {
        this.database = AppDatabase.getDatabase(context);
        this.deviceId = loadDeviceId(context);
    }

    private String loadDeviceId(Context context) {
        var sharedPref = context.getSharedPreferences("IdentifyEverything", Context.MODE_PRIVATE);
        String id = sharedPref.getString("device_id", null);
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
            sharedPref.edit().putString("device_id", id).apply();
        }
        return id;
    }

    // Item operations

    public void createItem(String guid, String url, String schemaType, String note) {
        viewModelScope.launch(Dispatchers.IO) {
            database.itemDao().insert(Item.newItem(guid, url, schemaType));
        };
    }

    public LiveData<Item> getItem(String guid) {
        return database.itemDao().getGuid(guid);
    }

    public LiveData<Void> deleteItem(String guid) {
        viewModelScope.launch(Dispatchers.IO) {
            database.itemDao().softDelete(guid, new Date());
        };
        return new MutableLiveData<>();
    }

    public LiveData<Boolean> syncInProgress() {
        return syncInProgress;
    }

    public LiveData<String> getSyncError() {
        return syncError;
    }
}
