import { useRouter } from "expo-router";
import { useState, useRef } from "react";
import { View, Text, StyleSheet, TouchableOpacity, Alert } from "react-native";
import { CameraView, useCameraPermissions } from "expo-camera";
import * as Location from "expo-location";
import { Ionicons } from "@expo/vector-icons";
import { normalizeGuid } from "../src/sync/guid";

export default function ScanScreen() {
  const router = useRouter();
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const cooldown = useRef(false);

  if (!permission) {
    return (
      <View style={styles.centered}>
        <Text style={styles.text}>Checking camera permission…</Text>
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={styles.centered}>
        <Ionicons name="camera-off-outline" size={64} color="#ccc" />
        <Text style={styles.text}>Camera access is required to scan QR codes.</Text>
        <TouchableOpacity style={styles.button} onPress={requestPermission}>
          <Text style={styles.buttonText}>Grant Permission</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.link} onPress={() => router.back()}>
          <Text style={styles.linkText}>Go Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const handleBarCodeScanned = async ({ data }: { data: string }) => {
    if (cooldown.current) return;
    cooldown.current = true;

    let guid = data.trim();
    const match = guid.match(/\/objects\/v1\/([a-p0-9_]{16,19})$/i);
    if (match) guid = match[1];

    const normalized = normalizeGuid(guid);
    if (!normalized) {
      Alert.alert(
        "Not an item QR code",
        `Scanned: ${data}\n\nThis doesn't look like a valid item GUID.`,
        [{ text: "Try Again", onPress: () => { cooldown.current = false; } }]
      );
      return;
    }

    setScanned(true);

    // Capture GPS location in background (non-blocking)
    let latParam = "";
    let lngParam = "";
    let accParam = "";
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status === "granted") {
        const loc = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
          timeInterval: 5000,
        });
        latParam = String(loc.coords.latitude);
        lngParam = String(loc.coords.longitude);
        accParam = String(loc.coords.accuracy ?? "");
      }
    } catch {
      // Location unavailable — proceed without it
    }

    const params = new URLSearchParams();
    if (latParam) params.set("lat", latParam);
    if (lngParam) params.set("lng", lngParam);
    if (accParam) params.set("acc", accParam);
    const qs = params.toString() ? `?${params.toString()}` : "";

    router.replace(`/details/${encodeURIComponent(normalized)}${qs}`);
  };

  return (
    <View style={styles.container}>
      <CameraView
        style={StyleSheet.absoluteFillObject}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
        onBarcodeScanned={scanned ? undefined : handleBarCodeScanned}
      />

      <View style={styles.overlay}>
        <View style={styles.topBar}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Ionicons name="arrow-back" size={24} color="#fff" />
            <Text style={styles.backText}>Cancel</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.scanArea}>
          <View style={styles.corner} />
          <View style={[styles.corner, styles.cornerTopRight]} />
          <View style={[styles.corner, styles.cornerBottomLeft]} />
          <View style={[styles.corner, styles.cornerBottomRight]} />
        </View>

        <View style={styles.bottomBar}>
          <Text style={styles.hint}>Point the camera at an item QR code</Text>
        </View>
      </View>
    </View>
  );
}

const CORNER = 32;
const BORDER = 4;

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#000" },
  centered: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 32,
    backgroundColor: "#f5f5f5",
  },
  text: {
    fontSize: 16,
    color: "#333",
    textAlign: "center",
    marginTop: 16,
    marginBottom: 24,
  },
  button: {
    backgroundColor: "#1976D2",
    paddingVertical: 14,
    paddingHorizontal: 32,
    borderRadius: 8,
    marginBottom: 12,
  },
  buttonText: { color: "#fff", fontSize: 16, fontWeight: "600" },
  link: { marginTop: 8 },
  linkText: { color: "#1976D2", fontSize: 14 },
  overlay: { ...StyleSheet.absoluteFillObject, justifyContent: "space-between" },
  topBar: {
    paddingTop: 56,
    paddingHorizontal: 16,
    backgroundColor: "rgba(0,0,0,0.5)",
  },
  backButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 8,
  },
  backText: { color: "#fff", fontSize: 16 },
  scanArea: {
    alignSelf: "center",
    width: 240,
    height: 240,
    position: "relative",
  },
  corner: {
    position: "absolute",
    top: 0,
    left: 0,
    width: CORNER,
    height: CORNER,
    borderTopWidth: BORDER,
    borderLeftWidth: BORDER,
    borderColor: "#fff",
  },
  cornerTopRight: {
    left: undefined,
    right: 0,
    borderLeftWidth: 0,
    borderRightWidth: BORDER,
  },
  cornerBottomLeft: {
    top: undefined,
    bottom: 0,
    borderTopWidth: 0,
    borderBottomWidth: BORDER,
  },
  cornerBottomRight: {
    top: undefined,
    bottom: 0,
    left: undefined,
    right: 0,
    borderTopWidth: 0,
    borderLeftWidth: 0,
    borderRightWidth: BORDER,
    borderBottomWidth: BORDER,
  },
  bottomBar: {
    paddingBottom: 48,
    paddingHorizontal: 32,
    backgroundColor: "rgba(0,0,0,0.5)",
    alignItems: "center",
    paddingTop: 16,
  },
  hint: { color: "#fff", fontSize: 14, textAlign: "center" },
});
