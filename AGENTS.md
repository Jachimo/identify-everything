# AGENTS.md - Agent Guide for Identify Everything

## Project Overview

**Identify Everything** is a mobile-first offline-first system for identifying items in the physical world using scannable QR codes. The project consists of three main components in this monorepo:

1. **Backend Server** (`server/`) - FastAPI-based REST API with PostgreSQL database
2. **Label Generator** (`labelgen/`) - Local QR code generator for Avery 64510 labels
3. **Android Client** (`android/`) - QR scanning and offline-first app (Kotlin)

**Current Status**: Architecture is fully specified. Server and Label Generator directories exist but contain minimal/stub code. Android app is in planning/implementation prep.

## Essential Commands

### Backend Server

```bash
# Start PostgreSQL database (docker-compose)
docker-compose up -d db

# Run database migrations
cd server
alembic upgrade head

# Start development server
uvicorn identify.api.main:app --reload

# Run tests
pytest tests/ --cov=identify --cov-report=html

# Run with environment variables
DATABASE_URL=postgresql://user:pass@localhost:5432/identify uvicorn identify.api.main:app
```

**API Endpoints**: http://localhost:8000/docs (Swagger UI), http://localhost:8000/redoc (ReDoc)

### Label Generator

```bash
# Start PostgreSQL for backend testing
docker-compose up -d db

# Generate single QR preview
cd labelgen
pip install python-qrcode[full] reportlab pillow
python -m identify.labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d" \
    --output preview.png

# Generate Avery 64510 batch sheet
python -m identify.labelgen \
    --batch examples/labels.csv \
    --output test_sheet.pdf \
    --rows 3 --cols 4

# Run tests
pytest tests/ -v
pytest tests/ --cov=labelgen --cov-report=html
```

**Dependencies**: `python-qrcode[full]==7.4.0`, `reportlab==4.0.7`, `pillow==10.1.0`

### Android App

```bash
cd android
./gradlew assembleDebug          # Build APK
./gradlew test                    # Unit tests (no emulator)
./gradlew connectedAndroidTest    # Instrumented tests (real device/emulator)
./gradlew connectedDebugAndroidTest
```

**Prerequisites**: Android Studio Hedgehog (2023.1.1+) or newer, JDK 11+, Android SDK API 26+

## Project Structure

```
identify-everything/
├── server/                           # FastAPI backend
│   ├── identify/
│   │   ├── api/                      # FastAPI routers & main app
│   │   ├── models/                   # SQLAlchemy ORM models
│   │   ├── schemas/                  # Pydantic request/response models
│   │   ├── services/                 # Business logic layer
│   │   └── database.py               # Database engine/config
│   ├── requirements.txt              # Python dependencies
│   ├── Dockerfile
│   └── alembic/                      # Database migrations (directory structure)
│
├── labelgen/                         # QR code generator
│   ├── identify/labelgen/
│   │   ├── generator.py              # QR code generation logic
│   │   ├── formats.py                # PDF/SVG format generation
│   │   └── cli.py                    # CLI interface
│   ├── scripts/
│   │   └── generate_labels.py
│   ├── examples/
│   │   └── labels.csv
│   ├── requirements.txt
│   └── Dockerfile
│
├── android/                          # Android mobile app
│   ├── app/
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/identify/Everything/
│   └── build.gradle
│
├── tests/                            # Test suites
│   ├── backend/                      # Server API tests
│   ├── label_generator/              # Label generator tests
│   └── android/                      # Android app tests
│
├── docs/                             # Documentation
├── docker-compose.yml                # Dev environment setup
└── .github/workflows/                # CI/CD pipelines
```

## Architecture & Control Flow

### Data Flow (Server)

```
Client Request → Router → Service → Model → Database
                  ↑                     ↓
                  └──── Pydantic Validation ────┘
```

- **Router Layer**: FastAPI path operations, request/response handling
- **Service Layer**: Business logic, transaction management, business rules
- **Model Layer**: SQLAlchemy ORM, database interactions
- **Pydantic Schemas**: Type validation, serialization, schema definitions

### Sync Protocol

1. **Download Incremental Sync**
   - `GET /api/v1/items/sync?after={timestamp}` with `device_id` and `sync_token`
   - Server responds with changes: `items_added`, `items_deleted`, `versions_updated`

2. **Upload Local Changes**
   - `POST /api/v1/sync/upload` with device_id, sync_token, and version payload
   - Server validates, stores with device attribution and timestamps

3. **Conflict Resolution**
   - Every version tagged with `device_id`, `created_at`, `parent_version_id`
   - Newer `created_at` timestamps win
   - Timestamp-based conflict resolution (not data-based merging)

### Offline-First Client Behavior

