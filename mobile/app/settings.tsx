import { useState, useEffect } from "react";
import { useRouter } from "expo-router";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { getServerUrl, setServerUrl, getDeviceId } from "../src/sync/storage";
import { registerDevice } from "../src/sync/api";

export default function SettingsScreen() {
  const router = useRouter();
  const [serverUrl, setServerUrlState] = useState("");
  const [deviceId, setDeviceId] = useState("");
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<"ok" | "fail" | null>(null);

  useEffect(() => {
    getServerUrl().then(setServerUrlState);
    getDeviceId().then(setDeviceId);
  }, []);

  const handleSave = async () => {
    const url = serverUrl.trim().replace(/\/$/, "");
    if (!url.startsWith("http")) {
      Alert.alert("Invalid URL", "Server URL must start with http:// or https://");
      return;
    }
    await setServerUrl(url);
    setServerUrlState(url);
    Alert.alert("Saved", "Server URL updated.");
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const url = serverUrl.trim().replace(/\/$/, "");
      const resp = await fetch(`${url}/api/v1/health`, {
        signal: AbortSignal.timeout(8000),
      });
      if (resp.ok) {
        setTestResult("ok");
      } else {
        setTestResult("fail");
      }
    } catch {
      setTestResult("fail");
    } finally {
      setTesting(false);
    }
  };

  const handleRegister = async () => {
    try {
      await registerDevice();
      Alert.alert("Success", "Device registered with server.");
    } catch (e: any) {
      Alert.alert("Error", e.message || "Registration failed.");
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.section}>Server Connection</Text>

      <Text style={styles.label}>Server URL</Text>
      <TextInput
        style={styles.input}
        value={serverUrl}
        onChangeText={setServerUrlState}
        placeholder="http://10.0.2.2:5000"
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
      />
      <Text style={styles.hint}>
        Use 10.0.2.2 for the Android emulator to reach localhost on your computer.
        Use your machine's LAN IP (e.g. 192.168.1.x) for a real device.
      </Text>

      <View style={styles.row}>
        <TouchableOpacity style={[styles.button, styles.buttonOutline]} onPress={handleTest}>
          {testing ? (
            <ActivityIndicator size="small" color="#1976D2" />
          ) : (
            <Ionicons name="wifi" size={18} color="#1976D2" />
          )}
          <Text style={styles.buttonOutlineText}>Test Connection</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={handleSave}>
          <Ionicons name="save" size={18} color="#fff" />
          <Text style={styles.buttonText}>Save</Text>
        </TouchableOpacity>
      </View>

      {testResult === "ok" && (
        <View style={[styles.badge, styles.badgeOk]}>
          <Ionicons name="checkmark-circle" size={16} color="#4CAF50" />
          <Text style={styles.badgeOkText}>Server reachable</Text>
        </View>
      )}
      {testResult === "fail" && (
        <View style={[styles.badge, styles.badgeFail]}>
          <Ionicons name="close-circle" size={16} color="#F44336" />
          <Text style={styles.badgeFailText}>Cannot reach server — check URL and network</Text>
        </View>
      )}

      <Text style={[styles.section, { marginTop: 32 }]}>Device</Text>

      <Text style={styles.label}>Device ID</Text>
      <View style={styles.monoBox}>
        <Text style={styles.mono}>{deviceId}</Text>
      </View>

      <TouchableOpacity style={[styles.button, styles.buttonOutline, { marginTop: 12 }]} onPress={handleRegister}>
        <Ionicons name="cloud-upload-outline" size={18} color="#1976D2" />
        <Text style={styles.buttonOutlineText}>Register Device with Server</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f5f5f5" },
  content: { padding: 20, paddingBottom: 40 },
  section: {
    fontSize: 12,
    fontWeight: "700",
    color: "#999",
    textTransform: "uppercase",
    letterSpacing: 1,
    marginBottom: 12,
  },
  label: {
    fontSize: 14,
    fontWeight: "600",
    color: "#555",
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    backgroundColor: "#fff",
    color: "#333",
    marginBottom: 8,
  },
  hint: {
    fontSize: 12,
    color: "#999",
    marginBottom: 16,
    lineHeight: 18,
  },
  row: {
    flexDirection: "row",
    gap: 10,
    marginBottom: 12,
  },
  button: {
    flex: 1,
    flexDirection: "row",
    backgroundColor: "#1976D2",
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
  },
  buttonText: { color: "#fff", fontSize: 14, fontWeight: "600" },
  buttonOutline: {
    backgroundColor: "#fff",
    borderWidth: 1,
    borderColor: "#1976D2",
  },
  buttonOutlineText: { color: "#1976D2", fontSize: 14, fontWeight: "600" },
  badge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    padding: 10,
    borderRadius: 8,
    marginBottom: 8,
  },
  badgeOk: { backgroundColor: "#E8F5E9" },
  badgeOkText: { color: "#388E3C", fontSize: 13 },
  badgeFail: { backgroundColor: "#FFEBEE" },
  badgeFailText: { color: "#C62828", fontSize: 13 },
  monoBox: {
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 8,
    padding: 12,
    backgroundColor: "#fff",
  },
  mono: { fontFamily: "monospace", fontSize: 13, color: "#333" },
});
