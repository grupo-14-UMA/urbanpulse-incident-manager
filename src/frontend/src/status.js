// Spanish labels for each incident Status code (see GLOSSARY.md). An unknown code is shown as is.
const STATUS_LABELS = {
  REPORTED: 'Registrada',
};

export function statusLabel(status) {
  return STATUS_LABELS[status] ?? status;
}
