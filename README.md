# Identify Everything

A mobile-first, offline-first system for identifying items in the
physical world using scannable QR codes. 

Each item is assigned a scannable identifier containing a unique URL,
enabling phone-based viewing, editing, and tracking without internet
connectivity.

## Architecture

```
┌─────────────────┐         ┌──────────────────────────────────┐
│  Mobile Client  │ ─────▶  │  Server (FastAPI + PostgreSQL)    │
│  (Android)      │         │  - Items & version history        │
│                 │         │  - Sync protocol (incremental)     │
│  Local SQLite   │         │  - Full-text search               │
│  + HTTP sync    │         │  - File attachments               │
└─────────────────┘         └──────────────────────────────────┘
         ▲                                                           
         │ Offline-first data storage
         │ Background sync when online
         │ Conflict resolution via timestamps
         │
         │────────────────────────────────────┐
         │                                    │
  ┌──────┴────────────────────┐  ┌──────────┴────────────┐
  │  QR code generator         │  │  Label sheets (MVP)   │
  │  labelgen/                 │  │  Avery 64510 (2" × 2")│
  │  - Single QR preview       │  │  12 per sheet         │
  │  - Batch PDF generation    │  │  Desktop printing     │
  │  - CSV batch import        │  │  Local generation     │
  └────────────────────────────┘  └───────────────────────┘
```

## Components

### 1. Backend Server (`server/`)

FastAPI-based REST API with PostgreSQL database. Central repository
for item data, sync protocol enforcement, and API endpoints.

**MVP Features**:
- REST API with OpenAPI documentation (`/docs`, `/redoc`)
- PostgreSQL with SQLAlchemy ORM
- Version history with timestamp-based conflict resolution
- Incremental sync protocol
- Attachment storage (local filesystem, 5MB limit)
- Full-text search via PostgreSQL `tsvector`

**Status**: Architecture defined, requires implementation

**See**: [`server/README.md`](server/README.md)

### 2. Label Generator (`labelgen/`)

Local QR code generator for printable label sheets. No external
dependencies; works completely offline.

**Features**:
- Single QR code preview (PNG) for development
- Batch generation from CSV (Avery 64510: 2" × 2", 12 per sheet)
- PNG preview, PDF generation, JSON data output
- CLI interface: `python -m identify.labelgen --batch labels.csv --output sheet.pdf`

**MVP Scope v0.5** (locked May 24, 2026):
- Fixed 2" × 2" Avery 64510 labels
- 12 labels per 8.5" × 11" sheet
- Desktop printer support only
- **No thermal printing**

**Status**: Fully specified, ready for implementation

**See**: [`labelgen/README.md`](labelgen/README.md), [`docs/QR_GENERATOR.md`](docs/QR_GENERATOR.md)

### 3. Android App (`android/`)

Mobile client for scanning QR codes and managing items offline. Planned for implementation after backend and label generator.

**MVP Features (planned)**:
- QR scanning with CameraX and ZXing
- Local SQLite database for offline storage
- Background sync when network available
- GPS location tagging
- Version history tracking

**Status**: Planning phase

**See**: [`android/README.md`](android/README.md)

## Project Structure

```
identify-everything/
├── server/                      # FastAPI backend
│   ├── identify/
│   │   ├── api/                 # Routers & route handlers
│   │   ├── models/              # SQLAlchemy ORM models
│   │   ├── schemas/             # Pydantic request/response
│   │   ├── services/            # Business logic layer
│   │   └── database.py          # Database engine/config
│   ├── alembic/                 # Database migrations
│   ├── requirements.txt
│   └── Dockerfile
│
├── labelgen/                    # QR code generator
│   ├── identify/labelgen/
│   │   ├── generator.py         # QR encoding
│   │   ├── formats.py           # PDF/SVG output
│   │   └── cli.py               # CLI interface
│   ├── scripts/generate_labels.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── android/                     # Android client (Kotlin)
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/identify/Everything/
│   ├── build.gradle
│   └── proguard-rules.pro
│
├── docs/                        # Architecture docs
│   ├── DRAFT_ARCHITECTURE.md    # Complete system specification
│   └── QR_GENERATOR.md          # Label generator spec
│
├── tests/                       # Test suites
│   ├── backend/                 # Server API tests
│   ├── label_generator/         # Generator tests
│   └── android/                 # App tests (planned)
│
├── docker-compose.yml           # Dev environment (PostgreSQL)
├── .github/workflows/           # CI/CD pipelines
│   ├── backend.yaml             # Backend tests
│   └── label-generator.yaml     # Generator tests
│
├── AGENTS.md                    # Agent implementation guide
└── README.md                    # This file
```

