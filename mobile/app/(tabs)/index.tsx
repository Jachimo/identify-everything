import { useRouter } from "expo-router";
import { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Platform,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { isValidGuid, normalizeGuid, generateGuid } from "../../src/sync/guid";
import { getDeviceId } from "../../src/sync/storage";
import { registerDevice } from "../../src/sync/api";

export default function IdentifyScreen() {
  const router = useRouter();
  const [input, setInput] = useState("");
  const [valid, setValid] = useState<boolean | null>(null);
  const [deviceId, setDeviceId] = useState("");

  useEffect(() => {
    getDeviceId().then(setDeviceId);
    // Try registering device on load (best-effort)
    registerDevice().catch(() => {});
  }, []);

  const validate = useCallback((text: string) => {
    setInput(text);
    if (!text) {
      setValid(null);
    } else {
      setValid(isValidGuid(text));
    }
  }, []);

  const handleOpen = () => {
    const normalized = normalizeGuid(input);
    if (!normalized) {
      Alert.alert("Invalid GUID", "Please enter a valid 16-character Base26 GUID.");
      return;
    }
    router.push(`/details/${normalized}`);
  };

  const handleGenerate = () => {
    const guid = generateGuid();
    setInput(guid);
    setValid(true);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Identify Everything</Text>
      <Text style={styles.subtitle}>Enter or scan an item GUID</Text>

      <View style={styles.inputRow}>
        <TextInput
          style={[
            styles.input,
            valid === true && styles.inputValid,
            valid === false && styles.inputInvalid,
          ]}
          placeholder="e.g. 3k7x_9bp1_j4nv_6dab"
          value={input}
          onChangeText={validate}
          autoCapitalize="none"
          autoCorrect={false}
        />
        {input ? (
          <View style={styles.validationIcon}>
            <Ionicons
              name={valid ? "checkmark-circle" : "close-circle"}
              size={24}
              color={valid ? "#4CAF50" : "#F44336"}
            />
          </View>
        ) : null}
      </View>

      {valid === true && (
        <Text style={styles.validText}>Valid GUID</Text>
      )}
      {valid === false && (
        <Text style={styles.invalidText}>Invalid GUID format</Text>
      )}

      <TouchableOpacity
        style={[styles.button, !valid && styles.buttonDisabled]}
        onPress={handleOpen}
        disabled={!valid}
      >
        <Ionicons name="search" size={20} color="#fff" />
        <Text style={styles.buttonText}>View Item Details</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.secondaryButton} onPress={handleGenerate}>
        <Ionicons name="refresh" size={20} color="#1976D2" />
        <Text style={styles.secondaryButtonText}>Generate New GUID</Text>
      </TouchableOpacity>

      {Platform.OS !== "web" && (
        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={() => {
            // In-app QR scanning via expo-camera
            router.push("/scan");
          }}
        >
          <Ionicons name="camera" size={20} color="#1976D2" />
          <Text style={styles.secondaryButtonText}>Scan QR Code</Text>
        </TouchableOpacity>
      )}

      <Text style={styles.deviceId}>Device: {deviceId.slice(0, 12)}...</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    backgroundColor: "#f5f5f5",
  },
  title: {
    fontSize: 28,
    fontWeight: "bold",
    color: "#1976D2",
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: "#666",
    marginBottom: 32,
  },
  inputRow: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 8,
  },
  input: {
    flex: 1,
    borderWidth: 2,
    borderColor: "#ccc",
    borderRadius: 8,
    padding: 14,
    fontSize: 18,
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
    backgroundColor: "#fff",
  },
  inputValid: {
    borderColor: "#4CAF50",
  },
  inputInvalid: {
    borderColor: "#F44336",
  },
  validationIcon: {
    marginLeft: 8,
  },
  validText: {
    color: "#4CAF50",
    fontSize: 12,
    marginBottom: 16,
  },
  invalidText: {
    color: "#F44336",
    fontSize: 12,
    marginBottom: 16,
  },
  button: {
    flexDirection: "row",
    backgroundColor: "#1976D2",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    marginTop: 16,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
  },
  secondaryButton: {
    flexDirection: "row",
    backgroundColor: "#fff",
    padding: 14,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    marginTop: 12,
    borderWidth: 1,
    borderColor: "#1976D2",
  },
  secondaryButtonText: {
    color: "#1976D2",
    fontSize: 16,
    fontWeight: "600",
  },
  deviceId: {
    fontSize: 11,
    color: "#999",
    textAlign: "center",
    marginTop: 40,
  },
});