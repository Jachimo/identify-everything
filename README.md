# Identify Everything

A mobile-first offline-first system for identifying items in the physical world using scannable QR codes that map to unique URLs. Developed as an MVP starting May 2024 for personal use and exploration of offline-sync capabilities.

## Overview

**The Problem**: When things are lost or misplaced, they often lack any meaningful context about what they are, when they were last used, or where they might be found.

**The Solution**: Apply a scannable QR code to items. Scan with your phone to view or edit item details - see ownership, location, history, and attachments. Works completely offline if needed.

[![Python Version](https://img.shields.io/badge/python-3.11-blue)](https://www.python.org/downloads/)
[![Android](https://img.shields.io/badge/android-26%2B-green)](https://developer.android.com/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.104+-0099CC)](https://fastapi.tiangolo.com/)
[![License](https://img.shields.io/badge/license-proprietary-red.svg)]()
[![MVP Scope](https://img.shields.io/badge/Status-MVP-yellow)]()

## What This Project Does

```
┌──────────────────────────────────────────────────────────────┐
│                     Identify Everything                       │
│                                                                │
│   ┌─────────────┐      ┌────────────────────────────────┐    │
│   │  Scan QR    │ ───> │  View/Edit Item Details        │    │
│   │  Code       │      │  - Title, description          │    │
│   │             │      │  - Location (GPS)               │    │
│   └─────────────┘      │  - Metadata, attachments        │    │
│                        │  - Version history              │    │
│                        └────────────────────────────────┘    │
│                         ▲                                  │
│                         │                                  │
│   ┌─────────────────────┴────────────────────────────────┐   │
│   │          Offline First Capability                    │   │
│   │  - Local SQLite storage                            │   │
│   │  - Background sync when online                     │   │
│   │  - Conflict resolution (newer timestamps win)       │   │
│   └────────────────────────────────────────────────────┘   │
│                         │                                  │
│                        Local                               │
└────────────────────────────────────────────────────────────┘
```

## Components

This project is organized as a monorepo with three main components:

### 1. Android App (`android/`)
Android mobile client for scanning QR codes and managing item records locally.

**Purpose**: Primary user interface for creating, editing, and syncing item data

**MVP Features**:
- QR code scanning with CameraX and ZXing
- Local SQLite database for offline-first storage
- Version history tracking
- GPS location tagging
- Background sync when network available
- Simple conflict resolution

**Status**: Planned for implementation after label generator and backend are deployed

**Documentation**: See [`android/README.md`](android/README.md)

### 2. Backend Server (`server/`)
FastAPI-based REST API and database layer for data synchronization.

**Purpose**: Central repository for item data, sync protocol enforcement, and API endpoints

**MVP Features**:
- HTTP API with automatic OpenAPI documentation
- PostgreSQL database via SQLAlchemy ORM
- Version history and conflict resolution
- Incremental sync protocol
- Attachment storage (local filesystem)
- Full-text search

**Status**: Backend architecture defined, requires implementation

**Documentation**: See [`server/README.md`](server/README.md)

### 3. Label Generator (`labelgen/`)
Local QR code generator for creating printable label sheets.

**Purpose**: Generate QR codes from data and create printable labels

**MVP Features**:
- Local generation only (no external API dependencies)
- Avery 64510 format (2" × 2", 12 labels per sheet)
- Batch processing from CSV files
- PNG preview, PDF printing, JSON data output
- Completely offline-capable

**Status**: Fully specified and ready for implementation

**Documentation**: See [`labelgen/README.md`](labelgen/README.md) and [`QR_GENERATOR.md`](QR_GENERATOR.md)

## Project Structure

```
identify-everything/
├── android/                          # Android mobile app
│   ├── app/
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/identify/Everything/
│   ├── build.gradle
│   └── README.md
├── server/                           # Backend API & database
│   ├── identify/
│   │   ├── api/                      # FastAPI application
│   │   ├── models/                   # SQLAlchemy models
│   │   ├── schemas/                  # Pydantic schemas
│   │   └── services/                 # Business logic
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
├── labelgen/                         # QR code generator
│   ├── identify/labelgen/
│   │   ├── generator.py              # QR code generation logic
│   │   ├── formats.py                # PDF/SVG formats
│   │   └── cli.py                    # Command-line interface
│   ├── scripts/
│   │   └── generate_labels.py
│   ├── examples/
│   │   └── labels.csv
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
├── docker-compose.yml                # Development environment
├── .github/workflows/                # CI/CD pipelines
│   ├── backend.yaml                  # Backend tests
│   └── label-generator.yaml          # Label generator tests
├── .gitignore
├── DRAFT_ARCHITECTURE.md             # Detailed architecture
├── QR_GENERATOR.md                   # QR code generation spec
└── README.md                         // This file
```

## Data Model

### URL Format

Item records identified by URLs:

```
https://{domain}/objects/v1/{guid}
```

- `{domain}` = User's DNS domain they own/control
- `{guid}` = Base26-encoded unique identifier (28 characters)
- Example: `https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d`

### GUID Generation

Base26 encoding of UUIDs for scannability:

```
UUID: 00000000-0000-0000-0000-000000000000
Encoding: 3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f
```

Full specification in [`DRAFT_ARCHITECTURE.md`](DRAFT_ARCHITECTURE.md#3.-guid-generation)

### Item Version History

Each change to an item creates a version:

```sql
item_versions {
  version_id: UUID PRIMARY KEY,
  item_id: UUID,
  device_id: TEXT,                  -- Which device made the change
  data: JSONB,                      -- Metadata
  parent_version_id: UUID,          -- Links to previous version (chain)
  created_at: TIMESTAMPTZ,           -- Timestamp for conflict resolution
  is_canonical: BOOLEAN              -- Latest version for this device
}
```

**Why version history?**
- Never actually delete information, just create new versions
- Audit trail of ownership and changes
- Conflict resolution via timestamps and device attribution
- Store attachments per version

## Version History

- **v0.5** (May 24, 2026) - Locked MVP scope: 2"x2" Avery 64510 labels, 12 per sheet, thermal printer support deferred
- **v0.4** (May 23, 2026) - Added local QR code generator specification with python-qrcode library
- **v0.3** (May 23, 2026) - Switched server stack from Node.js to Python + FastAPI with SQLAlchemy
- **v0.1** - Initial architecture review after Section 1 & 2 updates

**See DRAFT_ARCHITECTURE.md revision history for full changes**

## Getting Started

### Prerequisites

**Basic**:
- Python 3.11+
- Docker & Docker Compose (for server testing)
- Git

**For Android development** (when implementing):
- Android Studio Hedgehog (2023.1.1+) or newer
- JDK 11+
- Android SDK API 26+

### Repository Setup

**Clone repository**:

```bash
git clone https://github.com/your-username/identify-everything.git
cd identify-everything
```

**Initialize local Replit** (recommended for development environment):

1. Go to https://replit.com
2. Click "Import Repl" → "Clone from Git"
3. Paste: your GitHub repository URL
4. Use the existing `.gitignore` and project structure

### Component Implementation Order

**Recommended order for implementation**:

1. **Week 1: Label Generator** (easiest, no external dependencies)
   ```bash
   cd labelgen
   pip install -r requirements.txt
   python -m labelgen --batch examples/labels.csv --output test.pdf
   ```

2. **Week 2: Backend Server** (requires database)
   ```bash
   docker-compose up -d db
   cd server
   pip install -r requirements.txt
   alembic upgrade head
   uvicorn identify.api.main:app --reload
   ```

3. **Week 3: Android App** (most complex dependency chain)
   ```bash
   cd android
   ./gradlew assembleDebug
   # Connect Android device or start emulator
   ./gradlew connectedAndroidTest
   ```

### Development Workflow

With Replit + GitHub integration (your current setup):

```
1. I (AI assistant) implement features using Edit tool
2. You review git diff against workspace
3. You review and approve the changes
4. You commit and push to GitHub if approved
5. GitHub Actions triggers automated tests
6. If tests pass: success: move to next feature
7. If tests fail: I debug, fix, and iterate
```

**Quick start commands**:

```bash
# Environment: Replit
# Navigate to the directory in the workspace
cd /home/jtuttle/ai_tools/identify-everything

# Start Docker services (database)
docker-compose up -d db

# Test backend connection
curl http://localhost:5432

# Run backend tests
cd server && pytest tests/
```

### Label Generator (Starting Point)

**Test the label generator**:

```bash
cd labelgen

# Generate preview PNG
python -m labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b" \
    --output preview.png

# Generate full sheet (Avery 64510)
python -m labelgen \
    --batch examples/labels.csv \
    --output test_sheet.pdf \
    --rows 3 --cols 4
```

## Architecture Documentation

- [`DRAFT_ARCHITECTURE.md`](DRAFT_ARCHITECTURE.md) - Complete architecture design
  - Core components and data models
  - Sync protocol specification
  - Scaling considerations
  - Future enhancements
- [`QR_GENERATOR.md`](QR_GENERATOR.md) - Local QR code generator implementation
  - Python API reference
  - CLI interface design
  - Testing strategy
  - Performance characteristics

## Testing

### Label Generator Tests

```bash
cd labelgen
python -m pytest tests/ -v
python -m pytest tests/ --cov=labelgen --cov-report=html
```

### Backend Tests

GitHub Actions runs tests automatically on push/PR:

```yaml
# .github/workflows/backend.yaml
# Runs pytest on Ubuntu and macOS with PostgreSQL service
```

### Android Tests (when implementing)

```bash
cd android
./gradlew test                     # Unit tests (no emulator)
./gradlew connectedAndroidTest     # Instrumented tests
```

## Deployment (MVP)

**Scalable targeting**: 10-20 concurrent users on a small VPS

**Recommended stack**:
- **Backend**: 1 vCPU, 2GB RAM, PostgreSQL + FastAPI (Docker)
- **Storage**: Local filesystem (5MB per attachment limit)
- **Network**: Beginner-friendly VPS provider (Linode, DigitalOcean, Vultr)
- **Domains**: User-managed DNS domains

No load balancers or microservices required for MVP.

### Docker Deployment

```bash
# Build and start in production
docker-compose up -d --build

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

## Changing Project Approach

**Scalable paths** (detected later, post-MVP):

1. **Microservices**: Separate backend into API, sync, storage services
2. **Horizontal scaling**: Add read replicas, CDN, load balancers
3. **Dedicated services**: Replace local file storage with cloud object storage
4. **Peer-to-peer**: Merge offline-first clients with sync servers

None of these needed for MVP (10-20 users).

## Contributing

This is a personal project for now. The structure is designed for clarity and future team collaboration.

**Future team entry points**:
1. Fork this repository
2. Pick a component from the README
3. Follow its README.md for context
4. Run tests locally before opening PR

## License

Proprietary - All rights reserved
