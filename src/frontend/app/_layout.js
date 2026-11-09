import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Platform } from 'react-native';

import { AuthProvider } from '../src/AuthContext';

// Loaded at startup so a missing EXPO_PUBLIC_API_URL is reported right away.
import '../src/config';

export default function RootLayout() {
  return (
    <AuthProvider>
      <Stack screenOptions={{ headerTransparent: Platform.OS === 'ios' }}>
        <Stack.Screen name="index" options={{ title: 'UrbanPulse' }} />
        <Stack.Screen name="incidents/new" options={{ title: 'Nueva incidencia' }} />
        <Stack.Screen name="incidents/[id]" options={{ title: 'Incidencia' }} />
      </Stack>
      <StatusBar style="dark" />
    </AuthProvider>
  );
}
