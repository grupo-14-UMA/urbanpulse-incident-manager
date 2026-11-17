import { Redirect, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getMyIncidents } from '../../src/api';
import { useAuth } from '../../src/AuthContext';
import { IncidentCard } from '../../src/IncidentCard';
import { COLORS } from '../../src/theme';

// Only the citizen follows their own incidents; every other role lands on the map.
export default function MyIncidentsScreen() {
  const { status, session } = useAuth();

  if (status === 'restoring') {
    return <View style={styles.centered}><ActivityIndicator size="large" color={COLORS.primary} /></View>;
  }
  if (status !== 'signedIn' || session.user.role !== 'CITIZEN') {
    return <Redirect href="/" />;
  }

  return <MyIncidentsList />;
}

function MyIncidentsList() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [incidents, setIncidents] = useState([]);
  const [state, setState] = useState('loading');
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const loadIncidents = useCallback(async ({ nextPage = 0, append = false } = {}) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setState('loading');
    }
    setError(null);
    try {
      const result = await getMyIncidents({ page: nextPage });
      setIncidents((current) => append ? mergePages(current, result.incidents) : result.incidents);
      setPage(result.page);
      setHasMore(result.hasMore);
      setState('ready');
    } catch (loadError) {
      setError(loadError?.message || 'No se pudieron cargar tus incidencias.');
      if (!append) setState('error');
    } finally {
      setLoadingMore(false);
    }
  }, []);

  // Coming back from the detail does not remount the screen, so reload on every focus.
  useFocusEffect(useCallback(() => {
    loadIncidents();
  }, [loadIncidents]));

  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Mis incidencias</Text>
      {state === 'loading' && (
        <View style={styles.centered}><ActivityIndicator color={COLORS.primary} /></View>
      )}
      {state === 'error' && (
        <View style={styles.centered}>
          <Text style={styles.message}>{error}</Text>
          <Pressable onPress={() => loadIncidents()} accessibilityRole="button">
            <Text style={styles.retryText}>Reintentar</Text>
          </Pressable>
        </View>
      )}
      {state === 'ready' && incidents.length === 0 && (
        <View style={styles.centered}>
          <Text style={styles.message}>Todavía no has registrado ninguna incidencia.</Text>
        </View>
      )}
      {state === 'ready' && incidents.length > 0 && (
        <ScrollView contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 24 }]}>
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
              onPress={() => loadIncidents({ nextPage: page + 1, append: true })}
              disabled={loadingMore}
              accessibilityRole="button"
            >
              {loadingMore
                ? <ActivityIndicator color={COLORS.onPrimary} />
                : <Text style={styles.loadMoreButtonText}>Cargar más incidencias</Text>}
            </Pressable>
          )}
        </ScrollView>
      )}
    </View>
  );
}

// Appends the next page without repeating an incident that is already listed.
function mergePages(current, next) {
  const listed = new Set(current.map((incident) => incident.id));
  return [...current, ...next.filter((incident) => !listed.has(incident.id))];
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    padding: 24,
    backgroundColor: COLORS.surface,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    color: COLORS.text,
    fontSize: 24,
    fontWeight: '700',
    textAlign: 'center',
  },
  message: {
    marginTop: 16,
    color: COLORS.text,
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
  },
  retryText: {
    marginTop: 6,
    textAlign: 'center',
    color: COLORS.primary,
    fontWeight: '600',
  },
  list: {
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
  buttonPressed: {
    opacity: 0.75,
  },
});
