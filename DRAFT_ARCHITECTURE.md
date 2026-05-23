# "Identify Everything" - Draft Architecture

## Revision History

* v0.0 - Initial version by GLM 4.7 Flash via Venice.ai
* v0.1 - Partial human review (Sections 1 & 2)
* v0.3 - Switch to Python + FastAPI stack (GLM 4.7 Flash Heretic)

## Overview

A mobile-first offline-first system for identifying items in the
physical world using scannable QR codes that map to unique URLs. Data
lives on local SQLite databases on devices, synchronizes to a central
server via periodic uploads, and can be queried/searched via web
interface.

## Core Components

### 1. Client Components

#### Mobile App (Android - MVP)
- QR code scanning with fallback to manual string entry
- Local SQLite database for full data persistence
- Offline sync queue
- Background sync when network is available
- Conflict resolution (newer timestamp wins)
- WebDAV-compatible sync protocol (HTTP+JSON)

Key features:
- Sync only changed records (incremental sync)
- Store last 3 versions per item by default (server keeps all)
- Store attachments locally in device filesystem
- Search entire local database for offline queries
- Background sync when network is available
- Easy configuration through environment variables

#### Server Components

**API Server** (Python + FastAPI)
- REST API powered by FastAPI with async support
- Sync endpoint that receives change sets
- Search endpoint for JSON queries
- Version history endpoint
- Attachment storage (local disk or S3-compatible)
- Built-in OpenAPI/Swagger documentation via FastAPI

