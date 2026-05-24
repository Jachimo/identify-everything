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
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
            database.itemDao().insert(Item.newLocalItem(guid, url, schemaType));
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

    // Backend sync operations

    /**
     * Queue item creation for background sync
     * TODO: Implement sync upload logic
     */
    public void queueCreate(String guid, String url, String schemaType, String note) {
        viewModelScope.launch(Dispatchers.IO) {
            // Don't create locally yet until sync is implemented
            // Backend will create item with UUID on first sync
        };
    }

    /**
     * Queue item update for background sync
     */
    public void queueUpdate(String guid, String data, String summary) {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Push to backend and queue sync
        };
    }

    /**
     * Queue item deletion for background sync
     */
    public void queueDelete(String guid) {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Push to backend and queue sync
            database.itemDao().softDelete(guid, new Date());
        };
    }

    /**
     * Download sync changes from server using hardcoded URL
     * GET /api/v1/items/sync?after={timestamp}
     *
     * @param timestamp timestamp to sync from (or null for initial sync)
     * @return JSON response from server
     */
    public String downloadBackendSync(String timestamp) throws IOException {
        // TODO: Use actual server URL and implement auto-discovery
        String serverUrl = "http://10.0.2.2:8000/api/v1/items/sync";

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build();

        StringBuilder urlBuilder = new StringBuilder(serverUrl);
        if (timestamp != null) {
            urlBuilder.append("?after=").append(timestamp);
        }

        Request request = new Request.Builder()
            .url(urlBuilder.toString())
            .header("X-Device-Id", deviceId)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }
            return response.body().string();
        }
    }

    /**
     * Upload local changes to server
     * POST /api/v1/sync/upload
     */
    public void uploadToBackend(String payload) throws IOException {
        // TODO: Implement actual upload logic
        String serverUrl = "http://10.0.2.2:8000/api/v1/sync/upload";

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build();

        Request request = new Request.Builder()
            .url(serverUrl)
            .header("X-Device-Id", deviceId)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload, MediaType.parse("application/json")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }
        }
    }

    /**
     * Backend sync response from GET /api/v1/ and sync/upload
     */
    private static class ItemDataDto {
        String itemId;
        String guid;
        String url;
        String domain;
        String schemaType;
        Object data;
        String changeSummary;
        String parentVersionId;
        String createdAt;
        String updatedAt;

        public static ItemDataDto fromJson(JsonObject json) {
            ItemDataDto dto = new ItemDataDto();
            dto.itemId = json.has("item_id") ? json.get("item_id").getAsString() : null;
            dto.guid = json.has("guid") ? json.get("guid").getAsString() : null;
            dto.url = json.has("url") ? json.get("url").getAsString() : null;
            dto.domain = json.has("domain") ? json.get("domain").getAsString() : null;
            dto.schemaType = json.has("schema_type") ? json.get("schema_type").getAsString() : "generic";
            dto.data = json.has("data") ? json.get("data") : null;
            dto.changeSummary = json.has("change_summary") ? json.get("change_summary").getAsString() : null;
            dto.parentVersionId = json.has("parent_version_id") ? json.get("parent_version_id").getAsString() : null;
            dto.createdAt = json.has("created_at") ? json.get("created_at").getAsString() : null;
            dto.updatedAt = json.has("updated_at") ? json.get("updated_at").getAsString() : null;
            return dto;
        }
    }

    /**
     * URL structure for the backend
     */
    private String buildServerUrl() {
        // TODO: Use environment variable or user config
        // For MVP: auto-discover from "http://10.0.2.2:8000" in emulator
        return System.getenv("SERVER_URL") != null
            ? System.getenv("SERVER_URL")
            : "http://10.0.2.2:8000";
    }

    /**
     * Fetch specific item details from backend
     * GET /api/v1/items/{guid}
     */
    public String getItemFromBackend(String guid) throws IOException {
        String url = buildServerUrl() + "/api/v1/items/" + guid;

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build();

        Request request = new Request.Builder()
            .url(url)
            .header("X-Device-Id", deviceId)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }
            return response.body().string();
        }
    }
}
