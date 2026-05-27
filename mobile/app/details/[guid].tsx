import { useLocalSearchParams, useRouter } from "expo-router";
import { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
  Image,
  Platform,
} from "react-native";
import * as ImagePicker from "expo-image-picker";
import * as Location from "expo-location";
import { Ionicons } from "@expo/vector-icons";
import { ItemData, LocationData, PendingPhotoUpload, AttachmentData } from "../../src/types";
import {
  getItemByGuid,
  saveItem,
  getServerUrl,
  addPendingPhoto,
  getPendingPhotosForItem,
  getCachedAttachments,
  saveCachedAttachments,
  addCachedAttachment,
} from "../../src/sync/storage";
import {
  getItem as apiGetItem,
  createItem,
  updateItem,
  uploadPhoto,
  getAttachmentUrl,
  mapAttachmentFromServer,
} from "../../src/sync/api";
import { buildItemUrl } from "../../src/sync/guid";
import { performSync } from "../../src/sync/sync-manager";

function formatCoords(loc: LocationData) {
  const lat = loc.latitude.toFixed(5);
  const lng = loc.longitude.toFixed(5);
  const acc = loc.accuracy != null ? ` ±${Math.round(loc.accuracy)}m` : "";
  return `${lat}, ${lng}${acc}`;
}

function formatTimestamp(ts: string) {
  try {
    return new Date(ts).toLocaleString();
  } catch {
    return ts;
  }
}

