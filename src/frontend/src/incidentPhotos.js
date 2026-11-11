import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import { File } from 'expo-file-system';

export const MAX_INCIDENT_PHOTOS = 3;
export const MAX_INCIDENT_PHOTO_BYTES = 5 * 1024 * 1024;

const TARGET_PHOTO_BYTES = MAX_INCIDENT_PHOTO_BYTES - 256 * 1024;
const PHOTO_RESIZE_STEPS = [1920, 1600, 1280, 1024];
const PHOTO_COMPRESSION_STEPS = [0.78, 0.66, 0.54, 0.45];

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

function resizeActions(asset, maxDimension) {
  const width = Number(asset.width);
  const height = Number(asset.height);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return [];
  }

  const scale = Math.min(1, maxDimension / Math.max(width, height));
  if (scale === 1) {
    return [];
  }

  return [{
    resize: {
      width: Math.round(width * scale),
      height: Math.round(height * scale),
    },
  }];
}

/** Convert selected images to JPEG and keep the saved file below the backend's 5 MB limit. */
export async function prepareIncidentPhoto(asset) {
  if (!asset?.uri) {
    throw new Error('No se pudo preparar la foto seleccionada.');
  }

  for (let index = 0; index < PHOTO_RESIZE_STEPS.length; index += 1) {
    const result = await ImageManipulator.manipulateAsync(
      asset.uri,
      resizeActions(asset, PHOTO_RESIZE_STEPS[index]),
      {
        compress: PHOTO_COMPRESSION_STEPS[index],
        format: ImageManipulator.SaveFormat.JPEG,
      },
    );
    const fileSize = new File(result.uri).size;
    if (typeof fileSize === 'number' && fileSize > 0 && fileSize <= TARGET_PHOTO_BYTES) {
      const originalName = asset.fileName?.replace(/\.[^.]+$/, '') || 'incidencia';
      return {
        ...normalizeAsset(asset),
        uri: result.uri,
        mimeType: 'image/jpeg',
        fileName: `${originalName}.jpg`,
        fileSize,
        width: result.width,
        height: result.height,
      };
    }
  }

  throw new Error('No se pudo reducir la foto a menos de 5 MB. Elige otra imagen.');
}

export async function pickPhotoFromCamera(picker = ImagePicker, preparePhoto = prepareIncidentPhoto) {
  const permission = await picker.requestCameraPermissionsAsync();
  if (permission?.status !== 'granted') {
    return {
      status: 'denied',
      message: CAMERA_PERMISSION_DENIED_MESSAGE,
    };
  }

  const result = await picker.launchCameraAsync({
    mediaTypes: ['images'],
    quality: 1,
  });
  if (result?.canceled || !Array.isArray(result?.assets) || result.assets.length === 0) {
    return { status: 'canceled' };
  }

  return {
    status: 'picked',
    photo: await preparePhoto(result.assets[0]),
  };
}

export async function pickPhotoFromGallery(picker = ImagePicker, preparePhoto = prepareIncidentPhoto) {
  const permission = await picker.requestMediaLibraryPermissionsAsync();
  if (permission?.status !== 'granted') {
    return {
      status: 'denied',
      message: GALLERY_PERMISSION_DENIED_MESSAGE,
    };
  }

  const result = await picker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    quality: 1,
  });
  if (result?.canceled || !Array.isArray(result?.assets) || result.assets.length === 0) {
    return { status: 'canceled' };
  }

  return {
    status: 'picked',
    photo: await preparePhoto(result.assets[0]),
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
