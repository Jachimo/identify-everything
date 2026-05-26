import { getDeviceId, getServerUrl, getSyncToken, setSyncToken } from "./storage";
import {
  SyncUploadVersion,
  SyncDownloadResponse,
  DeviceRegistration,
  AttachmentData,
} from "../types";

const TIMEOUT_MS = 15000;
const UPLOAD_TIMEOUT_MS = 60000;

async function request(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const baseUrl = await getServerUrl();
  const url = `${baseUrl.replace(/\/$/, "")}${path}`;
  const deviceId = await getDeviceId();
  const syncToken = await getSyncToken();

  const headers: Record<string, string> = {
    "X-Device-Id": deviceId,
    ...(options.headers as Record<string, string>),
  };
  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }
  if (syncToken) {
    headers["X-Sync-Token"] = syncToken;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal,
    });
    return response;
  } finally {
    clearTimeout(timeout);
  }
}

export async function registerDevice(): Promise<DeviceRegistration> {
  const deviceId = await getDeviceId();
  const response = await request("/api/v1/devices/register", {
    method: "POST",
    body: JSON.stringify({ device_id: deviceId }),
  });
  if (!response.ok) {
    throw new Error(`Device registration failed: ${response.status}`);
  }
  const data = await response.json();
  if (data.sync_token) {
    await setSyncToken(data.sync_token);
  }
  return data;
}

export async function downloadSync(
  after?: string
): Promise<SyncDownloadResponse> {
  const params = after ? `?after=${encodeURIComponent(after)}` : "";
  const response = await request(`/api/v1/items/sync${params}`);
  if (!response.ok) {
    throw new Error(`Sync download failed: ${response.status}`);
  }
  const data = await response.json();
  if (data.sync_token) {
    await setSyncToken(data.sync_token);
  }
  return data;
}

export async function createItem(
  guid: string,
  url: string,
  domain: string
): Promise<{ item_id: string } | { status: string }> {
  const deviceId = await getDeviceId();
  const response = await request("/api/v1/items", {
    method: "POST",
    body: JSON.stringify({ guid, url, domain, device_id: deviceId }),
  });
  if (response.status === 409) {
    return { status: "exists" };
  }
  if (!response.ok) {
    throw new Error(`Item creation failed: ${response.status}`);
  }
  return response.json();
}

export async function getItem(guid: string): Promise<Record<string, unknown> | null> {
  const response = await request(`/api/v1/items/${encodeURIComponent(guid)}`);
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Get item failed: ${response.status}`);
  }
  return response.json();
}

export async function updateItem(
  guid: string,
  data: Record<string, unknown>,
  changeSummary?: string
): Promise<Record<string, unknown>> {
  const deviceId = await getDeviceId();
  const body: Record<string, unknown> = { device_id: deviceId, data };
  if (changeSummary) body.change_summary = changeSummary;

  const response = await request(`/api/v1/items/${encodeURIComponent(guid)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`Update item failed: ${response.status}`);
  }
  return response.json();
}

export async function uploadSync(
  versions: SyncUploadVersion[]
): Promise<{ processed: number; created: number }> {
  const response = await request("/api/v1/sync/upload", {
    method: "POST",
    body: JSON.stringify({
      changes: { item_versions: versions },
    }),
  });
  if (!response.ok) {
    throw new Error(`Sync upload failed: ${response.status}`);
  }
  return response.json();
}

export async function uploadPhoto(
  guid: string,
  localUri: string,
  filename: string,
  mimeType: string
): Promise<AttachmentData> {
  const baseUrl = await getServerUrl();
  const deviceId = await getDeviceId();

  const formData = new FormData();
  formData.append("file", {
    uri: localUri,
    type: mimeType,
    name: filename,
  } as unknown as Blob);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), UPLOAD_TIMEOUT_MS);

  try {
    const response = await fetch(
      `${baseUrl.replace(/\/$/, "")}/api/v1/items/${encodeURIComponent(guid)}/attach`,
      {
        method: "POST",
        body: formData,
        headers: { "X-Device-Id": deviceId },
        signal: controller.signal,
      }
    );
    if (!response.ok) throw new Error(`Photo upload failed: ${response.status}`);
    const data = await response.json();
    return {
      attachmentId: data.attachment_id,
      versionId: data.version_id,
      filename: data.filename,
      mimeType: data.mime_type,
      sizeBytes: data.size_bytes,
      contentHash: data.content_hash,
      localUri,
      synced: true,
    };
  } finally {
    clearTimeout(timeout);
  }
}

export async function getAttachmentUrl(guid: string, attachmentId: string): Promise<string> {
  const baseUrl = await getServerUrl();
  return `${baseUrl.replace(/\/$/, "")}/api/v1/items/${encodeURIComponent(guid)}/attach/${encodeURIComponent(attachmentId)}`;
}

export async function checkServerReachable(): Promise<boolean> {
  try {
    const baseUrl = await getServerUrl();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    const response = await fetch(`${baseUrl.replace(/\/$/, "")}/health`, {
      signal: controller.signal,
    });
    clearTimeout(timeout);
    return response.ok;
  } catch {
    return false;
  }
}
