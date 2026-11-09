const CATEGORIES = [
  'Alumbrado',
  'Limpieza',
  'Mobiliario urbano',
  'Tráfico y semáforos',
  'Arbolado',
];

const INCIDENT_SEEDS = [
  ['Farola fundida en calle Larios', 'Alumbrado', 'REPORTED', 'HIGH'],
  ['Contenedor desbordado en El Perchel', 'Limpieza', 'VALIDATED', 'MEDIUM'],
  ['Semáforo apagado en avenida de Andalucía', 'Tráfico y semáforos', 'IN_PROGRESS', 'URGENT'],
  ['Banco roto en el paseo de La Malagueta', 'Mobiliario urbano', 'RESOLVED', 'LOW'],
  ['Rama caída en calle Victoria', 'Arbolado', 'ASSIGNED', 'HIGH'],
  ['Acera con residuos en Huelin', 'Limpieza', 'REPORTED', 'LOW'],
  ['Farola parpadeante en Teatinos', 'Alumbrado', 'VALIDATED', 'URGENT'],
  ['Papelera dañada en plaza de la Merced', 'Mobiliario urbano', 'IN_PROGRESS', 'MEDIUM'],
  ['Señal caída junto al puente de Tetuán', 'Tráfico y semáforos', 'REJECTED', 'LOW'],
  ['Árbol con ramas sobre la calzada', 'Arbolado', 'REPORTED', 'HIGH'],
  ['Contenedor sin tapa en El Palo', 'Limpieza', 'ASSIGNED', 'MEDIUM'],
  ['Paso de peatones con poca iluminación', 'Alumbrado', 'RESOLVED', 'URGENT'],
  ['Bolardo suelto en calle Granada', 'Mobiliario urbano', 'VALIDATED', 'LOW'],
  ['Semáforo peatonal averiado en avenida Velázquez', 'Tráfico y semáforos', 'REPORTED', 'URGENT'],
  ['Riego averiado en el parque de Málaga', 'Arbolado', 'IN_PROGRESS', 'MEDIUM'],
  ['Acumulación de basura en calle Carretería', 'Limpieza', 'RESOLVED', 'HIGH'],
  ['Farola sin encender en Cruz de Humilladero', 'Alumbrado', 'ASSIGNED', 'MEDIUM'],
  ['Banco con una pata suelta en el parque Huelin', 'Mobiliario urbano', 'REPORTED', 'LOW'],
];

const MOCK_INCIDENTS = INCIDENT_SEEDS.map(([title, categoryName, status, priority], index) => {
  const date = new Date(Date.UTC(2026, 10, 8 - index, 8 + (index % 10), 15));
  return {
    id: index + 1,
    title,
    categoryName,
    status,
    priority,
    createdAt: date.toISOString(),
  };
});

export const INCIDENT_CATEGORIES = CATEGORIES;
export const INCIDENT_STATUSES = ['REPORTED', 'VALIDATED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'];
export const INCIDENT_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

const PRIORITY_RANK = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };

/**
 * Local stand-in for GET /api/incidents. Replace this boundary with the
 * paginated API from issue #55 when it is ready.
 */
export async function loadMockIncidentPage({ filters, page, pageSize, sort }) {
  await new Promise((resolve) => window.setTimeout(resolve, 120));

  const filtered = MOCK_INCIDENTS.filter((incident) => {
    const createdDate = incident.createdAt.slice(0, 10);
    return (!filters.status || incident.status === filters.status)
      && (!filters.category || incident.categoryName === filters.category)
      && (!filters.priority || incident.priority === filters.priority)
      && (!filters.dateFrom || createdDate >= filters.dateFrom)
      && (!filters.dateTo || createdDate <= filters.dateTo);
  });

  filtered.sort((left, right) => {
    if (sort === 'priority-desc') {
      return PRIORITY_RANK[right.priority] - PRIORITY_RANK[left.priority]
        || right.createdAt.localeCompare(left.createdAt);
    }
    if (sort === 'priority-asc') {
      return PRIORITY_RANK[left.priority] - PRIORITY_RANK[right.priority]
        || right.createdAt.localeCompare(left.createdAt);
    }
    return sort === 'date-asc'
      ? left.createdAt.localeCompare(right.createdAt)
      : right.createdAt.localeCompare(left.createdAt);
  });

  const total = filtered.length;
  const pageCount = Math.ceil(total / pageSize);
  const safePage = Math.min(page, Math.max(pageCount, 1));
  const start = (safePage - 1) * pageSize;

  return {
    items: filtered.slice(start, start + pageSize),
    total,
    page: safePage,
    pageSize,
    pageCount,
  };
}
