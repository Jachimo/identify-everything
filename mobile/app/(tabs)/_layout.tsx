import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";

export default function TabLayout() {
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: "#1976D2",
        headerStyle: { backgroundColor: "#1976D2" },
        headerTintColor: "#fff",
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Identify",
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="qr-code" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="explore"
        options={{
          title: "Items",
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="list" size={size} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}