from pathlib import Path
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Header
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from ...database import get_db
from ...models.database import Attachment
from ...schemas.item import (
    ItemCreate,
    ItemDetailOut,
    ItemOut,
    ItemUpdate,
    ItemVersionOut,
    AttachmentOut,
)
from ...services import item_service, storage_service
from ...api.config import settings
from .sync import _validate_device

router = APIRouter(prefix="/api/v1/items", tags=["items"])


@router.get("/{guid}", response_model=ItemDetailOut)
def get_item(guid: str, db: Session = Depends(get_db)):
    item, version = item_service.get_item_with_version(db, guid)
    if not item:
        raise HTTPException(status_code=404, detail="ITEM_NOT_FOUND")
    result = ItemDetailOut.model_validate(item)
    if version:
        result.latest_version = ItemVersionOut.model_validate(version)
    return result


@router.get("/{guid}/versions", response_model=list[ItemVersionOut])
def get_item_versions(guid: str, db: Session = Depends(get_db)):
    versions = item_service.get_item_versions(db, guid)
    if versions is None:
        raise HTTPException(status_code=404, detail="ITEM_NOT_FOUND")
    return [ItemVersionOut.model_validate(v) for v in versions]


@router.post("", response_model=ItemOut, status_code=201)
def create_item(payload: ItemCreate, db: Session = Depends(get_db)):
    try:
        item = item_service.create_item(db, payload)
    except ValueError as e:
        raise HTTPException(status_code=409, detail=str(e))
    return ItemOut.model_validate(item)


@router.put("/{guid}", response_model=ItemOut)
def update_item(guid: str, payload: ItemUpdate, db: Session = Depends(get_db)):
    item = item_service.update_item(db, guid, payload)
    if not item:
        raise HTTPException(status_code=404, detail="ITEM_NOT_FOUND")
    return ItemOut.model_validate(item)


@router.post("/{guid}/attach", response_model=AttachmentOut, status_code=201)
def upload_attachment(
    guid: str,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    item, version = item_service.get_item_with_version(db, guid)
    if not item:
        raise HTTPException(status_code=404, detail="ITEM_NOT_FOUND")
    if not version:
        raise HTTPException(status_code=404, detail="VERSION_NOT_FOUND")

    data = file.file.read()
    if len(data) > settings.max_upload_size:
        raise HTTPException(
            status_code=413,
            detail=f"File exceeds {settings.max_upload_size // (1024*1024)}MB limit",
        )
    file_path, content_hash, size = storage_service.save_file(file.filename, data)

    attachment = Attachment(
        version_id=version.version_id,
        filename=file.filename,
        file_path=file_path,
        content_hash=content_hash,
        size_bytes=size,
        mime_type=file.content_type,
    )
    db.add(attachment)
    db.commit()
    db.refresh(attachment)
    return AttachmentOut.model_validate(attachment)


@router.get("/{guid}/attach/{attachment_id}")
def download_attachment(guid: str, attachment_id: str, db: Session = Depends(get_db)):
    attachment = (
        db.query(Attachment).filter(Attachment.attachment_id == attachment_id).first()
    )
    if not attachment:
        raise HTTPException(status_code=404, detail="ATTACHMENT_NOT_FOUND")
    path = Path(attachment.file_path).resolve()
    upload_root = Path(settings.upload_dir).resolve()
    if not str(path).startswith(str(upload_root)):
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    if not path.exists():
        raise HTTPException(status_code=404, detail="FILE_NOT_FOUND")
    return FileResponse(
        str(path),
        media_type=attachment.mime_type or "application/octet-stream",
        filename=attachment.filename,
    )


@router.get("/{guid}/version/{vid}/attachments", response_model=list[AttachmentOut])
def list_attachments(
    guid: str,
    vid: str,
    x_device_id: str = Header(...),
    x_sync_token: str = Header(...),
    db: Session = Depends(get_db),
):
    _validate_device(db, x_device_id, x_sync_token)
    attachments = db.query(Attachment).filter(Attachment.version_id == vid).all()
    return [AttachmentOut.model_validate(a) for a in attachments]


@router.delete("/{guid}/version/{vid}/attach/{attachment_id}", status_code=204)
def delete_attachment(
    guid: str,
    vid: str,
    attachment_id: str,
    x_device_id: str = Header(...),
    x_sync_token: str = Header(...),
    db: Session = Depends(get_db),
):
    _validate_device(db, x_device_id, x_sync_token)
    attachment = (
        db.query(Attachment).filter(Attachment.attachment_id == attachment_id).first()
    )
    if not attachment:
        raise HTTPException(status_code=404, detail="ATTACHMENT_NOT_FOUND")
    if not storage_service.delete_file(attachment.file_path):
        if Path(attachment.file_path).exists():
            raise HTTPException(status_code=500, detail="FILE_DELETE_FAILED")
    db.delete(attachment)
    db.commit()
