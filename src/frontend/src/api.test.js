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
});
