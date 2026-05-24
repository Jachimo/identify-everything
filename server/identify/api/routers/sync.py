import secrets
from datetime import datetime, timezone
from typing import Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Query
from sqlalchemy.orm import Session

from ...database import get_db
from ...models.database import Device, Item, ItemVersion
from ...schemas.sync import (
    SyncUploadRequest, SyncDownloadResponse, SyncChanges, SyncDownloadItem,
    DeviceRegisterRequest
)
from ...services import sync_service

router = APIRouter(prefix="/api/v1", tags=["sync"])


@router.post("/devices/register")
def register_device(payload: DeviceRegisterRequest, db: Session = Depends(get_db)):
    device = sync_service.register_or_update_device(db, payload.device_id, payload.device_name)
    return {"device_id": device.device_id, "sync_token": device.sync_token}


def _validate_device(db: Session, device_id: str, sync_token: Optional[str] = None) -> Device:
    """Shared helper: validate device_id exists and optionally check sync_token."""
    device = db.query(Device).filter(Device.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=401, detail="DEVICE_NOT_FOUND")

    if sync_token and device.sync_token and sync_token != device.sync_token:
        raise HTTPException(status_code=401, detail="INVALID_SYNC_TOKEN")

    return device


@router.get("/items/sync", response_model=SyncDownloadResponse)
def sync_download(
    after: Optional[str] = Query(None),
    x_device_id: Optional[str] = Header(None),
    x_sync_token: Optional[str] = Header(None),
    db: Session = Depends(get_db),
):
    if not x_device_id:
        raise HTTPException(status_code=400, detail="X-Device-Id header required")
    _validate_device(db, x_device_id, x_sync_token)

    after_dt = None
    if after:
        try:
            after_dt = datetime.fromisoformat(after.replace("Z", "+00:00"))
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid 'after' timestamp format")

    items, deleted_items = sync_service.get_changes_since(db, after_dt)

    items_added = []
    for item in items:
        version = (
            db.query(ItemVersion)
            .filter(ItemVersion.item_id == item.item_id, ItemVersion.is_canonical == True)
            .order_by(ItemVersion.created_at.desc())
            .first()
        )
        items_added.append(SyncDownloadItem(
            item_id=item.item_id,
            guid=item.guid,
            url=item.url,
            data=version.data if version else {},
            timestamp=item.created_at,
        ))

    sync_token = secrets.token_hex(16)
    return SyncDownloadResponse(
        sync_token=sync_token,
        changes=SyncChanges(
            items_added=items_added,
            items_deleted=[i.item_id for i in deleted_items],
        ),
    )


@router.post("/sync/upload")
def sync_upload(
    payload: SyncUploadRequest,
    x_device_id: Optional[str] = Header(None),
    x_sync_token: Optional[str] = Header(None),
    db: Session = Depends(get_db),
):
    if not x_device_id:
        raise HTTPException(status_code=400, detail="X-Device-Id header required")

    _validate_device(db, x_device_id, x_sync_token)
    result = sync_service.process_sync_upload(db, x_device_id, payload.changes)
    return {"status": "ok", "processed": result["processed"], "created": result.get("created", 0)}
