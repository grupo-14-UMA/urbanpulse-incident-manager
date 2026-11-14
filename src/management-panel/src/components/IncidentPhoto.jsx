import { useEffect, useState } from 'react';

import { getIncidentPhoto } from '../api.js';

export default function IncidentPhoto({ incidentId, photo, index }) {
  const [attempt, setAttempt] = useState(0);
  const [imageUrl, setImageUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    let objectUrl = '';
    setLoading(true);
    setError('');
    setImageUrl('');

    getIncidentPhoto(incidentId, photo.id, { signal: controller.signal })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setImageUrl(objectUrl);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('No se pudo cargar esta foto.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [incidentId, photo.id, attempt]);

  function handleImageError() {
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    setImageUrl('');
    setError('No se pudo cargar esta foto.');
    setLoading(false);
  }

  return (
    <figure className="detail-photo-card">
      {loading ? (
        <div className="detail-photo-placeholder" role="status">
          <span aria-hidden="true" className="auth-spinner" />
          <span>Cargando foto…</span>
        </div>
      ) : error ? (
        <div className="detail-photo-placeholder detail-photo-error" role="alert">
          <span>{error}</span>
          <button className="detail-retry-button" onClick={() => setAttempt((current) => current + 1)} type="button">
            Reintentar
          </button>
        </div>
      ) : (
        <a
          aria-label={'Abrir foto ' + index + ' en tamaño completo'}
          className="detail-photo-link"
          href={imageUrl}
          rel="noreferrer"
          target="_blank"
        >
          <img alt={'Foto ' + index + ' de la incidencia'} className="detail-photo-image" onError={handleImageError} src={imageUrl} />
        </a>
      )}
      <figcaption>Foto {index}</figcaption>
    </figure>
  );
}