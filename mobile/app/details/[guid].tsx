import { useLocalSearchParams, useRouter } from "expo-router";
import { useState, useEffect } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { ItemData } from "../../src/types";
import {
  getItemByGuid,
  saveItem,
  getServerUrl,
} from "../../src/sync/storage";
import { getItem as apiGetItem, createItem, updateItem } from "../../src/sync/api";
import { buildItemUrl } from "../../src/sync/guid";

export default function ItemDetailsScreen() {
  const { guid } = useLocalSearchParams<{ guid: string }>();
  const router = useRouter();
  const guidStr = guid || "";

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [item, setItem] = useState<ItemData | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [serverUrl, setServerUrl] = useState("");

  useEffect(() => {
    getServerUrl().then(setServerUrl);
    loadItem();
  }, [guidStr]);

  const loadItem = async () => {
    setLoading(true);
    try {
      // Try local first
      let local = await getItemByGuid(guidStr);
      if (local) {
        setItem(local);
        setTitle(local.title || "");
        setDescription(local.description || "");
      }

      // Then try remote (may have newer data)
      try {
        const remote = await apiGetItem(guidStr);
        if (remote && local) {
          // Update local from remote
          local.title = (remote as any).latest_version?.data?.title || local.title;
          local.description =
            (remote as any).latest_version?.data?.description || local.description;
          local.synced = true;
          if (remote.item_id) local.itemId = remote.item_id as string;
          await saveItem(local);
          setTitle(local.title || "");
          setDescription(local.description || "");
        } else if (remote && !local) {
          // Create local from remote
          const newItem: ItemData = {
            guid: guidStr,
            itemId: (remote as any).item_id || "",
            url: (remote as any).url || buildItemUrl("mylabels.example.com", guidStr),
            domain: (remote as any).domain || "mylabels.example.com",
            title: (remote as any).latest_version?.data?.title || "",
            description: (remote as any).latest_version?.data?.description || "",
            createdAt: (remote as any).created_at || new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            deleted: false,
            synced: true,
          };
          await saveItem(newItem);
          setItem(newItem);
          setTitle(newItem.title || "");
          setDescription(newItem.description || "");
        }
      } catch {
        // Remote unavailable — use local data
      }
    } finally {
      setLoading(false);
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

      if (!item) {
        // Create new item
        const result = await createItem(guidStr, url, domain);
        const itemId =
          "item_id" in result ? result.item_id : guidStr;

        const newItem: ItemData = {
          guid: guidStr,
          itemId,
          url,
          domain,
          title: title.trim(),
          description: description.trim(),
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          deleted: false,
          synced: "item_id" in result,
        };
        await saveItem(newItem);
        setItem(newItem);
      } else {
        // Update existing item
        const data: Record<string, unknown> = {
          title: title.trim(),
          description: description.trim(),
        };

        try {
          await updateItem(guidStr, data, "Updated via mobile app");
        } catch {
          // Offline — will sync later
        }

        item.title = title.trim();
        item.description = description.trim();
        item.updatedAt = new Date().toISOString();
        item.synced = false;
        await saveItem(item);
      }

      Alert.alert("Saved", "Item saved successfully.");
    } catch (e: any) {
      Alert.alert("Error", e.message || "Failed to save item.");
    } finally {
      setSaving(false);
    }
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
      <Text style={styles.guid}>{guidStr}</Text>
      <Text style={styles.url}>{item?.url || buildItemUrl("mylabels.example.com", guidStr)}</Text>

      <Text style={styles.label}>Title</Text>
      <TextInput
        style={styles.input}
        value={title}
        onChangeText={setTitle}
        placeholder="Item title"
        placeholderTextColor="#999"
      />

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

      <Text style={styles.label}>Location</Text>
      <View style={styles.locationBox}>
        <Ionicons name="location-outline" size={16} color="#999" />
        <Text style={styles.locationText}>GPS: Pending</Text>
      </View>

      <TouchableOpacity
        style={[styles.saveButton, saving && styles.saveButtonDisabled]}
        onPress={handleSave}
        disabled={saving}
      >
        <Ionicons name="save" size={20} color="#fff" />
        <Text style={styles.saveButtonText}>
          {saving ? "Saving..." : "Save Changes"}
        </Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  content: {
    padding: 20,
    paddingBottom: 40,
  },
  centered: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  guid: {
    fontSize: 16,
    fontFamily: "monospace",
    color: "#333",
    marginBottom: 4,
  },
  url: {
    fontSize: 12,
    color: "#999",
    marginBottom: 24,
  },
  label: {
    fontSize: 14,
    fontWeight: "600",
    color: "#555",
    marginBottom: 6,
    marginTop: 12,
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
  textArea: {
    minHeight: 100,
    textAlignVertical: "top",
  },
  locationBox: {
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 8,
    padding: 12,
    backgroundColor: "#fff",
    gap: 8,
  },
  locationText: {
    fontSize: 14,
    color: "#999",
  },
  saveButton: {
    flexDirection: "row",
    backgroundColor: "#1976D2",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    marginTop: 32,
  },
  saveButtonDisabled: {
    opacity: 0.6,
  },
  saveButtonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
  },
});