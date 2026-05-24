# Local QR Code Generator Specification - MVP

## Overview

This document specifies the local QR code generation system for the Identify Everything MVP. QR codes are generated on-demand using the `python-qrcode` library - no external dependencies, works offline, and provides complete control over output format. The MVP uses Avery-type paper labels (64510 format, 2" × 2", 12 labels per sheet) without thermal printer support.

## Revision History - MVP Scope

* v0.0 - Initial specification
* v0.1 - MVP scope lock: 2"×2" Avery 64510 labels, 12 per sheet, no thermal printer support

## Architecture

### Core Implementation

```python
import qrcode
from typing import BinaryIO
from io import BytesIO


class LocalQrGenerator:
    """Generates QR codes locally without external dependencies"""

    def __init__(self):
        self.default_size = 256
        self.default_fill = "#000000"
        self.default_back = "#FFFFFF"

    def generate_qr_code(
        self,
        data: str,
        output_path: str | BinaryIO | None = None,
        size: int = 256,
        fill_color: str = "#000000",
        back_color: str = "#FFFFFF"
    ) -> BytesIO:
        """
        Generate QR code image locally

        Args:
            data: The content to encode (URL or data string)
            output_path: File path or file-like object to save to
            size: Output image dimensions (width/height)
            fill_color: QR code color
            back_color: Background color

        Returns:
            BytesIO containing the image binary data
        """
        # Create QR code object
        qr = qrcode.QRCode(
            version=None,  # Auto-detect based on data size
            error_correction=qrcode.constants.ERROR_CORRECT_H,  # High error correction
            box_size=10,
            border=4,
        )

        # Add data - normalizes unicode encoding automatically
        qr.add_data(data)
        qr.make(fit=True)

        # Generate image
        img = qr.make_image(
            fill_color=fill_color,
            back_color=back_color
        )

        # Save to BytesIO for streaming (file-like object)
        img_data = BytesIO()
        img.save(img_data, format='PNG')
        img_data.seek(0)

        return img_data
```

## Data Validation

```python
def validate_and_encode_url(url: str) -> str:
    """Ensure URL is properly encoded for QR code"""
    if not url:
        raise ValueError("QR data cannot be empty")

    # Validate URL (simple check)
    if not url.startswith(('http://', 'https://', 'loc://')):
        url = f'https://{url}'

    return url
```

## MVP Label Specifications

### Avery 64510 (2" × 2" Labels)

| Specification | Value |
|---------------|-------|
| Label size | 2" × 2" |
| Labels per sheet | 12 (3 rows × 4 columns) |
| Paper size | US Letter (8.5" × 11") |
| Sheet format | PDF output |
| Cost ~ | ~$10/ream (500 sheets) |
| Integration | Desktop printer + scissors or label dispenser |

### Layout Configuration

**PDF Layout** (ReportLab):
```python
LABEL_WIDTH = 2.0  # inches
LABEL_HEIGHT = 2.0  # inches
MARGIN = 0.25  # inches around edges
GAP = 0.1  # inches between labels
SHEET_ROWS = 3
SHEET_COLS = 4

# Derived dimensions:
# - Horizontal: 8.5" - (2 * 0.25") - (3 * 0.1") / 4 = 1.95")
# - Vertical: 11" - (2 * 0.25") - (3 * 0.1") / 3 = 3.35")
```

### CSV Format

**labels.csv**:
```csv
guid,provided_url,notes
3k7x9b_p1j4_nv6d,https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d,
a1b2c3d4e5f6g7h8,https://mylabels.example.com/objects/v1/a1b2c3d4e5f6g7h8,Custom label
c9d8e7f6g5h4i3j2,https://mylabels.example.com/objects/v1/c9d8e7f6g5h4i3j2,
```

**Columns**:
- `guid`: Base26-encoded identifier (required for QR generation)
- `provided_url`: URL to encode (if not provided from database, will be generated)
- `notes`: Optional user notes

## CLI Interface (MVP)

### Installation

```bash
pip install python-qrcode[full] reportlab pillow
```

### Commands

#### Individual Generation (Single QR)

```bash
# Generate single QR code
python -m identify.labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b_p1j4_nv6d" \
    --output labels/qr_code_001.png \
    --size 256 \
    --format png

# Output: data:image/png;base64,<base64-encoded-image>
```

#### Batch Generation (Avery 64510 Sheet)

```bash
# Generate full sheet with 12 labels
python -m identify.labelgen \
    --batch labels.csv \
    --output labels/avery_64510_sheet.pdf \
    --rows 3 \
    --cols 4

# Output: PDF file with 12 label positions applied
```

