/* global afterEach, beforeEach, describe, expect, jest, test */

jest.mock('expo/fetch', () => ({
  fetch: (...args) => global.fetch(...args),
}));

jest.mock('expo-file-system', () => ({
  File: class MockFile {
    constructor(...uris) {
      this.uri = uris.reduce((path, uri) => {
        const segment = typeof uri === 'string' ? uri : uri?.uri;
        return path ? path.replace(/\/$/, '') + '/' + segment.replace(/^\/+/, '') : segment;
      }, '');
      this.name = this.uri.split('/').pop();
      this.type = this.name.endsWith('.png') ? 'image/png' : 'image/jpeg';
      this.bytes = async () => new Uint8Array([1, 2, 3]);
    }

    create() {}

    async write(content) {
      this.content = content;
    }

    delete() {}
  },
  Paths: { cache: { uri: 'file:///cache' } },
}));

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
    jest.useFakeTimers();
    jest.spyOn(Math, 'random').mockReturnValue(0.5);
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    global.fetch
      .mockRejectedValueOnce(new Error('socket closed'))
      .mockRejectedValueOnce(new Error('socket closed'))
      .mockRejectedValueOnce(new Error('socket closed'));
    const { getCategories } = require('./api');
    const request = getCategories();
    const rejection = expect(request).rejects.toMatchObject({
      message: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
    });

    await jest.advanceTimersByTimeAsync(1000);
    await jest.advanceTimersByTimeAsync(3000);
    await rejection;
    expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('Error: socket closed'));
    warnSpy.mockRestore();
    Math.random.mockRestore();
  });

  test('retries transient GET failures with bounded backoff', async () => {
    jest.useFakeTimers();
    jest.spyOn(Math, 'random').mockReturnValue(0.5);
    global.fetch
      .mockRejectedValueOnce(new Error('offline'))
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => '[]' });
    const { getCategories } = require('./api');
    const request = getCategories();

    await jest.advanceTimersByTimeAsync(1500);
    await jest.advanceTimersByTimeAsync(4500);
    await expect(request).resolves.toEqual([]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
    Math.random.mockRestore();
  });

  test('does not retry writes after a transient failure', async () => {
    global.fetch.mockRejectedValueOnce(new Error('offline'));
    const { createIncident } = require('./api');

    await expect(
      createIncident({ title: 'Farola', description: 'Fundida', latitude: 36.72, longitude: -4.42 }),
    ).rejects.toMatchObject({ kind: 'connection' });
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  test('aborts a request that exceeds the timeout', async () => {
    jest.useFakeTimers();
    global.fetch.mockImplementation((_url, { signal }) =>
      new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new Error('aborted')));
      }),
    );
    const { getCategories } = require('./api');
    const request = getCategories();
    const rejection = expect(request).rejects.toMatchObject({
      message: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
    });

    // The first timeout is retried once after 1 s; the 15 s budget prevents
    // starting a third attempt after the second timeout.
    await jest.advanceTimersByTimeAsync(22000);
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
      const [fieldName, file] = appendSpy.mock.calls[0];
      expect(fieldName).toBe('photos');
      expect(file).toMatchObject({
        uri: 'file:///tmp/photo.jpg',
        name: 'photo.jpg',
        type: 'image/jpeg',
      });
      expect(file.bytes).toEqual(expect.any(Function));
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

  test('allows a photo upload 60 seconds before timing out and logs the timeout distinctly', async () => {
    jest.useFakeTimers();
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    global.fetch.mockImplementation((_url, { signal }) =>
      new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new Error('aborted')));
      }),
    );
    const { uploadIncidentPhoto } = require('./api');
    let settled = false;
    const request = uploadIncidentPhoto(42, {
      uri: 'file:///tmp/photo.jpg',
      mimeType: 'image/jpeg',
      fileName: 'photo.jpg',
    });
    request.then(() => { settled = true; }, () => { settled = true; });
    const rejection = expect(request).rejects.toMatchObject({
      kind: 'connection',
      message: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
    });

    try {
      await jest.advanceTimersByTimeAsync(10000);
      expect(settled).toBe(false);
      expect(global.fetch).toHaveBeenCalledTimes(1);

      await jest.advanceTimersByTimeAsync(50000);
      await rejection;
      expect(settled).toBe(true);
      expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('timeout'));
    } finally {
      warnSpy.mockRestore();
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

  test('explains an HTTP 413 photo upload response instead of showing a generic validation error', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: false,
      status: 413,
      text: async () => '',
    });
    const { uploadIncidentPhoto } = require('./api');

    await expect(
      uploadIncidentPhoto(42, {
        uri: 'file:///too-large.jpg',
        mimeType: 'image/jpeg',
        fileName: 'too-large.jpg',
      }),
    ).rejects.toMatchObject({
      kind: 'invalid',
      status: 413,
      message: 'La foto supera el límite de 5 MB. Selecciona otra imagen o reduce su tamaño.',
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

  test('does not send the session token to a photo URL on another host', () => {
    const { getIncidentPhotoSource, setAccessToken } = require('./api');
    setAccessToken('jwt-token');

    expect(getIncidentPhotoSource('https://another-host.example/photo.jpg')).toBeNull();
  });

});
