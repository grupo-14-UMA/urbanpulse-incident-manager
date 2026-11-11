import { getIncidentsWithinBounds } from './api.js';

// Temporary adapter for the map endpoint. Replace this file's API boundary and
// client-side filtering when the paginated incident endpoint from issue #55 is ready.
export const MALAGA_LIST_BOUNDS = {
  west: -4.65,
  south: 36.60,
  east: -4.25,
  north: 36.90,
};

export async function loadIncidentList({ signal } = {}) {
  return getIncidentsWithinBounds(MALAGA_LIST_BOUNDS, { signal });
}

export function getIncidentPage({ incidents, filters, page, pageSize, sort }) {
  const filtered = incidents.filter((incident) => {
    const createdDate = toLocalDateInputValue(incident.createdAt);
    return (!filters.status || incident.status === filters.status)
      && (!filters.category || incident.categoryName === filters.category)
      && (!filters.dateFrom || createdDate >= filters.dateFrom)
      && (!filters.dateTo || createdDate <= filters.dateTo);
  });

  filtered.sort((left, right) => sort === 'date-asc'
    ? left.createdAt.localeCompare(right.createdAt)
    : right.createdAt.localeCompare(left.createdAt));

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

function toLocalDateInputValue(value) {
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
