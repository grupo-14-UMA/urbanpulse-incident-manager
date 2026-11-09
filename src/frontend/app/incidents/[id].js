import { Redirect, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getIncident } from '../../src/api';
import { categoryLabel, statusLabel } from '../../src/labels';
import { OSM_MAP_PROPS, OsmAttribution, OsmTiles, STREET_DELTA } from '../../src/map';
import { COLORS } from '../../src/theme';
import { useAuth } from '../../src/AuthContext';
import { useLoad } from '../../src/useLoad';

export default function IncidentDetailScreen() {
  const { status, session } = useAuth();
  const { id } = useLocalSearchParams();

  if (status === 'restoring') {
    return <View style={styles.centered}><ActivityIndicator size="large" color={COLORS.primary} /></View>;
  }
  if (status !== 'signedIn' || session.user.role !== 'CITIZEN') {
    return <Redirect href="/" />;
  }

  return <IncidentDetailContent id={id} />;
}

function IncidentDetailContent({ id }) {
  const insets = useSafeAreaInsets();
  const { status: state, data: incident, error, reload } = useLoad(() => getIncident(id));

  if (state === 'loading') {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" color={COLORS.primary} />
      </View>
    );
  }

  if (state === 'notFound') {
    return (
      <View style={styles.centered}>
        <Text style={styles.message}>Esta incidencia no existe</Text>
      </View>
    );
  }

  if (state === 'error') {
    return (
      <View style={styles.centered}>
        <Text style={styles.message}>{error?.message}</Text>
        <Pressable
          style={({ pressed }) => [styles.retryButton, pressed && styles.retryButtonPressed]}
          onPress={reload}
          accessibilityRole="button"
        >
          <Text style={styles.retryButtonText}>Reintentar</Text>
        </Pressable>
      </View>
    );
  }

  const coordinate = { latitude: incident.latitude, longitude: incident.longitude };

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
      contentInsetAdjustmentBehavior={Platform.OS === 'ios' ? 'automatic' : undefined}
    >
      {error && (
        <View style={styles.notice}>
          <Text style={styles.noticeText}>{error.message}</Text>
          <Pressable onPress={reload} accessibilityRole="button">
            <Text style={styles.retryButtonText}>Reintentar</Text>
          </Pressable>
        </View>
      )}
      <Text style={styles.title}>{incident.title}</Text>

      <Text style={styles.label}>Descripción</Text>
      <Text style={styles.value}>{incident.description}</Text>

      <Text style={styles.label}>Categoría</Text>
      <Text style={styles.value}>{categoryLabel(incident.categoryName)}</Text>

      <Text style={styles.label}>Estado</Text>
      <Text style={styles.value}>{statusLabel(incident.status)}</Text>

      <Text style={styles.label}>Fecha de registro</Text>
      <Text style={styles.value}>{new Date(incident.createdAt).toLocaleString('es-ES')}</Text>

      <Text style={styles.label}>Localización</Text>
      {/* Static, so scrolling the detail never moves the map by accident. */}
      <View style={styles.mapContainer} pointerEvents="none">
        <MapView
          style={StyleSheet.absoluteFill}
          initialRegion={{ ...coordinate, latitudeDelta: STREET_DELTA, longitudeDelta: STREET_DELTA }}
          scrollEnabled={false}
          zoomEnabled={false}
          rotateEnabled={false}
          pitchEnabled={false}
          {...OSM_MAP_PROPS}
        >
          <OsmTiles />
          <Marker coordinate={coordinate} />
        </MapView>
        <OsmAttribution />
      </View>
    </ScrollView>
  );
}


const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: COLORS.surface,
  },
  content: {
    padding: 16,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: COLORS.surface,
  },
  message: {
    fontSize: 16,
    color: COLORS.text,
    textAlign: 'center',
  },
  notice: {
    marginBottom: 12,
    padding: 12,
    borderRadius: 8,
    backgroundColor: COLORS.errorSoft,
  },
  noticeText: {
    color: COLORS.error,
    textAlign: 'center',
  },
  retryButton: {
    marginTop: 16,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: COLORS.primary,
  },
  retryButtonPressed: {
    backgroundColor: COLORS.primaryPressed,
  },
  retryButtonText: {
    color: COLORS.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    color: COLORS.text,
  },
  label: {
    marginTop: 16,
    marginBottom: 4,
    fontSize: 13,
    fontWeight: '600',
    color: COLORS.textMuted,
    textTransform: 'uppercase',
  },
  value: {
    fontSize: 16,
    color: COLORS.text,
  },
  mapContainer: {
    height: 200,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
  },
});
