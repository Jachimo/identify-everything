# Code Review: Identify Everything

**Reference commit:** `ff86700` (package rename to `io.github.jachimo.identifyeverything`)  
**Author:** Senior Engineer  
**Date:** 2026-05-24  

---

## Documentation

### docs/DRAFT_ARCHITECTURE.md — 555 lines

Well-structured architecture document. Three issues:

1. **Revision history is stale** — v0.4 is the newest entry, but MVP scope was locked at v0.5 and the backend has been implemented (by Replit AI). The doc says the server "requires implementation" but it's now fully coded.

2. **Conflict resolution gap** — The design says "newer timestamps win" (line 23) but the implementation stores all versions and returns `is_canonical`. These are compatible, but conflict resolution on the client is not implemented anywhere. The design assumes it works, but the Android app has no merge logic.

3. **URL/model for item_id vs guid** — The doc describes `item_id: UUID PRIMARY KEY` and `guid: TEXT UNIQUE` correctly. Backend matches. The earlier UUID/GUID integration bug was Android-side.

### docs/QR_GENERATOR.md — 696 lines

Complete and polished spec. Matches the implemented code well. No issues.

### README.md — 272 lines

Clean. References QR_GENERATOR.md correctly.

### AGENTS.md — 474 lines

Thorough. No issues found.

---

## Backend Server — Bugs

### BUG-1: Sync upload silently drops items (P0: data loss)

**File:** `services/sync_service.py:52-54`

```python
item = db.query(Item).filter(Item.item_id == item_id).first()
if not item:
    continue  # ← silently discards the entire version update!
```

If an Android device uploads a version for an item that doesn't exist on the server (e.g., created offline before sync), the version is **silently discarded** with no error. The client gets `{"status": "ok", "processed": 0}` and has no way to know the upload failed.

**Fix:** Return an error response or create the item automatically. The sync protocol must handle "item doesn't exist yet" because offline-first means Android creates items locally before the server knows about them.

### BUG-2: Async/sync mismatch in attachment upload (P1: performance)

**File:** `routers/items.py:50-76`

```python
async def upload_attachment(...):
    data = await file.read()
    file_path = await storage_service.save_file(...)
    db.add(attachment)    # ← synchronous SQLAlchemy
    db.commit()           # ← synchronous SQLAlchemy
```

This route is declared `async def` while using synchronous SQLAlchemy calls. This **blocks the event loop** for database operations on the main thread.

**Fix:** Remove `async` from the route declaration, or switch to SQLAlchemy's async session.

### BUG-3: Sync upload doesn't validate sync_token (P1: security)

**File:** `routers/sync.py:65-76`

The route receives `x_sync_token: Optional[str] = Header(None)` but **never checks it against the device's stored token**. The `process_sync_upload` function also doesn't validate — it accepts any `device_id` and creates versions.

**Fix:** Validate `x_sync_token` against `Device.sync_token` before processing the upload.

### BUG-4: Avery 64510 label margins are wrong (P2: physical print offset)

**File:** `labelgen/identify/labelgen/formats.py:17-18`

```python
MARGIN_LEFT_IN = 0.75
MARGIN_TOP_IN = 0.5
```

Avery 64510 spec requires 0.25" margins, not 0.75" left. With 0.75" left margin + 2" labels × 4 columns, total width = 0.75 + 8.0 = 8.75" on an 8.5" page. The right column will be cut off.

**Fix:**
```python
MARGIN_LEFT_IN = 0.25
MARGIN_TOP_IN = 0.25
```

---

## Backend Server — Bad Patterns

### PATTERN-1: CORS misconfiguration (P2: deployment gotcha)

**File:** `main.py:32-38`

```python
allow_origins=["*"],
allow_credentials=True,  # ← incompatible with "*"
```

`allow_credentials=True` with `allow_origins=["*"]` is invalid per the Fetch spec. FastAPI may not enforce this strictly, but it's wrong.

**Fix:** Use `allow_origins=["*"], allow_credentials=False` for dev, or specify concrete origins.

### PATTERN-2: Sync token rotation never implemented

**File:** `services/sync_service.py`

Tokens are generated on registration but **never refreshed/rotated**. Combined with BUG-3 (not validating them), tokens are unused noise. If validation is added later, lack of rotation means compromised tokens are permanent.

**Fix:** Add token validation now. Implement `last_sync_at` tracking for rotation decisions.

### PATTERN-3: Search is not full-text search (P2: architecture mismatch)

**File:** `services/item_service.py:131-145`

```python
pattern = f"%{q}%"
...ILike(Item.guid)...  # simple LIKE, not tsvector
```

The architecture doc specifies PostgreSQL `tsvector`, but the implementation is a simple LIKE search. This:
- Is extremely slow on > 1,000 items (full table scan)
- Does not search inside the JSON `data` field (where descriptions live)
- Does not produce relevance-ordered results

**Fix:** Implement PostgreSQL `tsvector` on `Item.url`, `Item.guid`, and `ItemVersion.data`.

