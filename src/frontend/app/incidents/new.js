import * as Location from 'expo-location';
import { useNavigation, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { createIncident, getCategories } from '../../src/api';
import { MALAGA_REGION, OSM_MAP_PROPS, OsmAttribution, OsmTiles } from '../../src/map';
import { isValid, validateIncident } from '../../src/validateIncident';

// Some phones never return a fix indoors; stop waiting and let the citizen tap.
const POSITION_TIMEOUT_MS = 10000;
const POSITION_DELTA = 0.005;

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

  const [categories, setCategories] = useState([]);
  const [locating, setLocating] = useState(true);
  const [scrollEnabled, setScrollEnabled] = useState(true);
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [sending, setSending] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    let active = true;
    getCategories()
      .then((result) => active && setCategories(result))
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

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
        const region = { ...coordinate, latitudeDelta: POSITION_DELTA, longitudeDelta: POSITION_DELTA };
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

  function placeByHand(coordinate) {
    if (sending) {
      return;
    }
    placedByHandRef.current = true;
    setLocation(coordinate);
  }

  const form = { title, description, location };
  const errors = attemptedSubmit ? validateIncident(form) : {};

  async function handleSubmit() {
    if (sendingRef.current) {
      return;
    }
    setAttemptedSubmit(true);
    if (!isValid(validateIncident(form))) {
      return;
    }

    sendingRef.current = true;
    setSending(true);
    setSubmitError(null);
    try {
      await createIncident({
        title: title.trim(),
        description: description.trim(),
        latitude: location.latitude,
        longitude: location.longitude,
        categoryId,
      });
      sendingRef.current = false;
      router.back();
    } catch {
      setSubmitError('No se pudo enviar la incidencia. Inténtalo de nuevo.');
      sendingRef.current = false;
      setSending(false);
    }
  }

  function toggleCategory(id) {
    setCategoryId((current) => (current === id ? null : id));
  }

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
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
        editable={!sending}
        returnKeyType="next"
      />
      {errors.title && <Text style={styles.errorText}>{errors.title}</Text>}

      <Text style={styles.label}>Descripción</Text>
      <TextInput
        style={[styles.input, styles.multiline, errors.description && styles.inputError]}
        value={description}
        onChangeText={setDescription}
        placeholder="Describe el problema"
        editable={!sending}
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
              disabled={sending}
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
              draggable={!sending}
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
          <Text style={styles.submitButtonText}>Enviar incidencia</Text>
        )}
      </Pressable>
    </ScrollView>
  );
}

const ERROR_COLOR = '#c62828';
const PRIMARY_COLOR = '#1565c0';

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    padding: 16,
  },
  label: {
    marginTop: 16,
    marginBottom: 6,
    fontSize: 15,
    fontWeight: '600',
    color: '#222',
  },
  input: {
    borderWidth: 1,
    borderColor: '#bbb',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: '#222',
  },
  multiline: {
    minHeight: 100,
  },
  inputError: {
    borderColor: ERROR_COLOR,
  },
  errorText: {
    marginTop: 4,
    color: ERROR_COLOR,
    fontSize: 13,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#bbb',
    backgroundColor: '#fff',
  },
  chipSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: PRIMARY_COLOR,
  },
  chipText: {
    fontSize: 14,
    color: '#222',
  },
  chipTextSelected: {
    color: '#fff',
  },
  mapContainer: {
    height: 250,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#bbb',
  },
  mapContainerError: {
    borderColor: ERROR_COLOR,
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
    color: '#222',
  },
  submitError: {
    marginTop: 16,
    padding: 10,
    borderRadius: 8,
    backgroundColor: '#fdecea',
    color: ERROR_COLOR,
  },
  submitButton: {
    marginTop: 24,
    height: 48,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  submitButtonPressed: {
    backgroundColor: '#0d47a1',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
