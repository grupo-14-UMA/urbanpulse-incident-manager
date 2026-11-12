package es.uma.urbanpulse.incident;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import es.uma.urbanpulse.auth.TokenSubject;
import es.uma.urbanpulse.incident.api.IncidentQueryParameters;
import es.uma.urbanpulse.user.Role;
import jakarta.persistence.criteria.Predicate;

/** Builds the role scope and optional filters for a bounded page query. */
final class IncidentListSpecification {

	private IncidentListSpecification() {
	}

	static Specification<Incident> forQuery(IncidentQueryParameters query, TokenSubject caller) {
		return (root, criteriaQuery, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (caller.role() == Role.CITIZEN) {
				predicates.add(builder.equal(root.get("reporter").get("id"), caller.userId()));
			}
			else if (caller.role() == Role.TECHNICIAN) {
				predicates.add(builder.equal(root.get("assignee").get("id"), caller.userId()));
			}
			if (query.getStatus() != null) {
				predicates.add(builder.equal(root.get("status"), query.getStatus()));
			}
			if (query.getCategoryId() != null) {
				predicates.add(builder.equal(root.get("category").get("id"), query.getCategoryId()));
			}
			else if (query.isUncategorized()) {
				predicates.add(builder.isNull(root.get("category")));
			}
			if (query.getPriority() != null) {
				predicates.add(builder.equal(root.get("priority"), query.getPriority()));
			}
			if (query.getCreatedAfter() != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.getCreatedAfter()));
			}
			if (query.getCreatedBefore() != null) {
				predicates.add(builder.lessThan(root.get("createdAt"), query.getCreatedBefore()));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
