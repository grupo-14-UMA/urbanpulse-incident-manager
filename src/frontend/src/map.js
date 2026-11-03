import { Platform, StyleSheet, Text, View } from 'react-native';
import { UrlTile } from 'react-native-maps';

// Shared by every map in the app so they all show the same OSM tiles (ADR-0004,
// ADR-0005) and the attribution the tile licence requires.

export const MALAGA_REGION = {
  latitude: 36.7213,
  longitude: -4.4214,
  latitudeDelta: 0.08,
  longitudeDelta: 0.08,
};

// Span used to show a single Localización at street level.
export const STREET_DELTA = 0.005;

// Served by the German OSM community (FOSSGIS): tile.openstreetmap.org blocks the
// generic User-Agent react-native-maps sends, and Expo Go cannot change it.
const TILE_URL = 'https://tile.openstreetmap.de/{z}/{x}/{y}.png';

// Props for MapView so the OSM layer replaces the platform's base map.
export const OSM_MAP_PROPS = {
  mapType: Platform.OS === 'android' ? 'none' : 'standard',
  toolbarEnabled: false,
};

export function OsmTiles() {
  return <UrlTile urlTemplate={TILE_URL} maximumZ={19} shouldReplaceMapContent />;
}

export function OsmAttribution({ style }) {
  return (
    <View style={[styles.attribution, style]} pointerEvents="none">
      <Text style={styles.attributionText}>© OpenStreetMap contributors</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  attribution: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    paddingHorizontal: 6,
    paddingVertical: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.8)',
  },
  attributionText: {
    fontSize: 11,
    color: '#333',
  },
});
