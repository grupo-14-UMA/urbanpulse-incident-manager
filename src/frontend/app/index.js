import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getAssignedIncidents, getIncidentsWithinBounds } from '../src/api';
import { IncidentCard } from '../src/IncidentCard';
import { MALAGA_REGION, OSM_MAP_PROPS, OsmAttribution, OsmTiles } from '../src/map';
import { COLORS } from '../src/theme';
import { AuthenticationScreen } from '../src/AuthenticationScreen';
import { useAuth } from '../src/AuthContext';

// Distance from the bottom safe area to the "+ Nueva incidencia" button.
const BUTTON_BOTTOM = 32;
// The card sits above the button so the button is always visible and usable.
const CARD_BOTTOM = BUTTON_BOTTOM + 64;

export default function HomeScreen() {
  const { status, session, notice } = useAuth();

  if (status === 'restoring') {
    return (
      <View style={styles.centered}>
        <Text style={styles.loadingText}>Recuperando tu sesión…</Text>
      </View>
    );
  }

  if (status === 'signedOut') {
    return <AuthenticationScreen initialNotice={notice} />;
  }

  if (session.user.role === 'TECHNICIAN') {
    return <TechnicianHome />;
  }

  if (session.user.role === 'OPERATOR') {
    return <OperatorHome />;
  }

  return <CitizenMapScreen />;
}

function CitizenMapScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { signOut } = useAuth();
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
      <Pressable
        style={[styles.signOutButton, { top: insets.top + 12 }]}
        onPress={signOut}
        accessibilityRole="button"
      >
        <Text style={styles.signOutButtonText}>Cerrar sesión</Text>
      </Pressable>
      {notice && (
        <View
          style={[
            styles.notice,
            { top: insets.top + 58 },
            loadError ? styles.noticeError : styles.noticeInfo,
          ]}
          accessibilityLiveRegion="polite"
        >
          <Text style={[styles.noticeText, loadError ? styles.noticeErrorText : styles.noticeInfoText]}>
            {notice}
          </Text>
          {loadError && (
            <Pressable onPress={loadVisibleIncidents} accessibilityRole="button">
              <Text style={styles.retryText}>Reintentar</Text>
            </Pressable>
          )}
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

function TechnicianHome() {
  const router = useRouter();
  const { signOut } = useAuth();
  const [incidents, setIncidents] = useState([]);
  const [state, setState] = useState('loading');
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const loadAssignedIncidents = useCallback(async ({ nextPage = 0, append = false } = {}) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setState('loading');
    }
    setError(null);
    try {
      const result = await getAssignedIncidents({ page: nextPage });
      setIncidents((current) => append ? [...current, ...result.incidents] : result.incidents);
      setPage(result.page);
      setHasMore(result.hasMore);
      setState('ready');
    } catch (loadError) {
      setError(loadError?.message || 'No se pudieron cargar tus trabajos.');
      if (!append) setState('error');
    } finally {
      setLoadingMore(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    loadAssignedIncidents();
  }, [loadAssignedIncidents]));

  return (
    <View style={styles.roleScreen}>
      <Pressable style={styles.roleSignOutButton} onPress={signOut} accessibilityRole="button">
        <Text style={styles.roleSignOutButtonText}>Cerrar sesión</Text>
      </Pressable>
      <Text style={styles.roleTitle}>Mis trabajos</Text>
      {state === 'loading' && <ActivityIndicator color={COLORS.primary} />}
      {state === 'error' && (
        <View style={styles.roleNotice}>
          <Text style={styles.roleMessage}>{error}</Text>
          <Pressable onPress={loadAssignedIncidents} accessibilityRole="button">
            <Text style={styles.retryText}>Reintentar</Text>
          </Pressable>
        </View>
      )}
      {state === 'ready' && incidents.length === 0 && (
        <Text style={styles.roleMessage}>Todavía no tienes trabajos asignados.</Text>
      )}
      {state === 'ready' && incidents.length > 0 && (
        <ScrollView contentContainerStyle={styles.assignedList}>
          {incidents.map((incident) => (
            <IncidentCard
              key={incident.id}
              incident={incident}
              onOpen={() => router.push(`/incidents/${incident.id}`)}
              onClose={() => {}}
            />
          ))}
          {hasMore && (
            <Pressable
              style={({ pressed }) => [styles.loadMoreButton, pressed && styles.buttonPressed]}
              onPress={() => loadAssignedIncidents({ nextPage: page + 1, append: true })}
              disabled={loadingMore}
              accessibilityRole="button"
            >
              {loadingMore
                ? <ActivityIndicator color={COLORS.onPrimary} />
                : <Text style={styles.loadMoreButtonText}>Cargar más trabajos</Text>}
            </Pressable>
          )}
        </ScrollView>
      )}
    </View>
  );
}

function OperatorHome() {
  return (
    <RoleScreen
      title="Panel de gestión"
      message="Usa el panel de gestión desde un ordenador para gestionar las incidencias."
    />
  );
}

function RoleScreen({ title, message }) {
  const { signOut } = useAuth();
  return (
    <View style={styles.roleScreen}>
      <Text style={styles.roleTitle}>{title}</Text>
      <Text style={styles.roleMessage}>{message}</Text>
      <Pressable style={styles.roleSignOutButton} onPress={signOut} accessibilityRole="button">
        <Text style={styles.roleSignOutButtonText}>Cerrar sesión</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.surface,
  },
  loadingText: {
    color: COLORS.text,
    fontSize: 16,
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
  retryText: {
    marginTop: 6,
    textAlign: 'center',
    color: COLORS.primary,
    fontWeight: '600',
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
  signOutButton: {
    position: 'absolute',
    right: 16,
    borderRadius: 18,
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: COLORS.surface,
    elevation: 3,
  },
  signOutButtonText: {
    color: COLORS.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  roleScreen: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
    backgroundColor: COLORS.surface,
  },
  roleTitle: {
    color: COLORS.text,
    fontSize: 24,
    fontWeight: '700',
    textAlign: 'center',
  },
  roleMessage: {
    marginTop: 16,
    color: COLORS.text,
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
  },
  roleNotice: {
    alignItems: 'center',
    gap: 12,
  },
  assignedList: {
    width: '100%',
    gap: 12,
    paddingVertical: 24,
  },
  loadMoreButton: {
    alignItems: 'center',
    borderRadius: 8,
    paddingVertical: 12,
    backgroundColor: COLORS.primary,
  },
  loadMoreButtonText: {
    color: COLORS.onPrimary,
    fontWeight: '600',
  },
  roleSignOutButton: {
    alignSelf: 'center',
    marginTop: 28,
    borderRadius: 8,
    paddingHorizontal: 20,
    paddingVertical: 12,
    backgroundColor: COLORS.primary,
  },
  roleSignOutButtonText: {
    color: COLORS.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
});