1. **Initial Scan**:
   - Decode GUID from QR code
   - Check local SQLite database
   - Attempt HTTP GET to fetch if record missing
   - Save to `offline_records` table

2. **Offline Edit**:
   - Data saved to `local_item_versions` with `pending=true` flag
   - Attachment saved to local filesystem
   - `sync_queue` record created with `status=pending`

3. **Background Sync**:
   - When network available, process `sync_queue` records sequentially
   - Upload to `/api/v1/sync/upload`
   - Update `last_local_sync` timestamp

## Naming Conventions

### Backend (Python)

- **Classes**: PascalCase (e.g., `Item`, `LabelGenerator`, `FqConst`)
- **Functions/Variables**: snake_case (e.g., `generate_qr_code`, `db_session`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_ATTACHMENT_SIZE`)
- **Models**: Singular with `_entity` suffix (e.g., `User_entity`)

### Android (Kotlin)

- **Classes/Files**: PascalCase (e.g., `MainActivity`, `SyncQueue`)
- **Functions/Variables**: camelCase (e.g., `generateGuid`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DATABASE_VERSION`)
- **Package**: `com.identify.Everything`

### Mobile Storage Schema (SQLite)

- **Tables**: Lowercase with `_` (e.g., `local_items`, `item_versions`)
- **Columns**: snake_case (e.g., `created_at`, `is_canonical`)
- **Foreign Keys**: `{table}_{column}` format (e.g., `item_id`, `version_id`)

## Code Organization

### Backend

- **`server/identify/models/`**: SQLAlchemy ORM models for PostgreSQL
  - `database.py`: Engine setup, session factory
  - `item_models.py`: Item, Device, ItemVersion, Attachment models

- **`server/identify/schemas/`**: Pydantic schemas for validation
  - `item.py`: ItemCreate, ItemUpdate, ItemResponse schemas
  - `sync.py`: SyncRequest, SyncResponse, VersionResponse schemas

- **`server/identify/services/`**: Business logic
  - `item_service.py`: CRUD operations, validation
  - `storage_service.py`: File storage operations
  - `sync_service.py`: Sync protocol implementation

- **`server/identify/api/routers/`**: FastAPI endpoints
  - Files named by functionality: `items.py`, `sync.py`, `search.py`
  - Request/response handled by Pydantic schemas

- **`server/identify/database/`**: (Directory structure exists, content TBD)
  - Database configuration and connection management

### Label Generator

- **`identify/labelgen/generator.py`**: QR code generation
  - `LocalQrGenerator` class for encoding and image generation
  - `validate_and_encode_url()` for input validation

- **`identify/labelgen/formats.py`**: Output format generation
  - `PdfGenerator` for Avery 64510 PDF sheets
  - SVG generation for future versions

- **`scripts/generate_labels.py`**: CLI entry point
  - Parses CSV input (`guid`, `provided_url`, `notes`)
  - Handles single and batch generation modes

### Android

- **`com/identify/Everything/data/`**: Data layer
  - `AppDatabase.kt`: SQLite database wrapper
  - `entities/`: Kotlin data classes for tables
  - `ItemRepository.kt`: Data access patterns

- **`com/identify/Everything/util/`**: Utilities
  - `GuidGenerator.kt`: Base26 encoding for GUIDs
  - `SyncApiClient.kt`: HTTP client for server sync

## Testing Approach

### Backend Tests

```bash
# Unit tests
cd server
pytest tests/backend/ --env 0

# Integration tests with Docker
docker-compose up -d
pytest tests/backend/ --test-api

# Coverage
pytest tests/ --cov=identify --cov-report=html
```

**GitHub Actions**: Tests run on Ubuntu and macOS with PostgreSQL service
- `lint`: Black formatting check
- `test`: Unit tests on Ubuntu
- `test-integration`: Integration tests on macOS with API server

### Label Generator Tests

```bash
cd labelgen
pytest tests/label_generator/ -v
pytest tests/label_generator/ --cov=labelgen --cov-report=html
```

Test structure:
- `tests/test_qr_generator.py`: QR generation functionality
- `tests/test_avery_generation.py`: PDF format and Avery 64510 layout
- `tests/test_csv_parsing.py`: CSV input validation

### Android Tests

```bash
cd android
./gradlew test                    # Unit tests (tests/ directory)
./gradlew connectedAndroidTest    # Instrumented tests (AndroidTest directory)
```

**Manual Testing Checklist**:
- [ ] Scan QR code successfully
- [ ] Manual entry fallback
- [ ] Create, edit, view item details
- [ ] Background sync simulation
- [ ] Offline editing
- [ ] GPS location tagging
- [ ] Conflict resolution (two devices)

