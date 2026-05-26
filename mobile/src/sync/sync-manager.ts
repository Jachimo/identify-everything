import { AppState, AppStateStatus } from "react-native";
import {
  getUnsyncedItems,
  getPendingPhotos,
  markItemSynced,
  removePendingPhoto,
  getItemByGuid,
  saveItem,
} from "./storage";
import {
  registerDevice,
  createItem,
  updateItem,
  uploadPhoto,
  checkServerReachable,
} from "./api";

type SyncListener = (status: SyncStatus) => void;

export interface SyncStatus {
  running: boolean;
  lastSync: string | null;
  pendingItems: number;
  pendingPhotos: number;
  lastError: string | null;
}

let status: SyncStatus = {
  running: false,
  lastSync: null,
  pendingItems: 0,
  pendingPhotos: 0,
  lastError: null,
};

const listeners = new Set<SyncListener>();
let appStateSubscription: { remove: () => void } | null = null;
let syncInterval: ReturnType<typeof setInterval> | null = null;
let isSyncing = false;

function notify() {
  listeners.forEach((l) => l({ ...status }));
}

export function addSyncListener(listener: SyncListener): () => void {
  listeners.add(listener);
  listener({ ...status });
  return () => listeners.delete(listener);
}

export function getSyncStatus(): SyncStatus {
  return { ...status };
}

export async function performSync(): Promise<void> {
  if (isSyncing) return;
  isSyncing = true;

  const reachable = await checkServerReachable();
  if (!reachable) {
    // Update pending counts even when offline
    const pendingItems = await getUnsyncedItems();
    const pendingPhotos = await getPendingPhotos();
    status = {
      ...status,
      pendingItems: pendingItems.length,
      pendingPhotos: pendingPhotos.length,
    };
    notify();
    isSyncing = false;
    return;
  }

  status = { ...status, running: true, lastError: null };
  notify();

  try {
    // Register device (best-effort)
    try {
      await registerDevice();
    } catch { /* ignore */ }

    // 1. Sync unsynced items
    const unsynced = await getUnsyncedItems();
    for (const item of unsynced) {
      try {
        const data: Record<string, unknown> = {
          ...(item.data || {}),
          title: item.title,
          description: item.description,
        };
        if (item.location) data.location = item.location;

        let result: { item_id?: string } | { status: string };
        if (!item.itemId) {
          result = await createItem(item.guid, item.url, item.domain);
        } else {
          result = { status: "exists" };
        }

        const itemId = "item_id" in result ? result.item_id! : item.itemId;

        // Also push latest data
        if (itemId) {
          try {
            await updateItem(item.guid, data, "Sync from mobile");
          } catch { /* ignore */ }
          await markItemSynced(item.guid, itemId);
        }
      } catch { /* server error — leave for next sync */ }
    }

    // 2. Upload pending photos
    const pending = await getPendingPhotos();
    for (const photo of pending) {
      try {
        await uploadPhoto(photo.guid, photo.localUri, photo.filename, photo.mimeType);
        await removePendingPhoto(photo.id);

        // Mark item as dirty so its version gets the new attachment
        const item = await getItemByGuid(photo.guid);
        if (item) {
          item.synced = false;
          await saveItem(item);
        }
      } catch { /* leave for next sync */ }
    }

    const remaining = await getPendingPhotos();
    const remainingItems = await getUnsyncedItems();

    status = {
      running: false,
      lastSync: new Date().toISOString(),
      pendingItems: remainingItems.length,
      pendingPhotos: remaining.length,
      lastError: null,
    };
  } catch (e: any) {
    status = { ...status, running: false, lastError: e?.message || "Sync error" };
  } finally {
    isSyncing = false;
    notify();
  }
}

export function startSyncManager(): void {
  // Sync when app comes to foreground
  appStateSubscription = AppState.addEventListener(
    "change",
    (state: AppStateStatus) => {
      if (state === "active") {
        performSync();
      }
    }
  );

  // Periodic sync every 2 minutes
  syncInterval = setInterval(performSync, 2 * 60 * 1000);

  // Initial sync
  performSync();
}

export function stopSyncManager(): void {
  appStateSubscription?.remove();
  appStateSubscription = null;
  if (syncInterval) {
    clearInterval(syncInterval);
    syncInterval = null;
  }
}
