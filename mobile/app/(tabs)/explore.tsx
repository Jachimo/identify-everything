import { useCallback, useState, useEffect } from "react";
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
import { getAllItems } from "../../src/sync/storage";
import {
  performSync,
  addSyncListener,
  getSyncStatus,
  SyncStatus,
} from "../../src/sync/sync-manager";

export default function ItemsScreen() {
  const router = useRouter();
  const [items, setItems] = useState<ItemData[]>([]);
  const [syncStatus, setSyncStatus] = useState<SyncStatus>(getSyncStatus());

  useFocusEffect(
    useCallback(() => {
      getAllItems().then(setItems);
    }, [])
  );

  useEffect(() => {
    const unsub = addSyncListener(setSyncStatus);
    return unsub;
  }, []);

  const handleSync = async () => {
    try {
      await performSync();
      const refreshed = await getAllItems();
      setItems(refreshed);
    } catch (e: any) {
      Alert.alert("Sync Failed", e.message);
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
        {item.location ? (
          <Text style={styles.itemLocation} numberOfLines={1}>
            <Ionicons name="location" size={11} color="#1976D2" />{" "}
            {item.location.latitude.toFixed(4)},{" "}
            {item.location.longitude.toFixed(4)}
          </Text>
        ) : null}
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

  const hasPending = syncStatus.pendingItems > 0 || syncStatus.pendingPhotos > 0;

  return (
    <View style={styles.container}>
      {/* Sync status bar */}
      <View style={styles.syncBar}>
        <View style={styles.syncInfo}>
          {syncStatus.running ? (
            <View style={styles.syncRow}>
              <Ionicons name="sync" size={14} color="#1976D2" />
              <Text style={styles.syncText}>Syncing…</Text>
            </View>
          ) : hasPending ? (
            <View style={styles.syncRow}>
              <Ionicons name="cloud-upload-outline" size={14} color="#E65100" />
              <Text style={[styles.syncText, { color: "#E65100" }]}>
                {syncStatus.pendingItems} item(s), {syncStatus.pendingPhotos} photo(s) pending
              </Text>
            </View>
          ) : (
            <View style={styles.syncRow}>
              <Ionicons name="cloud-done" size={14} color="#4CAF50" />
              <Text style={[styles.syncText, { color: "#4CAF50" }]}>
                {syncStatus.lastSync
                  ? `Synced ${new Date(syncStatus.lastSync).toLocaleTimeString()}`
                  : "All synced"}
              </Text>
            </View>
          )}
          <Text style={styles.countText}>{items.length} item(s)</Text>
        </View>
        <TouchableOpacity
          style={[styles.syncButton, syncStatus.running && styles.syncButtonDisabled]}
          onPress={handleSync}
          disabled={syncStatus.running}
        >
          <Ionicons name="sync" size={16} color="#fff" />
          <Text style={styles.syncButtonText}>
            {syncStatus.running ? "Syncing…" : "Sync Now"}
          </Text>
        </TouchableOpacity>
      </View>

      {syncStatus.lastError && (
        <View style={styles.errorBar}>
          <Ionicons name="warning-outline" size={14} color="#C62828" />
          <Text style={styles.errorText} numberOfLines={1}>
            {syncStatus.lastError}
          </Text>
        </View>
      )}

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
          onRefresh={() => getAllItems().then(setItems)}
          refreshing={false}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f5f5f5" },

  syncBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: "#fff",
    borderBottomWidth: 1,
    borderBottomColor: "#e0e0e0",
  },
  syncInfo: { flex: 1, gap: 2 },
  syncRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  syncText: { fontSize: 12, color: "#1976D2" },
  countText: { fontSize: 11, color: "#999" },
  syncButton: {
    flexDirection: "row",
    backgroundColor: "#1976D2",
    paddingVertical: 7,
    paddingHorizontal: 14,
    borderRadius: 6,
    alignItems: "center",
    gap: 6,
    marginLeft: 12,
  },
  syncButtonDisabled: { opacity: 0.6 },
  syncButtonText: { color: "#fff", fontSize: 13, fontWeight: "600" },

  errorBar: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "#FFEBEE",
    paddingHorizontal: 16,
    paddingVertical: 6,
  },
  errorText: { color: "#C62828", fontSize: 12, flex: 1 },

  list: { padding: 12 },
  itemRow: {
    flexDirection: "row",
    backgroundColor: "#fff",
    padding: 14,
    borderRadius: 8,
    marginBottom: 8,
    alignItems: "center",
  },
  itemInfo: { flex: 1 },
  itemGuid: {
    fontSize: 14,
    fontWeight: "600",
    fontFamily: "monospace",
    color: "#333",
  },
  itemTitle: { fontSize: 13, color: "#555", marginTop: 2 },
  itemLocation: { fontSize: 11, color: "#1976D2", marginTop: 2 },
  itemBadge: { marginLeft: 8 },

  empty: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 32,
  },
  emptyText: { fontSize: 18, color: "#999", marginTop: 16 },
  emptySubtext: {
    fontSize: 13,
    color: "#bbb",
    textAlign: "center",
    marginTop: 8,
  },
});