## Important Gotchas & Patterns

### Server/Backend

1. **No Authentication/Authorization Yet**: Public read access only. Write access requires `device_id` and `sync_token`. Tokens issued on first successful sync after manual user approval.

2. **Black Code Formatting**: Server code MUST be formatted with Black. CI enforces this. Configure Black in project settings.

3. **Database Migrations**: Always use Alembic. Never manually alter schema. `alembic revision --autogenerate -m "description"` for changes, `alembic upgrade head` to apply.

4. **SQLite vs PostgreSQL**: Development can use local SQLite, but PostgreSQL converts/handles certain types/columns differently (TIMESTAMPTZ, JSONB). Use PostgreSQL for production and CI.

5. **Version History vs Soft Deletes**: Items are never physically deleted. Soft delete via `deleted=true`, `deleted_at` timestamp. Always create new `ItemVersion` for changes.

6. **Sync Token Management**: `sync_token` stored in `devices` table. Must be returned on `/api/v1/items/sync` response. Tokens don't rotate - new tokens issued on manual user approval (OTP/password).

7. **Base26 GUID Encoding**: 28-character codes using FreeBSD-5 convention (a=0, z=25) with `_` as separator every 4 characters. Example: `3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f`. Implemented in `GuidGenerator.kt` (Android) and `processor_guid` in architectures.

8. **File Storage**: Attachments stored in local filesystem (default: `/app/uploads`). No database storage for files. Simply store path string, serve from backend.

9. **Error Response Format**: All errors return consistent JSON structure:
   ```json
   {
     "detail": "Error message",
     "code": "ERROR_CODE",
     "timestamp": "2024-05-24T01:00:00Z"
   }
   ```

### Label Generator

1. **MVP Scope Locked**: Currently fixed to 2"x2" Avery 64510 labels, 12 per sheet, thermal printer support deferred. Version 0.5 (May 24, 2026) locked MVP scope - don't add thermal printer functionality in current implementation.

2. **QR Code Quality**: Default 320x320 pixels for better print fidelity. 256x256 is acceptable for reference but don't use for actual labels.

3. **CSV Required Columns**: CSV must have `guid` (required) and optional `url` (derived from database) and `notes`. Missing `guid` causes immediate error.

4. **Single QR Development**: Generate single QR with `--data "url" --output file.png`. This is fastest way to test QR encoding before batch work.

5. **PDF Generation Time**: ~0.8s per sheet (12 labels). For large batches, consider generating per-sheet PDFs rather than one massive PDF.

6. **Output Validation**: Always verify PDF exists and >0 bytes. Integration tests check for `%PDF` header. Manual printing verify all 12 QRs are scannable.

7. **Base64 Encoding**: JSON output encodes QR as base64 in `qr_code` field. Don't manually decode for use - use JSON data structure.

### Mobile (Android)

1. **Base26 GUID in QR**: QR contains URL format, not raw GUID. URL: `https://{domain}/objects/v1/{guid}`. Decode from QR → extract GUID → look up in local DB → fetch version.

2. **Location Encoding**: Optional alternative format: `loc://{lat},{lon}/{guid}` encoded directly in QR. Auto-inferred GPS when available. Server normalizes on sync.

3. **Conflict Resolution**: Client decides canonical version using newer timestamp wins (not merge of fields). Preferred version per device set via `is_canonical` flag. Server tracks all versions.

4. **Attachment Storage**: Attachments saved to device filesystem, path in `local_attachments.file_path`. NOT in local SQLite. Sync includes attachment paths.

5. **Sync Queue**: Pending changes queued with `sync_queue` table. Status: `pending`, `sent`, `failed`. Retry count tracked. Background worker processes when online.

6. **Offline Database**: Local SQLite uses different schema than PostgreSQL (simplified, some features). Data sync pushes local DB differences to server.

7. **Database on Migration**: Local DB schema defined in DRAFT_ARCHITECTURE.md. Don't auto-generate from server - design for offline use.

8. **Kotlin Coroutines**: All async operations use Kotlin Coroutines, not threads. Background sync runs as single coroutine worker.

### General

1. **Monorepo Structure**: All components share root. Backend can import LabelGenerator? Yes, but keep separate processes. Consider microservices later.

2. **Version Numbers**: Project uses semantic versioning (major.minor.patch). Backend follows semantic version, not tracking changes. Separate version tracking.

3. **API Change Management**: Version API at `/api/v1/`. When changing endpoints, increment minor version. Don't alter existing endpoints behavior in breaking ways.

4. **Deployment**: No backend currently deployed. MVP scope for 10-20 users, single VPS. No load balancing, no caching, no microservices currently planned.

