from datetime import datetime
from typing import Any, Optional
from pydantic import BaseModel


class SyncVersionUpload(BaseModel):
    item_id: str
    guid: Optional[str] = None  # Needed when item doesn't exist on server
    url: Optional[str] = None  # Needed when item doesn't exist on server
    domain: Optional[str] = None  # Needed when item doesn't exist on server
    data: Optional[Any] = None
    change_summary: Optional[str] = None
    parent_version: Optional[str] = None
    created_at: Optional[datetime] = None


class SyncDownloadItem(BaseModel):
    item_id: str
    guid: str
    url: str
    data: Optional[Any] = None
    timestamp: Optional[datetime] = None


class SyncChangesUpload(BaseModel):
    item_versions: list[SyncVersionUpload] = []


class SyncUploadRequest(BaseModel):
    changes: SyncChangesUpload


class SyncChanges(BaseModel):
    items_added: list[SyncDownloadItem] = []
    items_deleted: list[str] = []


class SyncDownloadResponse(BaseModel):
    sync_token: str
    changes: SyncChanges


class DeviceRegisterRequest(BaseModel):
    device_id: str
    device_name: Optional[str] = None
