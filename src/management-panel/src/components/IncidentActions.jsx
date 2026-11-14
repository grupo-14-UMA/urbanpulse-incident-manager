import { useEffect, useState } from 'react';

import {
  assignIncident,
  changeIncidentPriority,
  getTechnicians,
  rejectIncident,
  validateIncident,
} from '../api.js';

const PRIORITIES = [
  { value: 'LOW', label: 'Baja' },
  { value: 'MEDIUM', label: 'Media' },
  { value: 'HIGH', label: 'Alta' },
  { value: 'URGENT', label: 'Urgente' },
];

export default function IncidentActions({ incident, onUpdated }) {
  const [pendingAction, setPendingAction] = useState('');
  const [actionError, setActionError] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [validationPriority, setValidationPriority] = useState(incident.priority || '');
  const [validationJustification, setValidationJustification] = useState('');
  const [priority, setPriority] = useState(incident.priority || '');
  const [justification, setJustification] = useState(incident.priorityJustification || '');
  const [technicianId, setTechnicianId] = useState('');
  const [technicians, setTechnicians] = useState({ kind: 'idle', items: [] });
  const [technicianRetry, setTechnicianRetry] = useState(0);

  const canValidate = incident.status === 'REPORTED';
  const canReject = incident.status === 'REPORTED';
  const canAssign = incident.status === 'VALIDATED';
  const canChangePriority = ['VALIDATED', 'ASSIGNED', 'IN_PROGRESS'].includes(incident.status);

  useEffect(() => {
    setPriority(incident.priority || '');
    setJustification(incident.priorityJustification || '');
    setValidationPriority(incident.priority || '');
    setValidationJustification(incident.priorityJustification || '');
  }, [incident.id, incident.priority, incident.priorityJustification]);

  useEffect(() => {
    if (!canAssign) {
      setTechnicians({ kind: 'idle', items: [] });
      return undefined;
    }

    const controller = new AbortController();
    setTechnicians({ kind: 'loading', items: [] });
    getTechnicians({ limit: 200, signal: controller.signal })
      .then((items) => {
        if (!controller.signal.aborted) setTechnicians({ kind: 'loaded', items });
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setTechnicians({
            kind: 'error',
            items: [],
            message: error?.message || 'No se pudo cargar la lista de técnicos.',
          });
        }
      });

    return () => controller.abort();
  }, [incident.id, canAssign, technicianRetry]);

  async function performAction(action, operation) {
    if (pendingAction) return;
    setActionError('');
    setPendingAction(action);
    try {
      const updated = await operation();
      onUpdated(updated);
      if (action === 'reject') setRejectReason('');
      if (action === 'assign') setTechnicianId('');
    } catch (error) {
      setActionError(error?.message || 'No se pudo actualizar la incidencia. Inténtalo de nuevo.');
    } finally {
      setPendingAction('');
    }
  }

  function handleValidate(event) {
    event.preventDefault();
    const hasPriority = Boolean(validationPriority);
    const request = {
      priority: hasPriority ? validationPriority : null,
      priorityJustification: hasPriority ? validationJustification.trim() || null : null,
    };
    void performAction('validate', () => validateIncident(incident.id, request));
  }

  function handleReject(event) {
    event.preventDefault();
    const reason = rejectReason.trim();
    if (!reason) {
      setActionError('El motivo del rechazo es obligatorio.');
      return;
    }
    void performAction('reject', () => rejectIncident(incident.id, reason));
  }

  function handleAssign(event) {
    event.preventDefault();
    if (!technicianId) {
      setActionError('Elige un técnico antes de asignar la incidencia.');
      return;
    }
    void performAction('assign', () => assignIncident(incident.id, Number(technicianId)));
  }

  function handlePriorityChange(event) {
    event.preventDefault();
    if (!priority) {
      setActionError('Elige una prioridad.');
      return;
    }
    void performAction('priority', () =>
      changeIncidentPriority(incident.id, priority, justification.trim() || null));
  }

  if (!canValidate && !canReject && !canAssign && !canChangePriority) return null;

  const busy = Boolean(pendingAction);

  return (
    <section aria-labelledby="incident-actions-title" className="detail-section detail-actions">
      <h3 id="incident-actions-title">Acciones del Operador</h3>
      <div className="detail-action-grid">
        {canValidate && (
          <form className="detail-action-panel" onSubmit={handleValidate}>
            <h4>Validar incidencia</h4>
            <p>Confirma que la incidencia puede pasar a gestión.</p>
            <label className="detail-form-field" htmlFor="validation-priority">
              Prioridad inicial <span>(opcional)</span>
            </label>
            <select
              id="validation-priority"
              onChange={(event) => setValidationPriority(event.target.value)}
              value={validationPriority}
            >
              <option value="">Sin prioridad</option>
              {PRIORITIES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            {validationPriority && (
              <>
                <label className="detail-form-field" htmlFor="validation-justification">
                  Justificación <span>(opcional)</span>
                </label>
                <textarea
                  id="validation-justification"
                  maxLength={1000}
                  onChange={(event) => setValidationJustification(event.target.value)}
                  rows={3}
                  value={validationJustification}
                />
              </>
            )}
            <button className="detail-action-button detail-action-primary" disabled={busy} type="submit">
              {pendingAction === 'validate' ? 'Validando…' : 'Validar'}
            </button>
          </form>
        )}

        {canReject && (
          <form className="detail-action-panel" onSubmit={handleReject}>
            <h4>Rechazar incidencia</h4>
            <p>Indica el motivo para dejarlo registrado en el historial.</p>
            <label className="detail-form-field" htmlFor="rejection-reason">Motivo del rechazo</label>
            <textarea
              id="rejection-reason"
              maxLength={1000}
              onChange={(event) => setRejectReason(event.target.value)}
              required
              rows={3}
              value={rejectReason}
            />
            <button className="detail-action-button detail-action-danger" disabled={busy} type="submit">
              {pendingAction === 'reject' ? 'Rechazando…' : 'Rechazar'}
            </button>
          </form>
        )}

        {canAssign && (
          <form className="detail-action-panel" onSubmit={handleAssign}>
            <h4>Asignar a un Técnico</h4>
            <p>La incidencia validada se asignará una sola vez.</p>
            {technicians.kind === 'loading' && <p role="status">Cargando técnicos…</p>}
            {technicians.kind === 'error' && (
              <div className="detail-inline-error" role="alert">
                <span>{technicians.message}</span>
                <button className="detail-retry-button" onClick={() => setTechnicianRetry((current) => current + 1)} type="button">
                  Reintentar
                </button>
              </div>
            )}
            {technicians.kind === 'loaded' && technicians.items.length === 0 && (
              <p>No hay técnicos disponibles para asignar.</p>
            )}
            {technicians.kind === 'loaded' && technicians.items.length > 0 && (
              <>
                <label className="detail-form-field" htmlFor="assigned-technician">Técnico</label>
                <select
                  id="assigned-technician"
                  onChange={(event) => setTechnicianId(event.target.value)}
                  required
                  value={technicianId}
                >
                  <option value="">Selecciona un técnico</option>
                  {technicians.items.map((technician) => (
                    <option key={technician.id} value={technician.id}>{technician.name}</option>
                  ))}
                </select>
                <button className="detail-action-button detail-action-primary" disabled={busy} type="submit">
                  {pendingAction === 'assign' ? 'Asignando…' : 'Asignar'}
                </button>
              </>
            )}
          </form>
        )}

        {canChangePriority && (
          <form className="detail-action-panel" onSubmit={handlePriorityChange}>
            <h4>Cambiar prioridad</h4>
            <p>La justificación sustituye a la anterior.</p>
            <label className="detail-form-field" htmlFor="incident-priority">Prioridad</label>
            <select
              id="incident-priority"
              onChange={(event) => setPriority(event.target.value)}
              required
              value={priority}
            >
              <option value="">Selecciona una prioridad</option>
              {PRIORITIES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <label className="detail-form-field" htmlFor="priority-justification">
              Justificación <span>(opcional)</span>
            </label>
            <textarea
              id="priority-justification"
              maxLength={1000}
              onChange={(event) => setJustification(event.target.value)}
              rows={3}
              value={justification}
            />
            <button className="detail-action-button detail-action-primary" disabled={busy} type="submit">
              {pendingAction === 'priority' ? 'Guardando…' : 'Guardar prioridad'}
            </button>
          </form>
        )}
      </div>
      {actionError && <p className="detail-action-error" role="alert">{actionError}</p>}
    </section>
  );
}