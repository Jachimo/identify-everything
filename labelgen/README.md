# Identify Everything - Label Generator

Local QR code generator for creating printable labels with scannable QR codes.

## Overview

**What it does**:
- Generates QR codes from URLs or arbitrary data
- Creates printable label sheets in PDF format (Avery 64510: 2" × 2", 12 labels per sheet)
- Designed for offline-first use case (no external API dependencies)
- Supports batch processing from CSV input files

## Features (MVP)

- **Local generation only**: No external services, works offline
- **Avery 64510 format**: 2" × 2" labels, 12 per US Letter sheet
- **Batch processing**: Generate from CSV file with guid/domain mapping
- **Three output formats**: PNG (preview/debug), PDF (printing), JSON (data understand)
- **CLI interface**: Simple command-line tools for quick generation
- **Ready for MVP deployment**: Focus on paper labels, thermal printer support deferred

See [../QR_GENERATOR.md](../QR_GENERATOR.md) for complete implementation details.

## Project Structure

```
labelgen/
├── identify/
│   └── labelgen/
│       ├── __init__.py
│       ├── generator.py          # LocalQrGenerator class
│       ├── formats.py            # PDF/SVG formatters
│       └── cli.py                # CLI interface
├── scripts/
│   └── generate_labels.py        # Entry point
├── examples/
│   ├── labels.csv                # Example CSV input
│   └── labels-preview.html       # Example preview (future)
├── requirements.txt
├── Dockerfile
└── README.md
```

## Usage

### Installation

From source (Linux/macOS):

```bash
# Clone repository
git clone https://github.com/your-username/identify-everything.git
cd identify-everything/labelgen

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

Using Docker:

```bash
# Build image
docker build -t identify-labelgen .

# Run label generator
docker run --rm identify-labelgen --data "https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d" --output label.png
```

### Basic Commands

#### Single QR Code (PNG Preview)

```bash
python -m labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d" \
    --output label.png

# OR with JSON output (base64-encoded QR)
python -m labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d" \
    --output label.json
```

#### Batch Generation (Avery 64510 Sheet - MVP)

```bash
# From CSV file
python -m labelgen \
    --batch labels.csv \
    --output sheet.pdf \
    --rows 3 \
    --cols 4

# Or execute from scripts folder
python scripts/generate_labels.py batch \
    --batch labels.csv \
    --output sheet.pdf \
    --rows 3 \
    --cols 4
```

#### CSV Format

Create `labels.csv`:

```csv
guid,url
3k7x9b_p1j4_nv6d,https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d
a1b2c3d4e5f6g7h8,https://mylabels.example.com/objects/v1/a1b2c3d4e5f6g7h8
c9d8e7f6g5h4i3j2,https://mylabels.example.com/objects/v1/c9d8e7f6g5h4i3j2
```

**Required columns**:
- `guid`: Base26-encoded identifier (must be unique)
- `url`: URL to encode (or leave empty for auto-generation)

**Optional columns**:
- `notes`: User notes (not used in QR generation)

### Advanced Options

```bash
# Higher quality QR (better printing)
python -m labelgen \
    --batch labels.csv \
    --output sheet.pdf \
    --size 320 \
    --rows 3 \
    --cols 4 \
    --quality 92

# Custom colors
python -m labelgen \
    --data "https://example.com/..." \
    --output label.png \
    --fill black \
    --back white \
    --size 256
```

### Command Reference

```
python -m labelgen --help

Options:
  --help              Show help message
  --data TEXT         Data to encode in QR code (URL or arbitrary string)
  --batch FILE        CSV file with guid,url columns
  --output FILE       Output file path (PDF, JSON, or PNG for label)
  --size INT          QR code size in pixels (default: 256 for preview)
  --rows INT          Rows per sheet (default: 3)
  --cols INT          Columns per sheet (default: 4)
  --format FORMAT     Output format: png, svg, pdf (default: png)
  --fill FILL         QR code color (default: black)
  --back BACK         Background color (default: white)
