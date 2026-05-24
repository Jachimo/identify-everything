import uuid
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy.orm import Session
from ..models.database import Item, ItemVersion, Device
from ..schemas.item import ItemCreate, ItemUpdate


def _ensure_device(db: Session, device_id: Optional[str]) -> Optional[Device]:
    if not device_id:
        return None
    device = db.query(Device).filter(Device.device_id == device_id).first()
    if not device:
        device = Device(device_id=device_id)
        db.add(device)
        db.flush()
    return device


def get_item(db: Session, guid: str) -> Optional[Item]:
    return db.query(Item).filter(Item.guid == guid, Item.deleted == False).first()


def get_item_with_version(db: Session, guid: str):
    item = get_item(db, guid)
    if not item:
        return None, None
    version = (
        db.query(ItemVersion)
        .filter(ItemVersion.item_id == item.item_id, ItemVersion.is_canonical == True)
        .order_by(ItemVersion.created_at.desc())
        .first()
    )
    if not version:
        version = (
            db.query(ItemVersion)
            .filter(ItemVersion.item_id == item.item_id)
            .order_by(ItemVersion.created_at.desc())
            .first()
        )
    return item, version


def get_item_versions(db: Session, guid: str) -> list[ItemVersion]:
    item = get_item(db, guid)
    if not item:
        return []
    return (
        db.query(ItemVersion)
        .filter(ItemVersion.item_id == item.item_id)
        .order_by(ItemVersion.created_at.desc())
        .all()
    )


def create_item(db: Session, payload: ItemCreate) -> Item:
    existing = db.query(Item).filter(Item.guid == payload.guid).first()
    if existing:
        raise ValueError("DUPLICATE_GUID")

    _ensure_device(db, payload.device_id)

    item = Item(
        item_id=str(uuid.uuid4()),
        guid=payload.guid,
        url=payload.url,
        domain=payload.domain,
        schema_type=payload.schema_type,
    )
    db.add(item)
    db.flush()

    version = ItemVersion(
        version_id=str(uuid.uuid4()),
        item_id=item.item_id,
        device_id=payload.device_id,
        data=payload.data or {},
        change_summary=payload.change_summary or "Initial version",
        is_canonical=True,
    )
    db.add(version)
    db.commit()
    db.refresh(item)
    return item


def update_item(db: Session, guid: str, payload: ItemUpdate) -> Optional[Item]:
    item = get_item(db, guid)
    if not item:
        return None

    _ensure_device(db, payload.device_id)

    db.query(ItemVersion).filter(
        ItemVersion.item_id == item.item_id,
        ItemVersion.is_canonical == True,
    ).update({"is_canonical": False})

    if payload.schema_type:
        item.schema_type = payload.schema_type

    prev = (
        db.query(ItemVersion)
        .filter(ItemVersion.item_id == item.item_id)
        .order_by(ItemVersion.created_at.desc())
        .first()
    )

    version = ItemVersion(
        version_id=str(uuid.uuid4()),
        item_id=item.item_id,
        device_id=payload.device_id,
        data=payload.data or {},
        change_summary=payload.change_summary,
        parent_version_id=prev.version_id if prev else None,
        is_canonical=True,
    )
    db.add(version)
    db.commit()
    db.refresh(item)
    return item


def list_items_since(db: Session, after: Optional[datetime] = None) -> list[Item]:
    q = db.query(Item).filter(Item.deleted == False)
    if after:
        q = q.filter(Item.created_at > after)
    return q.order_by(Item.created_at.asc()).all()


def search_items(db: Session, q: str) -> list[Item]:
    pattern = f"%{q}%"
    return (
        db.query(Item)
        .filter(Item.deleted == False)
        .join(ItemVersion, ItemVersion.item_id == Item.item_id)
        .filter(
            (Item.guid.ilike(pattern))
            | (Item.url.ilike(pattern))
            | (Item.domain.ilike(pattern))
        )
        .distinct()
        .limit(50)
        .all()
    )
