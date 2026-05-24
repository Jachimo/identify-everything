# Identify Everything - Backend Server

FastAPI-based backend API for the Identify Everything project, providing sync and data management.

## Features

- REST API with automatic OpenAPI documentation
- PostgreSQL database with SQLAlchemy ORM
- Version history tracking for items
- Attachment storage (local file system or S3-compatible)
- Incremental sync protocol for mobile clients
- Full-text search via PostgreSQL tsvector

## Project Structure

```
server/
├── identify/
│   ├── api/
│   │   ├── __init__.py
│   │   ├── main.py                    # FastAPI application
│   │   ├── config.py                  # Configuration management
│   │   └── routers/
│   │       ├── __init__.py
│   │       ├── items.py               # Items CRUD endpoints
│   │       ├── sync.py                # Sync protocol endpoints
│   │       └── search.py              # Search endpoints
│   ├── models/
│   │   ├── __init__.py
│   │   ├── database.py               # SQLAlchemy models
│   │   └── item_models.py            # Item, Version, Attachment models
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── item.py                   # Pydantic schemas for items
│   │   └── sync.py                   # Pydantic schemas for sync
│   ├── services/
│   │   ├── __init__.py
│   │   ├── item_service.py           # Business logic for items
│   │   ├── storage_service.py        # File storage
│   │   └── sync_service.py           # Sync protocol implementation
│   └── database.py
├── requirements.txt
├── Dockerfile
└── README.md
```

## Quick Start

### Local Development with Docker Compose

See [docker-compose.yml](../docker-compose.yml) for full setup including database.

```bash
# Start Oraclem Database (PostgreSQL)
docker-compose up -d db

# Run migrations
docker-compose exec db alembic upgrade head

# Start API server
docker-compose up api
```

API available at http://localhost:8000

- Swagger docs: http://localhost:8000/docs
- ReDoc docs: http://localhost:8000/redoc

### Local Development (PostgreSQL installed)

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure environment variables
cp .env.example .env
# Edit .env with your settings

# Run migrations
alembic upgrade head

# Start server
uvicorn identify.api.main:app --reload
```

### Running Tests

```bash
# Unit tests
pytest tests/

# Integration tests
pytest tests/ -v

# Test coverage
pytest tests/ --cov=identify --cov-report=html
```

## Environment Variables

Required:
- `DATABASE_URL`: PostgreSQL connection string (e.g., `postgresql://user:pass@localhost:5432/identify`)
- `UPLOAD_DIR`: Directory for storing attachments (default: `/app/uploads`)

Optional:
- `SYNC_TOKEN_SECRET`: Secret for generating sync tokens
- `LOG_LEVEL`: Log level (default: `INFO`)

## API Endpoints

### Authentication & Sync

```
POST   /api/v1/sync/upload                 # Upload local changes (device_id required)
GET    /api/v1/items/sync?after=...        # Get changes for sync (device_id required)
```

### Items

```
GET    /api/v1/items/:guid                 # Get latest item version
GET    /api/v1/items/:guid/versions       # Get all versions of item
POST   /api/v1/items                       # Create new item
PUT    /api/v1/items/:guid                 # Update item version
```

### Attachments

```
POST   /api/v1/items/:guid/attach          # Upload attachment to item version (multipart)
GET    /api/v1/items/:guid/version/:vid/attachments  # List attachments
GET    /api/v1/attach/:file_id             # Download attachment
DELETE /api/v1/items/:guid/version/:vid/attach/:attachment_id  # Delete attachment
```

### Search

```
GET    /api/v1/search?q=...                # Search items (full-text search)
GET    /api/v1/search/items/by-location?lat=...&lon=...&radius=...  # Location-based search
```

Detailed API documentation available at `/docs` and `/redoc`.

## Database Schema

See [../DRAFT_ARCHITECTURE.md](../DRAFT_ARCHITECTURE.md#2.-data-model) for detailed schema documentation.

Key tables:
- `devices` - Device registration and sync tokens
- `items` - Item records with GUIDs and URLs
- `item_versions` - Version history for each item
- `attachments` - File attachments linked to versions

### Migrations

```bash
# Create new migration
alembic revision --autogenerate -m "description"

# Apply migrations
alembic upgrade head

# Rollback last migration
alembic downgrade -1
```

## Sync Protocol

Incremental sync works as follows:

1. **Download any updates**: Client requests `/api/v1/items/sync?after={timestamp}`
2. **Upload local changes**: Client POSTs `/api/v1/sync/upload` with device metadata
3. **Conflict resolution**: Server stores all versions with timestamps and device_id
4. **Client decision**: Newer timestamps win based on client's `is_canonical` logic

All sync messages are JSON. Authentication uses `device_id` and `sync_token`.

See [../DRAFT_ARCHITECTURE.md](../DRAFT_ARCHITECTURE.md#5.-sync-protocol) for detailed protocol spec.

## Error Handling

API handles errors consistently:

```json
{
  "detail": "Error message",
  "code": "ERROR_CODE",
  "timestamp": "2024-05-24T01:00:00Z"
}
```

Common errors:
- `DEVICE_NOT_FOUND` - Unknown device_id
- `INVALID_SYNC_TOKEN` - Invalid sync_token for device
- `ITEM_NOT_FOUND` - GUID doesn't exist
- `VERSION_NOT_FOUND` - Version_id not found
- `DUPLICATE_GUID` - GUID already exists

## Security

- **Public read access**: No authentication required for reading items
- **Write access**: Device_id + sync_token required for writes
- **Attachments**: File size limits enforced (MVP default: 5MB)
- **Sync tokens**: Issued on first successful sync after manual approval

Adjust security requirements per environment via environment variables.

## Performance Considerations

### Current Load (MVP): 10-20 concurrent users

Sufficient for:
- Single PostgreSQL instance on a small VPS
- One API server (FastAPI async)
- No load balancing or read replicas needed (yet)

### Vertical Scaling

When traffic increases:
- Add PostgreSQL read replicas for `/search` endpoint
- Add CDN for static assets if needed
- Redis caching for frequently-accessed data

### Horizontal Scaling

When traffic exceeds MVP capacity:
- Kong/Envoy API gateway
- Container orchestration (Docker Swarm or Kubernetes)
- Microservices: Separate API, sync, and storage services
- Event-driven: SQS for async processing

## Troubleshooting

### Database connection failed

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Verify connection string in .env
echo $DATABASE_URL

# Test connection
psql $DATABASE_URL -c "SELECT 1"
```

### Migrations fail

```bash
# Check current migration version
alembic current

# Rollback to previous version if needed
alembic downgrade -1
```

### Sync upload fails

```bash
# Check device_id in logs
log_file | grep "X-Device-Id"

# Verify sync_token
SELECT * FROM devices WHERE device_id = '...';
```

## Future Enhancements

- Rate limiting and API gateway
- WebSocket support for real-time sync
- Batch upload endpoint for large file batches
- Per-item access control (multiple devices)
- Peer-to-peer sync mode (optional)

## Contributing

See [../ARCHITECTURE.md](../docs/ARCHITECTURE.md#backend-server) for implementation details.

## License

Proprietary - All rights reserved
