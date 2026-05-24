package io.github.jachimo.identifyeverything.data;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * HTTP client for communicating with the server
 */
public class SyncApiClient {

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

    public JSONObject downloadSyncData(String timestamp) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(serverUrl);
        urlBuilder.append("/api/v1/items/sync");

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

            String responseBody = response.body().string();
            return new com.google.gson.JsonParser().parse(responseBody).getAsJsonObject();
        }
    }
}
