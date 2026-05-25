import { useCallback, useState } from "react";
import { useFocusEffect, useRouter } from "expo-router";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { ItemData } from "../../src/types";
import { getAllItems, getUnsyncedItems, getDeviceId } from "../../src/sync/storage";
import { uploadSync } from "../../src/sync/api";

export default function ItemsScreen() {
  const router = useRouter();
  const [items, setItems] = useState<ItemData[]>([]);
  const [syncing, setSyncing] = useState(false);

  useFocusEffect(
    useCallback(() => {
      getAllItems().then(setItems);
    }, [])
  );

  const handleSync = async () => {
    setSyncing(true);
    try {
      const unsynced = await getUnsyncedItems();
      if (unsynced.length === 0) {
        Alert.alert("Sync", "All items are already synced.");
        setSyncing(false);
        return;
      }

      const versions = unsynced.map((item) => ({
        item_id: item.itemId || item.guid,
        guid: item.guid,
        url: item.url,
        domain: item.domain,
      }));

      const result = await uploadSync(versions);
      // Re-fetch items after sync
      await getAllItems().then(setItems);
      Alert.alert("Sync Complete", `Uploaded ${result.processed} item(s).`);
    } catch (e: any) {
      Alert.alert("Sync Failed", e.message);
    } finally {
      setSyncing(false);
    }
  };

  const renderItem = ({ item }: { item: ItemData }) => (
    <TouchableOpacity
      style={styles.itemRow}
      onPress={() => router.push(`/details/${item.guid}`)}
    >
      <View style={styles.itemInfo}>
        <Text style={styles.itemGuid} numberOfLines={1}>
          {item.guid}
        </Text>
        {item.title ? (
          <Text style={styles.itemTitle} numberOfLines={1}>
            {item.title}
          </Text>
        ) : null}
        <Text style={styles.itemUrl} numberOfLines={1}>
          {item.url}
        </Text>
      </View>
      <View style={styles.itemBadge}>
        {!item.synced && (
          <Ionicons name="cloud-upload-outline" size={16} color="#FF9800" />
        )}
        {item.synced && (
          <Ionicons name="cloud-done" size={16} color="#4CAF50" />
        )}
      </View>
    </TouchableOpacity>
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.count}>{items.length} item(s)</Text>
        <TouchableOpacity
          style={[styles.syncButton, syncing && styles.syncButtonDisabled]}
          onPress={handleSync}
          disabled={syncing}
        >
          <Ionicons
            name={syncing ? "sync" : "cloud-upload"}
            size={18}
            color="#fff"
          />
          <Text style={styles.syncButtonText}>
            {syncing ? "Syncing..." : "Sync"}
          </Text>
        </TouchableOpacity>
      </View>

      {items.length === 0 ? (
        <View style={styles.empty}>
          <Ionicons name="cube-outline" size={64} color="#ccc" />
          <Text style={styles.emptyText}>No items yet</Text>
          <Text style={styles.emptySubtext}>
            Enter a GUID on the Identify tab to get started
          </Text>
        </View>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.guid}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 16,
    backgroundColor: "#fff",
    borderBottomWidth: 1,
    borderBottomColor: "#e0e0e0",
  },
  count: {
    fontSize: 14,
    color: "#666",
  },
  syncButton: {
    flexDirection: "row",
    backgroundColor: "#1976D2",
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 6,
    alignItems: "center",
    gap: 6,
  },
  syncButtonDisabled: {
    opacity: 0.6,
  },
  syncButtonText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "600",
  },
  list: {
    padding: 12,
  },
  itemRow: {
    flexDirection: "row",
    backgroundColor: "#fff",
    padding: 14,
    borderRadius: 8,
    marginBottom: 8,
    alignItems: "center",
  },
  itemInfo: {
    flex: 1,
  },
  itemGuid: {
    fontSize: 14,
    fontWeight: "600",
    fontFamily: "monospace",
    color: "#333",
  },
  itemTitle: {
    fontSize: 13,
    color: "#666",
    marginTop: 2,
  },
  itemUrl: {
    fontSize: 11,
    color: "#999",
    marginTop: 2,
  },
  itemBadge: {
    marginLeft: 8,
  },
  empty: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 32,
  },
  emptyText: {
    fontSize: 18,
    color: "#999",
    marginTop: 16,
  },
  emptySubtext: {
    fontSize: 13,
    color: "#bbb",
    textAlign: "center",
    marginTop: 8,
  },
});