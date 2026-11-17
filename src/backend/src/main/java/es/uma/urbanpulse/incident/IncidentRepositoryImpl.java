package es.uma.urbanpulse.incident;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import es.uma.urbanpulse.auth.TokenSubject;
import es.uma.urbanpulse.incident.api.IncidentCursor;
import es.uma.urbanpulse.incident.api.IncidentQueryFilters;
import es.uma.urbanpulse.user.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class IncidentRepositoryImpl implements IncidentCursorRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public List<Incident> findNextPage(IncidentQueryFilters filters, TokenSubject caller, IncidentCursor cursor,
			String sortBy, String sortDirection, int limit) {
		boolean ascending = sortDirection.equals("asc");
		String direction = ascending ? "ASC" : "DESC";
		String rankColumn = ascending ? "priority_rank" : "priority_rank_desc";
		StringBuilder sql = new StringBuilder("SELECT incident.* FROM \"Incident\" incident WHERE TRUE");
		Map<String, Object> parameters = new HashMap<>();

		if (caller.role() == Role.CITIZEN) {
			appendEquals(sql, parameters, "reporter_id", "callerId", caller.userId());
		}
		else if (caller.role() == Role.TECHNICIAN) {
			appendEquals(sql, parameters, "assignee_id", "callerId", caller.userId());
		}
		if (filters.getStatus() != null) {
			appendEquals(sql, parameters, "status_id", "statusId", filters.getStatus().getId());
		}
		if (filters.getCategoryId() != null) {
			appendEquals(sql, parameters, "category_id", "categoryId", filters.getCategoryId());
		}
		else if (filters.isUncategorized()) {
			sql.append(" AND incident.\"category_id\" IS NULL");
		}
		if (filters.getPriority() != null) {
			appendEquals(sql, parameters, "priority", "priority", filters.getPriority().name());
		}
		if (filters.getCreatedAfter() != null) {
			appendComparison(sql, parameters, "created_at", "createdAfter", ">=", filters.getCreatedAfter());
		}
		if (filters.getCreatedBefore() != null) {
			appendComparison(sql, parameters, "created_at", "createdBefore", "<", filters.getCreatedBefore());
		}

		if (cursor != null) {
			String comparison = ascending ? ">" : "<";
			if (sortBy.equals("priority")) {
				sql.append(" AND (incident.\"").append(rankColumn)
						.append("\", incident.\"created_at\", incident.\"id\") ")
						.append(comparison).append(" (:cursorPriorityRank, :cursorCreatedAt, :cursorId)");
				parameters.put("cursorPriorityRank", cursor.priorityRank());
				parameters.put("cursorCreatedAt", cursor.createdAt());
				parameters.put("cursorId", cursor.id());
			}
			else {
				sql.append(" AND (incident.\"created_at\", incident.\"id\") ")
						.append(comparison).append(" (:cursorCreatedAt, :cursorId)");
				parameters.put("cursorCreatedAt", cursor.createdAt());
				parameters.put("cursorId", cursor.id());
			}
		}

		sql.append(" ORDER BY ");
		if (sortBy.equals("priority")) {
			sql.append("incident.\"").append(rankColumn).append("\" ").append(direction).append(", ");
		}
		sql.append("incident.\"created_at\" ").append(direction)
				.append(", incident.\"id\" ").append(direction);

		Query query = entityManager.createNativeQuery(sql.toString(), Incident.class);
		parameters.forEach(query::setParameter);
		@SuppressWarnings("unchecked")
		List<Incident> incidents = query.setMaxResults(limit).getResultList();
		return fetchRelationsInQueryOrder(incidents);
	}

	private static void appendEquals(StringBuilder sql, Map<String, Object> parameters,
			String column, String parameter, Object value) {
		appendComparison(sql, parameters, column, parameter, "=", value);
	}

	private static void appendComparison(StringBuilder sql, Map<String, Object> parameters,
			String column, String parameter, String comparison, Object value) {
		sql.append(" AND incident.\"").append(column).append("\" ").append(comparison)
				.append(" :").append(parameter);
		parameters.put(parameter, value);
	}

	private List<Incident> fetchRelationsInQueryOrder(List<Incident> incidents) {
		if (incidents.isEmpty()) {
			return incidents;
		}
		List<Integer> ids = incidents.stream().map(Incident::getId).toList();
		Map<Integer, Incident> originallySelected = new HashMap<>();
		incidents.forEach(incident -> originallySelected.put(incident.getId(), incident));
		List<Incident> hydrated = entityManager.createQuery("""
				SELECT incident FROM Incident incident
				LEFT JOIN FETCH incident.category
				LEFT JOIN FETCH incident.assignee
				WHERE incident.id IN :ids
				""", Incident.class)
				.setParameter("ids", ids)
				.getResultList();
		Map<Integer, Incident> byId = new HashMap<>();
		hydrated.forEach(incident -> byId.put(incident.getId(), incident));
		return ids.stream().map(id -> byId.getOrDefault(id, originallySelected.get(id))).toList();
	}
}
