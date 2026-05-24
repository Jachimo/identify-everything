from datetime import datetime
from typing import Any, Optional
from pydantic import BaseModel


class SyncVersionUpload(BaseModel):
    item_id: str
    data: Optional[Any] = None
    change_summary: Optional[str] = None
    parent_version: Optional[str] = None
    created_at: Optional[datetime] = None


class SyncUploadRequest(BaseModel):
    changes: dict[str, list[Any]]


class SyncDownloadItem(BaseModel):
    item_id: str
    guid: str
    url: str
    data: Optional[Any] = None
    timestamp: Optional[datetime] = None


class SyncChanges(BaseModel):
    items_added: list[SyncDownloadItem] = []
    items_deleted: list[str] = []
    versions_updated: list[Any] = []


class SyncDownloadResponse(BaseModel):
    sync_token: str
    changes: SyncChanges


class DeviceRegisterRequest(BaseModel):
    device_id: str
    device_name: Optional[str] = None
