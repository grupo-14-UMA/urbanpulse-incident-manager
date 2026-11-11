import { Redirect, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { Image } from 'expo-image';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getIncident, getIncidentPhotoSource } from '../../src/api';
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
  const photos = Array.isArray(incident.photos) ? incident.photos : [];

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

      {photos.length > 0 && (
        <>
          <Text style={styles.label}>Fotos</Text>
          <View style={styles.photoGrid}>
            {photos.map((photo, index) => {
              return (
                <IncidentPhotoImage
                  key={photo.id ?? `${photo.url}-${index}`}
                  photo={photo}
                  index={index}
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

function IncidentPhotoImage({ photo, index }) {
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const source = getIncidentPhotoSource(photo.url);

  if (!source) {
    return null;
  }

  return (
    <View style={styles.photoFrame}>
      <Image
        key={attempt}
        source={source}
        style={styles.photo}
        contentFit="cover"
        cachePolicy="none"
        accessibilityLabel={`Foto ${index + 1} de la incidencia`}
        onLoad={() => setFailed(false)}
        onError={(error) => {
          const message = typeof error?.error === 'string' ? error.error : '';
          const safeMessage = message.replace(/https?:\/\/\S+/gi, '<photo-url>').slice(0, 160);
          console.warn('[photos] No se pudo cargar una foto de incidencia' + (safeMessage ? ': ' + safeMessage : ''));
          setFailed(true);
        }}
      />
      {failed && (
        <View style={styles.photoError}>
          <Text style={styles.photoErrorText}>No se pudo cargar esta foto.</Text>
          <Pressable
            onPress={() => {
              setFailed(false);
              setAttempt((current) => current + 1);
            }}
            accessibilityRole="button"
          >
            <Text style={styles.photoRetryText}>Reintentar</Text>
          </Pressable>
        </View>
      )}
    </View>
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
  photoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginTop: 4,
  },
  photoFrame: {
    width: '100%',
    height: 200,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surfaceMuted,
  },
  photo: {
    ...StyleSheet.absoluteFillObject,
  },
  photoError: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 12,
    backgroundColor: COLORS.surfaceMuted,
  },
  photoErrorText: {
    color: COLORS.textMuted,
    fontSize: 14,
    textAlign: 'center',
  },
  photoRetryText: {
    marginTop: 8,
    color: COLORS.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  mapContainer: {
    height: 200,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
  },
});
