package io.github.jachimo.identifyeverything.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP client for syncing with the Identify Everything backend.
 * Communicates with the FastAPI server via REST endpoints.
 */
public class SyncApiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;

    private final OkHttpClient client;
    private final Gson gson;
    private String baseUrl;
    private String deviceId;
    private String syncToken;

    public SyncApiClient(String baseUrl, String deviceId) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.deviceId = deviceId;
        this.gson = new Gson();

        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Register this device with the backend.
     */
    public JsonObject registerDevice() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("device_id", deviceId);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/devices/register")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("Device registration failed: " + response.code() + " " + responseBody);
            }
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("sync_token")) {
                this.syncToken = json.get("sync_token").getAsString();
            }
            return json;
        }
    }

    /**
     * Download changes since a given timestamp.
     */
    public JsonObject downloadSync(String afterTimestamp) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + "/api/v1/items/sync" + (afterTimestamp != null ? "?after=" + afterTimestamp : ""))
                .addHeader("X-Device-Id", deviceId);

        if (syncToken != null) {
            builder.addHeader("X-Sync-Token", syncToken);
        }

        try (Response response = client.newCall(builder.get().build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("Sync download failed: " + response.code() + " " + responseBody);
            }
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("sync_token")) {
                this.syncToken = json.get("sync_token").getAsString();
            }
            return json;
        }
    }

    /**
     * Create a new item on the backend.
     */
    public JsonObject createItem(String guid, String itemId, String url, String domain) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("guid", guid);
        body.addProperty("url", url);
        body.addProperty("domain", domain);
        body.addProperty("device_id", deviceId);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/items")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Device-Id", deviceId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (response.code() == 409) {
                // Item already exists on server - not an error
                JsonObject json = new JsonObject();
                json.addProperty("status", "exists");
                return json;
            }
            if (!response.isSuccessful()) {
                throw new IOException("Item creation failed: " + response.code() + " " + responseBody);
            }
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }

    /**
     * Fetch item details from the backend.
     */
    public JsonObject getItem(String guid) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/items/" + guid)
                .addHeader("X-Device-Id", deviceId)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful()) {
                throw new IOException("Get item failed: " + response.code() + " " + responseBody);
            }
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }

    /**
     * Update an item on the backend.
     */
    public JsonObject updateItem(String guid, String data, String changeSummary) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("device_id", deviceId);
        if (data != null) {
            body.add("data", JsonParser.parseString(data));
        }
        if (changeSummary != null) {
            body.addProperty("change_summary", changeSummary);
        }

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/items/" + guid)
                .put(RequestBody.create(body.toString(), JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Device-Id", deviceId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("Update item failed: " + response.code() + " " + responseBody);
            }
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }

    /**
     * Upload version changes to the backend.
     */
    public JsonObject uploadSync(JsonArray itemVersions) throws IOException {
        JsonObject body = new JsonObject();
        JsonObject changes = new JsonObject();
        changes.add("item_versions", itemVersions);
        body.add("changes", changes);

        Request.Builder builder = new Request.Builder()
                .url(baseUrl + "/api/v1/sync/upload")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Device-Id", deviceId);

        if (syncToken != null) {
            builder.addHeader("X-Sync-Token", syncToken);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("Sync upload failed: " + response.code() + " " + responseBody);
            }
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }
}