package com.identify.everything;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.identify.everything.data.AppDatabase;
import com.identify.everything.data.Item;
import com.identify.everything.data.ItemVersion;
import com.identify.everything.data.SyncQueue;

import java.io.IOException;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ItemRepository {

    private static final String TAG = "ItemRepository";
    private static final String BASE_URL = "http://10.0.2.2:5000";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AppDatabase database;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public ItemRepository(Context context) {
        this.database = AppDatabase.getInstance(context);
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    public Item getItem(String guid) {
        return database.itemDao().getByGuid(guid);
    }

    public void saveItem(Item item) {
        database.itemDao().insert(item);
    }

    public void saveVersion(ItemVersion version) {
        database.itemVersionDao().insert(version);
    }

    public ItemVersion getCanonicalVersion(String itemId) {
        return database.itemVersionDao().getCanonicalVersion(itemId);
    }

    public String downloadBackendSync(String timestamp) {
        String url = BASE_URL + "/api/v1/items/sync";
        if (timestamp != null && !timestamp.isEmpty()) {
            url += "?after=" + timestamp;
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            Log.e(TAG, "downloadBackendSync failed: " + e.getMessage());
        }
        return null;
    }

    public boolean uploadToBackend(String payload) {
        String deviceId = getOrCreateDeviceId();
        RequestBody body = RequestBody.create(payload, JSON);
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/v1/sync/upload")
            .addHeader("X-Device-Id", deviceId)
            .post(body)
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e(TAG, "uploadToBackend failed: " + e.getMessage());
            return false;
        }
    }

    public String loadItemDetails(String guid) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/v1/items/" + guid)
            .get()
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            Log.e(TAG, "loadItemDetails failed: " + e.getMessage());
        }
        return null;
    }

    public void enqueueSyncRecord(String itemId, String operation, String payload) {
        SyncQueue record = new SyncQueue(UUID.randomUUID().toString(), itemId, operation, payload);
        database.syncQueueDao().insert(record);
    }

    private String getOrCreateDeviceId() {
        return UUID.randomUUID().toString();
    }
}
