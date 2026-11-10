import * as Location from 'expo-location';
import { Redirect, useNavigation, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Pressable,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { createIncident, getCategories, uploadIncidentPhoto } from '../../src/api';
import {
  MAX_INCIDENT_PHOTOS,
  addPickedPhoto,
  pickPhotoFromCamera,
  pickPhotoFromGallery,
  removePhotoAt,
  submitIncidentWithPhotos,
} from '../../src/incidentPhotos';
import {
  MALAGA_REGION,
  OSM_MAP_PROPS,
  OsmAttribution,
  OsmTiles,
  STREET_DELTA,
} from '../../src/map';
import { COLORS } from '../../src/theme';
import { useAuth } from '../../src/AuthContext';
import { isValid, validateIncident } from '../../src/validateIncident';

// Some phones never return a fix indoors; stop waiting and let the citizen tap.
const POSITION_TIMEOUT_MS = 10000;

async function getCurrentCoordinate() {
  const { status } = await Location.requestForegroundPermissionsAsync();
  if (status !== 'granted') {
    return null;
  }
  let timer;
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => resolve(null), POSITION_TIMEOUT_MS);
  });
  const position = await Promise.race([
    Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
    timeout,
  ]).finally(() => clearTimeout(timer));
  if (!position) {
    return null;
  }
  return { latitude: position.coords.latitude, longitude: position.coords.longitude };
}

export default function NewIncidentScreen() {
  const { status, session } = useAuth();

  if (status === 'restoring') {
    return <View style={styles.centered}><ActivityIndicator size="large" color={COLORS.primary} /></View>;
  }
  if (status !== 'signedIn' || session.user.role !== 'CITIZEN') {
    return <Redirect href="/" />;
  }

  return <NewIncidentForm />;
}