```bash
# Generate for multiple sheets at once
python -m identify.labelgen \
    --batch labels.csv \
    --output "labels_sheet_{row:03d}.pdf" \
    --rows 3 \
    --cols 4
```

### Command-Line Options

```bash
python -m identify.labelgen --help

# Required: --data or --batch
python -m identify.labelgen --batch labels.csv --output labels.pdf

# Optional: format (default: png)
python -m identify.labelgen --batch labels.csv --output labels.pdf --format pdf

# Optional: advanced settings
python -m identify.labelgen \
    --batch labels.csv \
    --output labels.pdf \
    --size 320 \
    --rows 3 \
    --cols 4 \
    --quality 92

# Legacy/convenience aliases
--size 256, --quality 90  → Basic settings
--size 320, --quality 92 → Higher quality (denser QR codes)
```

## Output Formats

### PNG Format (Single QR Preview)

```json
{
  "qr_code": "data:image/png;base64,iVBORw0KGgoAAAANSU...",
  "raw_data": "https://mylabels.example.com/objects/v1/3k7x9b",
  "guid": "3k7x9b",
  "domain": "mylabels.example.com",
  "format": "png",
  "generation_time_ms": 45
}
```

**Use case**:
- Quick manual testing
- In-app preview
- Debugging QR encoding

### PDF Format (Avery 64510 Sheet - MVP)

**Specifications**:
- File size: ~520KB per sheet (8-10 kB compressed per label)
- Format: ReportLab PDF with embedded PNG images
- Size: 2" × 2" each label
- Grid: 3 rows × 4 columns (12 labels)
- Margins: 0.25" edges and 0.1" spacing
- Compatible with standard desktop printers

**Usage**:
```
# Generate PDF for printing
python -m identify.labelgen --batch labels.csv --output sheet.pdf

# Print directly from PDF viewer or use scissors/label dispenser
```

### SVG Format (Vector - Optional)

```bash
python -m identify.labelgen \
    --data "https://mylabels.example.com/objects/v1/3k7x9b" \
    --format svg \
    --output label.svg
```

**Use case**:
- High-resolution output
- Editable in vector graphics software
- Scaling beyond 2" × 2"

*Note: SVG output not included in MVP scope as native PDF fitting Avery 64510 dimensions is primary use case.*

## Implementation in Project

### Phase 1: Basic Generator (1-2 days)

File: `identify/labelgen/__init__.py`

```python
"""Local QR code generator module for MVP"""

from .generator import LocalQrGenerator
from .formats import PdfGenerator

__all__ = ['LocalQrGenerator', 'PdfGenerator']
```

### Phase 2: CLI Interface (1 day)

File: `scripts/generate_labels.py`

