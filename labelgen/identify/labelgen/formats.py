import csv
import io
from pathlib import Path
from typing import Optional

from PIL import Image
from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
from reportlab.pdfgen import canvas as rl_canvas

from .generator import LocalQrGenerator

AVERY_64510_ROWS = 3
AVERY_64510_COLS = 4
LABEL_W_IN = 2.0
LABEL_H_IN = 2.0
MARGIN_LEFT_IN = 0.75
MARGIN_TOP_IN = 0.5


def generate_sheet_pdf(
    entries: list[dict],
    output_path: str,
    rows: int = AVERY_64510_ROWS,
    cols: int = AVERY_64510_COLS,
    size: int = 320,
    fill: str = "black",
    back: str = "white",
) -> None:
    gen = LocalQrGenerator(fill_color=fill, back_color=back)
    page_w, page_h = letter

    c = rl_canvas.Canvas(output_path, pagesize=letter)

    labels_per_sheet = rows * cols
    for sheet_start in range(0, len(entries), labels_per_sheet):
        sheet_entries = entries[sheet_start : sheet_start + labels_per_sheet]
        for idx, entry in enumerate(sheet_entries):
            row = idx // cols
            col = idx % cols
            x = (MARGIN_LEFT_IN + col * LABEL_W_IN) * inch
            y = page_h - (MARGIN_TOP_IN + (row + 1) * LABEL_H_IN) * inch

            url = entry.get("url", entry.get("data", ""))
            img = gen.generate_qr_image(url, size=size)

            buf = io.BytesIO()
            img.save(buf, format="PNG")
            buf.seek(0)

            img_w = LABEL_W_IN * inch * 0.85
            img_h = LABEL_H_IN * inch * 0.85
            img_x = x + (LABEL_W_IN * inch - img_w) / 2
            img_y = y + (LABEL_H_IN * inch - img_h) / 2

            c.drawInlineImage(img, img_x, img_y, width=img_w, height=img_h)

        c.showPage()

    c.save()


def load_csv(csv_path: str) -> list[dict]:
    entries = []
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            entries.append(dict(row))
    return entries