export default function ItemDetailsScreen() {
  const params = useLocalSearchParams<{
    guid: string;
    lat?: string;
    lng?: string;
    acc?: string;
  }>();
  const router = useRouter();
  const guidStr = params.guid || "";

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [item, setItem] = useState<ItemData | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [serverUrl, setServerUrl] = useState("");
  const [location, setLocation] = useState<LocationData | null>(null);
  const [locating, setLocating] = useState(false);
  const [attachments, setAttachments] = useState<AttachmentData[]>([]);
  const [pendingPhotos, setPendingPhotos] = useState<PendingPhotoUpload[]>([]);
  const [uploading, setUploading] = useState(false);

  // Build scan-time location from URL params
  const scanLocation: LocationData | null =
    params.lat && params.lng
      ? {
          latitude: parseFloat(params.lat),
          longitude: parseFloat(params.lng),
          accuracy: params.acc ? parseFloat(params.acc) : null,
          timestamp: new Date().toISOString(),
        }
      : null;

  useEffect(() => {
    getServerUrl().then(setServerUrl);
    loadItem();
  }, [guidStr]);

  const loadItem = useCallback(async () => {
    setLoading(true);
    try {
      let local = await getItemByGuid(guidStr);

      if (local) {
        setItem(local);
        setTitle(local.title || "");
        setDescription(local.description || "");
        if (local.location) setLocation(local.location);
      }

      // Load cached attachments immediately
      const cached = await getCachedAttachments(guidStr);
      setAttachments(cached);

      // Load pending photos
      const pending = await getPendingPhotosForItem(guidStr);
      setPendingPhotos(pending);

      // Try to fetch fresh data from server
      try {
        const remote = await apiGetItem(guidStr);
        if (remote) {
          const remoteData = (remote as any).latest_version?.data || {};

          if (!local) {
            const newItem: ItemData = {
              guid: guidStr,
              itemId: (remote as any).item_id || "",
              url: (remote as any).url || buildItemUrl("mylabels.example.com", guidStr),
              domain: (remote as any).domain || "mylabels.example.com",
              title: remoteData.title || "",
              description: remoteData.description || "",
              location: remoteData.location || undefined,
              createdAt: (remote as any).created_at || new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              deleted: false,
              synced: true,
            };
            await saveItem(newItem);
            setItem(newItem);
            setTitle(newItem.title || "");
            setDescription(newItem.description || "");
            if (newItem.location) setLocation(newItem.location);
            local = newItem;
          } else {
            local.title = remoteData.title || local.title;
            local.description = remoteData.description || local.description;
            local.location = remoteData.location || local.location;
            local.synced = true;
            if ((remote as any).item_id) local.itemId = (remote as any).item_id;
            await saveItem(local);
            setItem({ ...local });
            setTitle(local.title || "");
            setDescription(local.description || "");
            if (local.location) setLocation(local.location);
          }

          // Load attachments from server
          const serverAttachments: AttachmentData[] = (
            (remote as any).latest_version?.attachments || []
          ).map((a: any) => mapAttachmentFromServer(a));
          await saveCachedAttachments(guidStr, serverAttachments);
          setAttachments(serverAttachments);
        }
      } catch {
        // Offline — use local data
      }

      // Apply scan-time location if fresh and no existing location
      if (scanLocation && !location) {
        setLocation(scanLocation);
      }
    } finally {
      setLoading(false);
    }
  }, [guidStr]);

  const captureLocation = async () => {
    setLocating(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== "granted") {
        Alert.alert("Permission denied", "Location access is required to tag this item.");
        return;
      }
      const loc = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });
      const newLoc: LocationData = {
        latitude: loc.coords.latitude,
        longitude: loc.coords.longitude,
        accuracy: loc.coords.accuracy,
        timestamp: new Date().toISOString(),
      };
      setLocation(newLoc);
    } catch (e: any) {
      Alert.alert("Location error", e.message || "Could not get location.");
    } finally {
      setLocating(false);
    }
  };

  const takePhoto = async () => {
    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== "granted") {
      Alert.alert("Permission denied", "Camera access is required to take a photo.");
      return;
    }

    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ["images"],
      quality: 0.7,
      allowsEditing: false,
    });

    if (result.canceled || !result.assets?.[0]) return;

    const asset = result.assets[0];
    const uri = asset.uri;
    const filename = `photo_${guidStr}_${Date.now()}.jpg`;
    const mimeType = asset.mimeType || "image/jpeg";

    // Try to upload immediately
    setUploading(true);
    try {
      const uploaded = await uploadPhoto(guidStr, uri, filename, mimeType);
      await addCachedAttachment(guidStr, uploaded);
      setAttachments((prev) => [...prev, uploaded]);
      Alert.alert("Uploaded", "Photo saved to server.");
    } catch {
      // Offline — queue for later
      const pending: PendingPhotoUpload = {
        id: Math.random().toString(36).slice(2),
        guid: guidStr,
        localUri: uri,
        filename,
        mimeType,
        createdAt: new Date().toISOString(),
      };
      await addPendingPhoto(pending);
      setPendingPhotos((prev) => [...prev, pending]);
      Alert.alert(
        "Saved locally",
        "Photo queued for upload. It will sync to the server when a connection is available."
      );
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (!title.trim()) {
      Alert.alert("Validation", "Title is required.");
      return;
    }

    setSaving(true);
    try {
      const domain = item?.domain || "mylabels.example.com";
      const url = item?.url || buildItemUrl(domain, guidStr);
      const data: Record<string, unknown> = {
        title: title.trim(),
        description: description.trim(),
      };
      if (location) data.location = location;

      if (!item) {
        // Create new item locally
        let itemId = "";
        try {
          const result = await createItem(guidStr, url, domain);
          itemId = "item_id" in result ? result.item_id : "";
          if (itemId) await updateItem(guidStr, data, "Created via mobile app");
        } catch { /* offline */ }

        const newItem: ItemData = {
          guid: guidStr,
          itemId,
          url,
          domain,
          title: title.trim(),
          description: description.trim(),
          location: location ?? undefined,
          data,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          deleted: false,
          synced: !!itemId,
        };
        await saveItem(newItem);
        setItem(newItem);
      } else {
        let synced = false;
        try {
          if (!item.itemId) {
            const result = await createItem(guidStr, url, domain);
            if ("item_id" in result) item.itemId = result.item_id;
          }
          await updateItem(guidStr, data, "Updated via mobile app");
          synced = true;
        } catch { /* offline */ }

        const updated: ItemData = {
          ...item,
          title: title.trim(),
          description: description.trim(),
          location: location ?? item.location,
          data,
          updatedAt: new Date().toISOString(),
          synced,
        };
        await saveItem(updated);
        setItem(updated);
      }

      // Trigger background sync
      performSync().catch(() => {});

      Alert.alert("Saved", "Item saved successfully.");
    } catch (e: any) {
      Alert.alert("Error", e.message || "Failed to save item.");
    } finally {
      setSaving(false);
    }
  };

  const buildPhotoUrl = async (attachmentId: string) => {
    return getAttachmentUrl(guidStr, attachmentId);
  };

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" color="#1976D2" />
      </View>
    );
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* GUID & URL */}
      <Text style={styles.guid}>{guidStr}</Text>
      <Text style={styles.url}>
        {item?.url || buildItemUrl("mylabels.example.com", guidStr)}
      </Text>

      {/* Sync badge */}
      {item && (
        <View style={[styles.badge, item.synced ? styles.badgeOk : styles.badgePending]}>
          <Ionicons
            name={item.synced ? "cloud-done" : "cloud-upload-outline"}
            size={14}
            color={item.synced ? "#388E3C" : "#E65100"}
          />
          <Text style={item.synced ? styles.badgeOkText : styles.badgePendingText}>
            {item.synced ? "Synced" : "Pending sync"}
          </Text>
        </View>
      )}

      {/* ── TITLE ── */}
      <Text style={styles.label}>Title</Text>
      <TextInput
        style={styles.input}
        value={title}
        onChangeText={setTitle}
        placeholder="Item title"
        placeholderTextColor="#999"
      />

      {/* ── DESCRIPTION ── */}
      <Text style={styles.label}>Description</Text>
      <TextInput
        style={[styles.input, styles.textArea]}
        value={description}
        onChangeText={setDescription}
        placeholder="Item description"
        placeholderTextColor="#999"
        multiline
        numberOfLines={4}
      />

      {/* ── GPS LOCATION ── */}
      <Text style={styles.label}>Location</Text>
      <View style={styles.locationCard}>
        {location ? (
          <>
            <View style={styles.locationRow}>
              <Ionicons name="location" size={18} color="#1976D2" />
              <Text style={styles.locationCoords}>{formatCoords(location)}</Text>
            </View>
            <Text style={styles.locationTime}>
              Tagged {formatTimestamp(location.timestamp)}
            </Text>
          </>
        ) : (
          <View style={styles.locationRow}>
            <Ionicons name="location-outline" size={18} color="#999" />
            <Text style={styles.locationEmpty}>No location recorded</Text>
          </View>
        )}
        <TouchableOpacity
          style={styles.locationButton}
          onPress={captureLocation}
          disabled={locating}
        >
          {locating ? (
            <ActivityIndicator size="small" color="#1976D2" />
          ) : (
            <Ionicons name="navigate" size={16} color="#1976D2" />
          )}
          <Text style={styles.locationButtonText}>
            {locating ? "Getting location…" : location ? "Update Location" : "Capture Location"}
          </Text>
        </TouchableOpacity>
      </View>

      {/* ── PHOTOS ── */}
      <Text style={styles.label}>Photos</Text>

      {/* Existing server attachments */}
      {attachments.length > 0 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.photoStrip}>
          {attachments.map((att) => (
            <AttachmentThumb
              key={att.attachmentId}
              attachment={att}
              guid={guidStr}
              serverUrl={serverUrl}
            />
          ))}
        </ScrollView>
      )}

      {/* Pending (offline) photos */}
      {pendingPhotos.length > 0 && (
        <>
          <Text style={styles.pendingLabel}>
            {pendingPhotos.length} photo(s) pending upload
          </Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.photoStrip}>
            {pendingPhotos.map((p) => (
              <View key={p.id} style={styles.thumbContainer}>
                <Image source={{ uri: p.localUri }} style={styles.thumb} />
                <View style={styles.thumbBadge}>
                  <Ionicons name="cloud-upload-outline" size={12} color="#fff" />
                </View>
              </View>
            ))}
          </ScrollView>
        </>
      )}

      {attachments.length === 0 && pendingPhotos.length === 0 && (
        <View style={styles.emptyPhotos}>
          <Ionicons name="images-outline" size={32} color="#ccc" />
          <Text style={styles.emptyPhotosText}>No photos yet</Text>
        </View>
      )}

      {/* Take photo button */}
      {Platform.OS !== "web" && (
        <TouchableOpacity
          style={[styles.photoButton, uploading && styles.buttonDisabled]}
          onPress={takePhoto}
          disabled={uploading}
        >
          {uploading ? (
            <ActivityIndicator size="small" color="#1976D2" />
          ) : (
            <Ionicons name="camera" size={20} color="#1976D2" />
          )}
          <Text style={styles.photoButtonText}>
            {uploading ? "Uploading…" : "Take Photo"}
          </Text>
        </TouchableOpacity>
      )}

      {/* ── SAVE ── */}
      <TouchableOpacity
        style={[styles.saveButton, saving && styles.buttonDisabled]}
        onPress={handleSave}
        disabled={saving}
      >
        <Ionicons name="save" size={20} color="#fff" />
        <Text style={styles.saveButtonText}>
          {saving ? "Saving…" : "Save Changes"}
        </Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

