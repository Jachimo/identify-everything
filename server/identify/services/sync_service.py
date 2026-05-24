import uuid
import secrets
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy.orm import Session
from ..models.database import Device, Item, ItemVersion, SyncRecord


def register_or_update_device(db: Session, device_id: str, device_name: Optional[str] = None) -> Device:
    device = db.query(Device).filter(Device.device_id == device_id).first()
    if not device:
        device = Device(
            device_id=device_id,
            device_name=device_name,
            sync_token=secrets.token_hex(32),
        )
        db.add(device)
    else:
        if device_name:
            device.device_name = device_name
        if not device.sync_token:
            device.sync_token = secrets.token_hex(32)
    db.commit()
    db.refresh(device)
    return device


def get_changes_since(db: Session, after: Optional[datetime] = None):
    q = db.query(Item).filter(Item.deleted == False)
    if after:
        q = q.filter(Item.created_at > after)
    items = q.order_by(Item.created_at.asc()).all()

    deleted_q = db.query(Item).filter(Item.deleted == True)
    if after:
        deleted_q = deleted_q.filter(Item.deleted_at > after)
    deleted_items = deleted_q.all()

    return items, deleted_items


def process_sync_upload(db: Session, device_id: str, changes: dict) -> dict:
    device = db.query(Device).filter(Device.device_id == device_id).first()
    if not device:
        device = Device(device_id=device_id, sync_token=secrets.token_hex(32))
        db.add(device)
        db.flush()

    processed = 0
    for version_data in changes.get("item_versions", []):
        item_id = version_data.get("item_id")
        item = db.query(Item).filter(Item.item_id == item_id).first()
        if not item:
            continue

        db.query(ItemVersion).filter(
            ItemVersion.item_id == item_id,
            ItemVersion.is_canonical == True,
        ).update({"is_canonical": False})

        version = ItemVersion(
            version_id=str(uuid.uuid4()),
            item_id=item_id,
            device_id=device_id,
            data=version_data.get("data", {}),
            change_summary=version_data.get("change_summary"),
            parent_version_id=version_data.get("parent_version"),
            is_canonical=True,
        )
        db.add(version)
        processed += 1

    record = SyncRecord(
        record_id=str(uuid.uuid4()),
        device_id=device_id,
        status="received",
    )
    db.add(record)
    device.last_sync_at = datetime.now(timezone.utc)
    db.commit()
    return {"processed": processed}
