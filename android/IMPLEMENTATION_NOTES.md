# Identify Everything - Android App

Android mobile client using QR codes for item identification.

## Current Status

**Basic structure implemented** (Java + Coroutines + Room):

- ✅ MainActivity with GUID input form
- ✅ GuidGenerator utility (Base26 encoding)
- ✅ SQLite database (Room) with Item/ItemVersion entities
- ✅ ViewModel layer (ItemRepository)
- ✅ HTTP client for server sync
- ✅ Dependencies: ZXing, Play Services Location, OkHttp
- ⏳ QR scanning not yet integrated
- ⏳ Item details View not yet built
- ⏳ Background sync worker not yet implemented

## How to Continue

1. **Learn current app structure**
   - Explore `MainActivity.java`
   - Review `GuidGenerator.java` (Self-contained, no dependencies)
   - Check `AppDatabase.java` and entities

2. **Add QR Code Scanning** (PREREQUISITE for Android functionality)
   ```java
   IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
   integrator.initiateScan();
   ```
   See ZXing documentation for integration instructions.

3. **Implement item -->
   - Replace Toast in MainActivity with actual navigation
   - Display item metadata (title, location, history)

4. **Add Sync**
   - Background worker with WorkManager
   - Upload local changes to `/api/v1/sync/upload`
   - Download updates from server

## Replit Development

Replit supports Android compilation. To build:

```bash
cd android
./gradlew assembleDebug
```

For testing on device:
```bash
./gradlew installDebug
```

After build, open build APK in emulator or physical device.

## Architecture

```
MainActivity
    ↓
ItemRepository (ViewModel)
    ↓
AppDatabase (SQLite)
    ↓
GuidGenerator (Utility - no dependencies)
    ↓
SyncApiClient (OkHttp - async)
```

## Testing

Build and run basic app:
```bash
./gradlew assembleDebug
```

Test GUID validation:
- Valid: `3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f`
- Invalid: `abc` or `3k7x9b` (too short)

## Next Steps

1. Integrate ZXing QR scanner → Auto-fill GUID field
2. Build ItemDetailsView for viewing/editing
3. Implement WorkManager background sync
4. Test on physical Android device
