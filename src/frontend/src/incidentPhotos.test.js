/* global describe, expect, jest, test */

import {
  CAMERA_PERMISSION_DENIED_MESSAGE,
  GALLERY_PERMISSION_DENIED_MESSAGE,
  MAX_INCIDENT_PHOTOS,
  addPickedPhoto,
  pickPhotoFromCamera,
  pickPhotoFromGallery,
  removePhotoAt,
  submitIncidentWithPhotos,
} from './incidentPhotos';

describe('incidentPhotos helper', () => {
  test('picks a photo from camera or gallery and caps the list at 3 photos', async () => {
    const mockPicker = {
      requestCameraPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
      launchCameraAsync: jest.fn().mockResolvedValue({
        canceled: false,
        assets: [
          {
            uri: 'file:///camera-1.jpg',
            mimeType: 'image/jpeg',
            fileName: 'camera-1.jpg',
            fileSize: 2048,
          },
        ],
      }),
      requestMediaLibraryPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
      launchImageLibraryAsync: jest.fn().mockResolvedValue({
        canceled: false,
        assets: [
          {
            uri: 'file:///gallery-1.png',
            mimeType: 'image/png',
            fileName: 'gallery-1.png',
            fileSize: 4096,
          },
        ],
      }),
    };

    const fromCamera = await pickPhotoFromCamera(mockPicker);
    const fromGallery = await pickPhotoFromGallery(mockPicker);

    expect(fromCamera).toEqual({
      status: 'picked',
      photo: {
        uri: 'file:///camera-1.jpg',
        mimeType: 'image/jpeg',
        fileName: 'camera-1.jpg',
        fileSize: 2048,
        uploaded: false,
        error: null,
      },
    });
    expect(fromGallery.status).toBe('picked');

    let list = [];
    list = addPickedPhoto(list, fromCamera.photo);
    list = addPickedPhoto(list, fromGallery.photo);
    list = addPickedPhoto(list, { uri: 'file:///third.webp' });
    list = addPickedPhoto(list, { uri: 'file:///fourth-ignored.jpg' });

    expect(list).toHaveLength(MAX_INCIDENT_PHOTOS);

    list = removePhotoAt(list, 1);
    expect(list.map((item) => item.uri)).toEqual(['file:///camera-1.jpg', 'file:///third.webp']);
  });

  test('returns a non-blocking notice when camera or gallery permission is denied', async () => {
    const deniedPicker = {
      requestCameraPermissionsAsync: jest.fn().mockResolvedValue({ status: 'denied' }),
      requestMediaLibraryPermissionsAsync: jest.fn().mockResolvedValue({ status: 'denied' }),
    };

    await expect(pickPhotoFromCamera(deniedPicker)).resolves.toEqual({
      status: 'denied',
      message: CAMERA_PERMISSION_DENIED_MESSAGE,
    });
    await expect(pickPhotoFromGallery(deniedPicker)).resolves.toEqual({
      status: 'denied',
      message: GALLERY_PERMISSION_DENIED_MESSAGE,
    });
  });

  test('creates the incident and uploads 0 to 3 photos in order', async () => {
    const createIncidentFn = jest.fn().mockResolvedValue({ id: 99, title: 'Bache' });
    const uploadPhotoFn = jest.fn().mockResolvedValue({ id: 1, url: '/api/incidents/99/photos/1' });

    const withoutPhotos = await submitIncidentWithPhotos({
      incidentDraft: { title: 'Bache', description: 'Hondo', latitude: 36.72, longitude: -4.42 },
      photos: [],
      createIncidentFn,
      uploadPhotoFn,
    });
    expect(withoutPhotos.ok).toBe(true);
    expect(uploadPhotoFn).not.toHaveBeenCalled();

    const withPhotos = await submitIncidentWithPhotos({
      incidentDraft: { title: 'Bache', description: 'Hondo', latitude: 36.72, longitude: -4.42 },
      photos: [
        { uri: 'file:///1.jpg', uploaded: false, error: null },
        { uri: 'file:///2.png', uploaded: false, error: null },
      ],
      createIncidentFn,
      uploadPhotoFn,
    });

    expect(withPhotos.ok).toBe(true);
    expect(uploadPhotoFn).toHaveBeenCalledTimes(2);
    expect(withPhotos.photos.every((photo) => photo.uploaded)).toBe(true);
  });

  test('preserves the created incident when a photo is rejected and skips already uploaded photos on retry', async () => {
    const createIncidentFn = jest.fn().mockResolvedValue({ id: 105, title: 'Farola' });
    const uploadPhotoFn = jest
      .fn()
      .mockResolvedValueOnce({ id: 1, url: '/api/incidents/105/photos/1' })
      .mockRejectedValueOnce(new Error('Solo se permiten imágenes JPEG, PNG o WebP.'))
      .mockResolvedValueOnce({ id: 2, url: '/api/incidents/105/photos/2' });

    const firstAttempt = await submitIncidentWithPhotos({
      incidentDraft: { title: 'Farola', description: 'Sin luz', latitude: 36.72, longitude: -4.42 },
      photos: [
        { uri: 'file:///valid.jpg', uploaded: false, error: null },
        { uri: 'file:///invalid.txt', uploaded: false, error: null },
      ],
      createIncidentFn,
      uploadPhotoFn,
    });

    expect(firstAttempt.ok).toBe(false);
    expect(firstAttempt.incident).toEqual({ id: 105, title: 'Farola' });
    expect(firstAttempt.errorMessage).toBe('Solo se permiten imágenes JPEG, PNG o WebP.');
    expect(firstAttempt.photos[0].uploaded).toBe(true);
    expect(firstAttempt.photos[1]).toMatchObject({
      uploaded: false,
      error: 'Solo se permiten imágenes JPEG, PNG o WebP.',
    });

    // Replace the invalid photo and retry: must NOT re-create the incident or re-upload the first photo.
    const retryPhotos = [
      firstAttempt.photos[0],
      { uri: 'file:///replacement.webp', uploaded: false, error: null },
    ];
    const secondAttempt = await submitIncidentWithPhotos({
      createdIncident: firstAttempt.incident,
      incidentDraft: { title: 'Farola', description: 'Sin luz', latitude: 36.72, longitude: -4.42 },
      photos: retryPhotos,
      createIncidentFn,
      uploadPhotoFn,
    });

    expect(secondAttempt.ok).toBe(true);
    expect(createIncidentFn).toHaveBeenCalledTimes(1);
    expect(uploadPhotoFn).toHaveBeenCalledTimes(3);
  });
});