## Data Model

### URL Format

Items identified by scannable URLs:

```
https://{domain}/objects/v1/{guid}
```

- `{domain}`: User's DNS domain
- `{guid}`: Base26-encoded ID (28 characters) — example: `3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f`

See [`docs/DRAFT_ARCHITECTURE.md`](docs/DRAFT_ARCHITECTURE.md#3.-guid-generation) for Base26 encoding algorithm and schema details.

**Version History**: Each change creates a new version with timestamps for conflict resolution. See [`docs/DRAFT_ARCHITECTURE.md`](docs/DRAFT_ARCHITECTURE.md#8。-item-version-history) for full schema.

## Example URL

```
https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f
```

## Implementation Order

### 1. Label Generator (Week 1)

Easiest entry point—local code, no external dependencies.

```bash
cd labelgen
pip install python-qrcode[full] reportlab pillow
python -m identify.labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b" \
    --output preview.png
```

See [`docs/QR_GENERATOR.md`](docs/QR_GENERATOR.md) for complete CLI API.

### 2. Backend Server (Week 2)

Requires database. PostgreSQL via Docker.

```bash
docker-compose up -d
cd server
pip install -r requirements.txt
alembic upgrade head
uvicorn identify.api.main:app --reload
```

API available at `http://localhost:8000` with docs at `/docs`.

### 3. Android App (Week 3)

Complex dependency chain. Use Android Studio.

```bash
cd android
./gradlew assembleDebug
./gradlew connectedAndroidTest
```

## Testing

### Label Generator

```bash
cd labelgen
python -m pytest tests/ -v

# With coverage
python -m pytest tests/ --cov=labelgen --cov-report=html
```

### Backend

```bash
cd server
pytest tests/

# With Docker database
docker-compose up -d && pytest tests/
```

GitHub Actions runs tests on push/PR.

### Android

```bash
cd android
./gradlew test                     # Unit tests
./gradlew connectedAndroidTest     # Instrumented tests (device/emulator)
```

## Deployment (MVP)

**Target**: 10-20 concurrent users on a small VPS.

**Recommended Stack**:
- Backend: 1 vCPU, 2GB RAM, PostgreSQL + FastAPI (Docker)
- Storage: Local filesystem (5MB per attachment limit)
- VPS Provider: DigitalOcean, Linode, or Vultr
- DNS: User-managed domains

No load balancers, microservices, or caching needed for MVP.

```bash
docker-compose up -d --build
docker-compose logs -f
docker-compose down
```

## Contributing

This is currently a personal project. The structure is designed for clarity and future team collaboration.

**Future contributors**: Fork, pick a component, read its README, run tests locally.

## Documentation

| File | Purpose |
|------|---------|
| [`docs/DRAFT_ARCHITECTURE.md`](docs/DRAFT_ARCHITECTURE.md) | Complete system architecture, data models, sync protocol, scaling suggestions |
| [`docs/QR_GENERATOR.md`](docs/QR_GENERATOR.md) | Label generator specifications, CLI API, testing strategy |
| [`server/README.md`](server/README.md) | Backend implementation guide |
| [`labelgen/README.md`](labelgen/README.md) | Label generator usage guide |
| [`android/README.md`](android/README.md) | Android app implementation guide |
| [`AGENTS.md`](AGENTS.md) | Technical details for AI agents | working_dir>/home/jtuttle/ai_tools/identify-everything

## License

Proprietary — All rights reserved
