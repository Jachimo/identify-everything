import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column, String, Boolean, DateTime, Integer, Text, ForeignKey, JSON
)
from sqlalchemy.orm import DeclarativeBase, relationship


class Base(DeclarativeBase):
    pass


def utcnow():
    return datetime.now(timezone.utc)


class Device(Base):
    __tablename__ = "devices"

    device_id = Column(String, primary_key=True)
    device_name = Column(String, nullable=True)
    last_sync_at = Column(DateTime(timezone=True), nullable=True)
    sync_token = Column(String, nullable=True)

    versions = relationship("ItemVersion", back_populates="device")
    sync_records = relationship("SyncRecord", back_populates="device")


class Item(Base):
    __tablename__ = "items"

    item_id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    guid = Column(String, unique=True, nullable=False)
    url = Column(String, unique=True, nullable=False)
    domain = Column(String, nullable=False)
    schema_type = Column(String, default="generic")
    created_at = Column(DateTime(timezone=True), default=utcnow)
    deleted = Column(Boolean, default=False)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    versions = relationship("ItemVersion", back_populates="item")


class ItemVersion(Base):
    __tablename__ = "item_versions"

    version_id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    item_id = Column(String, ForeignKey("items.item_id"), nullable=False)
    device_id = Column(String, ForeignKey("devices.device_id"), nullable=True)
    data = Column(JSON, default=dict)
    change_summary = Column(Text, nullable=True)
    parent_version_id = Column(String, ForeignKey("item_versions.version_id"), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    is_canonical = Column(Boolean, default=False)

    item = relationship("Item", back_populates="versions")
    device = relationship("Device", back_populates="versions")
    attachments = relationship("Attachment", back_populates="version")


class Attachment(Base):
    __tablename__ = "attachments"

    attachment_id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    version_id = Column(String, ForeignKey("item_versions.version_id"), nullable=False)
    filename = Column(String, nullable=False)
    file_path = Column(String, nullable=False)
    content_hash = Column(String, nullable=True)
    size_bytes = Column(Integer, nullable=True)
    mime_type = Column(String, nullable=True)

    version = relationship("ItemVersion", back_populates="attachments")


class SyncRecord(Base):
    __tablename__ = "sync_records"

    record_id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String, ForeignKey("devices.device_id"), nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    status = Column(String, default="pending")

    device = relationship("Device", back_populates="sync_records")
