import base64
import io
import time
from typing import Optional

import qrcode
from qrcode.constants import ERROR_CORRECT_H
from PIL import Image


class LocalQrGenerator:
    def __init__(self, fill_color: str = "black", back_color: str = "white"):
        self.fill_color = fill_color
        self.back_color = back_color

    def generate_qr_image(self, data: str, size: int = 256) -> Image.Image:
        qr = qrcode.QRCode(
            version=None,
            error_correction=ERROR_CORRECT_H,
            box_size=10,
            border=4,
        )
        qr.add_data(data)
        qr.make(fit=True)
        img = qr.make_image(fill_color=self.fill_color, back_color=self.back_color)
        img = img.resize((size, size), Image.LANCZOS)
        return img

    def generate_qr_code(self, data: str, size: int = 256) -> dict:
        start = time.time()
        img = self.generate_qr_image(data, size)
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        b64 = base64.b64encode(buf.getvalue()).decode()
        elapsed_ms = int((time.time() - start) * 1000)

        guid = data.rstrip("/").split("/")[-1]
        from urllib.parse import urlparse
        parsed = urlparse(data)
        domain = parsed.netloc or "unknown"

        return {
            "qr_code": f"data:image/png;base64,{b64}",
            "raw_data": data,
            "guid": guid,
            "domain": domain,
            "format": "png",
            "generation_time_ms": elapsed_ms,
        }

    def save_png(self, data: str, output_path: str, size: int = 256) -> None:
        img = self.generate_qr_image(data, size)
        img.save(output_path, format="PNG")

    def save_json(self, data: str, output_path: str, size: int = 256) -> None:
        import json
        result = self.generate_qr_code(data, size)
        with open(output_path, "w") as f:
            json.dump(result, f, indent=2)
