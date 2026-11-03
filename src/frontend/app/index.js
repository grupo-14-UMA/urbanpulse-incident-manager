import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import MapView from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MALAGA_REGION, OSM_MAP_PROPS, OsmAttribution, OsmTiles } from '../src/map';

export default function MapScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();

  return (
    <View style={styles.container}>
      <MapView style={StyleSheet.absoluteFill} initialRegion={MALAGA_REGION} {...OSM_MAP_PROPS}>
        <OsmTiles />
      </MapView>
      <OsmAttribution style={{ bottom: insets.bottom }} />
      <Pressable
        style={({ pressed }) => [
          styles.newIncidentButton,
          { bottom: insets.bottom + 32 },
          pressed && styles.newIncidentButtonPressed,
        ]}
        onPress={() => router.push('/incidents/new')}
        accessibilityRole="button"
      >
        <Text style={styles.newIncidentButtonText}>+ Nueva incidencia</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  newIncidentButton: {
    position: 'absolute',
    alignSelf: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 28,
    backgroundColor: '#1565c0',
    elevation: 4,
    shadowColor: '#000',
    shadowOpacity: 0.25,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
  },
  newIncidentButtonPressed: {
    backgroundColor: '#0d47a1',
  },
  newIncidentButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
