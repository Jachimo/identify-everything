import argparse
import sys
from pathlib import Path

from .generator import LocalQrGenerator
from .formats import generate_sheet_pdf, load_csv


def main():
    parser = argparse.ArgumentParser(
        prog="labelgen",
        description="Generate QR code labels for the Identify Everything system.",
    )
    parser.add_argument("--data", help="Data/URL to encode in a single QR code")
    parser.add_argument("--batch", help="CSV file with guid,url columns for batch generation")
    parser.add_argument("--output", required=True, help="Output file path (PNG, PDF, or JSON)")
    parser.add_argument("--size", type=int, default=256, help="QR code size in pixels (default: 256)")
    parser.add_argument("--rows", type=int, default=3, help="Rows per sheet (default: 3)")
    parser.add_argument("--cols", type=int, default=4, help="Columns per sheet (default: 4)")
    parser.add_argument("--fill", default="black", help="QR code color (default: black)")
    parser.add_argument("--back", default="white", help="Background color (default: white)")

    args = parser.parse_args()

    output = args.output.lower()

    if args.batch:
        entries = load_csv(args.batch)
        if output.endswith(".pdf"):
            generate_sheet_pdf(
                entries,
                args.output,
                rows=args.rows,
                cols=args.cols,
                size=args.size,
                fill=args.fill,
                back=args.back,
            )
            print(f"PDF sheet written to {args.output} ({len(entries)} labels)")
        else:
            print("Batch mode currently supports PDF output only.", file=sys.stderr)
            sys.exit(1)
    elif args.data:
        gen = LocalQrGenerator(fill_color=args.fill, back_color=args.back)
        if output.endswith(".json"):
            gen.save_json(args.data, args.output, size=args.size)
        else:
            gen.save_png(args.data, args.output, size=args.size)
        print(f"QR code written to {args.output}")
    else:
        print("Provide --data or --batch", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
