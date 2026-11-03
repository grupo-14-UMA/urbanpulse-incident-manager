import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import MapView, { Callout, Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getIncidents } from '../src/api';
import { MALAGA_REGION, OSM_MAP_PROPS, OsmAttribution, OsmTiles } from '../src/map';
import { statusLabel } from '../src/status';

export default function MapScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [incidents, setIncidents] = useState([]);
  const [loadFailed, setLoadFailed] = useState(false);
  // Bumped on every load and on blur, so only the latest load while focused applies.
  const loadIdRef = useRef(0);

  // Going back from a pushed screen does not remount the map, so load on every focus.
  useFocusEffect(
    useCallback(() => {
      const loadId = ++loadIdRef.current;
      getIncidents()
        .then((result) => {
          if (loadId !== loadIdRef.current) {
            return;
          }
          setIncidents(result);
          setLoadFailed(false);
        })
        .catch(() => {
          // Keep the markers already shown; they are only possibly out of date.
          if (loadId === loadIdRef.current) {
            setLoadFailed(true);
          }
        });
      return () => {
        loadIdRef.current++;
      };
    }, []),
  );

  return (
    <View style={styles.container}>
      <MapView style={StyleSheet.absoluteFill} initialRegion={MALAGA_REGION} {...OSM_MAP_PROPS}>
        <OsmTiles />
        {incidents.map((incident) => (
          <Marker
            key={incident.id}
            coordinate={{ latitude: incident.latitude, longitude: incident.longitude }}
            onCalloutPress={() => router.push(`/incidents/${incident.id}`)}
          >
            {/* Android draws the callout as a static image: no buttons inside, the whole callout is the tap target. */}
            <Callout>
              <View style={styles.callout}>
                <Text style={styles.calloutTitle} numberOfLines={2}>
                  {incident.title}
                </Text>
                <Text style={styles.calloutText}>{incident.categoryName ?? 'Sin categoría'}</Text>
                <Text style={styles.calloutText}>{statusLabel(incident.status)}</Text>
                <Text style={styles.calloutLink}>Ver detalles ›</Text>
              </View>
            </Callout>
          </Marker>
        ))}
      </MapView>
      <OsmAttribution style={{ bottom: insets.bottom }} />
      {loadFailed && (
        <View style={styles.notice} pointerEvents="none" accessibilityLiveRegion="polite">
          <Text style={styles.noticeText}>No se pudieron cargar las incidencias</Text>
        </View>
      )}
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

const PRIMARY_COLOR = '#1565c0';

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  callout: {
    width: 200,
    padding: 4,
  },
  calloutTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#222',
  },
  calloutText: {
    marginTop: 2,
    fontSize: 13,
    color: '#555',
  },
  calloutLink: {
    marginTop: 6,
    fontSize: 13,
    fontWeight: '600',
    color: PRIMARY_COLOR,
  },
  notice: {
    position: 'absolute',
    top: 12,
    left: 16,
    right: 16,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#fdecea',
    elevation: 2,
  },
  noticeText: {
    textAlign: 'center',
    color: '#c62828',
    fontSize: 14,
  },
  newIncidentButton: {
    position: 'absolute',
    alignSelf: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 28,
    backgroundColor: PRIMARY_COLOR,
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