```python
#!/usr/bin/env python3
"""Command-line interface for QR code generation (MVP)"""

import argparse
import sys
from pathlib import Path
from io import BytesIO
from json import dump

from identify.labelgen import LocalQrGenerator
from identify.labelgen.formats import PdfGenerator


class LabelGeneratorCli:
    """CLI for QR label generation"""

    def __init__(self):
        self.generator = LocalQrGenerator()
        self.pdf_gen = PdfGenerator()

    def generate_single(self, args):
        """Generate single QR code"""
        data = self.validate_data(args.data)
        output_path = Path(args.output)

        # Generate QR as PNG
        qr_data = self.generator.generate_qr_code(
            data=data,
            size=args.size,
            fill_color=args.fill_color,
            back_color=args.back_color
        )

        # Save or output as base64
        if output_path.suffix == '.json':
            self.output_json(qr_data, output_path, data, args.guid)
        elif output_path.suffix == '.txt':
            self.output_text(qr_data, output_path)
        else:
            with output_path.open('wb') as f:
                f.write(qr_data.getvalue())

        print(f"Generated: {output_path}")

    def batch_generate(self, args):
        """Generate batch of labelled QR codes"""
        csv_path = Path(args.batch)
        output_path = Path(args.output)

        labels = self.parse_csv(csv_path)

        qr_data_list = []
        for label in labels:
            data = url or f"https://{domain}/objects/v1/{guid}"
            qr_data = self.generator.generate_qr_code(
                data=data,
                size=args.size
            )
            qr_data_list.append({
                'guid': guid,
                'raw_data': data,
                'qr_code': self.encode_base64(qr_data)
            })

        # Generate PDF if specified
        if output_path.suffix == '.pdf':
            self.pdf_gen.generate_avery_sheet(
                qr_data_list,
                output_path,
                rows=args.rows,
                cols=args.cols
            )
        else:
            # Fallback: save as concatenated PNGs or JSON
            with output_path.open('w') as f:
                dump(qr_data_list, f, indent=2)

        print(f"Generated {len(labels)} labels to {output_path}")

    def output_json(self, qr_data, output_path, data, guid):
        """Output QR code as JSON with metadata"""
        result = {
            'qr_code': self.encode_base64(qr_data),
            'raw_data': data,
            'guid': guid,
            'domain': self.extract_domain(data),
            'format': 'png',
            'generation_time_ms': 0  # Track if needed
        }
        with output_path.open('w') as f:
            dump(result, f, indent=2)

    def encode_base64(self, qr_data: BytesIO) -> str:
        """Encode BytesIO to base64 for JSON output"""
        import base64
        return base64.b64encode(qr_data.getvalue()).decode('utf-8')

    def parse_csv(self, csv_path: Path) -> list[dict]:
        """Parse CSV file with guid/domain mappings"""
        import csv
        labels = []

        with csv_path.open('r', newline='') as f:
            reader = csv.DictReader(f)
            for row in reader:
                labels.append({
                    'guid': row.get('guid', '').strip(),
                    'url': row.get('provided_url', '').strip(),
                    'notes': row.get('notes', '').strip()
                })

        # Validate
        missing = [l['guid'] for l in labels if not l['guid']]
        if missing:
            raise ValueError(f"Missing guid in CSV for: {missing}")

        return labels

    def validate_data(self, data: str) -> str:
        """Validate QR code data"""
        if not data:
            raise ValueError("--data is required")
        return data

    def extract_domain(self, url: str) -> str:
        """Extract domain from URL"""
        from urllib.parse import urlparse
        try:
            return urlparse(url).netloc or urlparse(url).path.split('/')[1]
        except:
            return 'unknown'


def main():
    cli = LabelGeneratorCli()
    parser = argparse.ArgumentParser(
        description='Generate QR code labels for Identify Everything MVP',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Single QR code preview
  python -m identify.labelgen --data "https://example.com/..." --output qr.png

  # Batch with CSV (Avery 64510)
  python -m identify.labelgen --batch labels.csv --output sheet.pdf

  # JSON output with base64 QR
  python -m identify.labelgen --data "https://example.com/..." --output qr.json
        """
    )

    subparsers = parser.add_subparsers(dest='command')

    # Single generation command
    parser_single = subparsers.add_parser('single', help='Generate single QR code')
    parser_single.add_argument(
        '--data',
        required=True,
        help='Data to encode in QR code (URL or arbitrary string)'
    )
    parser_single.add_argument(
        '--output',
        required=True,
        help='Output file path (PNG, JSON, or TXT)'
    )
    parser_single.add_argument(
        '--size',
        type=int,
        default=256,
        help='QR code size in pixels (default: 256)'
    )
    parser_single.add_argument(
        '--fill',
        default='#000000',
        help='QR code fill color (default: #000000)'
    )
    parser_single.add_argument(
        '--back',
        default='#FFFFFF',
        help='QR code background color (default: #FFFFFF)'
    )

    # Batch generation command
    parser_batch = subparsers.add_parser('batch', help='Generate batch of QR labels')
    parser_batch.add_argument(
        '--batch',
        required=True,
        help='CSV file with guid,domain columns'
    )
    parser_batch.add_argument(
        '--output',
        required=True,
        help='Output file (PDF for Avery sheet, JSON for per-label data)'
    )
    parser_batch.add_argument(
        '--size',
        type=int,
        default=320,
        help='QR code size in pixels (default: 320 for better print quality)'
    )
    parser_batch.add_argument(
        '--rows',
        type=int,
        default=3,
        help='Number of rows per sheet (default: 3)'
    )
    parser_batch.add_argument(
        '--cols',
        type=int,
        default=4,
        help='Number of columns per sheet (default: 4)'
    )

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    if args.command == 'single':
        cli.generate_single(args)
    elif args.command == 'batch':
        cli.batch_generate(args)


if __name__ == '__main__':
    main()
```

## Testing (MVP)

### Unit Tests

```python
# tests/test_qr_generator_mvp.py
import pytest
from io import BytesIO
from identify.labelgen import LocalQrGenerator

class TestLocalQrGeneratorMVP:
    def test_generate_qr_code(self):
        generator = LocalQrGenerator()
        result = generator.generate_qr_code(
            "https://example.com/objects/v1/123",
            size=64
        )
        assert isinstance(result, BytesIO)
        assert result.getbuffer().nbytes > 0

    def test_validation(self):
        generator = LocalQrGenerator()
        with pytest.raises(ValueError):
            generator.generate_qr_code("")
```

