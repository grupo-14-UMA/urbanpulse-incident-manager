// Spanish text shown for an incident's fields, shared by every screen.

// One label per incident Status code (see GLOSSARY.md). An unknown code is shown as is.
const STATUS_LABELS = {
  REPORTED: 'Registrada',
};

export function statusLabel(status) {
  return STATUS_LABELS[status] ?? status;
}

// Category is optional, so a missing one gets a label instead of an empty space.
export function categoryLabel(categoryName) {
  return categoryName ?? 'Sin categoría';
}
