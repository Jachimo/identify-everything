package io.github.jachimo.identifyeverything.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jachimo.identifyeverything.data.entities.Item;
import io.github.jachimo.identifyeverything.data.entities.SyncQueue;
import io.github.jachimo.identifyeverything.util.GuidGenerator;

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

public class ItemRepository extends ViewModel {

    private final AppDatabase database;
    private final String deviceId;

    private final MutableLiveData<Boolean> syncInProgress = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncError = new MutableLiveData<>(null);
    private String serverUrl = "http://10.0.2.2:8000";

    public ItemRepository(Context context) {
        this.database = AppDatabase.getDatabase(context);
        this.deviceId = loadDeviceId(context);
    }

    public void setServerUrl(String url) {
        this.serverUrl = url;
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

    public LiveData<Boolean> syncInProgress() {
        return syncInProgress;
    }

    public LiveData<String> getSyncError() {
        return syncError;
    }

    private static class ItemDataDto {
        String itemId;
        String guid;
        String url;
        String created_at;
        Object data;

        public static ItemDataDto fromJson(JsonObject json) {
            ItemDataDto dto = new ItemDataDto();
            dto.guid = json.has("guid") ? json.get("guid").getAsString() : null;
            dto.url = json.has("url") ? json.get("url").getAsString() : null;
            dto.created_at = json.has("created_at") ? json.get("created_at").getAsString() : null;
            dto.data = json.has("data") ? json.get("data") : null;
            return dto;
        }
    }

    private String buildServerUrl() {
        return serverUrl;
    }

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

    public void createItem(String guid, String url, String schemaType, String note) {
        viewModelScope.launch(Dispatchers.IO) {
            // First save locally for offline access
            database.itemDao().insert(Item.newLocalItem(guid, url, schemaType));

            // Then try to push to backend in background (best-effort)
            try {
                Gson gson = new Gson();
                JsonObject payload = new JsonObject();
                payload.addProperty("guid", guid);
                payload.addProperty("url", url);
                payload.addProperty("domain", "mylabels.example.com");
                payload.addProperty("schema_type", schemaType);
                payload.addProperty("device_id", deviceId);
                if (note != null) {
                    payload.addProperty("change_summary", "Created: " + note);
                }

                String backendUrl = buildServerUrl() + "/api/v1/items";
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();

                Request request = new Request.Builder()
                    .url(backendUrl)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(
                        payload.toString(),
                        MediaType.parse("application/json")
                    ))
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        // Parse backend response to get item_id (UUID)
                        String body = response.body().string();
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        String itemId = json.get("item_id").getAsString();

                        // Update local record with backend-assigned UUID
                        Item localItem = database.itemDao().getGuid(guid).getValue();
                        if (localItem != null) {
                            localItem.itemId = itemId;
                            localItem.lastRemoteUpdated = new java.util.Date();
                            database.itemDao().update(localItem);
                        }
                    }
                }
            } catch (IOException e) {
                // Network issue — item will sync later via background worker
                // Queue for future retry
                database.syncQueueDao().insert(
                    SyncQueue.newOperation(guid, "create",
                        "{\"guid\":\"" + guid + "\",\"url\":\"" + url + "\"}")
                );
            }
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
}
