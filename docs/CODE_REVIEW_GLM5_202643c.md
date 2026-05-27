# Code Review — Identify Everything

**Reviewer**: GLM 5.1
**Date**: 2026-05-27
**Branch**: `expo-mobile`
**Scope**: Full codebase — server, mobile, docs, CI

---

## Summary

| Dimension | Score | Notes |
|-----------|-------|-------|
| Correctness | 4/10 | Two data-corruption bugs, health endpoint broken, sync token broken |
| Client/Server Compatibility | 5/10 | Three contract mismatches, GUID charset divergence |
| Security | 4/10 | No auth on attachments, no upload size limit, path traversal risk |
| Code Quality | 6/10 | Reasonable structure but AI artifacts (unused imports/deps), no pagination |
| Documentation Accuracy | 3/10 | AGENTS.md describes Kotlin app; Alembic/CI steps are fictional |
| Test Coverage | 6/10 | Good integration test suite but GUIDs in tests are invalid per own spec |

**Overall: 5.5/10** — Functional MVP shell with serious latent bugs and documentation drift.

---

## Critical Bugs

### BUG-1: Sync upload uses client `item_id` as server PK — data corruption risk

**File**: `server/identify/services/sync_service.py:33-40`

When a mobile device uploads an item that doesn't exist on the server, `process_sync_upload` creates `Item(item_id=item_id, ...)` using the mobile's local UUID as the server primary key. If two devices create the same GUID offline, the second device's upload hits the `existing` branch (matched by GUID), but the `ItemVersion` on line 50 still uses the *mobile's* `item_id` as the FK — which may differ from the server item's actual PK.

**Fix**:
1. After the `if existing: item = existing` block, always use `item.item_id` (the server PK) when creating `ItemVersion`, never the client-supplied `item_id`.
2. Never set `Item.item_id` to the client's value. Always let the server generate it via `default=lambda: str(uuid.uuid4())`.
3. Store the client's local ID in a separate column (e.g., `client_item_id`) if needed for idempotency.

```python
# In sync_service.py, process_sync_upload():
if not item:
    guid = version_data.get("guid")
    url = version_data.get("url")
    domain = version_data.get("domain", "unknown")
    if not guid:
        continue

    existing = db.query(Item).filter(Item.guid == guid).first()
    if existing:
        item = existing
    else:
        item = Item(                          # Let item_id auto-generate
            guid=guid,
            url=url or f"https://{domain}/objects/v1/{guid}",
            domain=domain,
        )
        db.add(item)
        db.flush()

# Always use the SERVER's item_id for the version FK:
version = ItemVersion(
    version_id=str(uuid.uuid4()),
    item_id=item.item_id,                      # ← server PK, not client's
    device_id=device_id,
    data=version_data.get("data", {}),
    change_summary=version_data.get("change_summary"),
    parent_version_id=version_data.get("parent_version"),
    is_canonical=True,
)
```

---

### BUG-2: No upload size enforcement

**File**: `server/identify/api/routers/items.py:62`

`file.file.read()` is called with no size check. `Settings.max_upload_size` (5 MB) is defined but never referenced. A single request can exhaust server memory.

**Fix**: Add a size guard before reading the full body.

```python
# In items.py, upload_attachment():
MAX_UPLOAD = settings.max_upload_size  # already defined in config

@router.post("/{guid}/attach", response_model=AttachmentOut, status_code=201)
def upload_attachment(guid: str, file: UploadFile = File(...), db: Session = Depends(get_db)):
    item, version = item_service.get_item_with_version(db, guid)
    if not item:
        raise HTTPException(status_code=404, detail="ITEM_NOT_FOUND")
    if not version:
        raise HTTPException(status_code=404, detail="VERSION_NOT_FOUND")

    data = file.file.read()
    if len(data) > MAX_UPLOAD:
        raise HTTPException(status_code=413, detail=f"File exceeds {MAX_UPLOAD // (1024*1024)}MB limit")

    file_path, content_hash, size = storage_service.save_file(file.filename, data)
    # ... rest unchanged
```

---

### BUG-3: Path traversal / missing validation on file download

