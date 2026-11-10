import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ApiError, getIncidentById } from '../api.js';

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

function formatDate(date) {
  return new Date(date).toLocaleString('es-ES', {
    dateStyle: 'long',
    timeStyle: 'short',
  });
}

export default function IncidentDetailPage() {
  const { incidentId } = useParams();
  const [incident, setIncident] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setIncident(null);
    setError('');

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

  if (loading) {
    return <section className="placeholder-page" role="status"><span aria-hidden="true" className="auth-spinner" /><p>Cargando incidencia…</p></section>;
  }

  if (error || !incident) {
    return (
      <section className="placeholder-page" role="status">
        <div className="placeholder-icon" aria-hidden="true">?</div>
        <span className="section-kicker">{error?.notFound || !error ? 'INCIDENCIA NO DISPONIBLE' : 'ERROR DE CARGA'}</span>
        <h2>{error?.notFound || !error ? 'No se encontró esta incidencia' : 'No se pudo cargar la incidencia'}</h2>
        <p>{error?.message || 'No se encontró esta incidencia.'}</p>
        <Link className="detail-back-link" to="/">Volver al mapa</Link>
      </section>
    );
  }

  return (
    <section className="incident-detail-page" aria-labelledby="incident-detail-title">
      <Link className="detail-back-link" to="/">← Volver al mapa</Link>
      <article className="incident-detail-card">
        <div className="detail-heading">
          <div>
            <span className="section-kicker">{incident.categoryName || 'Sin categoría'}</span>
            <h2 id="incident-detail-title">{incident.title}</h2>
          </div>
          <span className={`detail-status detail-status-${(incident.status || 'REPORTED').toLowerCase()}`}>
            {STATUS_LABELS[incident.status] || 'Registrada'}
          </span>
        </div>
        <p className="detail-description">{incident.description}</p>
        <dl className="detail-facts">
          <div><dt>Prioridad</dt><dd>{PRIORITY_LABELS[incident.priority] || 'Sin asignar'}</dd></div>
          <div><dt>Fecha de registro</dt><dd>{formatDate(incident.createdAt)}</dd></div>
          <div><dt>Coordenadas</dt><dd>{incident.latitude.toFixed(5)}, {incident.longitude.toFixed(5)}</dd></div>
        </dl>
        <p className="mock-data-disclaimer">Datos actualizados desde el backend. Las acciones del Operador se incorporarán en la issue #73.</p>
      </article>
    </section>
  );
}
