import { Redirect, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Image as NativeImage,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Image } from 'expo-image';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  downloadIncidentPhoto,
  getIncident,
  resolveIncident,
  startIncident,
  uploadIncidentPhoto,
} from '../../src/api';
import {
  MAX_INCIDENT_PHOTOS,
  pickPhotoFromCamera,
  pickPhotoFromGallery,
} from '../../src/incidentPhotos';
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
  if (status !== 'signedIn' || !['CITIZEN', 'TECHNICIAN'].includes(session.user.role)) {
    return <Redirect href="/" />;
  }

  return <IncidentDetailContent id={id} session={session} />;
}

function IncidentDetailContent({ id, session }) {
  const insets = useSafeAreaInsets();
  const { status: state, data: incident, error, reload } = useLoad(() => getIncident(id));
  const [pendingPhoto, setPendingPhoto] = useState(null);
  const [photoMessage, setPhotoMessage] = useState(null);
  const [photoUploading, setPhotoUploading] = useState(false);
  const [resolutionNote, setResolutionNote] = useState('');
  const [actionError, setActionError] = useState(null);
  const [actionBusy, setActionBusy] = useState(false);

  async function choosePhoto(source) {
    if (photoUploading) return;
    setPhotoMessage(null);
    try {
      const outcome = source === 'camera' ? await pickPhotoFromCamera() : await pickPhotoFromGallery();
      if (outcome.status === 'denied') {
        setPhotoMessage(outcome.message);
      } else if (outcome.status === 'picked') {
        setPendingPhoto(outcome.photo);
      }
    } catch (photoError) {
      setPhotoMessage(photoError?.message || 'No se pudo preparar la foto seleccionada.');
    }
  }

  async function uploadPhoto() {
    if (!pendingPhoto || photoUploading) return;
    setPhotoUploading(true);
    setPhotoMessage(null);
    try {
      await uploadIncidentPhoto(id, pendingPhoto);
      setPendingPhoto(null);
      setPhotoMessage('La foto se ha añadido a la incidencia.');
      reload();
    } catch (photoError) {
      setPhotoMessage(photoError?.message || 'No se pudo subir la foto. Inténtalo de nuevo.');
    } finally {
      setPhotoUploading(false);
    }
  }

  async function startWork() {
    if (actionBusy) return;
    setActionBusy(true);
    setActionError(null);
    try {
      await startIncident(id);
      await reload();
    } catch (actionFailure) {
      setActionError(actionFailure?.message || 'No se pudo empezar el trabajo.');
    } finally {
      setActionBusy(false);
    }
  }

  async function resolveWork() {
    const note = resolutionNote.trim();
    if (!note || actionBusy) {
      setActionError('Escribe una nota de resolución.');
      return;
    }
    setActionBusy(true);
    setActionError(null);
    try {
      await resolveIncident(id, note);
      await reload();
    } catch (actionFailure) {
      setActionError(actionFailure?.message || 'No se pudo resolver el trabajo.');
    } finally {
      setActionBusy(false);
    }
  }

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
  const canWork = session.user.role === 'TECHNICIAN' && incident.assignee?.id === session.user.id;

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

      {canWork && incident.status === 'ASSIGNED' && (
        <View style={styles.workActions}>
          <Pressable
            style={({ pressed }) => [styles.primaryButton, pressed && styles.buttonPressed]}
            onPress={startWork}
            disabled={actionBusy}
            accessibilityRole="button"
          >
            {actionBusy ? <ActivityIndicator color={COLORS.onPrimary} /> : <Text style={styles.primaryButtonText}>Empezar trabajo</Text>}
          </Pressable>
        </View>
      )}

      {canWork && incident.status === 'IN_PROGRESS' && (
        <View style={styles.workActions}>
          <Text style={styles.label}>Nota de resolución</Text>
          <TextInput
            style={styles.resolutionInput}
            value={resolutionNote}
            onChangeText={setResolutionNote}
            placeholder="Describe el trabajo realizado"
            multiline
            maxLength={1000}
            editable={!actionBusy}
          />
          <Pressable
            style={({ pressed }) => [styles.primaryButton, pressed && styles.buttonPressed]}
            onPress={resolveWork}
            disabled={actionBusy}
            accessibilityRole="button"
          >
            {actionBusy ? <ActivityIndicator color={COLORS.onPrimary} /> : <Text style={styles.primaryButtonText}>Resolver trabajo</Text>}
          </Pressable>
        </View>
      )}
      {actionError && <Text style={styles.actionError}>{actionError}</Text>}

      <Text style={styles.label}>Fecha de registro</Text>
      <Text style={styles.value}>{new Date(incident.createdAt).toLocaleString('es-ES')}</Text>

      <Text style={styles.label}>Fotos ({photos.length}/{MAX_INCIDENT_PHOTOS})</Text>
      {photos.length > 0 ? (
        <View style={styles.photoGrid}>
          {photos.map((photo, index) => (
            <IncidentPhotoImage
              key={photo.id ?? `${photo.url}-${index}`}
              photo={photo}
              index={index}
            />
          ))}
        </View>
      ) : (
        <Text style={styles.value}>Esta incidencia todavía no tiene fotos.</Text>
      )}

      {photos.length < MAX_INCIDENT_PHOTOS && (
        <View style={styles.addPhotoSection}>
          <Text style={styles.value}>Añade una foto para completar la incidencia.</Text>
          <View style={styles.photoActions}>
            <Pressable
              style={({ pressed }) => [styles.secondaryButton, pressed && styles.buttonPressed]}
              onPress={() => choosePhoto('camera')}
              disabled={photoUploading}
              accessibilityRole="button"
            >
              <Text style={styles.secondaryButtonText}>Tomar foto</Text>
            </Pressable>
            <Pressable
              style={({ pressed }) => [styles.secondaryButton, pressed && styles.buttonPressed]}
              onPress={() => choosePhoto('gallery')}
              disabled={photoUploading}
              accessibilityRole="button"
            >
              <Text style={styles.secondaryButtonText}>Elegir de galería</Text>
            </Pressable>
          </View>
          {pendingPhoto && (
            <View style={styles.pendingPhoto}>
              <Image source={{ uri: pendingPhoto.uri }} style={styles.pendingPhotoImage} contentFit="cover" />
              <Pressable
                onPress={() => {
                  setPendingPhoto(null);
                  setPhotoMessage(null);
                }}
                disabled={photoUploading}
                accessibilityRole="button"
              >
                <Text style={styles.removePhotoText}>Quitar foto seleccionada</Text>
              </Pressable>
              <Pressable
                style={({ pressed }) => [styles.uploadButton, pressed && styles.uploadButtonPressed]}
                onPress={uploadPhoto}
                disabled={photoUploading}
                accessibilityRole="button"
              >
                {photoUploading
                  ? <ActivityIndicator color={COLORS.onPrimary} />
                  : <Text style={styles.uploadButtonText}>Subir foto</Text>}
              </Pressable>
            </View>
          )}
          {photoMessage && (
            <View style={photoMessage.startsWith('La foto se ha añadido') ? styles.photoSuccess : styles.photoFailure}>
              <Text
                style={
                  photoMessage.startsWith('La foto se ha añadido')
                    ? styles.photoSuccessText
                    : styles.photoFailureText
                }
              >
                {photoMessage}
              </Text>
            </View>
          )}
        </View>
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
  const photoUrl = typeof photo.url === 'string' ? photo.url : '';
  const [photoState, setPhotoState] = useState({ status: 'loading', uri: null });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    let downloadedFile = null;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);

    console.info(`[photos] Iniciando descarga autenticada de la foto ${index + 1}.`);
    downloadIncidentPhoto(photoUrl, { signal: controller.signal })
      .then((file) => {
        downloadedFile = file;
        if (active) {
          setPhotoState({ status: 'ready', uri: file.uri });
        } else {
          removeTemporaryPhoto(file);
        }
      })
      .catch((error) => {
        if (active) {
          const message = typeof error?.message === 'string'
            ? error.message.replace(/https?:\/\/\S+/gi, '<photo-url>').slice(0, 160)
            : '';
          console.warn('[photos] No se pudo preparar una foto para mostrarla' + (message ? ': ' + message : ''));
          setPhotoState({ status: 'error', uri: null });
        }
      });

    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
      if (downloadedFile) {
        removeTemporaryPhoto(downloadedFile);
      }
    };
  }, [photoUrl, attempt, index]);

  return (
    <View style={styles.photoFrame}>
      {photoState.uri && (
        <NativeImage
          key={photoState.uri}
          source={{ uri: photoState.uri }}
          style={styles.photoImage}
          resizeMode="cover"
          accessibilityLabel={`Foto ${index + 1} de la incidencia`}
          onLoad={() => {
            console.info(`[photos] Foto ${index + 1} mostrada en el detalle.`);
            setPhotoState((current) => ({ ...current, status: 'displayed' }));
          }}
          onError={(event) => {
            const message = typeof event?.nativeEvent?.error === 'string'
              ? event.nativeEvent.error.replace(/https?:\/\/\S+/gi, '<photo-url>').slice(0, 160)
              : '';
            console.warn('[photos] No se pudo mostrar una foto de incidencia' + (message ? ': ' + message : ''));
            setPhotoState({ status: 'error', uri: null });
          }}
        />
      )}
      {photoState.status === 'loading' && (
        <View style={styles.photoLoading}>
          <ActivityIndicator color={COLORS.primary} />
          <Text style={styles.photoErrorText}>Cargando foto…</Text>
        </View>
      )}
      {photoState.status === 'error' && (
        <View style={styles.photoError}>
          <Text style={styles.photoErrorText}>No se pudo cargar esta foto.</Text>
          <Pressable
            onPress={() => {
              setPhotoState({ status: 'loading', uri: null });
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

function removeTemporaryPhoto(file) {
  try {
    file.delete();
  } catch {
    // The cache may have been cleared by the operating system already.
  }
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
  workActions: {
    marginTop: 8,
    gap: 10,
  },
  primaryButton: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    borderRadius: 8,
    backgroundColor: COLORS.primary,
  },
  primaryButtonText: {
    color: COLORS.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  resolutionInput: {
    minHeight: 100,
    padding: 12,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 8,
    color: COLORS.text,
    backgroundColor: COLORS.surface,
    textAlignVertical: 'top',
  },
  actionError: {
    marginTop: 10,
    color: COLORS.error,
    fontSize: 14,
  },
  photoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginTop: 4,
  },
  addPhotoSection: {
    marginTop: 10,
    gap: 10,
  },
  photoActions: {
    flexDirection: 'row',
    gap: 10,
  },
  secondaryButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: COLORS.primary,
    backgroundColor: COLORS.surface,
  },
  secondaryButtonText: {
    color: COLORS.primary,
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  buttonPressed: {
    opacity: 0.75,
  },
  pendingPhoto: {
    gap: 8,
  },
  pendingPhotoImage: {
    width: 132,
    height: 132,
    borderRadius: 8,
    backgroundColor: COLORS.surfaceMuted,
  },
  removePhotoText: {
    color: COLORS.error,
    fontSize: 14,
    fontWeight: '600',
  },
  uploadButton: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    borderRadius: 8,
    backgroundColor: COLORS.primary,
  },
  uploadButtonPressed: {
    backgroundColor: COLORS.primaryPressed,
  },
  uploadButtonText: {
    color: COLORS.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  photoSuccess: {
    padding: 10,
    borderRadius: 8,
    backgroundColor: COLORS.successSoft,
  },
  photoSuccessText: {
    color: COLORS.success,
    fontSize: 14,
  },
  photoFailure: {
    padding: 10,
    borderRadius: 8,
    backgroundColor: COLORS.errorSoft,
  },
  photoFailureText: {
    color: COLORS.error,
    fontSize: 14,
  },
  photoFrame: {
    width: '100%',
    height: 200,
    position: 'relative',
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surfaceMuted,
  },
  photoImage: {
    width: '100%',
    height: '100%',
    flex: 1,
    zIndex: 1,
    backgroundColor: 'transparent',
  },
  photoError: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 12,
    backgroundColor: COLORS.surfaceMuted,
  },
  photoLoading: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
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
