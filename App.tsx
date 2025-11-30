import React from "react";
import { Button, NativeModules, Text, View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";

const { V2RayCore } = NativeModules;

export default function App() {
  return (
    <SafeAreaProvider>
      <SafeAreaView style={{ flex: 1 }}>
        <View style={{ flex: 1, justifyContent: "center", padding: 24 }}>
          <Text style={{ marginBottom: 12 }}>libxray bridge test</Text>

          <Button
            title="Start core (hardcoded)"
            onPress={() => V2RayCore.startHardcoded()}
          />

          <View style={{ height: 12 }} />

          <Button
            title="Stop core"
            onPress={() => V2RayCore.stop()}
          />

          <View style={{ height: 12 }} />

          <Button
            title="Start VPN (skeleton)"
            onPress={async () => {
              const r = await V2RayCore.startVpn();
              console.log("startVpn result:", r);
            }}
          />
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}