**File**: `server/identify/api/routers/items.py:79-92`

`download_attachment` serves `attachment.file_path` directly via `FileResponse`. While `storage_service` constructs paths under `UPLOAD_DIR`, there is no validation that the stored path hasn't been tampered with or that it remains under the upload root.

**Fix**: Add a containment check in `download_attachment`:

```python
# In items.py, download_attachment():
from pathlib import Path
from ...api.config import settings

@router.get("/{guid}/attach/{attachment_id}")
def download_attachment(guid: str, attachment_id: str, db: Session = Depends(get_db)):
    attachment = db.query(Attachment).filter(Attachment.attachment_id == attachment_id).first()
    if not attachment:
        raise HTTPException(status_code=404, detail="ATTACHMENT_NOT_FOUND")
    path = Path(attachment.file_path).resolve()
    upload_root = Path(settings.upload_dir).resolve()
    if not str(path).startswith(str(upload_root)):
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    if not path.exists():
        raise HTTPException(status_code=404, detail="FILE_NOT_FOUND")
    return FileResponse(str(path), media_type=attachment.mime_type or "application/octet-stream", filename=attachment.filename)
```

---

### BUG-4: Health endpoint path mismatch — client tests wrong URL

**File**: `mobile/app/settings.tsx:56` — tests `${url}/api/v1/health`
**File**: `server/identify/api/main.py:52` — serves `/health`

The settings screen's "Test Connection" button will always report failure because it appends `/api/v1/health` but the server exposes just `/health`.

**Fix**: Change `settings.tsx:56` from:
```
const resp = await fetch(`${url}/api/v1/health`, { ... });
```
to:
```
const resp = await fetch(`${url}/health`, { ... });
```

---

### BUG-5: Sync token generated but never persisted on download

**File**: `server/identify/api/routers/sync.py:96`

`sync_download` creates `sync_token = secrets.token_hex(16)` and returns it, but never writes it to the `Device` row. The client saves it locally, but the server doesn't know it — so the next request's `_validate_device` checks against the *original* token, not the one just issued. Token rotation is therefore broken.

**Fix**: After generating the new token, persist it:

```python
# In sync.py, sync_download(), after generating sync_token:
device = db.query(Device).filter(Device.device_id == x_device_id).first()
device.sync_token = sync_token
db.commit()
```

Or: remove the random token generation entirely and return the device's existing `device.sync_token` if rotation is not needed for MVP.

---

### BUG-6: `get_item_versions` can never return 404

**File**: `server/identify/services/item_service.py:34-39` returns `[]` for missing items.
**File**: `server/identify/api/routers/items.py:28` checks `if versions is None`.

`get_item_versions` returns an empty list, never `None`. The 404 check in the router is dead code. Requesting versions for a nonexistent GUID returns `200 []` instead of `404`.

**Fix**: In `item_service.py`, change the function to return `None` when the item doesn't exist:

```python
def get_item_versions(db: Session, guid: str) -> list[ItemVersion] | None:
    item = get_item(db, guid)
    if not item:
        return None
    return (
        db.query(ItemVersion)
        .filter(ItemVersion.item_id == item.item_id)
        .order_by(ItemVersion.created_at.desc())
        .all()
    )
```

---

## Client/Server Incompatibilities

### INC-1: GUID charset divergence

**Client**: `mobile/src/sync/guid.ts:6` — `GUID_PATTERN = /^[a-p0-9]{4}_...$/` — only `[a-p]` and `[0-9]`
**Server**: No validation — accepts any string as GUID
**Tests**: `server/tests/conftest.py:14` — `SAMPLE_GUID = "abcd_1234_efgh_5678"` contains `h` (outside `[a-p]`)

Tests use GUIDs with characters the mobile regex would reject (`h`, `g`, `t`, `s`). The server accepts them freely. A real mobile scan of a valid server GUID containing `q-z` would fail client-side validation.

**Fix**: Either:
- (a) Update the client regex to `[a-z0-9]` if full alphabet is allowed, or
- (b) Add server-side GUID validation to enforce `[a-p0-9]` and fix all test GUIDs to comply.

