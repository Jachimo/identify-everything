from datetime import datetime
from typing import Any, Optional
from pydantic import BaseModel


class AttachmentOut(BaseModel):
    attachment_id: str
    version_id: str
    filename: str
    content_hash: Optional[str] = None
    size_bytes: Optional[int] = None
    mime_type: Optional[str] = None

    model_config = {"from_attributes": True}


class ItemVersionOut(BaseModel):
    version_id: str
    item_id: str
    device_id: Optional[str] = None
    data: Optional[Any] = None
    change_summary: Optional[str] = None
    parent_version_id: Optional[str] = None
    created_at: Optional[datetime] = None
    is_canonical: bool = False
    attachments: list[AttachmentOut] = []

    model_config = {"from_attributes": True}


class ItemOut(BaseModel):
    item_id: str
    guid: str
    url: str
    domain: str
    schema_type: Optional[str] = None
    created_at: Optional[datetime] = None
    deleted: bool = False
    deleted_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class ItemDetailOut(ItemOut):
    latest_version: Optional[ItemVersionOut] = None


class ItemCreate(BaseModel):
    guid: str
    url: str
    domain: str
    schema_type: str = "generic"
    data: Optional[Any] = None
    change_summary: Optional[str] = None
    device_id: Optional[str] = None


class ItemUpdate(BaseModel):
    data: Optional[Any] = None
    change_summary: Optional[str] = None
    device_id: Optional[str] = None
    schema_type: Optional[str] = None
