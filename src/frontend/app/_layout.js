import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

// Loaded at startup so a missing EXPO_PUBLIC_API_URL is reported right away.
import '../src/config';

export default function RootLayout() {
  return (
    <>
      <Stack>
        <Stack.Screen name="index" options={{ title: 'Mapa de incidencias' }} />
      </Stack>
      <StatusBar style="auto" />
    </>
  );
}
