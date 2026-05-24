package io.github.jachimo.identifyeverything.data;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jachimo.identifyeverything.util.GuidGenerator;

/**
 * Repository that manages data operations and sync with the backend.
 * Acts as the single source of truth for item data.
 */
public class ItemRepository extends ViewModel {

    private static final String TAG = "ItemRepository";
    private static final String DEFAULT_SERVER_URL = "http://10.0.2.2:5000";

    private final AppDatabase database;
    private final ItemDao itemDao;
    private final ItemVersionDao itemVersionDao;
    private final SyncQueueDao syncQueueDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private SyncApiClient apiClient;
    private String deviceId;
    private boolean initialized;

    public ItemRepository(Application application) {
        this.database = AppDatabase.getInstance(application);
        this.itemDao = database.itemDao();
        this.itemVersionDao = database.itemVersionDao();
        this.syncQueueDao = database.syncQueueDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.deviceId = generateDeviceId();
        this.apiClient = new SyncApiClient(DEFAULT_SERVER_URL, deviceId);
    }

    public void setServerUrl(String serverUrl) {
        apiClient.setBaseUrl(serverUrl);
    }

    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Initialize by registering with the backend.
     * Call this once at app startup.
     */
    public void initialize() {
        if (initialized) return;
        initialized = true;

        executor.execute(() -> {
            try {
                apiClient.registerDevice();
                Log.i(TAG, "Device registered: " + deviceId);
            } catch (IOException e) {
                Log.w(TAG, "Device registration failed (offline): " + e.getMessage());
            }
        });
    }

    // === Local CRUD Operations ===

    public LiveData<Item> getItem(String guid) {
        return itemDao.getByGuid(guid);
    }

    public LiveData<List<Item>> getAllItems() {
        return itemDao.getAllItems();
    }

    public LiveData<List<Item>> searchItems(String query) {
        return itemDao.searchItems(query);
    }

    /**
     * Create a new item locally, then attempt to push to backend.
     * Falls back to sync queue if backend is unreachable.
     */
    public void createItem(String guid, String url, String domain, SyncCallback callback) {
        executor.execute(() -> {
            Item existing = itemDao.getByGuidSync(guid);
            if (existing != null) {
                notifyCallback(callback, existing);
                return;
            }

            String itemId = UUID.randomUUID().toString();
            Item item = new Item(guid, itemId, url, domain);
            itemDao.insert(item);

            // Try to push to backend
            try {
                JsonObject result = apiClient.createItem(guid, itemId, url, domain);
                String serverItemId = result.has("item_id") ? result.get("item_id").getAsString() : itemId;
                item.setItemId(serverItemId);
                item.setSynced(true);
                itemDao.update(item);
                notifyCallback(callback, item);
            } catch (IOException e) {
                Log.w(TAG, "Backend unreachable, queuing sync: " + e.getMessage());
                enqueueSync(guid, "create", item);
                notifyCallback(callback, item);
            }
        });
    }

    /**
     * Create a new item with auto-generated GUID.
     */
    public void createItem(String url, String domain, SyncCallback callback) {
        String guid = GuidGenerator.generate();
        createItem(guid, url, domain, callback);
    }

    /**
     * Save/update an item's data locally, then sync to backend.
     */
    public void saveItem(Item item, String dataJson, String changeSummary, SyncCallback callback) {
        executor.execute(() -> {
            // Update canonical version status
            ItemVersion canonical = itemVersionDao.getCanonicalVersion(item.getGuid());
            if (canonical != null) {
                canonical.setCanonical(false);
                itemVersionDao.update(canonical);
            }

            // Create new version
            String versionId = UUID.randomUUID().toString();
            ItemVersion newVersion = new ItemVersion(
                    item.getGuid(), versionId, dataJson,
                    changeSummary != null ? changeSummary : "Updated via mobile app"
            );
            newVersion.setParentVersionId(canonical != null ? canonical.getVersionId() : null);
            itemVersionDao.insert(newVersion);

            // Update item timestamp
            item.setUpdatedAt(System.currentTimeMillis());
            itemDao.update(item);

            // Try sync to backend
            try {
                apiClient.updateItem(item.getGuid(), dataJson, changeSummary);
                item.setSynced(true);
                itemDao.update(item);
                notifyCallback(callback, item);
            } catch (IOException e) {
                Log.w(TAG, "Sync save failed, queuing: " + e.getMessage());
                enqueueSync(item.getGuid(), "update", item);
                notifyCallback(callback, item);
            }
        });
    }