function NewIncidentForm() {
  const router = useRouter();
  const navigation = useNavigation();
  const insets = useSafeAreaInsets();
  const mapRef = useRef(null);
  const sendingRef = useRef(false);
  const mapReadyRef = useRef(false);
  const pendingRegionRef = useRef(null);
  // Set once the citizen taps or drags, so a late GPS fix never overrides them.
  const placedByHandRef = useRef(false);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState(null);
  const [location, setLocation] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [photoMessage, setPhotoMessage] = useState(null);
  const [createdIncident, setCreatedIncident] = useState(null);

  const [categories, setCategories] = useState([]);
  // 'loading' | 'loaded' | 'error'
  const [categoriesState, setCategoriesState] = useState('loading');
  const [categoriesError, setCategoriesError] = useState(null);
  const [categoriesAttempt, setCategoriesAttempt] = useState(0);
  const [locating, setLocating] = useState(true);
  const [scrollEnabled, setScrollEnabled] = useState(true);
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [sending, setSending] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    let active = true;
    getCategories()
      .then((result) => {
        if (active) {
          setCategories(result);
          setCategoriesState('loaded');
        }
      })
      .catch((error) => {
        if (active) {
          setCategoriesError(error?.message || 'No se pudieron cargar las categorías.');
          setCategoriesState('error');
        }
      });
    return () => {
      active = false;
    };
  }, [categoriesAttempt]);

  useEffect(() => {
    let active = true;
    getCurrentCoordinate()
      .catch(() => null)
      .then((coordinate) => {
        if (!active) {
          return;
        }
        setLocating(false);
        if (!coordinate || placedByHandRef.current) {
          return;
        }
        setLocation(coordinate);
        const region = { ...coordinate, latitudeDelta: STREET_DELTA, longitudeDelta: STREET_DELTA };
        // Before the map is ready animateToRegion is ignored, so leave it for onMapReady.
        if (mapReadyRef.current) {
          mapRef.current?.animateToRegion(region);
        } else {
          pendingRegionRef.current = region;
        }
      });
    return () => {
      active = false;
    };
  }, []);

  // Leaving while the incident is being sent would lose the result; wait for it.
  useEffect(
    () =>
      navigation.addListener('beforeRemove', (event) => {
        if (sendingRef.current) {
          event.preventDefault();
        }
      }),
    [navigation],
  );

  function handleMapReady() {
    mapReadyRef.current = true;
    if (pendingRegionRef.current) {
      mapRef.current?.animateToRegion(pendingRegionRef.current);
      pendingRegionRef.current = null;
    }
  }

  const incidentLocked = sending || createdIncident !== null;

  function placeByHand(coordinate) {
    if (incidentLocked) {
      return;
    }
    placedByHandRef.current = true;
    setLocation(coordinate);
  }

  async function handlePickPhoto(source) {
    if (sending || photos.length >= MAX_INCIDENT_PHOTOS) {
      return;
    }
    setPhotoMessage(null);
    try {
      const outcome =
        source === 'camera' ? await pickPhotoFromCamera() : await pickPhotoFromGallery();
      if (outcome.status === 'denied') {
        setPhotoMessage(outcome.message);
        return;
      }
      if (outcome.status === 'picked') {
        setPhotos((current) => addPickedPhoto(current, outcome.photo));
      }
    } catch {
      setPhotoMessage('No se pudo abrir el selector de imágenes. Puedes enviar la incidencia sin fotos.');
    }
  }

  function handleRemovePhoto(index) {
    if (sending) {
      return;
    }
    setPhotos((current) => removePhotoAt(current, index));
    setSubmitError(null);
  }

  const form = { title, description, location };
  const errors = attemptedSubmit ? validateIncident(form) : {};

  async function handleSubmit() {
    if (sendingRef.current) {
      return;
    }
    setAttemptedSubmit(true);
    if (!createdIncident && !isValid(validateIncident(form))) {
      return;
    }

    sendingRef.current = true;
    setSending(true);
    setSubmitError(null);
    try {
      const result = await submitIncidentWithPhotos({
        createdIncident,
        incidentDraft: {
          title: title.trim(),
          description: description.trim(),
          latitude: location.latitude,
          longitude: location.longitude,
          categoryId,
        },
        photos,
        createIncidentFn: createIncident,
        uploadPhotoFn: uploadIncidentPhoto,
      });
      if (!result.ok) {
        setCreatedIncident(result.incident);
        setPhotos(result.photos);
        setSubmitError(result.errorMessage);
        sendingRef.current = false;
        setSending(false);
        return;
      }
      sendingRef.current = false;
      router.back();
    } catch (error) {
      setSubmitError(error?.message || 'No se pudo enviar la incidencia. Inténtalo de nuevo.');
      sendingRef.current = false;
      setSending(false);
    }
  }

  function toggleCategory(id) {
    if (incidentLocked) {
      return;
    }
    setCategoryId((current) => (current === id ? null : id));
  }

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
      contentInsetAdjustmentBehavior={Platform.OS === 'ios' ? 'automatic' : undefined}
      scrollEnabled={scrollEnabled}
      keyboardShouldPersistTaps="handled"
      automaticallyAdjustKeyboardInsets
    >
      <Text style={styles.label}>Título</Text>
      <TextInput
        style={[styles.input, errors.title && styles.inputError]}
        value={title}
        onChangeText={setTitle}
        placeholder="Ej.: Farola fundida"
        editable={!incidentLocked}
        returnKeyType="next"
      />
      {errors.title && <Text style={styles.errorText}>{errors.title}</Text>}

      <Text style={styles.label}>Descripción</Text>
      <TextInput
        style={[styles.input, styles.multiline, errors.description && styles.inputError]}
        value={description}
        onChangeText={setDescription}
        placeholder="Describe el problema"
        editable={!incidentLocked}
        multiline
        textAlignVertical="top"
      />
      {errors.description && <Text style={styles.errorText}>{errors.description}</Text>}

      <Text style={styles.label}>Categoría (opcional)</Text>
      <View style={styles.chips}>
        {categories.map((category) => {
          const selected = category.id === categoryId;
          return (
            <Pressable
              key={category.id}
              style={[styles.chip, selected && styles.chipSelected]}
              onPress={() => toggleCategory(category.id)}
              disabled={incidentLocked}
              accessibilityRole="button"
              accessibilityState={{ selected }}
            >
              <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
                {category.name}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {categoriesState === 'loading' && (
        <Text style={styles.categoryStatus}>Cargando categorías…</Text>
      )}
      {categoriesState === 'error' && (
        <>
          <Text style={styles.errorText}>{categoriesError}</Text>
          <Pressable
            style={({ pressed }) => [
              styles.retryButton,
              pressed && styles.retryButtonPressed,
            ]}
            onPress={() => {
              setCategoriesState('loading');
              setCategoriesAttempt((attempt) => attempt + 1);
            }}
            accessibilityRole="button"
          >
            <Text style={styles.retryButtonText}>Reintentar</Text>
          </Pressable>
        </>
      )}
      {categoriesState === 'loaded' && categories.length === 0 && (
        <Text style={styles.categoryStatus}>No hay categorías disponibles.</Text>
      )}

      <Text style={styles.label}>Localización</Text>
      <View
        style={[styles.mapContainer, errors.location && styles.mapContainerError]}
        // Freeze the form while a finger is on the map, so the gesture moves the map.
        onTouchStart={() => setScrollEnabled(false)}
        onTouchEnd={() => setScrollEnabled(true)}
        onTouchCancel={() => setScrollEnabled(true)}
      >
        <MapView
          ref={mapRef}
          style={StyleSheet.absoluteFill}
          initialRegion={MALAGA_REGION}
          onMapReady={handleMapReady}
          onPress={(event) => placeByHand(event.nativeEvent.coordinate)}
          {...OSM_MAP_PROPS}
        >
          <OsmTiles />
          {location && (
            <Marker
              coordinate={location}
              draggable={!incidentLocked}
              onDragEnd={(event) => placeByHand(event.nativeEvent.coordinate)}
            />
          )}
        </MapView>
        <OsmAttribution />
        {!location && (
          <View style={styles.mapHint} pointerEvents="none">
            <Text style={styles.mapHintText}>
              {locating ? 'Buscando tu ubicación…' : 'Toca el mapa para marcar la ubicación'}
            </Text>
          </View>
        )}
      </View>
      {errors.location && <Text style={styles.errorText}>{errors.location}</Text>}

      <Text style={styles.label}>
        Fotos (opcional, {photos.length}/{MAX_INCIDENT_PHOTOS})
      </Text>
      {photos.length < MAX_INCIDENT_PHOTOS && (
        <View style={styles.photoActions}>
          <Pressable
            style={({ pressed }) => [
              styles.photoActionButton,
              (pressed || sending) && styles.photoActionButtonPressed,
            ]}
            onPress={() => handlePickPhoto('camera')}
            disabled={sending}
            accessibilityRole="button"
          >
            <Text style={styles.photoActionText}>Tomar foto</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [
              styles.photoActionButton,
              (pressed || sending) && styles.photoActionButtonPressed,
            ]}
            onPress={() => handlePickPhoto('gallery')}
            disabled={sending}
            accessibilityRole="button"
          >
            <Text style={styles.photoActionText}>Elegir de galería</Text>
          </Pressable>
        </View>
      )}
      {photoMessage && <Text style={styles.photoMessage}>{photoMessage}</Text>}

      {photos.length > 0 && (
        <View style={styles.photoList}>
          {photos.map((photo, index) => (
            <View
              key={`${photo.uri}-${index}`}
              style={[styles.photoItem, photo.error && styles.photoItemError]}
            >
              <Image source={{ uri: photo.uri }} style={styles.photoPreview} />
              {photo.uploaded ? (
                <View style={styles.photoUploadedBadge}>
                  <Text style={styles.photoUploadedText}>Subida</Text>
                </View>
              ) : (
                <Pressable
                  style={styles.photoRemoveButton}
                  onPress={() => handleRemovePhoto(index)}
                  disabled={sending}
                  accessibilityRole="button"
                  accessibilityLabel={`Quitar foto ${index + 1}`}
                >
                  <Text style={styles.photoRemoveText}>Quitar</Text>
                </Pressable>
              )}
            </View>
          ))}
        </View>
      )}

      {createdIncident && (
        <Text style={styles.savedNotice}>
          La incidencia ya se ha registrado. Puedes quitar o cambiar la foto con error y reintentar, o continuar sin añadir más fotos.
        </Text>
      )}

      {submitError && <Text style={styles.submitError}>{submitError}</Text>}

      <Pressable
        style={({ pressed }) => [
          styles.submitButton,
          (pressed || sending) && styles.submitButtonPressed,
        ]}
        onPress={handleSubmit}
        disabled={sending}
        accessibilityRole="button"
        accessibilityState={{ disabled: sending, busy: sending }}
      >
        {sending ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.submitButtonText}>
            {createdIncident
              ? photos.some((photo) => !photo.uploaded)
                ? 'Reintentar subida de fotos'
                : 'Finalizar'
              : 'Enviar incidencia'}
          </Text>
        )}
      </Pressable>

      {createdIncident && !sending && (
        <Pressable
          style={({ pressed }) => [
            styles.secondaryButton,
            pressed && styles.photoActionButtonPressed,
          ]}
          onPress={() => router.back()}
          accessibilityRole="button"
        >
          <Text style={styles.secondaryButtonText}>Continuar sin más fotos</Text>
        </Pressable>
      )}
    </ScrollView>
  );
}


