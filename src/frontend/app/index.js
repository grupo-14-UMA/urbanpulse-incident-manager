import { Platform, StyleSheet, Text, View } from 'react-native';
import MapView, { UrlTile } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const MALAGA_REGION = {
  latitude: 36.7213,
  longitude: -4.4214,
  latitudeDelta: 0.08,
  longitudeDelta: 0.08,
};

// Served by the German OSM community (FOSSGIS): tile.openstreetmap.org blocks the
// generic User-Agent react-native-maps sends, and Expo Go cannot change it.
const TILE_URL = 'https://tile.openstreetmap.de/{z}/{x}/{y}.png';

export default function MapScreen() {
  const insets = useSafeAreaInsets();

  return (
    <View style={styles.container}>
      <MapView
        style={StyleSheet.absoluteFill}
        initialRegion={MALAGA_REGION}
        mapType={Platform.OS === 'android' ? 'none' : 'standard'}
        toolbarEnabled={false}
      >
        <UrlTile urlTemplate={TILE_URL} maximumZ={19} shouldReplaceMapContent />
      </MapView>
      <View style={[styles.attribution, { bottom: insets.bottom }]} pointerEvents="none">
        <Text style={styles.attributionText}>© OpenStreetMap contributors</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  attribution: {
    position: 'absolute',
    right: 0,
    paddingHorizontal: 6,
    paddingVertical: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.8)',
  },
  attributionText: {
    fontSize: 11,
    color: '#333',
  },
});