Recommendation: **(b)** — keep the restricted charset for shorter codes, fix test data.

Test GUIDs that need fixing in `conftest.py` and `test_core_flow.py`:
- `abcd_1234_efgh_5678` → `abcd_1234_efab_5678` (`h`,`g` → `a`,`b`)
- `sync_aaaa_test_0001` → `synb_aaaa_test_0001` (contains `s`,`t`)
- All other test GUIDs containing characters `g-z`

---

### INC-2: `SyncUploadRequest` schema is untyped

**File**: `server/identify/schemas/sync.py:17-18`

```python
class SyncUploadRequest(BaseModel):
    changes: dict[str, list[Any]]
```

`Any` means Pydantic validates nothing inside the request body. The client sends structured `SyncUploadVersion` objects, but the server doesn't enforce the shape.

**Fix**: Replace with typed schema:

```python
class SyncUploadRequest(BaseModel):
    changes: SyncChangesUpload

class SyncChangesUpload(BaseModel):
    item_versions: list[SyncVersionUpload] = []
```

---

### INC-3: `versions_updated` in sync download is always empty

**File**: `server/identify/schemas/sync.py:29` — `versions_updated: list[Any] = []`

Never populated by the server. Not referenced by the client. Dead field.

**Fix**: Remove from `SyncChanges` schema, or implement delta version sync.

---

### INC-4: CamelCase / snake_case mismatch in attachment fields

**Client**: `types.ts` uses `attachmentId`, `mimeType`, `sizeBytes`
**Server**: Returns `attachment_id`, `mime_type`, `size_bytes`

`uploadPhoto()` in `api.ts` manually maps snake→camel. But `apiGetItem()` returns raw server JSON — downstream code accessing `.attachmentId` on a server-fetched attachment gets `undefined`.

**Fix**: Create a single `mapAttachmentFromServer(data: any): AttachmentData` function in `api.ts` and use it everywhere server attachment data is consumed (both in `uploadPhoto` and in the `[guid].tsx` screen where `latest_version.attachments` is mapped).

---

## Security Issues

### SEC-1: No device auth on attachment endpoints

**File**: `server/identify/api/routers/items.py:50-76,79-92,95-109`

All attachment endpoints (upload, download, list, delete) have no `X-Device-Id` / `X-Sync-Token` validation. Any anonymous caller can upload photos to any item or delete attachments.

**Fix**: Add `x_device_id: Optional[str] = Header(None)` and call `_validate_device()` from `sync.py` (or move it to a shared module) in every attachment endpoint.

---

### SEC-2: `delete_attachment` returns 500 for already-deleted files

**File**: `server/identify/api/routers/items.py:106`

`storage_service.delete_file()` returns `False` for both "file not found" and "delete failed". If the file was already removed (crash recovery, manual cleanup), the DB record still exists and the endpoint returns 500.

**Fix**: In `storage_service.py`, distinguish the two cases. Or in the router, check if the file exists before calling delete and treat "already gone" as success:

```python
if not storage_service.delete_file(attachment.file_path):
    if Path(attachment.file_path).exists():
        raise HTTPException(status_code=500, detail="FILE_DELETE_FAILED")
    # File already gone — that's fine, remove the DB record
db.delete(attachment)
db.commit()
```

---

## Code Smells & AI Artifacts

### SMELL-1: Unused imports and dependencies

| Location | Item | Action |
|----------|------|--------|
| `items.py:1` | `import os` (unused) | Remove |
| `items.py:84` | `from pathlib import Path` inside function | Move to top of file |
| `requirements.txt:8` | `python-jose[cryptography]` (unused) | Remove |
| `requirements.txt:9` | `passlib[bcrypt]` (unused) | Remove |
| `requirements.txt:11` | `aiofiles` (unused) | Remove |

---

### SMELL-2: All UUIDs stored as `String` instead of native `UUID`

**File**: `server/identify/models/database.py` — every PK uses `Column(String, default=lambda: str(uuid.uuid4()))`

PostgreSQL has a native `UUID` type that provides DB-level validation, better indexing, and half the storage size.