```

## Output Formats

### PNG Format (Preview/Debug)

Simple image files for quick viewing and testing:
- Resolution: Configurable (default: 256px recommended for web)
- Format: PNG (lossless)
- File size: ~10-20 KB per QR code at 256x256

**Use case**: Quick testing, app preview, debugging QR encoding

### JSON Format (Data Exchange)

JSON with base64-encoded QR code:
```json
{
  "qr_code": "data:image/png;base64,iVBORw0KGgoANS...",
  "raw_data": "https://mylabels.example.com/objects/v1/3k7x9b",
  "guid": "3k7x9b",
  "domain": "mylabels.example.com",
  "format": "png",
  "generation_time_ms": 45
}
```

**Use case**: Integrating QR generation into other systems, data interchange

### PDF Format (Printing - MVP)

Avery-compatible label sheet (2" × 2", 12 labels per sheet):
- Format: ReportLab PDF embedding PNG images
- File size: ~520 KB per sheet (8-10 KB per label compressed)
- Grid: 3 rows × 4 columns with proper margins
- Compatible with standard desktop printers

**Use case**: Final label production, printer output

## Testing

### Run Unit Tests

```bash
python -m pytest tests/ -v

# With coverage
python -m pytest tests/ --cov=labelgen --cov-report=html
```

### Test Suite

- `tests/test_qr_generator.py` - Unit tests for LocalQrGenerator
- `tests/test_cli.py` - Integration tests for CLI commands
- `tests/test_avery_generation.py` - PDF generation validation

### Practice Turnout Labels

```bash
# Create test CSV
cat > test_labels.csv << 'EOF'
guid,url
TEST001,https://test.example.com/v1/TEST001
TEST002,https://test.example.com/v1/TEST002
TEST003,https://test.example.com/v1/TEST003
EOF

# Generate PDF
python -m labelgen \
    --batch test_labels.csv \
    --output test_sheet.pdf \
    --rows 3 \
    --cols 4

# Print test sheet
# - Verify 12 labels printed correctly
# - Scan each QR with phone to verify content
# - Check size and spacing matches 2" × 2" format
```

## Troubleshooting

### QR code too small for data

```bash
# Increase size parameter
python -m labelgen --batch labels.csv --sheet.pdf --size 320 --rows 3 --cols 4
```

### CSV parsing errors

```
Ensure CSV has headers: guid,url
File must be UTF-8 encoded
GUIDs must not contain empty strings
```

### PDF generation fails

```bash
# Check ReportLab installation
pip install reportlab

# Increase system memory if generating large batches
# Each sheet requires ~100-150 MB memory at peak
```

### Labels too small after printing

```
Print at 100% scale (not "Fit to Page")
Ensure printer margins are set to normal
Choose "US Letter" paper size
```

## Integration with Project

### From Server

The label generator module can be imported into the FastAPI backend:

```python
from identify.labelgen import LocalQrGenerator

gen = LocalQrGenerator()
qr_data = gen.generate_qr_code(
    data="https://mydomain.com/objects/v1/3k7x9b",
    size=320
)
```

This enables server-side label generation when needed.

### From Android App

Generate labels on server, download PDF from API:

```
Android App → GET /api/v1/items/:guid/labels → PDF → Print
```

## Dependencies

- **python-qrcode**: QR code encoding library
- **reportlab**: PDF generation from images
- **pillow**: Image manipulation library

## Performance Characteristics

| Operation | Time (single QR @ 320x320) | Time (12 QRs + PDF) |
|-----------|-----------------------------|---------------------|
| PNG generation | ~90ms average | ~1.1s |
| PDF generation | N/A | ~0.8s |
| Total (batch) | N/A | ~1.9s |

Memory usage: 100-150 MB during batch processing (averaged down on disk)

## Future Enhancements (Post-MVP)

- **Thermal printer support**: 58mm/80mm continuous roll labels
- **SVG output**: Editable vector graphics for custom designs
- **Watermark integration**: Add logos or text overlays to QR codes
- **Barcode support**: EAN-13, Code128 alongside QR codes
- **Batch HTML preview**: Easier manual review before printing
- **Custom label dimensions**: Non-standard size support
- **Implementation details**: I don't hesitate to bring up complex tasks

## Contributing

See [../QR_GENERATOR.md](../QR_GENERATOR.md) for full implementation specifications.

## License

Proprietary - All rights reserved
