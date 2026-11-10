import { Link, useParams } from 'react-router-dom';

import { getMockIncidentById } from '../mock/incidents.js';

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
  const incident = getMockIncidentById(incidentId);

  if (!incident) {
    return (
      <section className="placeholder-page" role="status">
        <div className="placeholder-icon" aria-hidden="true">?</div>
        <span className="section-kicker">INCIDENCIA NO DISPONIBLE</span>
        <h2>No se encontró esta incidencia</h2>
        <p>La incidencia puede no estar incluida en los datos simulados.</p>
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
            <span className="section-kicker">{incident.categoryName}</span>
            <h2 id="incident-detail-title">{incident.title}</h2>
          </div>
          <span className={`detail-status detail-status-${incident.status.toLowerCase()}`}>
            {STATUS_LABELS[incident.status]}
          </span>
        </div>
        <p className="detail-description">{incident.description}</p>
        <dl className="detail-facts">
          <div><dt>Prioridad</dt><dd>{PRIORITY_LABELS[incident.priority]}</dd></div>
          <div><dt>Fecha de registro</dt><dd>{formatDate(incident.createdAt)}</dd></div>
          <div><dt>Coordenadas</dt><dd>{incident.latitude.toFixed(5)}, {incident.longitude.toFixed(5)}</dd></div>
        </dl>
        <p className="mock-data-disclaimer">Detalle de muestra; las acciones del Operador se incorporarán en la issue #73.</p>
      </article>
    </section>
  );
}
