import hashlib
import os
import uuid
from pathlib import Path

from ..api.config import settings


def get_upload_dir() -> Path:
    p = Path(settings.upload_dir)
    p.mkdir(parents=True, exist_ok=True)
    return p


def save_file(filename: str, data: bytes) -> tuple[str, str, int]:
    upload_dir = get_upload_dir()
    file_id = str(uuid.uuid4())
    safe_name = f"{file_id}_{filename}"
    file_path = upload_dir / safe_name
    file_path.write_bytes(data)
    content_hash = hashlib.sha256(data).hexdigest()
    return str(file_path), content_hash, len(data)


def get_file_path(stored_path: str) -> Path:
    return Path(stored_path)


def delete_file(stored_path: str) -> bool:
    path = Path(stored_path)
    if path.exists():
        path.unlink()
        return True
    return False
