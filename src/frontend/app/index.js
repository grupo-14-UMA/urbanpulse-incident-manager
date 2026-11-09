import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getIncidentsWithinBounds } from '../src/api';
import { IncidentCard } from '../src/IncidentCard';
import { MALAGA_REGION, OSM_MAP_PROPS, OsmAttribution, OsmTiles } from '../src/map';
import { COLORS } from '../src/theme';

// Distance from the bottom safe area to the "+ Nueva incidencia" button.
const BUTTON_BOTTOM = 32;
// The card sits above the button so the button is always visible and usable.
const CARD_BOTTOM = BUTTON_BOTTOM + 64;

export default function MapScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [incidents, setIncidents] = useState([]);
  const [mapTruncated, setMapTruncated] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const mapRef = useRef(null);
  const mapReadyRef = useRef(false);
  const focusedRef = useRef(false);
  const regionTimeoutRef = useRef(null);
  const requestControllerRef = useRef(null);
  // Bumped when the viewport changes and on blur, so stale responses are ignored.
  const loadIdRef = useRef(0);

  const loadVisibleIncidents = useCallback(async () => {
    const map = mapRef.current;
    if (!map || !mapReadyRef.current || !focusedRef.current) {
      return;
    }

    requestControllerRef.current?.abort();
    const controller = new AbortController();
    requestControllerRef.current = controller;
    const loadId = ++loadIdRef.current;

    try {
      const { northEast, southWest } = await map.getMapBoundaries();
      if (loadId !== loadIdRef.current || controller.signal.aborted) {
        return;
      }

      const result = await getIncidentsWithinBounds(
        {
          west: southWest.longitude,
          south: southWest.latitude,
          east: northEast.longitude,
          north: northEast.latitude,
        },
        { signal: controller.signal },
      );
      if (loadId === loadIdRef.current && !controller.signal.aborted) {
        setIncidents(result.incidents);
        setMapTruncated(result.truncated);
        setLoadError(null);
      }
    } catch (error) {
      if (loadId === loadIdRef.current && !controller.signal.aborted) {
        setLoadError(error?.message || 'No se pudieron cargar las incidencias.');
      }
    }
  }, []);

  const scheduleVisibleLoad = useCallback(() => {
    if (!focusedRef.current || !mapReadyRef.current) {
      return;
    }
    clearTimeout(regionTimeoutRef.current);
    requestControllerRef.current?.abort();
    loadIdRef.current++;
    regionTimeoutRef.current = setTimeout(loadVisibleIncidents, 250);
  }, [loadVisibleIncidents]);

  const handleMapReady = useCallback(() => {
    mapReadyRef.current = true;
    if (focusedRef.current) {
      scheduleVisibleLoad();
    }
  }, [scheduleVisibleLoad]);

  // Going back from a pushed screen does not remount the map, so load on every focus.
  useFocusEffect(
    useCallback(() => {
      focusedRef.current = true;
      if (mapReadyRef.current) {
        loadVisibleIncidents();
      }
      return () => {
        focusedRef.current = false;
        loadIdRef.current++;
        requestControllerRef.current?.abort();
        clearTimeout(regionTimeoutRef.current);
      };
    }, [loadVisibleIncidents]),
  );

  // Derived from the latest load, so the card shows current data and disappears
  // if the incident is no longer there.
  const selectedIncident = incidents.find((incident) => incident.id === selectedId) ?? null;
  const notice = loadError || (mapTruncated ? 'Acerca el mapa para ver más incidencias de esta zona.' : null);

  return (
    <View style={styles.container}>
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={MALAGA_REGION}
        onMapReady={handleMapReady}
        onRegionChangeComplete={scheduleVisibleLoad}
        onPress={() => setSelectedId(null)}
        {...OSM_MAP_PROPS}
      >
        <OsmTiles />
        {incidents.map((incident) => (
          <Marker
            key={incident.id}
            coordinate={{ latitude: incident.latitude, longitude: incident.longitude }}
            onPress={() => setSelectedId(incident.id)}
            // On iOS a marker tap also reaches the map's onPress, which would close the card.
            stopPropagation
          />
        ))}
      </MapView>
      <OsmAttribution style={{ bottom: insets.bottom }} />
      {notice && (
        <View
          style={[styles.notice, loadError ? styles.noticeError : styles.noticeInfo]}
          pointerEvents="none"
          accessibilityLiveRegion="polite"
        >
          <Text style={[styles.noticeText, loadError ? styles.noticeErrorText : styles.noticeInfoText]}>
            {notice}
          </Text>
        </View>
      )}
      {selectedIncident && (
        <IncidentCard
          incident={selectedIncident}
          onOpen={() => router.push(`/incidents/${selectedIncident.id}`)}
          onClose={() => setSelectedId(null)}
          style={[styles.card, { bottom: insets.bottom + CARD_BOTTOM }]}
        />
      )}
      <Pressable
        style={({ pressed }) => [
          styles.newIncidentButton,
          { bottom: insets.bottom + BUTTON_BOTTOM },
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
  notice: {
    position: 'absolute',
    top: 12,
    left: 16,
    right: 16,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    elevation: 2,
  },
  noticeText: {
    textAlign: 'center',
    fontSize: 14,
  },
  noticeError: {
    backgroundColor: COLORS.errorSoft,
  },
  noticeErrorText: {
    color: COLORS.error,
  },
  noticeInfo: {
    backgroundColor: '#eef7ff',
  },
  noticeInfoText: {
    color: '#24547a',
  },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
  },
  newIncidentButton: {
    position: 'absolute',
    alignSelf: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 28,
    backgroundColor: COLORS.primary,
    elevation: 4,
    shadowColor: '#000',
    shadowOpacity: 0.25,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
  },
  newIncidentButtonPressed: {
    backgroundColor: COLORS.primaryPressed,
  },
  newIncidentButtonText: {
    color: COLORS.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
});
