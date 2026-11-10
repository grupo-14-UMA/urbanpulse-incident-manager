import { Redirect, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getIncident, getIncidentPhotoSource } from '../../src/api';
import { categoryLabel, statusLabel } from '../../src/labels';
import { OSM_MAP_PROPS, OsmAttribution, OsmTiles, STREET_DELTA } from '../../src/map';
import { COLORS } from '../../src/theme';
import { useAuth } from '../../src/AuthContext';

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
  // 'loading' | 'loaded' | 'notFound' | 'error'
  const [state, setState] = useState('loading');
  const [incident, setIncident] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [attempt, setAttempt] = useState(0);

  // Loaded by id, not passed through navigation, so it follows GET /api/incidents/{id}
  // and works after a deep link or a reload.
  useEffect(() => {
    let active = true;
    getIncident(id)
      .then((result) => {
        if (active) {
          setIncident(result);
          setState('loaded');
        }
      })
      .catch((error) => {
        if (active) {
          setState(error?.status === 404 ? 'notFound' : 'error');
          setErrorMessage(error?.message || 'Algo ha fallado en UrbanPulse. Inténtalo de nuevo en unos minutos.');
        }
      });
    return () => {
      active = false;
    };
  }, [id, attempt]);

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
        <Text style={styles.message}>{errorMessage}</Text>
        <Pressable
          style={({ pressed }) => [styles.retryButton, pressed && styles.retryButtonPressed]}
          onPress={() => {
            setState('loading');
            setAttempt((n) => n + 1);
          }}
          accessibilityRole="button"
        >
          <Text style={styles.retryButtonText}>Reintentar</Text>
        </Pressable>
      </View>
    );
  }

  const coordinate = { latitude: incident.latitude, longitude: incident.longitude };
  const photos = Array.isArray(incident.photos) ? incident.photos : [];

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
      contentInsetAdjustmentBehavior={Platform.OS === 'ios' ? 'automatic' : undefined}
    >
      <Text style={styles.title}>{incident.title}</Text>

      <Text style={styles.label}>Descripción</Text>
      <Text style={styles.value}>{incident.description}</Text>

      <Text style={styles.label}>Categoría</Text>
      <Text style={styles.value}>{categoryLabel(incident.categoryName)}</Text>

      <Text style={styles.label}>Estado</Text>
      <Text style={styles.value}>{statusLabel(incident.status)}</Text>

      <Text style={styles.label}>Fecha de registro</Text>
      <Text style={styles.value}>{new Date(incident.createdAt).toLocaleString('es-ES')}</Text>

      {photos.length > 0 && (
        <>
          <Text style={styles.label}>Fotos</Text>
          <View style={styles.photoGrid}>
            {photos.map((photo, index) => {
              const source = getIncidentPhotoSource(photo.url);
              if (!source) {
                return null;
              }
              return (
                <Image
                  key={photo.id ?? `${photo.url}-${index}`}
                  source={source}
                  style={styles.photo}
                  resizeMode="cover"
                  accessibilityLabel={`Foto ${index + 1} de la incidencia`}
                />
              );
            })}
          </View>
        </>
      )}

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
  photoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginTop: 4,
  },
  photo: {
    width: '100%',
    height: 200,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surfaceMuted,
  },
  mapContainer: {
    height: 200,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
  },
});
