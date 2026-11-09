import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getIncidents } from '../src/api';
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
  const [loadError, setLoadError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
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
          setLoadError(null);
        })
        .catch((error) => {
          // Keep the markers already shown; they are only possibly out of date.
          if (loadId === loadIdRef.current) {
            setLoadError(error?.message || 'No se pudieron cargar las incidencias.');
          }
        });
      return () => {
        loadIdRef.current++;
      };
    }, []),
  );

  // Derived from the latest load, so the card shows current data and disappears
  // if the incident is no longer there.
  const selectedIncident = incidents.find((incident) => incident.id === selectedId) ?? null;

  return (
    <View style={styles.container}>
      <MapView
        style={StyleSheet.absoluteFill}
        initialRegion={MALAGA_REGION}
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
      {loadError && (
        <View style={styles.notice} pointerEvents="none" accessibilityLiveRegion="polite">
          <Text style={styles.noticeText}>{loadError}</Text>
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
  return <RoleScreen title="Mis trabajos" message="Todavía no tienes trabajos asignados." />;
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
    backgroundColor: COLORS.errorSoft,
    elevation: 2,
  },
  noticeText: {
    textAlign: 'center',
    color: COLORS.error,
    fontSize: 14,
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