**Fix**: Change to `Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)` and update all FK columns accordingly. Note: this requires a migration and is a bigger change — can be deferred post-MVP.

---

### SMELL-3: `search_items` has a pointless JOIN

**File**: `server/identify/services/item_service.py:95-103`

Joins `ItemVersion` but only filters on `Item` columns. The join adds overhead and returns duplicates (hence the `.distinct()`).

**Fix**: Remove the join:

```python
def search_items(db: Session, q: str) -> list[Item]:
    pattern = f"%{q}%"
    return (
        db.query(Item)
        .filter(Item.deleted == False)
        .filter(
            (Item.guid.ilike(pattern))
            | (Item.url.ilike(pattern))
            | (Item.domain.ilike(pattern))
        )
        .limit(50)
        .all()
    )
```

---

### SMELL-4: No pagination on any list endpoint

`list_items_since`, `search_items`, and `sync_download` all return unbounded result sets. Acceptable for 10-20 users but will break at scale.

**Fix** (post-MVP): Add `limit`/`offset` query parameters and return a pagination envelope.

---

### SMELL-5: `identify.db` committed to repo

**File**: `server/identify.db` — SQLite database file is tracked in git.

**Fix**: Add `identify.db` to `.gitignore` and `git rm --cached server/identify.db`.

---

### SMELL-6: `README.md/` is a directory, not a file

**File**: `server/identify/README.md/` — exists as a directory (trailing slash in `ls` output).

**Fix**: `rm -rf server/identify/README.md/` and create a proper `README.md` file if needed.

---

## Documentation Issues

### DOC-1: AGENTS.md describes Kotlin/Android app; actual mobile is Expo/React Native

The entire "Android App" section of AGENTS.md (commands, package structure, Kotlin conventions) is stale. The real mobile app lives in `mobile/` using Expo Router + TypeScript.

**Fix**: Replace the Android section with a "Mobile App" section covering the Expo/React Native stack:
- Commands: `cd mobile && npm start`, `npx expo start --android`
- Structure: `mobile/app/` (routes), `mobile/src/sync/` (sync logic), `mobile/src/types.ts`
- Stack: Expo 52, React Native 0.76, TypeScript, expo-router, expo-camera, expo-location

---

### DOC-2: No Alembic migrations exist; CI step will fail

**CI file**: `.github/workflows/backend.yaml:51` — `alembic upgrade head`
**Reality**: No `alembic/` directory, no `alembic.ini`, no migration files.

**Fix**: Either:
- (a) Initialize Alembic (`cd server && alembic init alembic`) and create an initial migration, or
- (b) Remove the `alembic upgrade head` step from CI and note that `init_db()` handles schema creation for MVP.

---

### DOC-3: CI uses undefined pytest flags

**CI file**: `.github/workflows/backend.yaml:55` — `pytest tests/ --env 0`
**CI file**: `.github/workflows/backend.yaml:81` — `--test-api`

Neither `--env` nor `--test-api` are defined in `pytest.ini` or any conftest. CI will fail.

**Fix**: Remove these flags from the CI workflow, or add custom `conftest.py` options:
```python
# conftest.py
def pytest_addoption(parser):
    parser.addoption("--env", default=0, type=int)
    parser.addoption("--test-api", action="store_true")
```

---

### DOC-4: Root `tests/` directory has empty subdirectories and unrunnable test

- `tests/backend/`, `tests/android/`, `tests/label_generator/` — all empty
- `tests/test_guid_utils.js` — standalone Node.js script with no test runner configured

**Fix**: Either populate these with real tests, or remove the empty directories. Add a `"test"` script to root `package.json` if `test_guid_utils.js` is to be used.

---

### DOC-5: Label generator is completely unimplemented

`labelgen/identify/labelgen/__init__.py` is empty. CI workflow `label-generator.yaml` will fail. AGENTS.md and `QR_GENERATOR.md` describe full specs but nothing exists.

**Fix**: Either implement the label generator or remove its CI workflow and mark it as "not yet implemented" in docs.

---

## Recommendation Checklist

Instructions for implementing agent: work through items in priority order. Each item is self-contained. After each fix, run `PYTHONPATH=server python -m pytest server/tests/ -v` to verify no regressions.