### PATTERN-4: Config mixes pydantic-settings with os.environ

**File:** `api/config.py:6`

```python
database_url: str = os.environ.get("DATABASE_URL", "sqlite:///./identify.db")
```

`pydantic-settings` reads env vars automatically. Using `os.environ.get` defeats that mechanism. Also, `sqlite:///` default is inappropriate when PostgreSQL is the production target — a developer running without `DATABASE_URL` set gets silent SQLite.

**Fix:** Remove `os.environ.get` and let pydantic-settings do its job:
```python
database_url: str = "sqlite:///./identify.db"  # dev default only
```

### PATTERN-5: UUID columns are strings, not UUID type

**File:** `models/database.py`

All ID columns are `Column(String)` with `str(uuid.uuid4())`. PostgreSQL stores these as `TEXT` instead of `UUID` type:
- Uses more storage (37+ bytes string vs 16 bytes native)
- Loses type validation at DB level
- Prevents index optimization

**Fix:** Use `Column(UUID)` from `sqlalchemy.dialects.postgresql`.

### PATTERN-6: No Alembic migrations

**File:** `database.py:22-23`

```python
def init_db():
    Base.metadata.create_all(bind=engine)
```

Combined with Android's `fallbackToDestructiveMigration()`, migrations are entirely manual. Both server and client use "create schema from models" without migration tracking. This causes production data loss if models change.

**Fix:** Use Alembic for server migrations. Implement Room migration paths for Android.

### PATTERN-7: No pagination on list/search endpoints

`list_items_since` returns all matching items with no limit. With 10,000 items, this returns 10,000 records in a single response.

### PATTERN-8: N+1 query in sync download

**File:** `routers/sync.py:40-45`

```python
for item in items:
    version = db.query(ItemVersion)...  # N+1 query!
```

**Fix:** Single batch query:
```python
canonical = (
    db.query(ItemVersion)
    .filter(
        ItemVersion.item_id.in_([item.item_id for item in items]),
        ItemVersion.is_canonical == True
    )
    .all()
)
```

### PATTERN-9: No transaction rollback on sync failure

**File:** `services/sync_service.py:42-81`

If the function crashes partway through, the `SyncRecord` is not created but some items may have been processed. No rollback or partial processing indicator. Combined with BUG-1 (silent skip), synchronization state is unreliable.

### PATTERN-10: Attachment deletion ignores file deletion result

**File:** `services/item_service.py:90-92` and `storage_service.py:29-34`

`delete_file()` returns a boolean, but the router ignores it. If the file doesn't exist on disk, the DB attachment record is still deleted (orphan). If the file exists but `unlink()` fails, the DB record is gone anyway (dangling file).

---

## Android App — Critical Issues

1. **GUID regex doesn't match actual format** — `GuidGenerator.java` pattern `^[a-p0-9]{28}$` validates for 28 chars without separators, but real GUIDs have underscores: `3k7x9b_p1j4_nv6d`. The pattern will reject valid GUIDs.

2. **Sync methods are placeholders** — `queueCreate()`, `uploadToBackend()`, `downloadBackendSync()` all either skip or do nothing. Items store locally but never sync.

3. **SyncQueue entity is dead code** — Created in database, DAO methods exist, but no code reads from it or writes to it.

4. **Will not produce a working offline-first product** — Without the sync bridge, the app is a local-only data entry form with no connection to the backend.

---

## Label Generator

Solid, well-structured code. One bug noted above (BUG-4: Avery margins). The `generate_sheet_pdf()` function is well-written with proper canvas handling and CSV parsing.

Minor: `generate_sheet_pdf` creates a separate canvas per page but calculates image sizing correctly.

---

## Quality Score

| Component | Score | Rationale |
|-----------|-------|-----------|
| Documentation | 9/10 | Complete, well-structured, minor staleness |
| Backend Models | 8/10 | Good schema, string UUID types hurt PG |
| Backend Services | 6/10 | Sync has silent failures, no token validation |
| Backend Routers | 7/10 | Clean endpoints, async/sync mismatch |
| Android App | 4/10 | Sync unimplemented, GUID regex wrong, dead code |
| Label Generator | 7/10 | Correct code, wrong Avery margins |

**Overall: 6/10** — Functional surface, but sync protocol has correctness bugs that prevent offline-first from working. Backend is deployable but will silently lose data in sync scenarios.

---

## Priority Fixes

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| P0 | BUG-1: Sync silently drops items | Data loss | 1 day |
| P0 | Android GUID regex wrong | App won't validate | 30 min |
| P0 | Android sync placeholders | No actual sync | 3 days |
| P1 | BUG-2: Async/sync in attachment | Performance | 1 hour |
| P1 | BUG-3: Sync token not validated | Security | 1 hour |
| P1 | PATTERN-3: No full-text search | Architecture gap | 2 days |
| P1 | PATTERN-1: CORS misconfig | Deployment gotcha | 15 min |
| P2 | BUG-4: Avery margins wrong | Print offset | 15 min |
| P2 | PATTERN-4-10 | Various tech debt | 3 days |