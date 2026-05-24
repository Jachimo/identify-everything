package com.identify.Everything.data;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for communicating with the Identify Everything server
 * Handles sync protocol (download/upload)
 */
public class SyncApiClient {

    // Default server URL for testing
    private static final String DEFAULT_SERVER_URL = "http://10.0.2.2:8000";

    private final OkHttpClient client;
    private final String deviceId;
    private final String serverUrl;

    public SyncApiClient(String deviceId, String serverUrl) {
        this.deviceId = deviceId;
        this.serverUrl = serverUrl != null ? serverUrl : DEFAULT_SERVER_URL;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Download sync changes from server
     * GET /api/v1/items/sync?after={timestamp}
     *
     * @param timestamp timestamp to sync from (or null for initial sync)
     * @return sync response JSON, or null on error
     */
    public JSONObject downloadSyncData(String timestamp) throws IOException {
        StringBuilder urlBuild = new StringBuilder(serverUrl);
        urlBuild.append("/api/v1/items/sync");

        if (timestamp != null) {
            urlBuild.append("?after=").append(timestamp);
        }

        Request request = new Request.Builder()
            .url(urlBuild.toString())
            .header("X-Device-Id", deviceId)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }

            String responseBody = response.body().string();
            return new JSONObject(responseBody);
        }
    }

    /**
     * Upload local changes to server
     * POST /api/v1/sync/upload
     *
     * @param changes JSON payload with version data and attachments
     * @return void
     */
    public void uploadChanges(JSONObject changes) throws IOException {
        Request request = new Request.Builder()
            .url(serverUrl + "/api/v1/sync/upload")
            .header("X-Device-Id", deviceId)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(
                changes.toString(),
                MediaType.parse("application/json")
            ))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }
        }
    }

    /**
     * Generate sync URL for encoded item
     */
    public String getItemUrl(String guid) {
        return serverUrl + "/api/v1/items/" + guid;
    }
}
