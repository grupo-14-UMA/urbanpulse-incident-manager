package es.uma.urbanpulse.incident;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import es.uma.urbanpulse.incident.api.IncidentCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class IncidentRepositoryImpl implements IncidentCursorRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public List<Incident> findNextPage(Specification<Incident> specification, IncidentCursor cursor,
			String sortBy, String sortDirection, int limit) {
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Incident> query = builder.createQuery(Incident.class);
		Root<Incident> root = query.from(Incident.class);
		root.fetch("category", JoinType.LEFT);
		root.fetch("assignee", JoinType.LEFT);

		List<Predicate> predicates = new ArrayList<>();
		Predicate filters = specification.toPredicate(root, query, builder);
		if (filters != null) {
			predicates.add(filters);
		}
		if (cursor != null) {
			predicates.add(after(root, builder, cursor, sortBy));
		}
		query.where(builder.and(predicates.toArray(Predicate[]::new)));
		query.orderBy(orderBy(root, builder, sortBy, sortDirection));
		return entityManager.createQuery(query).setMaxResults(limit).getResultList();
	}

	private static Predicate after(Root<Incident> root, CriteriaBuilder builder,
			IncidentCursor cursor, String sortBy) {
		boolean ascending = cursor.sortDirection().equals("asc");
		Predicate tieBreak = integerAfter(builder, root.<Integer>get("id"), cursor.id(), ascending);
		Predicate sameCreatedAt = builder.equal(root.get("createdAt"), cursor.createdAt());
		Predicate createdAtAfter = instantAfter(builder, root.<Instant>get("createdAt"),
				cursor.createdAt(), ascending);
		Predicate afterCreatedAtOrId = builder.or(createdAtAfter,
				builder.and(sameCreatedAt, tieBreak));
		if (!sortBy.equals("priority")) {
			return afterCreatedAtOrId;
		}

		Predicate afterPriority = integerAfter(builder, root.<Integer>get("priorityRank"),
				cursor.priorityRank(), ascending);
		Predicate samePriority = builder.equal(root.get("priorityRank"), cursor.priorityRank());
		return builder.or(afterPriority, builder.and(samePriority, afterCreatedAtOrId));
	}

	private static Predicate instantAfter(CriteriaBuilder builder, Path<Instant> path,
			Instant value, boolean ascending) {
		return ascending ? builder.greaterThan(path, value) : builder.lessThan(path, value);
	}

	private static Predicate integerAfter(CriteriaBuilder builder, Path<Integer> path,
			Integer value, boolean ascending) {
		return ascending ? builder.greaterThan(path, value) : builder.lessThan(path, value);
	}

	private static List<Order> orderBy(Root<Incident> root, CriteriaBuilder builder,
			String sortBy, String sortDirection) {
		boolean ascending = sortDirection.equals("asc");
		List<Order> orders = new ArrayList<>();
		if (sortBy.equals("priority")) {
			orders.add(ascending ? builder.asc(root.get("priorityRank")) : builder.desc(root.get("priorityRank")));
		}
		orders.add(ascending ? builder.asc(root.get("createdAt")) : builder.desc(root.get("createdAt")));
		orders.add(ascending ? builder.asc(root.get("id")) : builder.desc(root.get("id")));
		return orders;
	}
}