    // === Sync Operations ===

    /**
     * Download changes from backend and apply locally.
     */
    public void syncDownload(SyncCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject response = apiClient.downloadSync(null);
                JsonObject changes = response.getAsJsonObject("changes");

                // Process added items
                if (changes.has("items_added")) {
                    JsonArray added = changes.getAsJsonArray("items_added");
                    for (JsonElement element : added) {
                        JsonObject remoteItem = element.getAsJsonObject();
                        String guid = remoteItem.get("guid").getAsString();
                        String itemId = remoteItem.get("item_id").getAsString();
                        String url = remoteItem.has("url") ? remoteItem.get("url").getAsString() : "";

                        Item local = itemDao.getByGuidSync(guid);
                        if (local == null) {
                            local = new Item(guid, itemId, url, "synced");
                            local.setSynced(true);
                            itemDao.insert(local);
                        } else {
                            local.setItemId(itemId);
                            local.setSynced(true);
                            itemDao.update(local);
                        }

                        // Store version data if present
                        if (remoteItem.has("data") && !remoteItem.get("data").isJsonNull()) {
                            String dataStr = new Gson().toJson(remoteItem.get("data"));
                            ItemVersion version = new ItemVersion(guid, itemId, dataStr, "Synced from server");
                            ItemVersion existing = itemVersionDao.getCanonicalVersion(guid);
                            if (existing == null) {
                                itemVersionDao.insert(version);
                            }
                        }
                    }
                }

                // Process deleted items
                if (changes.has("items_deleted")) {
                    JsonArray deleted = changes.getAsJsonArray("items_deleted");
                    for (JsonElement element : deleted) {
                        String itemId = element.getAsString();
                        // Find local item by itemId and mark deleted
                        Item local = itemDao.getByGuidSync(itemId);
                        if (local != null) {
                            local.setDeleted(true);
                            itemDao.update(local);
                        }
                    }
                }

                notifyCallback(callback, "Sync download complete");
            } catch (IOException e) {
                Log.e(TAG, "Sync download failed", e);
                notifyCallback(callback, "Sync failed: " + e.getMessage());
            }
        });
    }

    /**
     * Upload pending local changes to backend.
     */
    public void syncUpload(SyncCallback callback) {
        executor.execute(() -> {
            try {
                List<Item> unsynced = itemDao.getUnsyncedItems();
                JsonArray versions = new JsonArray();

                for (Item item : unsynced) {
                    JsonObject versionData = new JsonObject();
                    versionData.addProperty("item_id", item.getItemId() != null ? item.getItemId() : item.getGuid());
                    versionData.addProperty("guid", item.getGuid());
                    versionData.addProperty("url", item.getUrl());
                    versionData.addProperty("domain", item.getDomain());
                    versions.add(versionData);

                    item.setSynced(true);
                    itemDao.update(item);
                }

                // Also upload sync queue items
                List<SyncQueue> pending = syncQueueDao.getPendingRetry();
                for (SyncQueue queue : pending) {
                    versions.add(JsonParser.parseString(queue.getPayload()));
                    syncQueueDao.delete(queue);
                }

                if (versions.size() > 0) {
                    JsonObject result = apiClient.uploadSync(versions);
                    int processed = result.has("processed") ? result.get("processed").getAsInt() : 0;
                    Log.i(TAG, "Uploaded " + processed + " items");
                }

                notifyCallback(callback, "Sync upload complete");
            } catch (IOException e) {
                Log.e(TAG, "Sync upload failed", e);
                notifyCallback(callback, "Sync failed: " + e.getMessage());
            }
        });
    }

    /**
     * Full bidirectional sync: download then upload.
     */
    public void syncAll(SyncCallback callback) {
        syncDownload(result -> {
            syncUpload(callback);
        });
    }

    // === Internal Helpers ===

    private void enqueueSync(String guid, String operation, Item item) {
        JsonObject payload = new JsonObject();
        payload.addProperty("item_id", item.getItemId() != null ? item.getItemId() : guid);
        payload.addProperty("guid", item.getGuid());
        payload.addProperty("url", item.getUrl());
        payload.addProperty("domain", item.getDomain());

        SyncQueue queue = new SyncQueue(guid, operation, payload.toString());
        syncQueueDao.insert(queue);
    }

    private String generateDeviceId() {
        return "android-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void notifyCallback(SyncCallback callback, Object result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(result));
        }
    }

    // === Callback Interface ===

    public interface SyncCallback {
        void onComplete(Object result);
    }
}