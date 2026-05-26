export interface LocationData {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  timestamp: string;
}

export interface AttachmentData {
  attachmentId: string;
  versionId: string;
  filename: string;
  mimeType: string | null;
  sizeBytes: number | null;
  contentHash: string | null;
  localUri?: string;
  synced: boolean;
}

export interface PendingPhotoUpload {
  id: string;
  guid: string;
  localUri: string;
  filename: string;
  mimeType: string;
  createdAt: string;
}

export interface ItemData {
  guid: string;
  itemId: string;
  url: string;
  domain: string;
  title?: string;
  description?: string;
  data?: Record<string, unknown>;
  location?: LocationData;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  synced: boolean;
  pendingPhotoIds?: string[];
}

export interface ItemVersionData {
  versionId: string;
  itemId: string;
  data: Record<string, unknown>;
  changeSummary?: string;
  createdAt: string;
  isCanonical: boolean;
}

export interface SyncUploadVersion {
  item_id: string;
  guid: string;
  url: string;
  domain: string;
  data?: Record<string, unknown>;
  change_summary?: string;
}

export interface SyncUploadRequest {
  changes: {
    item_versions: SyncUploadVersion[];
  };
}

export interface SyncDownloadItem {
  item_id: string;
  guid: string;
  url: string;
  data?: Record<string, unknown>;
  timestamp?: string;
}

export interface SyncDownloadResponse {
  sync_token: string;
  changes: {
    items_added: SyncDownloadItem[];
    items_deleted: string[];
  };
}

export interface DeviceRegistration {
  device_id: string;
  sync_token: string;
}