// ── Attachment thumbnail ────────────────────────────────────────────────────

function AttachmentThumb({
  attachment,
  guid,
  serverUrl,
}: {
  attachment: AttachmentData;
  guid: string;
  serverUrl: string;
}) {
  const url = attachment.localUri
    ? attachment.localUri
    : `${serverUrl.replace(/\/$/, "")}/api/v1/items/${encodeURIComponent(guid)}/attach/${attachment.attachmentId}`;

  return (
    <View style={styles.thumbContainer}>
      <Image
        source={{ uri: url }}
        style={styles.thumb}
        resizeMode="cover"
      />
      <View style={[styles.thumbBadge, { backgroundColor: "#4CAF50" }]}>
        <Ionicons name="cloud-done" size={12} color="#fff" />
      </View>
    </View>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f5f5f5" },
  content: { padding: 20, paddingBottom: 48 },
  centered: { flex: 1, justifyContent: "center", alignItems: "center" },

  guid: { fontSize: 16, fontFamily: "monospace", color: "#333", marginBottom: 2 },
  url: { fontSize: 11, color: "#999", marginBottom: 8 },

  badge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    alignSelf: "flex-start",
    paddingVertical: 3,
    paddingHorizontal: 8,
    borderRadius: 10,
    marginBottom: 16,
  },
  badgeOk: { backgroundColor: "#E8F5E9" },
  badgePending: { backgroundColor: "#FFF3E0" },
  badgeOkText: { color: "#388E3C", fontSize: 12 },
  badgePendingText: { color: "#E65100", fontSize: 12 },

  label: {
    fontSize: 13,
    fontWeight: "600",
    color: "#555",
    marginBottom: 6,
    marginTop: 14,
  },
  input: {
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    backgroundColor: "#fff",
    color: "#333",
  },
  textArea: { minHeight: 90, textAlignVertical: "top" },

  locationCard: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 8,
    backgroundColor: "#fff",
    padding: 12,
    gap: 6,
  },
  locationRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  locationCoords: { fontSize: 14, color: "#333", fontFamily: "monospace", flex: 1 },
  locationEmpty: { fontSize: 14, color: "#999" },
  locationTime: { fontSize: 11, color: "#999", marginLeft: 26 },
  locationButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: 6,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: "#eee",
  },
  locationButtonText: { color: "#1976D2", fontSize: 14, fontWeight: "600" },

  photoStrip: { marginBottom: 8 },
  thumbContainer: { position: "relative", marginRight: 8 },
  thumb: { width: 88, height: 88, borderRadius: 6, backgroundColor: "#eee" },
  thumbBadge: {
    position: "absolute",
    bottom: 4,
    right: 4,
    backgroundColor: "#FF9800",
    borderRadius: 8,
    padding: 2,
  },
  pendingLabel: { fontSize: 12, color: "#E65100", marginBottom: 6 },
  emptyPhotos: { alignItems: "center", paddingVertical: 16, gap: 6 },
  emptyPhotosText: { color: "#bbb", fontSize: 13 },

  photoButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    marginTop: 10,
    borderWidth: 1,
    borderColor: "#1976D2",
    backgroundColor: "#fff",
    paddingVertical: 12,
    borderRadius: 8,
  },
  photoButtonText: { color: "#1976D2", fontSize: 15, fontWeight: "600" },

  saveButton: {
    flexDirection: "row",
    backgroundColor: "#1976D2",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    marginTop: 24,
  },
  buttonDisabled: { opacity: 0.6 },
  saveButtonText: { color: "#fff", fontSize: 16, fontWeight: "600" },
});
