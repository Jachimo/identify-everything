---
name: GPS + Photo + Offline-Sync implementation
description: How GPS tagging, photo attachments, and offline-first sync are implemented in the mobile app.
---

## GPS tagging
- Captured at scan time (`scan.tsx`) using `expo-location` with `Accuracy.Balanced`; lat/lng/acc passed as URL params to details screen
- Also capturable on-demand on the details screen (`captureLocation()`) with `Accuracy.High`
- Stored in `item.data.location` (LocationData object) — no server schema change needed, the `data: JSON` field is flexible

## Photo attachments
- Uses `expo-image-picker` `launchCameraAsync()` (not expo-camera takePicture) — simpler UX, handles permissions automatically
- Tries immediate upload to `POST /api/v1/items/{guid}/attach` as multipart FormData
- If offline: stored as `PendingPhotoUpload` in AsyncStorage queue
- Server stores files on disk (upload_dir); new `GET /api/v1/items/{guid}/attach/{attachment_id}` endpoint added to serve files as `FileResponse`
- Photo display uses constructed URL `${serverUrl}/api/v1/items/${guid}/attach/${attachmentId}`

## Offline-first sync (sync-manager.ts)
- `performSync()` — first checks reachability via `/health` (5s timeout), then pushes unsynced items, then uploads pending photos
- Triggered on: AppState change to 'active', periodic interval (2min), explicit "Sync Now" button, after save
- `addSyncListener()` pub/sub pattern — explore tab subscribes to show live sync status badge
- `startSyncManager()` called in `_layout.tsx` on mount

**Why FormData for photos:**
React Native supports `FormData.append('file', { uri, type, name })` for multipart uploads. Must NOT set Content-Type header manually — the browser/RN sets it with the correct boundary.

**Why data.location (not a DB column):**
Server ItemVersion.data is a JSON column supporting arbitrary payloads. Avoids a migration and keeps the schema flexible.
