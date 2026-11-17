/* global describe, expect, test */

import { describeStatusChange } from './statusHistory';

describe('status history entries', () => {
  test('describes the initial entry as the registration of the incident', () => {
    const entry = describeStatusChange({
      previousStatus: null,
      newStatus: 'REPORTED',
      changedAt: '2026-11-12T10:00:00.123Z',
      actorRole: 'CITIZEN',
      note: null,
    });

    expect(entry.isRegistration).toBe(true);
    expect(entry.heading).toBe('Incidencia registrada');
    expect(entry.date).toContain('2026');
    expect(entry.note).toBeNull();
  });

  test('describes a transition from the previous status to the new one', () => {
    const entry = describeStatusChange({
      previousStatus: 'REPORTED',
      newStatus: 'VALIDATED',
      changedAt: '2026-11-13T10:00:00.123Z',
      actorRole: 'OPERATOR',
      note: null,
    });

    expect(entry.isRegistration).toBe(false);
    expect(entry.heading).toBe('Registrada → Validada');
  });

  test('labels the note of a rejected incident as its rejection reason', () => {
    const entry = describeStatusChange({
      previousStatus: 'REPORTED',
      newStatus: 'REJECTED',
      changedAt: '2026-11-13T10:00:00.123Z',
      actorRole: 'OPERATOR',
      note: 'Duplicada.',
    });

    expect(entry.note).toEqual({ label: 'Motivo de rechazo', text: 'Duplicada.', kind: 'rejection' });
  });

  test('labels the note of a resolved incident as its resolution note', () => {
    const entry = describeStatusChange({
      previousStatus: 'IN_PROGRESS',
      newStatus: 'RESOLVED',
      changedAt: '2026-11-13T10:00:00.123Z',
      actorRole: 'TECHNICIAN',
      note: 'Arreglada.',
    });

    expect(entry.note).toEqual({ label: 'Nota de resolución', text: 'Arreglada.', kind: 'resolution' });
  });

  test('shows no note when the change has none', () => {
    const entry = describeStatusChange({
      previousStatus: 'VALIDATED',
      newStatus: 'ASSIGNED',
      changedAt: '2026-11-13T10:00:00.123Z',
      actorRole: 'OPERATOR',
      note: null,
    });

    expect(entry.note).toBeNull();
  });

  test('leaves the date empty instead of showing an invalid instant', () => {
    const entry = describeStatusChange({ previousStatus: null, newStatus: 'REPORTED', changedAt: 'not-a-date' });

    expect(entry.date).toBe('');
  });
});