const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: COLORS.surface,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.surface,
  },
  content: {
    padding: 16,
  },
  label: {
    marginTop: 16,
    marginBottom: 6,
    fontSize: 15,
    fontWeight: '600',
    color: COLORS.text,
  },
  input: {
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: COLORS.text,
  },
  multiline: {
    minHeight: 100,
  },
  inputError: {
    borderColor: COLORS.error,
  },
  errorText: {
    marginTop: 4,
    color: COLORS.error,
    fontSize: 13,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  categoryStatus: {
    marginTop: 4,
    fontSize: 13,
    color: COLORS.textMuted,
  },
  retryButton: {
    alignSelf: 'flex-start',
    marginTop: 8,
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
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  chipSelected: {
    borderColor: COLORS.primary,
    backgroundColor: COLORS.primary,
  },
  chipText: {
    fontSize: 14,
    color: COLORS.text,
  },
  chipTextSelected: {
    color: COLORS.onPrimary,
  },
  mapContainer: {
    height: 250,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  mapContainerError: {
    borderColor: COLORS.error,
  },
  mapHint: {
    position: 'absolute',
    top: 8,
    alignSelf: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
  },
  mapHintText: {
    fontSize: 13,
    color: COLORS.text,
  },
  photoActions: {
    flexDirection: 'row',
    gap: 10,
  },
  photoActionButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: COLORS.primary,
    backgroundColor: COLORS.surface,
    alignItems: 'center',
  },
  photoActionButtonPressed: {
    backgroundColor: COLORS.primarySoft,
  },
  photoActionText: {
    color: COLORS.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  photoMessage: {
    marginTop: 6,
    fontSize: 13,
    color: COLORS.textMuted,
  },
  photoList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginTop: 12,
  },
  photoItem: {
    width: 96,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surfaceMuted,
  },
  photoItemError: {
    borderColor: COLORS.error,
    borderWidth: 2,
  },
  photoPreview: {
    width: '100%',
    height: 80,
  },
  photoRemoveButton: {
    paddingVertical: 6,
    alignItems: 'center',
    backgroundColor: COLORS.surface,
  },
  photoRemoveText: {
    fontSize: 12,
    fontWeight: '600',
    color: COLORS.error,
  },
  photoUploadedBadge: {
    paddingVertical: 6,
    alignItems: 'center',
    backgroundColor: COLORS.primarySoft,
  },
  photoUploadedText: {
    fontSize: 12,
    fontWeight: '600',
    color: COLORS.primary,
  },
  savedNotice: {
    marginTop: 16,
    padding: 10,
    borderRadius: 8,
    backgroundColor: COLORS.primarySoft,
    color: COLORS.text,
    fontSize: 13,
  },
  submitError: {
    marginTop: 16,
    padding: 10,
    borderRadius: 8,
    backgroundColor: COLORS.errorSoft,
    color: COLORS.error,
  },
  submitButton: {
    marginTop: 24,
    height: 48,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.primary,
  },
  submitButtonPressed: {
    backgroundColor: COLORS.primaryPressed,
  },
  submitButtonText: {
    color: COLORS.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButton: {
    marginTop: 12,
    height: 44,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: COLORS.border,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.surface,
  },
  secondaryButtonText: {
    color: COLORS.text,
    fontSize: 15,
    fontWeight: '600',
  },
});
