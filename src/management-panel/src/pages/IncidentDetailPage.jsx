import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CircleMarker, MapContainer, TileLayer } from 'react-leaflet';

import { ApiError, getIncidentById, getIncidentHistory } from '../api.js';
import IncidentActions from '../components/IncidentActions.jsx';
import IncidentPhoto from '../components/IncidentPhoto.jsx';

const STATUS_LABELS = {
  REPORTED: 'Registrada',
  VALIDATED: 'Validada',
  ASSIGNED: 'Asignada',
  IN_PROGRESS: 'En curso',
  RESOLVED: 'Resuelta',
  REJECTED: 'Rechazada',
};

const PRIORITY_LABELS = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  URGENT: 'Urgente',
};

const ROLE_LABELS = {
  CITIZEN: 'Ciudadano',
  OPERATOR: 'Operador',
  TECHNICIAN: 'Técnico',
};

function formatDate(date) {
  return new Date(date).toLocaleString('es-ES', {
    dateStyle: 'long',
    timeStyle: 'short',
  });
}

function statusLabel(status) {
  return STATUS_LABELS[status] || 'Estado actualizado';
}

export default function IncidentDetailPage() {
  const { incidentId } = useParams();
  const [incident, setIncident] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [history, setHistory] = useState({ kind: 'loading', entries: [] });
  const [historyRetry, setHistoryRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setIncident(null);
    setError(null);

    getIncidentById(incidentId, { signal: controller.signal })
      .then(setIncident)
      .catch((requestError) => {
        if (controller.signal.aborted) return;
        setError({
          notFound: requestError instanceof ApiError && requestError.status === 404,
          message: requestError?.message || 'No se pudo cargar la incidencia.',
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [incidentId]);

  useEffect(() => {
    const controller = new AbortController();
    setHistory({ kind: 'loading', entries: [] });

    getIncidentHistory(incidentId, { signal: controller.signal })
      .then((entries) => {
        if (!controller.signal.aborted) setHistory({ kind: 'loaded', entries });
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setHistory({
            kind: 'error',
            entries: [],
            message: requestError?.message || 'No se pudo cargar el historial.',
          });
        }
      });

    return () => controller.abort();
  }, [incidentId, historyRetry]);

  function handleIncidentUpdated(updated) {
    setIncident(updated);
    setHistoryRetry((current) => current + 1);
  }

  if (loading) {
    return <section className="placeholder-page" role="status"><span aria-hidden="true" className="auth-spinner" /><p>Cargando incidencia…</p></section>;
  }

  if (error || !incident) {
    const unavailable = error?.notFound || !error;
    return (
      <section className="placeholder-page" role="status">
        <div className="placeholder-icon" aria-hidden="true">?</div>
        <span className="section-kicker">{unavailable ? 'INCIDENCIA NO DISPONIBLE' : 'ERROR DE CARGA'}</span>
        <h2>{unavailable ? 'No se encontró esta incidencia' : 'No se pudo cargar la incidencia'}</h2>
        <p>{error?.message || 'No se encontró esta incidencia.'}</p>
        <Link className="detail-back-link" to="/">Volver al mapa</Link>
      </section>
    );
  }

  const photos = Array.isArray(incident.photos) ? incident.photos : [];

  return (
    <section className="incident-detail-page" aria-labelledby="incident-detail-title">
      <Link className="detail-back-link" to="/incidents">← Volver a incidencias</Link>
      <article className="incident-detail-card">
        <div className="detail-heading">
          <div>
            <span className="section-kicker">{incident.categoryName || 'Sin categoría'}</span>
            <h2 id="incident-detail-title">{incident.title}</h2>
          </div>
          <span className={'detail-status detail-status-' + (incident.status || 'REPORTED').toLowerCase()}>
            {statusLabel(incident.status)}
          </span>
        </div>

        <p className="detail-description">{incident.description}</p>

        <dl className="detail-facts">
          <div><dt>Prioridad</dt><dd>{PRIORITY_LABELS[incident.priority] || 'Sin asignar'}</dd></div>
          {incident.priorityJustification && (
            <div><dt>Justificación</dt><dd>{incident.priorityJustification}</dd></div>
          )}
          <div><dt>Fecha de registro</dt><dd>{formatDate(incident.createdAt)}</dd></div>
          <div><dt>Coordenadas</dt><dd>{incident.latitude.toFixed(5)}, {incident.longitude.toFixed(5)}</dd></div>
          {incident.assignee?.name && (
            <div><dt>Técnico asignado</dt><dd>{incident.assignee.name}</dd></div>
          )}
        </dl>

        <IncidentActions incident={incident} onUpdated={handleIncidentUpdated} />

        <section aria-labelledby="incident-photos-title" className="detail-section">
          <h3 id="incident-photos-title">Fotos adjuntas</h3>
          {photos.length === 0 ? (
            <p className="detail-empty-state">Esta incidencia no tiene fotos adjuntas.</p>
          ) : (
            <div className="detail-photo-grid">
              {photos.slice(0, 3).map((photo, index) => (
                <IncidentPhoto incidentId={incident.id} index={index + 1} key={photo.id} photo={photo} />
              ))}
            </div>
          )}
        </section>

        <section aria-label="Ubicación de la incidencia" className="detail-location">
          <h3>Ubicación</h3>
          <MapContainer
            center={[incident.latitude, incident.longitude]}
            className="detail-location-map"
            scrollWheelZoom={false}
            zoom={16}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <CircleMarker
              center={[incident.latitude, incident.longitude]}
              pathOptions={{ color: '#fff', fillColor: '#d45c63', fillOpacity: 1, weight: 3 }}
              radius={8}
            />
          </MapContainer>
        </section>

        <section aria-labelledby="incident-history-title" className="detail-section">
          <h3 id="incident-history-title">Historial de estados</h3>
          {history.kind === 'loading' && <p className="detail-empty-state" role="status">Cargando historial…</p>}
          {history.kind === 'error' && (
            <div className="detail-inline-error" role="alert">
              <span>{history.message}</span>
              <button className="detail-retry-button" onClick={() => setHistoryRetry((current) => current + 1)} type="button">
                Reintentar
              </button>
            </div>
          )}
          {history.kind === 'loaded' && history.entries.length === 0 && (
            <p className="detail-empty-state">No hay cambios de estado registrados.</p>
          )}
          {history.kind === 'loaded' && history.entries.length > 0 && (
            <ol className="detail-history-list">
              {history.entries.map((entry, index) => (
                <li className="detail-history-entry" key={entry.changedAt + '-' + entry.newStatus + '-' + index}>
                  <span aria-hidden="true" className="detail-history-marker" />
                  <div className="detail-history-content">
                    <div className="detail-history-heading">
                      <strong>
                        {entry.previousStatus
                          ? statusLabel(entry.previousStatus) + ' → ' + statusLabel(entry.newStatus)
                          : 'Incidencia registrada'}
                      </strong>
                      <time dateTime={entry.changedAt}>{formatDate(entry.changedAt)}</time>
                    </div>
                    <p>{ROLE_LABELS[entry.actorRole] || 'Usuario no identificado'}</p>
                    {entry.note && <blockquote>{entry.note}</blockquote>}
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      </article>
    </section>
  );
}