5. **Docker Development**: Use docker-compose to spin up PostgreSQL locally. Don't usevelopment PostgreSQL directly when running tests. CI does same pattern.

6. **No Production Secrets**: `SYNC_TOKEN_SECRET` environment variable must change in production. Docker Compose uses placeholder `your-super-secret-sync-token-change-in-production`.

## Configuration

### Environment Variables

**Backend**:
- `DATABASE_URL`: PostgreSQL connection string (required)
- `UPLOAD_DIR`: Attachment storage directory (default: `/app/uploads`)
- `SYNC_TOKEN_SECRET`: Secret for JWT-like sync token generation
- `LOG_LEVEL`: Log level (default: `INFO`)

**Android**: Configured via Gradle build variables, not environment files yet.

### Databases

**PostgreSQL (Server)**:
```sql
-- Key tables
devices (device_id TEXT PRIMARY KEY, device_name TEXT, last_sync_at TIMESTAMPTZ)
items (item_id UUID PRIMARY KEY, guid TEXT UNIQUE, url TEXT UNIQUE, schema_type TEXT)
item_versions (version_id UUID PRIMARY KEY, item_id UUID, device_id TEXT, created_at TIMESTAMPTZ, data JSONB)
attachments (attachment_id UUID PRIMARY KEY, version_id UUID, file_path TEXT, content_hash TEXT)
```

**SQLite (Mobile)**:
```sql
-- Key tables
local_items (guid TEXT PRIMARY KEY, url TEXT NOT NULL, deleted BOOLEAN)
local_item_versions (version_id TEXT PRIMARY KEY, item_id TEXT, data JSONB)
sync_queue (record_id TEXT PRIMARY KEY, item_id TEXT, operation TEXT, payload JSONB, status TEXT)
```

## Relationship Documentation

- **[DRAFT_ARCHITECTURE.md](./DRAFT_ARCHITECTURE.md)** - Complete architecture, data models, sync protocol, scaling considerations
- **[QR_GENERATOR.md](./QR_GENERATOR.md)** - QR code generator specifications, CLI API, testing patterns
- Each component's README.md (server/, android/, labelgen/) for component-specific context

## Quick Commands Reference

```bash
# Backend Development
docker-compose up -d db
cd server && pip install -r requirements.txt && alembic upgrade head
uvicorn identify.api.main:app --reload              # Server at http://localhost:8000

# Label Generator
cd labelgen && pip install python-qrcode[full] reportlab pillow
python -m identify.labelgen --batch examples/labels.csv --output test.pdf

# Android Development
cd android
./gradlew assembleDebug
./gradlew connectedAndroidTest

# Testing
docker-compose up -d && pytest tests/
                              # Tests use PostgreSQL from docker-compose

# Cleanup
docker-compose down
rm -rf server/uploads/ labelgen/extras/
```

### Development Environment (Replit)

**Replit Setup** (recommended for main development environment):
1. Go to https://replit.com
2. Click "Import Repl" → "Clone from Git"
3. Paste GitHub repository URL
4. Use existing `.gitignore` and project structure

**Development Workflow with Replit + GitHub**:
1. AI assistant implements features using Edit tool
2. User reviews git diff against workspace
3. User reviews and approves changes
4. User commits and pushes to GitHub
5. GitHub Actions triggers automated tests
6. If tests pass: move to next feature
7. If tests fail: AI debugs, fixes, and iterates

## Notes on Implementation Status

### Current Architecture Decisions

**MVP Scope** (v0.5, May 24, 2026 locked):
- 10-20 concurrent users, single PostgreSQL instance
- No load balancers, no microservices, no caching currently
- Local filesystem for attachments (5MB limit per attachment)
- MVP endpoints: items CRUD, sync (download/upload), search, attachments

**Future Scaling Paths** (post-MVP):
- **Microservices**: Separate API, sync, storage services when needed
- **Horizontal Scaling**: Add read replicas, CDN, load balancers when traffic >1000 users
- **Storage**: Replace local filesystem with S3-compatible object storage (attachments > 50MB)
- **Sync**: Peer-to-peer between clients when >50 concurrent users

None of these paths needed for MVP. Plan changes when actual usage patterns emerge.

## Notes on Implementation Status

- **Server**: Directory structure ready, models/schemas/routers defined in architecture docs. Actual implementation minimal. Follow DRAFT_ARCHITECTURE.md for database schema, sync protocol, error handling.
- **Label Generator**: No actual implementation files yet (generator.py, formats.py, cli.py all empty). QR_GENERATOR.md contains complete implementation spec.
- **Android App**: Directory structure ready. Application.kt, MainActivity.kt, GuidGenerator.kt all empty. Manually reference architecture docs when implementing.
