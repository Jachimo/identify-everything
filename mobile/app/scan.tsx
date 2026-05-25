import { useRouter } from "expo-router";
import { useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";

export default function ScanScreen() {
  const router = useRouter();
  const [scanning, setScanning] = useState(false);
  const [scanned, setScanned] = useState<string | null>(null);

  const handleBarCodeScanned = ({ data }: { data: string }) => {
    if (scanned) return;
    setScanned(data);
    router.replace(`/details/${encodeURIComponent(data)}`);
  };

  if (scanning) {
    // In a real Expo build with expo-camera, we'd render a Camera view here.
    // For now, show a manual entry fallback.
    return (
      <View style={styles.container}>
        <Text style={styles.text}>
          QR scanner requires a physical device or Expo Go.
        </Text>
        <Text style={styles.hint}>
          Go back and enter the GUID manually.
        </Text>
        <TouchableOpacity
          style={styles.button}
          onPress={() => router.back()}
        >
          <Text style={styles.buttonText}>Go Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Ionicons name="camera-outline" size={80} color="#1976D2" />
      <Text style={styles.text}>Scan an Item QR Code</Text>
      <TouchableOpacity
        style={styles.button}
        onPress={() => setScanning(true)}
      >
        <Ionicons name="scan" size={20} color="#fff" />
        <Text style={styles.buttonText}>Start Scanning</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={styles.link}
        onPress={() => router.back()}
      >
        <Text style={styles.linkText}>Enter GUID manually</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 24,
    backgroundColor: "#f5f5f5",
  },
  text: {
    fontSize: 18,
    color: "#333",
    marginTop: 16,
    marginBottom: 24,
    textAlign: "center",
  },
  hint: {
    fontSize: 14,
    color: "#999",
    textAlign: "center",
    marginBottom: 16,
  },
  button: {
    flexDirection: "row",
    backgroundColor: "#1976D2",
    paddingVertical: 14,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: "center",
    gap: 8,
  },
  buttonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
  },
  link: {
    marginTop: 20,
  },
  linkText: {
    color: "#1976D2",
    fontSize: 14,
  },
});