**Label Generator** (Python-based)
- Generate QR codes with formatted URLs using python-qrcode
- Batch label generation capability
- Personalized labels (serial numbers, custom data)
- Output for Avery labels (A4, 2x1") or thermal printers
- Command-line interface: `python -m identify.labelgen --count=500 --format=avery`

#### Web Interface (Future)

- Read-only search interface
- Display item metadata and history
- Manually upload attachments, select location, etc.

### 2. Data Model

#### Device
```sql
devices {
  device_id: TEXT PRIMARY KEY,          // UUID generated on first install
  device_name: TEXT,                    // User-provided name
  last_sync_at: TIMESTAMPTZ,
  sync_token: TEXT                      // For incremental sync tracking
}
```

#### Item
```sql
items {
  item_id: UUID PRIMARY KEY,
  guid: TEXT UNIQUE NOT NULL,           // Base26-encoded identifier
  url: TEXT UNIQUE NOT NULL,            // https://{domain}/objects/v1/{guid}
  domain: TEXT NOT NULL,                // User's DNS domain
  schema_type: TEXT,                    // e.g., "generic", "book", "clothing", "has_serial"
  created_at: TIMESTAMPTZ,
  deleted: BOOLEAN DEFAULT FALSE,
  deleted_at: TIMESTAMPTZ               // For soft delete
}
```

#### Item Version
```sql
item_versions {
  version_id: UUID PRIMARY KEY,
  item_id: UUID REFERENCES items(item_id),
  device_id: TEXT REFERENCES devices(device_id),
  data: JSONB,                          // Schema-specific metadata
  change_summary: TEXT,                 // Committed change description
  parent_version_id: UUID REFERENCES item_versions(version_id),  // For chain
  created_at: TIMESTAMPTZ,
  is_canonical: BOOLEAN                  // Latest version per device
}
```

#### Attachment
```sql
attachments {
  attachment_id: UUID PRIMARY KEY,
  version_id: UUID REFERENCES item_versions(version_id),
  filename: TEXT,
  file_path: TEXT,                      // Local storage path on device
  content_hash: TEXT,                   // SHA-256
  size_bytes: INTEGER,
  mime_type: TEXT
}
```

#### Sync Record (for tracking)
```sql
sync_records {
  record_id: UUID PRIMARY KEY,
  device_id: TEXT REFERENCES devices(device_id),
  created_at: TIMESTAMPTZ,
  status: TEXT                          // "pending", "sent", "received"
}
```

### 3. GUID Generation

**Base26 Encoding**: Use standard Base26 with `FreeBSD-5` convention (a=0, z=25)
- Convert to Base26 with `_` as visual separator every 4 characters
- Generates 28-character codes from 128-bit UUIDs

**Example**:
```
UUID: 00000000-0000-0000-0000-000000000000
Encoding: _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _
```

**Algorithm**:
```python
base26 = "0123456789abcdefghijklmnopqrstuv"
guid_hex = uuid.uuid4().hex
guid_base26 = ''.join([base26[int(d, 16)] for d in [guid_hex[i:i+2] for i in range(0, 32, 2)]])
guid_with_separators = '_'.join([guid_base26[i:i+4] for i in range(0, len(guid_base26), 4)])
```

### 4. URL Format

```
https://{domain}/objects/v1/{guid}
```

- `{domain}` = User's DNS domain (e.g., `mylabels.example.com`)
- Records retrieved via HTTP GET with query parameter `?version={timestamp}`
- GET without `?version={timestamp}` retrieves latest version in DB
- No authentication required for public read access
- Write access through sync protocol

### 5. Sync Protocol

**Incremental Sync**:

1. Client sends: `GET /sync/request?after={last_timestamp}` with auth token
2. Server responds: `{ sync_token, changes: [...] }`
3. Server data structure for changes:
   ```json
   {
     "sync_token": "generated-token",
     "changes": {
       "items_added": [
         { "item_id": "...", "data": {...}, "timestamp": "..." }
       ],
       "items_deleted": ["item_id"],
       "versions_updated": [
         { "version_id": "...", "item_id": "...", "timestamp": "..." }
       ]
     }
   }
   ```

4. Client processes changes:
   - `items_added`: Insert into local DB
   - `items_deleted`: Mark as deleted or hard delete
   - `versions_updated`: If client has older version, fetch full version record

5. Client uploads local changes:
   ```
   POST /sync/upload
   Headers: X-Device-Id: {device_id}, X-Sync-Token: {token}
   Body: {
     "changes": {
       "item_versions": [
         { "item_id": "...", "data": {...}, "change_summary": "...", "parent_version": "..." }
       ],
       "attachments": [...]
     }
   }
   ```

**Conflict Resolution**:
- Each version tagged with `device_id`, `created_at`, `parent_version`
- For same item_id, client decides canonical based on:
  - Downloaded `is_canonical` flag
  - If both devices claim new, newer `created_at` wins
  - Simpler: server tracks per-item canonical version, client updates if newer

### 6. Label Generator

**QR Code Generation**:

Output:
```
{
  "qr_code_url": "https://api.qrserver.com/v1/create-qr-code/?size=256x256&data=...&color=000000&bgcolor=FFFFFF",
  "raw_data": "https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d",
  "guid": "3k7x9b_p1j4_nv6d",
  "domain": "mylabels.example.com",
  "is_custom": false
}
```

**Label Formats**:

- Avery 5160 (2" × 0.75", 30 per sheet)
- Thermal printer (58mm/80mm)
- Custom sticky labels (user formats)

**Batch Processing**:
- Generate N random GUIDs
- Output CSV with QR code URLs, raw data, domain
- CLI: `npm run labels -- --count=500 --format=avery`
- Web interface: Upload spreadsheet, generate QR codes

### 7. Mobile Data Storage (SQLite)

**Schema**:

```sql
CREATE TABLE local_items (
  item_id TEXT PRIMARY KEY,
  guid TEXT UNIQUE NOT NULL,
  url TEXT NOT NULL,
  schema_type TEXT,
  deleted BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ,
  last_local_sync TIMESTAMPTZ
);

CREATE TABLE local_item_versions (
  version_id TEXT PRIMARY KEY,
  item_id TEXT,
  data JSONB,
  change_summary TEXT,
  parent_version TEXT,
  device_id TEXT NOT NULL,
  created_at TIMESTAMPTZ,
  is_canonical BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (item_id) REFERENCES local_items(item_id)
);

CREATE TABLE local_attachments (
  attachment_id TEXT PRIMARY KEY,
  version_id TEXT,
  filename TEXT,
  file_path TEXT,
  content_hash TEXT,
  size_bytes INTEGER,
  mime_type TEXT
);

CREATE TABLE sync_queue (
  record_id TEXT PRIMARY KEY,
  item_id TEXT,
  version_id TEXT,
  operation TEXT,  -- "create", "update", "delete"
  payload JSONB,
  created_at TIMESTAMPTZ,
  status TEXT DEFAULT "pending",  -- pending, sent, failed
  retry_count INTEGER DEFAULT 0
);

CREATE TABLE offline_records (
  guid TEXT PRIMARY KEY,
  location_lat REAL,
  location_lon REAL,
  last_modified TIMESTAMPTZ,
  remote_updated TIMESTAMPTZ,
  last_seen_device TEXT
);
```

### 8. Offline Behavior

**Initial Setup**:

1. User scans QR code
2. Mobile app decodes GUID
3. App checks local DB for existing record
4. If no record, attempts HTTP GET to fetch metadata
5. Saves to local DB with `offline_records` table

**Offline Edit**:

1. User edits item without network
2. Data saved to `local_item_versions` with `pending=true` flag
3. Attachment saved to local filesystem
4. `sync_queue` record created with `status=pending`
5. Background worker handles upload when network reconnects

**Location Tagging**:

- `loc://{lat},{lon}/{guid}` encoded in QR (optional alt format)
- Mobile app auto-derives location via GPS
- When online, sync server normalizes to standard URL format

### 9. Synchronization Flow

**Online → Download Any Updates**:
1. Check `offline_records.last_seen_device` against own `device_id`
2. If device mismatch, check remote `created_at` vs local `last_modified`
3. If remote newer, pull full version record
4. Merge into local DB, update `last_seen_device`, set `is_canonical=true`

**Online → Upload Local Changes**:
1. `sync_queue` records with `status=pending` processed sequentially
2. POST to `/sync/upload` with device_id and sync_token
3. Server validates, stores version with device attribution
4. Sets status to `sent`, updates `last_local_sync`

**Conflict Resolution**:

- Server stores all versions with timestamps and device_id
- User can view all versions per item via web interface
- Client default: newer timestamp wins
- "Merge" view allows selecting specific version elements

### 10. Server Design for MVP

**Technology Stack**:

- **Backend Framework**: Python 3.11+ with FastAPI
- **Database**: PostgreSQL (production), SQLite (dev) via SQLAlchemy ORM
- **Data Validation**: Pydantic for request/response validation (type-safe)
- **Queue**: Skip for MVP (process sync synchronously). For larger deployments: Redis + Celery with default concurrency
- **Storage**: Local filesystem (uploads/) or S3-compatible storage (MinIO, AWS S3)
- **Search**: PostgreSQL tsvector for full-text search via SQLAlchemy
- **API**: REST with device_id + sync_token authentication
- **API Documentation**: FastAPI auto-generated (Swagger UI, ReDoc)

**Why SQLAlchemy instead of raw SQL?**

For MVP with 10 users, writing raw Python with psycopg2 (direct SQL) is possible but introduces:
- Manual migrations and schema management
- No object-relational mapping benefits
- More boilerplate code
- Harder to switch databases later

SQLAlchemy gives you:
- ORM for expressive queries without raw SQL
- Alembic for schema migrations
- Different database backends with minimal changes
- Built-in lazy loading, relationships, and validation

For alignment with local SQLite on devices, we'll use: `SQLAlchemyConnection` with separate SQLite/PostgreSQL engines for local and remote

<div class="section-break"></div>

**Docker Setup**:
```yaml
services:
  api:
    build: .
    ports: ["8000:8000"]
    environment:
      - DATABASE_URL=postgresql://app:secret@db:5432/identify
      - UPLOAD_DIR=/app/uploads
    volumes:
      - uploads:/app/uploads

  db:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data

  worker:
    build: .
    command: celery -A app.worker worker --loglevel=info
    depends_on: [api, db, redis]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  uploads:
  postgres_data:
```

**Queue Decision for MVP**:
For 10 users or less, skip Celery entirely. Process `/sync/upload` synchronously in the API handler. It's simpler, uses fewer resources, and eliminates worker management during development. Add Celery + Redis once you need background processing for large attachments (>5MB) or want to test parallel sync processing.

**API Endpoints**:

```
GET    /api/v1/items/:guid                 # Get latest item version
GET    /api/v1/items/:guid/versions       # Get all versions
GET    /api/v1/items/sync?after=...        # Get changes for sync
POST   /api/v1/sync/upload                 # Upload local changes (device_id required)
GET    /api/v1/search?q=...                # Search items (full-text search)
POST   /api/v1/items/:guid/attach          # Upload attachment (multipart/form-data)
GET    /api/v1/items/:guid/version/:vid    # Get specific version
```

Endpoints are automatically documented with Swagger UI at `/docs` and ReDoc at `/redoc`

### 11. Error Handling

**Sync Errors**:
- Network failure: Mark sync_queue as `pending`, retry after backoff
- Server unavailable: Try later
- Conflicting version: Use newer timestamp as winner, notify user

**Offline Recovery**:
- Connection drop mid-upload: Resume from last failure point
- App crash: No data loss (SQLite writes are atomic by default)

**Data Corruption**:
- Version integrity: Ensure parent_version exists in local DB
- Item existence: Ensure item_id points to valid local_items record

### 12. Security Considerations

**Authentication**:
- Public read access to items
- Sync requires device_id and sync_token
- Token issued on first sync after manual user approval (OTP or password)

**Data Privacy**:
- `created_at` publicly visible in URL history
- `data` JSONB contains user-submitted content (no PII unless user adds it)
- GPS location attached to items (user can edit/remove)

**Access Control**:
- Future: Per-item ownership (multiple devices can have read access, owner controls write)
- Currently: Single device per sync token

### 13. Scaling Considerations

**Current Load (MVP)**:
- 10-20 concurrent users
- SQLite fine for up to 100k items per device
- PostgreSQL single-instance sufficient for <100K total items
- All-PostgreSQL can scale horizontally with read replicas

**Vertical Scaling**:
- Add PostgreSQL read replicas for `/search` endpoint
- Add CDN for label generation API
- Add caching (Redis) for frequently-accessed items

**Horizontal Scaling**:
- Sharding by item_id ranges or geographical region
- Kong/Envoy API gateway for load balancing
- Microservices: sync service separate from API service
- Event-driven: SQS for async processing of sync upload

**Database Migration Path**:
```
SQLite local dev
  ↓
PostgreSQL development instance (docker-compose)
  ↓
PostgreSQL single server (AWS RDS/managed)
  ↓
Sharded PostgreSQL (>=100K items)
  ↓
Microservices + Event-driven architecture
```

### 14. Testing Strategy

**Unit Tests**:
- GUID generator
- Base26 encoder/decoder
- Sync protocol parsing
- Conflict resolution logic

**Integration Tests**:
- CLI label generator
- API endpoints
- SQLite schema manipulation
- Sync flow end-to-end

**Mobile App**:
- Manual testing on offline device
- Background sync simulation
- GPS location tagging

**Performance Tests**:
- Generate 10k random items, verify sync performance
- Simulate 100 concurrent syncs
- Database query optimization

### 15. Future Enhancements

**Post-MVP**:
- iOS mobile app
- Web interface with search/filter
- Better conflict resolution UI
- Per-item access control (multiple devices)
- Peer-to-peer sync between clients

**Long-term**:
- Neural network for category prediction
- OCR for scanning barcodes
- GPS-based discovery (scan QR, nearby items displayed)
- Export/import to CSV/JSON
- Integration with existing inventory systems (Jira, Evernote)

## Implementation Phases

**Phase 1: MVP** (Weeks 1-4)
- QR code generator (web + CLI)
- Server API scaffolding
- SQLite schema on mobile
- Basic sync protocol
- Manual sync workflow

**Phase 2: Polish** (Weeks 5-8)
- Mobile app with offline editing
- Background sync worker
- Search functionality
- Error handling improvements

**Phase 3: Beta** (Weeks 9-12)
- Public MVP release
- User feedback loop
- Performance optimization
- Documentation

## Success Metrics

- Users can scan QR code and view/edit item data offline
- At least 50% of edits persist after lost data/device failure
- Sync completes within 5 minutes for 100 changes
- Label generation works for Avery and thermal printers
- Search works offline with full-text capabilities
