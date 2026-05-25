import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";
import * as SplashScreen from "expo-splash-screen";

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // Small delay to ensure fonts/assets load
    setTimeout(() => {
      setReady(true);
      SplashScreen.hideAsync();
    }, 100);
  }, []);

  if (!ready) {
    return (
      <View style={{ flex: 1, justifyContent: "center", alignItems: "center" }}>
        <ActivityIndicator size="large" color="#1976D2" />
      </View>
    );
  }

  return (
    <>
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen
          name="details/[guid]"
          options={{ title: "Item Details", headerBackTitle: "Back" }}
        />
      </Stack>
      <StatusBar style="auto" />
    </>
  );
}