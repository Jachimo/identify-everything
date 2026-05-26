import { ItemData } from "../types";
import { generateGuid, buildItemUrl } from "./guid";

const STORAGE_KEY_ITEMS = "@identify_everything/items";
const STORAGE_KEY_DEVICE_ID = "@identify_everything/device_id";
const STORAGE_KEY_SERVER_URL = "@identify_everything/server_url";
const STORAGE_KEY_SYNC_TOKEN = "@identify_everything/sync_token";

// In-memory fallback when AsyncStorage is unavailable (web/snack)
let memoryStore: Record<string, string> = {};

async function getStore(): Promise<typeof import("@react-native-async-storage/async-storage").default | null> {
  try {
    const mod = await import("@react-native-async-storage/async-storage");
    return mod.default;
  } catch {
    return null;
  }
}

async function getItem(key: string): Promise<string | null> {
  const store = await getStore();
  if (store) return store.getItem(key);
  return memoryStore[key] ?? null;
}

async function setItem(key: string, value: string): Promise<void> {
  const store = await getStore();
  if (store) await store.setItem(key, value);
  else memoryStore[key] = value;
}

async function removeItem(key: string): Promise<void> {
  const store = await getStore();
  if (store) await store.removeItem(key);
  else delete memoryStore[key];
}

// Device ID
export async function getDeviceId(): Promise<string> {
  let id = await getItem(STORAGE_KEY_DEVICE_ID);
  if (!id) {
    id = "android-" + Math.random().toString(36).substring(2, 10);
    await setItem(STORAGE_KEY_DEVICE_ID, id);
  }
  return id;
}

// Server URL
export async function getServerUrl(): Promise<string> {
  const url = await getItem(STORAGE_KEY_SERVER_URL);
  if (url) return url;
  // On web, default to same origin (works in Replit and browser previews)
  if (typeof window !== "undefined" && window.location) {
    return `${window.location.protocol}//${window.location.hostname}:8000`;
  }
  // On Android emulator, 10.0.2.2 routes to the host machine's localhost
  return "http://10.0.2.2:8000";
}

export async function setServerUrl(url: string): Promise<void> {
  await setItem(STORAGE_KEY_SERVER_URL, url);
}

// Sync token
export async function getSyncToken(): Promise<string | null> {
  return getItem(STORAGE_KEY_SYNC_TOKEN);
}

export async function setSyncToken(token: string): Promise<void> {
  await setItem(STORAGE_KEY_SYNC_TOKEN, token);
}

// Item CRUD
export async function getAllItems(): Promise<ItemData[]> {
  const json = await getItem(STORAGE_KEY_ITEMS);
  if (!json) return [];
  try {
    return JSON.parse(json);
  } catch {
    return [];
  }
}

export async function getItemByGuid(guid: string): Promise<ItemData | null> {
  const items = await getAllItems();
  return items.find((i) => i.guid === guid) || null;
}

export async function saveItem(item: ItemData): Promise<void> {
  const items = await getAllItems();
  const idx = items.findIndex((i) => i.guid === item.guid);
  if (idx >= 0) {
    items[idx] = item;
  } else {
    items.push(item);
  }
  await setItem(STORAGE_KEY_ITEMS, JSON.stringify(items));
}

export async function createLocalItem(
  guid?: string,
  domain?: string
): Promise<ItemData> {
  const g = guid || generateGuid();
  const d = domain || "mylabels.example.com";
  const now = new Date().toISOString();
  const item: ItemData = {
    guid: g,
    itemId: "",
    url: buildItemUrl(d, g),
    domain: d,
    createdAt: now,
    updatedAt: now,
    deleted: false,
    synced: false,
  };
  await saveItem(item);
  return item;
}

export async function deleteItem(guid: string): Promise<void> {
  const items = await getAllItems();
  const filtered = items.filter((i) => i.guid !== guid);
  await setItem(STORAGE_KEY_ITEMS, JSON.stringify(filtered));
}

export async function markItemSynced(guid: string, itemId: string): Promise<void> {
  const items = await getAllItems();
  const idx = items.findIndex((i) => i.guid === guid);
  if (idx >= 0) {
    items[idx].synced = true;
    items[idx].itemId = itemId;
    await setItem(STORAGE_KEY_ITEMS, JSON.stringify(items));
  }
}

export async function getUnsyncedItems(): Promise<ItemData[]> {
  const items = await getAllItems();
  return items.filter((i) => !i.synced && !i.deleted);
}