### P0 — Must fix before any real use

- [ ] **BUG-1**: Fix sync upload item creation in `server/identify/services/sync_service.py`. Never use client `item_id` as server PK. Always create Item with auto-generated `item_id` and use `item.item_id` (server PK) as the FK for `ItemVersion`. See code example above.
- [ ] **BUG-2**: Add upload size check in `server/identify/api/routers/items.py:upload_attachment`. Read `settings.max_upload_size` and raise `HTTPException(413)` if exceeded. See code example above.
- [ ] **BUG-3**: Add path containment check in `download_attachment`. Resolve both `file_path` and `settings.upload_dir` and verify the file is under the upload root. See code example above.
- [ ] **BUG-4**: Fix health endpoint path in `mobile/app/settings.tsx:56`. Change `/api/v1/health` → `/health`.

### P1 — Should fix before wider testing

- [ ] **BUG-5**: Fix sync token persistence in `server/identify/api/routers/sync.py:sync_download`. After generating `sync_token`, write it to `device.sync_token` and `db.commit()`.
- [ ] **BUG-6**: Fix `get_item_versions` return type in `server/identify/services/item_service.py`. Return `None` when item not found so the router's 404 check works.
- [ ] **SEC-1**: Add device auth to attachment endpoints in `server/identify/api/routers/items.py`. Import `_validate_device` from `sync.py` (or extract to shared module) and call it in `upload_attachment`, `download_attachment`, `list_attachments`, `delete_attachment`.
- [ ] **SEC-2**: Fix `delete_attachment` 500-on-missing-file in `server/identify/api/routers/items.py:106`. Treat "file already gone" as success. See code example above.
- [ ] **INC-1**: Fix GUID charset divergence. Update test constants in `server/tests/conftest.py` and `server/tests/test_core_flow.py` so all GUIDs use only `[a-p0-9_]`. Key replacements: `efgh` → `efab`, any GUIDs containing `s`, `t`, `h`, `g` outside `[a-p]`.
- [ ] **INC-4**: Add `mapAttachmentFromServer()` helper to `mobile/src/sync/api.ts` and use it in `[guid].tsx` where `latest_version.attachments` is mapped from server JSON.

### P2 — Should fix before production

- [ ] **SMELL-1**: Remove unused imports: `os` from `items.py:1`, inline `Path` import → move to top. Remove `python-jose`, `passlib`, `aiofiles` from `server/requirements.txt`.
- [ ] **SMELL-3**: Remove pointless JOIN in `search_items` in `server/identify/services/item_service.py:95-103`. See code example above.
- [ ] **SMELL-5**: Add `identify.db` to `.gitignore` and run `git rm --cached server/identify.db`.
- [ ] **SMELL-6**: Remove `server/identify/README.md/` directory.
- [ ] **INC-2**: Type the `SyncUploadRequest` schema in `server/identify/schemas/sync.py`. Replace `dict[str, list[Any]]` with proper nested models. See code example above.
- [ ] **INC-3**: Remove dead `versions_updated` field from `SyncChanges` in `server/identify/schemas/sync.py:29`.
- [ ] **DOC-1**: Update `AGENTS.md` "Android App" section to describe the Expo/React Native mobile app in `mobile/`.
- [ ] **DOC-2**: Either initialize Alembic or remove `alembic upgrade head` from `.github/workflows/backend.yaml:51`.
- [ ] **DOC-3**: Remove `--env 0` and `--test-api` from CI workflow, or add `pytest_addoption` definitions to `conftest.py`.

### P3 — Nice to have / post-MVP

- [ ] **SMELL-2**: Migrate UUID columns from `String` to native `UUID` type in `server/identify/models/database.py`. Requires Alembic migration.
- [ ] **SMELL-4**: Add pagination to `list_items_since`, `search_items`, and `sync_download` endpoints.
- [ ] **DOC-4**: Clean up root `tests/` — remove empty dirs, add test runner for `test_guid_utils.js`.
- [ ] **DOC-5**: Implement label generator or remove its CI workflow and mark as deferred in docs.
