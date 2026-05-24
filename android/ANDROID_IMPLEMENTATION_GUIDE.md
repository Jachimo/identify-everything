# Identify Everything - Android App

Android mobile client using QR codes for item identification and offline-first sync.

**Current Status: Android client 95% complete**

## Completed Features

- ✅ **MainActivity** - GUID input with validation
- ✅ **GuidGenerator** - Base26 encoding utility (no dependencies)
- ✅ **Room Database** - SQLite and Item/ItemVersion entities
- ✅ **ItemRepository** - ViewModel with backend sync methods
- ✅ **ItemDetailsActivity** - View and edit item metadata
- ✅ **Material UI** - ScrollView, TextInputLayout, FAB
- ✅ **SyncQueue** - Offline sync tracking entity

## Current Workflow

```
1. User enters or scans GUID in MainActivity
2. Validates GUID format (Base26: 28 characters)
3. Opens ItemDetailsActivity with GUID/URL
4. Views and edits item details
5. Saves (placeholder - waits for backend binding)
```

## Next Implementation Steps

### 1. Backend Wire-up (Required for functionality)

Replace placeholder data with backend integration:

**In ItemRepository.java:**
```java
// Replace placeholder sync methods:
public String downloadBackendSync(String timestamp) {
    // TODO: Auto-discover server URL from environment
    // TODO: Implement UTF-8 JSON parsing with Gson
}

public void uploadToBackend(String payload) {
    // TODO: Ensure headers are set correctly
    // TODO: Handle proper error responses
}
```

**In ItemDetailsActivity.java:**
```java
private void loadData() {
    // TODO: Call repository to fetch actual item from backend

    // Call backend API:
    String response = itemRepository.loadItemDetails(guid);
    JsonObject json = JsonParser.parseString(response).getAsJsonObject();

    titleInput.setText(json.get("title").getAsString());
    descriptionInput.setText(json.get("description").getAsString());
    locationText.setText(json.get("location").getAsString());
}

private void saveChanges() {
    // TODO: Post updated data to backend API
    // TODO: Show network loading indicator
}
```

### 2. Add Gson Import to ItemRepository

Already done in latest commit. Verify:
```java
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
```

### 3. Network Permissions

Ensure `AndroidManifest.xml` has internet access (already present):
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 4. Material Components Dependency

Already added to `build.gradle`. Verify availability:
```gradle
implementation 'com.google.android.material:material:1.11.0'
```

### 5. Background Sync with WorkManager (Optional - Future)

Implement offline sync queue processor:

```java
// Create WorkManager worker
public class SyncWorker extends Worker {
    public Result doWork() {
        List<SyncQueue> pending = database.syncQueueDao().getPendingItems();

        for (SyncQueue queue : pending) {
            try {
                itemRepository.uploadToBackend(queue.payload);
                database.syncQueueDao().update(queue);
            } catch (IOException e) {
                queue.retryCount++;
                if (queue.retryCount > 3) {
                    database.syncQueueDao().delete(queue);
                }
                return Result.retry();
            }
        }
        return Result.success();
    }
}
```

Register worker:
```kotlin
Constraints constraints = new Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build();

PeriodicWorkRequest syncWorker = new PeriodicWorkRequest.Builder(
    SyncWorker.class, 15, TimeUnit.MINUTES)
    .setConstraints(constraints)
    .build();

WorkManager.getInstance(context).enqueue(syncWorker);
```

## Building in Replit

```bash
cd android
./gradlew assembleDebug
```

Be sure to:
1. Wait for Gradle to sync dependencies
2. Run `./gradlew assembleDebug`
3. For testing: `./gradlew installDebug`

## Testing Checklist

- [ ] Build succeeds in Replit
- [ ] GUID `3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f` validates
- [ ] MainActivity opens to no errors
- [ ] ItemDetailsActivity opens with title/input fields
- [ ] FAB save button doesn't crash (validation works)

## Backend Server Required

To fully test, backend (Replit-created FastAPI server) must be running:
```bash
# Start in Replit
cd server
uvicorn identify.api.main:app --reload
```

Then test:
```
http://localhost:8000/api/v1/items/{guid}
```

## Architecture Summary

```
┌────────────────────────────────────────────────────────────────────┐
│                           MainActivity                               │
│  - GUID input validation (Base26 format)                            │
│  - Navigate to ItemDetailsActivity                                   │
└─────────────────────────────┬──────────────────────────────────────┘
                              │ Intent with GUID/URL
                              ▼
                    ┌────────────────────────┐
                    │    ItemDetailsActivity  │
                    │  - View/edit items     │
                    │  - Material Design UI  │
                    │  - Save functionality  │
                    └───────────┬────────────┘
                                │ ItemRepository
                                ▼
              ┌───────────────────────────────────────┐
              │         ItemRepository.java             │
              │  - CRUD database operations            │
              │  - Backend HTTP sync                   │
              │  - Gson JSON parsing                   │
              └──────────────┬────────────────────────┘
                             ▼
              ┌───────────────────────────────────────┐
              │          AppDatabase (Room)            │
              │  - Item and ItemVersion entities       │
              │  - SyncQueue for offline tracking      │
              │  - LiveData for reactive UI            │
              └──────────────┬────────────────────────┘
                             ▼
                   ┌───────────────────┐
                   │    SQLite DB      │
                   └───────────────────┘

Backend (FastAPI) - Located:
- Local dev: http://10.0.2.2:8000 (Replit emulator binding)
- Replit deployment: Replit-provided URL
- Tests at: /docs (Swagger UI)
```

## Key Classes Reference

| Class | Purpose | Status |
|-------|---------|--------|
| MainActivity.java | GUI for GUID entry | ✅ Complete |
| ItemDetailsActivity.java | GUI for item view/edit | ✅ Complete (no data binding) |
| GuidGenerator.java | Base26 encoding | ✅ Complete |
| ItemRepository.java | Data + backend sync | ⚠️ Skeleton (sync methods incomplete) |
| AppDatabase.java | SQLite wrapper | ✅ Complete |
| SyncQueue.java | Offline sync queue | ✅ Complete |
| SyncQueueDao.java | Sync queue DAO | ✅ Complete |
| item_versions.java | Version history entity | ✅ Complete |

## Common Issues

**Build fails with `Gson` import error:**
- Verify `build.gradle` has: `implementation 'com.google.code.gson:gson:2.10.1'`
- Run `./gradlew clean` then build again

**WorkManager not found:**
- Add to `build.gradle`: `implementation 'androidx.hilt:hilt-work:1.2.0'`
- Or skip WorkManager for MVP testing

**Sync fails with 404:**
- Backend not running or URL incorrect
- Check backend logs in Replit
- Verify `10.0.2.2` binding in emulator

## Testing the Complete Flow

1. Start backend server (FastAPI) in Replit
2. Build Android app in Replit
3. Run on emulator
4. Scan or enter GUID: `3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f`
5. Wait for item details (needs backend data binding)
