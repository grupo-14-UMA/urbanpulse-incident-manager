import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import { getCategories, getIncidentPage } from '../api.js';

const PAGE_SIZE = 6;

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

const CATEGORY_LABELS = {
  alumbrado: 'Alumbrado',
  limpieza: 'Limpieza',
  'mobiliario urbano': 'Mobiliario urbano',
  'tráfico y semáforos': 'Tráfico y semáforos',
  arbolado: 'Arbolado',
  otros: 'Otros',
};

const DATE_FORMAT = new Intl.DateTimeFormat('es-ES', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const EMPTY_FILTERS = { status: '', categoryId: '', priority: '', dateFrom: '', dateTo: '' };

function formatDate(value) {
  return DATE_FORMAT.format(new Date(value));
}

function startOfLocalDate(date) {
  return date ? new Date(`${date}T00:00:00`).toISOString() : '';
}

function endOfLocalDateExclusive(date) {
  if (!date) return '';
  const nextDay = new Date(`${date}T00:00:00`);
  nextDay.setDate(nextDay.getDate() + 1);
  return nextDay.toISOString();
}

export default function IncidentListPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [sort, setSort] = useState('date-desc');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState({ incidents: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [categoryError, setCategoryError] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const requestParameters = useMemo(() => {
    const [sortBy, sortDirection] = sort.split('-');
    return {
      page,
      size: PAGE_SIZE,
      sortBy: sortBy === 'date' ? 'createdAt' : 'priority',
      sortDirection,
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.categoryId === 'uncategorized'
        ? { uncategorized: true }
        : filters.categoryId ? { categoryId: filters.categoryId } : {}),
      ...(filters.priority ? { priority: filters.priority } : {}),
      ...(filters.dateFrom ? { createdAfter: startOfLocalDate(filters.dateFrom) } : {}),
      ...(filters.dateTo ? { createdBefore: endOfLocalDateExclusive(filters.dateTo) } : {}),
    };
  }, [filters, page, sort]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getIncidentPage(requestParameters, { signal: controller.signal })
      .then((response) => setResult(response))
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setError(requestError?.message || 'No se pudo cargar la lista. Inténtalo de nuevo.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [requestParameters, reloadKey]);

  useEffect(() => {
    const controller = new AbortController();
    getCategories({ signal: controller.signal })
      .then(setCategoryOptions)
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setCategoryError(requestError?.message || 'No se pudo cargar el catálogo de categorías.');
        }
      });
    return () => controller.abort();
  }, []);

  const categories = useMemo(
    () => [...categoryOptions].sort((left, right) => left.name.localeCompare(right.name, 'es')),
    [categoryOptions],
  );
  const firstItem = result.totalElements === 0 ? 0 : result.page * PAGE_SIZE + 1;
  const lastItem = Math.min((result.page + 1) * PAGE_SIZE, result.totalElements);

  function updateFilter(event) {
    const { name, value } = event.target;
    setFilters((current) => ({ ...current, [name]: value }));
    setPage(0);
  }

  function changeSort(event) {
    setSort(event.target.value);
    setPage(0);
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS);
    setSort('date-desc');
    setPage(0);
  }

  return (
    <section aria-labelledby="incidents-heading" className="incident-list-page">
      <div className="list-heading-row">
        <div>
          <p className="section-kicker">BANDEJA DE TRABAJO</p>
          <h2 id="incidents-heading">Incidencias</h2>
          <p className="list-description">Consulta y filtra las incidencias registradas en Málaga.</p>
        </div>
        <span className="list-source-note">Resultados paginados desde el backend</span>
      </div>

      {categoryError && <p className="list-limit-note" role="alert">{categoryError}</p>}

      <div aria-label="Filtros de incidencias" className="incident-filters">
        <label>
          Estado
          <select name="status" onChange={updateFilter} value={filters.status}>
            <option value="">Todos</option>
            {Object.entries(STATUS_LABELS).map(([status, label]) => (
              <option key={status} value={status}>{label}</option>
            ))}
          </select>
        </label>
        <label>
          Categoría
          <select name="categoryId" onChange={updateFilter} value={filters.categoryId}>
            <option value="">Todas</option>
            <option value="uncategorized">Sin categoría</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {CATEGORY_LABELS[category.name.toLowerCase()] || category.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Prioridad
          <select name="priority" onChange={updateFilter} value={filters.priority}>
            <option value="">Todas</option>
            {Object.entries(PRIORITY_LABELS).map(([priority, label]) => (
              <option key={priority} value={priority}>{label}</option>
            ))}
          </select>
        </label>
        <label>
          Desde
          <input max={filters.dateTo || undefined} name="dateFrom" onChange={updateFilter} type="date" value={filters.dateFrom} />
        </label>
        <label>
          Hasta
          <input min={filters.dateFrom || undefined} name="dateTo" onChange={updateFilter} type="date" value={filters.dateTo} />
        </label>
        <label className="sort-filter">
          Ordenar por
          <select onChange={changeSort} value={sort}>
            <option value="date-desc">Más recientes</option>
            <option value="date-asc">Más antiguas</option>
            <option value="priority-desc">Prioridad más alta</option>
            <option value="priority-asc">Prioridad más baja</option>
          </select>
        </label>
        <button
          className="clear-filters"
          disabled={Object.values(filters).every((value) => !value) && sort === 'date-desc'}
          onClick={clearFilters}
          type="button"
        >
          Limpiar filtros
        </button>
      </div>

      <div className="incident-table-card">
        <div className="table-summary" aria-live="polite">
          {loading ? 'Cargando incidencias…' : `${result.totalElements} incidencias · ${firstItem}–${lastItem}`}
        </div>

        {error ? (
          <div className="list-error" role="alert">
            <p>{error}</p>
            <button className="auth-secondary" onClick={() => setReloadKey((current) => current + 1)} type="button">Reintentar</button>
          </div>
        ) : loading && result.totalElements === 0 ? (
          <div className="list-state" role="status">
            <span aria-hidden="true" className="auth-spinner" />
            <p>Cargando incidencias…</p>
          </div>
        ) : result.totalElements === 0 ? (
          <div className="list-state" role="status">
            <span aria-hidden="true" className="placeholder-icon">⌕</span>
            <strong>No hay incidencias con esos filtros</strong>
            <p>Prueba a cambiar los filtros o a seleccionar otro rango de fechas.</p>
          </div>
        ) : (
          <div className="incident-table-scroll">
            <table className="incident-table">
              <thead>
                <tr>
                  <th scope="col">Incidencia</th>
                  <th scope="col">Categoría</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Prioridad</th>
                  <th scope="col">Fecha de registro</th>
                  <th scope="col"><span className="visually-hidden">Acciones</span></th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {result.incidents.map((incident) => (
                  <tr className="incident-table-row" key={incident.id}>
                    <td>
                      <Link className="incident-title-link" to={`/incidents/${incident.id}`}>
                        {incident.title}
                      </Link>
                      <small>#{incident.id.toString().padStart(4, '0')}</small>
                    </td>
                    <td>{CATEGORY_LABELS[incident.categoryName] || incident.categoryName || 'Sin categoría'}</td>
                    <td><span className={`incident-status status-${incident.status.toLowerCase()}`}>{STATUS_LABELS[incident.status] || incident.status}</span></td>
                    <td>{PRIORITY_LABELS[incident.priority] || 'Sin asignar'}</td>
                    <td>{formatDate(incident.createdAt)}</td>
                    <td>
                      <Link className="incident-detail-button" to={`/incidents/${incident.id}`}>
                        Ver detalle
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {result.totalElements > 0 && !error && (
          <nav aria-label="Paginación de incidencias" className="list-pagination">
            <span>Página {result.page + 1} de {result.totalPages}</span>
            <div>
              <button aria-label="Página anterior" disabled={loading || result.page <= 0} onClick={() => setPage((current) => Math.max(0, current - 1))} type="button">← Anterior</button>
              <button aria-label="Página siguiente" disabled={loading || result.page + 1 >= result.totalPages} onClick={() => setPage((current) => current + 1)} type="button">Siguiente →</button>
            </div>
          </nav>
        )}
      </div>
    </section>
  );
}
