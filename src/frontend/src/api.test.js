/* global afterEach, beforeEach, describe, expect, jest, test */

describe('mobile API client', () => {
  const apiUrl = 'http://localhost:8080';

  beforeEach(() => {
    jest.resetModules();
    process.env.EXPO_PUBLIC_API_URL = apiUrl;
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.useRealTimers();
    delete process.env.EXPO_PUBLIC_API_URL;
  });

  test('returns the decoded payload when the backend responds successfully', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => '[{"id":1,"name":"Limpieza"}]',
    });
    const { getCategories } = require('./api');

    await expect(getCategories()).resolves.toEqual([{ id: 1, name: 'Limpieza' }]);
    expect(global.fetch).toHaveBeenCalledWith(
      apiUrl + '/api/categories',
      expect.objectContaining({ method: 'GET', headers: { Accept: 'application/json' } }),
    );
  });

  test('exposes the backend error body for a non-success response', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: false,
      status: 400,
      text: async () => JSON.stringify({ message: 'El título es obligatorio.' }),
    });
    const { createIncident } = require('./api');

    await expect(
      createIncident({ title: '', description: 'Descripción', latitude: 36.72, longitude: -4.42 }),
    ).rejects.toMatchObject({
      message: 'El título es obligatorio.',
      status: 400,
      payload: { message: 'El título es obligatorio.' },
    });
  });

  test('maps a network failure to the connection error', async () => {
    global.fetch.mockRejectedValueOnce(new Error('socket closed'));
    const { getCategories } = require('./api');

    await expect(getCategories()).rejects.toMatchObject({
      message: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
    });
  });

  test('aborts a request that exceeds the timeout', async () => {
    jest.useFakeTimers();
    global.fetch.mockImplementationOnce((_url, { signal }) =>
      new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new Error('aborted')));
      }),
    );
    const { getCategories } = require('./api');
    const request = getCategories();
    const rejection = expect(request).rejects.toMatchObject({
      message: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
    });

    await jest.advanceTimersByTimeAsync(10000);
    await rejection;
  });

  test('fails before making a request when the API URL is missing', async () => {
    delete process.env.EXPO_PUBLIC_API_URL;
    const { getCategories } = require('./api');

    await expect(getCategories()).rejects.toMatchObject({
      message: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
    });
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test('uploads an incident photo as multipart FormData with the photos field and decodes the returned list', async () => {
    const appendSpy = jest.spyOn(FormData.prototype, 'append');
    global.fetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      text: async () =>
        JSON.stringify([
          {
            id: 7,
            url: '/api/incidents/42/photos/7',
            contentType: 'image/jpeg',
            sizeBytes: 12345,
            createdAt: '2026-11-10T10:00:00Z',
          },
        ]),
    });
    const { setAccessToken, uploadIncidentPhoto } = require('./api');
    setAccessToken('jwt-token');

    try {
      const result = await uploadIncidentPhoto(42, {
        uri: 'file:///tmp/photo.jpg',
        mimeType: 'image/jpeg',
        fileName: 'photo.jpg',
      });

      expect(result).toMatchObject({
        id: 7,
        url: '/api/incidents/42/photos/7',
        contentType: 'image/jpeg',
      });
      expect(appendSpy).toHaveBeenCalledWith('photos', {
        uri: 'file:///tmp/photo.jpg',
        name: 'photo.jpg',
        type: 'image/jpeg',
      });
      expect(global.fetch).toHaveBeenCalledWith(
        apiUrl + '/api/incidents/42/photos',
        expect.objectContaining({
          method: 'POST',
          headers: {
            Accept: 'application/json',
            Authorization: 'Bearer jwt-token',
          },
          body: expect.any(FormData),
        }),
      );
    } finally {
      appendSpy.mockRestore();
    }
  });

  test('surfaces the backend validation message when a photo is rejected for type or size', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: false,
      status: 400,
      text: async () =>
        JSON.stringify({
          message: 'El archivo supera el tamaño máximo de 5 MB.',
        }),
    });
    const { uploadIncidentPhoto } = require('./api');

    await expect(
      uploadIncidentPhoto(42, {
        uri: 'file:///tmp/huge.jpg',
        mimeType: 'image/jpeg',
        fileName: 'huge.jpg',
      }),
    ).rejects.toMatchObject({
      message: 'El archivo supera el tamaño máximo de 5 MB.',
      status: 400,
    });
  });

  test('builds an authenticated image source for incident detail photos', () => {
    const { getIncidentPhotoSource, setAccessToken } = require('./api');
    setAccessToken('jwt-token');

    expect(getIncidentPhotoSource('/api/incidents/42/photos/7')).toEqual({
      uri: apiUrl + '/api/incidents/42/photos/7',
      headers: { Authorization: 'Bearer jwt-token' },
    });
  });
});