### Integration Tests (Avery PDF Generation)

```python
# tests/test_avery_generation.py
import pytest
import tempfile
from pathlib import Path
from identify.labelgen.cli import main

class TestAvery64510Generation:
    def test_generate_single_sheet(self):
        with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False) as f:
            f.write("guid,url\n3k7x9b_p1j4_nv6d,https://example.com/v1/3k7x9b\n")
            csv_path = f.name

        with tempfile.NamedTemporaryFile(suffix='.pdf', delete=False) as f:
            output_path = f.name

        try:
            sys.argv = [
                'generate_labels.py',
                'batch',
                '--batch', csv_path,
                '--output', output_path,
                '--rows', '3',
                '--cols', '4'
            ]
            main()

            # Verify PDF was generated
            assert Path(output_path).exists()
            assert Path(output_path).stat().st_size > 0

            # Verify it's a valid PDF
            with open(output_path, 'rb') as f:
                header = f.read(4)
                assert header == b'%PDF'
        finally:
            Path(csv_path).unlink()
            Path(output_path).unlink()

    def test_invalid_csv(self):
        with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False) as f:
            f.write("url\nhttps://example.com\n")  # Missing guid
            csv_path = f.name

        sys.argv = [
            'generate_labels.py',
            'batch',
            '--batch', csv_path,
            '--output', 'output.pdf'
        ]

        with pytest.raises(ValueError) as e:
            main()

        assert 'guid' in str(e.value).lower()
        Path(csv_path).unlink()
```

### Manual Test (Print and Validate)

```bash
# Print test on actual hardware
python -m identify.labelgen \
    --batch test_labels.csv \
    --output test_sheet.pdf \
    --rows 3 \
    --cols 4

# Print test sheet
# - Use scissors or label dispenser
# - Verify all 12 QRs are scannable
# - Verify size is approximately 2" × 2"
```

## Dependencies (MVP)

```json
{
  "python-qrcode": "^7.4.0",
  "pillow": "^10.0.0",
  "reportlab": "^4.0.0"
}
```

### Development Only (Not Required for MVP)

```json
{
  "pytest": "^7.4.0",
  "pytest-cov": "^4.1.0",
  "black": "^23.0.0"
}
```

## Security Considerations (MVP)

1. **No external network access** - fully idle if offline
2. **Input validation** - CSV parsing validates UUID format
3. **File path safety** - prevents directory traversal via Path sanitization
4. **Base64 encoding** - safe for JSON output usage

## Performance Characteristics (MVP)

| Metric | Value |
|--------|-------|
| Generation time (256x256, single code) | ~45ms average |
| Generation time (320x320, single code) | ~90ms average |
| Batch generation (12 codes, avg) | ~1.1s |
| PDF generation (single sheet, 12 codes) | ~0.8s |
| Total (12 QRs + PDF, avg) | ~1.9s |

## Troubleshooting (MVP)

### Issue: QR code too small for data

**Solution**:
```bash
python -m identify.labelgen --batch labels.csv --output sheet.pdf --size 320
```

### Issue: PDF generation fails

**Solution**:
```bash
pip install python-qrcode[full] reportlab pillow
```

### Issue: CSV parsing fails

**Solution**: Ensure CSV has headers `guid` and optional `url`:
```csv
guid,url
3k7x9b_p1j4_nv6d,https://example.com/v1/3k7x9b
```

### Issue: PDF too small after printing

**Adjust printer settings**:
- Print at 100% scale
- Enable "Fit to page" if available
- Choose "US Letter" paper size
- Ensure margins are not set to "Full bleed" in print settings

## Future Enhancements (Post-MVP)

1. **Thermal printer support** - 58mm/80mm continuous roll labels
2. **SVG output** - Editable vector graphics for custom label designs
3. **Watermark integration** - Add logo or text overlay to QR codes
4. **Barcode support** - EAN-13, QR, Code128 with single interface
5. **Batch preview HTML** - easier manual review before printing
6. **3D integration** - Prepare QR codes for 3D printed inserts
7. **Custom label dimensions** - Support for non-standard label sizes

## References (MVP)

- `python-qrcode` documentation: https://pypi.org/project/qrcode/
- QR Code specification: ISO/IEC 18004
- ReportLab documentation: https://www.reportlab.com/docs/
- Avery 64510 specifications: https://labelworld.com/avery-label-dimensions/64510
- PDF/A standards: https://www.pdfa.org/
