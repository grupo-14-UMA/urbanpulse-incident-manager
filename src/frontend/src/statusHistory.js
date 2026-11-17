import { statusLabel } from './labels';

// Spanish description of one StatusChange of the API. The initial entry (no
// previous status) is the registration itself; the note is a rejection reason
// or a resolution note, and each one is labelled with its meaning.
export function describeStatusChange(change) {
  const previousStatus = change?.previousStatus;
  const newStatus = change?.newStatus;
  const isRegistration = previousStatus == null;
  return {
    isRegistration,
    heading: isRegistration
      ? 'Incidencia registrada'
      : `${statusLabel(previousStatus)} → ${statusLabel(newStatus)}`,
    date: formatDate(change?.changedAt),
    note: describeNote(change),
  };
}

// Localized timestamp for es-ES, empty rather than "Invalid Date" if malformed.
function formatDate(instant) {
  const date = new Date(instant);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('es-ES');
}

function describeNote(change) {
  if (!change?.note) {
    return null;
  }
  if (change.newStatus === 'REJECTED') {
    return { label: 'Motivo de rechazo', text: change.note, kind: 'rejection' };
  }
  if (change.newStatus === 'RESOLVED') {
    return { label: 'Nota de resolución', text: change.note, kind: 'resolution' };
  }
  return { label: 'Nota', text: change.note, kind: 'neutral' };
}
