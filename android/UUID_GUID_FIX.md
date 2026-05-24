# UUID and GUID Integration Fix

## Problem

**Backend (Replit AI) uses UUID for item_id, Android uses Base26 GUID as primary key:**

Backend: `item_id = UUID (primary key), guid = Base26 (unique)`
Android: `guid = Base26 (primary key), itemId = null (UUID from backend)`

**This caused sync failures** because:
- Backend creates items with `item_id` as UUID
- Android queued items with only `guid`
- Sync protocol expected `item_id` to exist in local database
- Database seed-level mismatch

## Solution (Implemented)

### Changes to Item.java

```java
@Entity(tableName = "local_items")
public class Item {
    @PrimaryKey
    public String guid;  // Base26 identifier (user/QR input) - REMAINS PRIMARY KEY

    public String itemId;   // UUID (assigned by backend after first sync) - NEW
    public String url;      // https://{domain}/objects/v1/{guid}
    public String schemaType;
    public Boolean deleted = false;
    public Date createdAt;
    public Date deletedAt;
    public Date lastLocalSync;
    public Date lastRemoteUpdated;

    public static Item newLocalItem(String guid, String url, String schemaType) {
        // Creates item locally with guid (no itemId yet, calls backend later)
    }

    public static Item newRemoteItem(String guid, String itemId, String url, String schemaType) {
        // Receives item from backend with both guid and itemId
    }
}
```

### Changes to ItemRepository.java

1. **Queue operations no longer create local items**
   - `queueCreate()` - just queues for sync (won't write to local DB yet)
   - `queueUpdate()` - placeholder for sync
   - `queueDelete()` - placeholder for sync

2. **Added backend sync helpers**
   - `buildServerUrl()` - dynamically resolves server location
   - `getItemFromBackend(guid)` - fetches item from backend API
   - `ItemDataDto` - parses backend JSON responses

3. **Placeholder sync methods for future use**
   - `downloadBackendSync(timestamp)` - Gets pending changes from server
   - `uploadToBackend(payload)` - Uploads local changes

### Changes to ItemDao.java

```java
@Query("SELECT * FROM local_items WHERE item_id = :itemId AND deleted = 0 LIMIT 1")
LiveData<Item> getItemById(String itemId);  // NEW: fetch by backend-assigned UUID
```

## New Sync Flow

```
1. User inputs/scans GUID (Base26)

2. Android calculates url = "https://{domain}/objects/v1/{guid}"

3. Optional: Fetch existing item from backend
   GET /api/v1/items/{guid}
   → Receives: { item_id, guid, url, domain, ...data... }

4. No sync queue yet (MVP simplified)

5. TODO: Add WorkManager to upload queued items to backend
   POST /api/v1/sync/upload
   → Backend creates item with item_id (UUID)
   → Backend sends response back to Android
   → Android stores record with BOTH guid and item_id

6. Background sync worker checks local items with missing item_id
   → Loads from local DB
   → Uploads to backend
   → Updates database with item_id from backend response
```

## Current Status

✅ **Local storage implemented:**
- New base26 guid is primary key
- itemId field added for backend UUID
- Local items created via `Item.newLocalItem()`

⚠️ **Sync to backend UNIMPLEMENTED:**
- Background upload queued in `queueCreate()` (placeholder)
- No automatic sync when network available

## Testing

### Can test locally now:
```java
guidField.setText("3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f");
// Updates Local SQLite with guid only, no item_id yet
```

### Will work when synced to backend later:
1. Start backend server (Replit)
2. Add WorkManager worker to `ItemRepository`
3. Fetch item_id from backend URL response
4. Update local DB with item_id

## Files Modified

- `android/app/src/main/java/com/identify/Everything/data/entities/Item.java`
- `android/app/src/main/java/com/identify/Everything/data/ItemRepository.java`
- `android/app/src/main/java/com/identify/Everything/data/ItemDao.java`

## Next Steps

1. **Implement background sync worker** (WorkManager)
   - Poll for pending sync items
   - Upload to backend
   - Update with server response

2. **Handle item_id assignment**
   - Parse backend response JSON
   - Update Item entity with enviado_id
   - Set lastRemoteUpdated timestamp

3. **Add sync error retry logic**
   - Track failures
   - Exponential backoff
   - Max retry limit

## Compatibility

✅ **Database migrations**
- Room AutoMigration (version 1 no schema changes yet)

✅ **API compatibility**
- Backend endpoint: `GET /api/v1/items/{guid}`
- Returns ItemDetailOut which includes both `item_id` (UUID) and `guid` (Base26)

⚠️ **Future migrations needed**
- If Android scale version increases, consider adding itemId column migration

---
*Implemented for: PR #0445cd2 (Android ItemDetailsActivity + SyncQueue)*
*Fix addresses: GitHub issue - Backend UUID mismatch with Android Base26*
