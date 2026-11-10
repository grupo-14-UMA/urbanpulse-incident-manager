import * as ImagePicker from 'expo-image-picker';

export const MAX_INCIDENT_PHOTOS = 3;

export const CAMERA_PERMISSION_DENIED_MESSAGE =
  'Has denegado el permiso de cámara. Puedes enviar la incidencia sin fotos o elegir una de la galería.';
export const GALLERY_PERMISSION_DENIED_MESSAGE =
  'Has denegado el permiso de galería. Puedes enviar la incidencia sin fotos o usar la cámara.';

function normalizeAsset(asset) {
  return {
    uri: asset.uri,
    mimeType: asset.mimeType ?? null,
    fileName: asset.fileName ?? null,
    fileSize: asset.fileSize ?? null,
    uploaded: false,
    error: null,
  };
}

export async function pickPhotoFromCamera(picker = ImagePicker) {
  const permission = await picker.requestCameraPermissionsAsync();
  if (permission?.status !== 'granted') {
    return {
      status: 'denied',
      message: CAMERA_PERMISSION_DENIED_MESSAGE,
    };
  }

  const result = await picker.launchCameraAsync({
    mediaTypes: ['images'],
    quality: 0.8,
  });
  if (result?.canceled || !Array.isArray(result?.assets) || result.assets.length === 0) {
    return { status: 'canceled' };
  }

  return {
    status: 'picked',
    photo: normalizeAsset(result.assets[0]),
  };
}

export async function pickPhotoFromGallery(picker = ImagePicker) {
  const permission = await picker.requestMediaLibraryPermissionsAsync();
  if (permission?.status !== 'granted') {
    return {
      status: 'denied',
      message: GALLERY_PERMISSION_DENIED_MESSAGE,
    };
  }

  const result = await picker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    quality: 0.8,
  });
  if (result?.canceled || !Array.isArray(result?.assets) || result.assets.length === 0) {
    return { status: 'canceled' };
  }

  return {
    status: 'picked',
    photo: normalizeAsset(result.assets[0]),
  };
}

export function addPickedPhoto(photos, photo) {
  if (!Array.isArray(photos) || photos.length >= MAX_INCIDENT_PHOTOS || !photo?.uri) {
    return photos ?? [];
  }
  return [...photos, photo];
}

export function removePhotoAt(photos, index) {
  if (!Array.isArray(photos)) {
    return [];
  }
  return photos.filter((_, currentIndex) => currentIndex !== index);
}

export async function submitIncidentWithPhotos({
  createdIncident = null,
  incidentDraft,
  photos = [],
  createIncidentFn,
  uploadPhotoFn,
}) {
  const incident = createdIncident ?? (await createIncidentFn(incidentDraft));
  const nextPhotos = photos.map((photo) => ({ ...photo, error: null }));

  for (let index = 0; index < nextPhotos.length; index += 1) {
    const current = nextPhotos[index];
    if (current.uploaded) {
      continue;
    }
    try {
      await uploadPhotoFn(incident.id, current);
      nextPhotos[index] = {
        ...current,
        uploaded: true,
        error: null,
      };
    } catch (error) {
      const errorMessage = error?.message || 'No se pudo subir la foto. Inténtalo de nuevo.';
      nextPhotos[index] = {
        ...current,
        uploaded: false,
        error: errorMessage,
      };
      return {
        ok: false,
        incident,
        photos: nextPhotos,
        errorMessage,
      };
    }
  }

  return {
    ok: true,
    incident,
    photos: nextPhotos,
  };
}
