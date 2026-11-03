// Checks the new incident form. Returns one message per invalid field; an empty
// object means the form is valid. Pure, so it can be tested without rendering.
export function validateIncident({ title, description, location }) {
  const errors = {};

  if (!title || !title.trim()) {
    errors.title = 'El título es obligatorio.';
  }
  if (!description || !description.trim()) {
    errors.description = 'La descripción es obligatoria.';
  }
  if (!location) {
    errors.location = 'Marca en el mapa dónde está el problema.';
  }

  return errors;
}

export function isValid(errors) {
  return Object.keys(errors).length === 0;
}
