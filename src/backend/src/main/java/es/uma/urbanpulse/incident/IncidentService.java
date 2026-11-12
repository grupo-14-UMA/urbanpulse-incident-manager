package es.uma.urbanpulse.incident;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.uma.urbanpulse.auth.TokenSubject;
import es.uma.urbanpulse.category.Category;
import es.uma.urbanpulse.category.CategoryRepository;
import es.uma.urbanpulse.incident.api.CreateIncidentRequest;
import es.uma.urbanpulse.incident.api.AssignIncidentRequest;
import es.uma.urbanpulse.incident.api.ChangePriorityRequest;
import es.uma.urbanpulse.incident.api.IncidentAccessDeniedException;
import es.uma.urbanpulse.incident.api.IncidentBounds;
import es.uma.urbanpulse.incident.api.IncidentMapResponse;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentResponse;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;
import es.uma.urbanpulse.incident.api.InvalidIncidentRequestException;
import es.uma.urbanpulse.incident.api.RejectIncidentRequest;
import es.uma.urbanpulse.incident.api.ResolveIncidentRequest;
import es.uma.urbanpulse.incident.api.StatusChangeResponse;
import es.uma.urbanpulse.incident.api.ValidateIncidentRequest;
import es.uma.urbanpulse.user.Role;
import es.uma.urbanpulse.user.TechnicianResponse;
import es.uma.urbanpulse.user.User;
import es.uma.urbanpulse.user.UserRepository;

@Service
public class IncidentService {

	private static final Logger LOGGER = LoggerFactory.getLogger(IncidentService.class);
	private static final int MAP_RESULT_LIMIT = 500;

	private static final GeometryFactory WGS84_GEOMETRY_FACTORY =
			new GeometryFactory(new PrecisionModel(), 4326);

	private final IncidentRepository incidentRepository;
	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;
	private final StatusChangeRepository statusChangeRepository;

	public IncidentService(IncidentRepository incidentRepository, CategoryRepository categoryRepository,
			UserRepository userRepository, StatusChangeRepository statusChangeRepository) {
		this.incidentRepository = incidentRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.statusChangeRepository = statusChangeRepository;
	}

	/** Registers an incident reported by the given citizen, taken from the token. */
	@Transactional
	public IncidentResponse create(CreateIncidentRequest request, TokenSubject caller) {
		Integer reporterId = caller.userId();
		Category category = request.categoryId() == null
				? null
				: categoryRepository.findById(request.categoryId())
						.orElseThrow(() -> new InvalidCategoryException(request.categoryId()));

		Point location = WGS84_GEOMETRY_FACTORY.createPoint(
				new Coordinate(request.longitude(), request.latitude()));
		Incident incident = new Incident(
				request.title().trim(),
				request.description().trim(),
				category,
				location,
				userRepository.getReferenceById(reporterId));

		Incident saved = incidentRepository.save(incident);
		statusChangeRepository.save(new StatusChange(
				saved, null, IncidentStatus.REPORTED, incident.getReporter(), saved.getCreatedAt(), null));
		return toResponse(saved, caller);
	}

	@Transactional(readOnly = true)
	public IncidentMapResponse findWithinBounds(IncidentBounds bounds) {
		long queryStartedAt = System.nanoTime();
		List<IncidentMapProjection> candidates = incidentRepository.findWithinBounds(
				bounds.west(), bounds.south(), bounds.east(), bounds.north(),
				IncidentStatus.activeIds(), MAP_RESULT_LIMIT + 1);
		long queryDurationMs = (System.nanoTime() - queryStartedAt) / 1_000_000;
		boolean truncated = candidates.size() > MAP_RESULT_LIMIT;
		List<IncidentResponse> incidents = candidates.stream()
				.limit(MAP_RESULT_LIMIT)
				.map(IncidentService::toResponse)
				.toList();
		LOGGER.info("Loaded {} public map incidents (truncated={}) in {} ms",
				incidents.size(), truncated, queryDurationMs);
		return new IncidentMapResponse(incidents, truncated);
	}

	@Transactional(readOnly = true)
	public IncidentResponse findById(Integer id, TokenSubject caller) {
		return toResponse(load(id), caller);
	}

	/** REPORTED to VALIDATED, setting the priority in the same step when given. */
	@Transactional
	public IncidentResponse validate(Integer id, ValidateIncidentRequest request, TokenSubject caller) {
		String justification = blankToNull(request.priorityJustification());
		// A body error, so it comes before the 404.
		if (justification != null && request.priority() == null) {
			throw new InvalidIncidentRequestException("priority", "Indica la prioridad si añades una justificación.");
		}
		Incident incident = load(id);
		IncidentStatus previous = incident.getStatus();
		incident.validate(request.priority(), justification);
		return recordChange(incident, previous, null, caller);
	}

	@Transactional
	public IncidentResponse reject(Integer id, RejectIncidentRequest request, TokenSubject caller) {
		Incident incident = load(id);
		IncidentStatus previous = incident.getStatus();
		incident.reject();
		return recordChange(incident, previous, request.reason().trim(), caller);
	}

	@Transactional
	public IncidentResponse assign(Integer id, AssignIncidentRequest request, TokenSubject caller) {
		Incident incident = load(id);
		incident.requireAssignable();
		User technician = userRepository.findById(request.technicianId())
				.filter(user -> user.getRole() == Role.TECHNICIAN)
				.orElseThrow(() -> new InvalidIncidentRequestException("technicianId", "El técnico elegido no existe."));
		IncidentStatus previous = incident.getStatus();
		incident.assign(technician);
		return recordChange(incident, previous, null, caller);
	}

	@Transactional
	public IncidentResponse start(Integer id, TokenSubject caller) {
		Incident incident = load(id);
		requireAssignee(incident, caller);
		IncidentStatus previous = incident.getStatus();
		incident.start();
		return recordChange(incident, previous, null, caller);
	}

	@Transactional
	public IncidentResponse resolve(Integer id, ResolveIncidentRequest request, TokenSubject caller) {
		Incident incident = load(id);
		requireAssignee(incident, caller);
		IncidentStatus previous = incident.getStatus();
		incident.resolve();
		return recordChange(incident, previous, request.note().trim(), caller);
	}

	/** Changes the priority of accepted, open work. Only status changes are audited, so no history entry. */
	@Transactional
	public IncidentResponse changePriority(Integer id, ChangePriorityRequest request, TokenSubject caller) {
		Incident incident = load(id);
		IncidentPriority previous = incident.getPriority();
		incident.changePriority(request.priority(), blankToNull(request.justification()));
		LOGGER.info("Incident {} priority {} -> {} by role {} user {}",
				incident.getId(), previous, incident.getPriority(), caller.role(), caller.userId());
		return toResponse(incident, caller);
	}

	/** The history of an incident, oldest first. Never exposes who made a change, only their role. */
	@Transactional(readOnly = true)
	public List<StatusChangeResponse> findHistory(Integer id, TokenSubject caller) {
		Incident incident = incidentRepository.findById(id)
				.orElseThrow(() -> new IncidentNotFoundException(id));
		boolean allowed = switch (caller.role()) {
			case OPERATOR -> true;
			case TECHNICIAN -> isUser(incident.getAssignee(), caller);
			case CITIZEN -> isUser(incident.getReporter(), caller);
		};
		if (!allowed) {
			throw new IncidentAccessDeniedException();
		}
		return statusChangeRepository.findByIncidentIdOrderByChangedAtAscIdAsc(id).stream()
				.map(change -> new StatusChangeResponse(
						change.getPreviousStatus(),
						change.getNewStatus(),
						change.getChangedAt(),
						change.getChangedBy() == null ? null : change.getChangedBy().getRole(),
						change.getNote()))
				.toList();
	}

	private Incident load(Integer id) {
		return incidentRepository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
	}

	private static void requireAssignee(Incident incident, TokenSubject caller) {
		if (!isUser(incident.getAssignee(), caller)) {
			throw new IncidentAccessDeniedException();
		}
	}

	private static boolean isUser(User user, TokenSubject caller) {
		return user != null && user.getId().equals(caller.userId());
	}

	private static String blankToNull(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return text.trim();
	}

	private IncidentResponse recordChange(Incident incident, IncidentStatus previous, String note,
			TokenSubject caller) {
		statusChangeRepository.save(new StatusChange(incident, previous, incident.getStatus(),
				userRepository.getReferenceById(caller.userId()), Instant.now().truncatedTo(ChronoUnit.MILLIS), note));
		LOGGER.info("Incident {} status {} -> {} by role {} user {}",
				incident.getId(), previous, incident.getStatus(), caller.role(), caller.userId());
		return toResponse(incident, caller);
	}

	/** The assignee is staff-only, like the reporter, which is never exposed. */
	private static IncidentResponse toResponse(Incident incident, TokenSubject caller) {
		Point location = incident.getLocation();
		Category category = incident.getCategory();
		User assignee = caller.role() == Role.CITIZEN ? null : incident.getAssignee();
		return new IncidentResponse(
				incident.getId(),
				incident.getTitle(),
				incident.getDescription(),
				location.getY(),
				location.getX(),
				category == null ? null : category.getId(),
				category == null ? null : category.getName(),
				incident.getStatus(),
				incident.getCreatedAt(),
				incident.getPriority(),
				incident.getPriorityJustification(),
				assignee == null ? null : TechnicianResponse.from(assignee));
	}

	private static IncidentResponse toResponse(IncidentMapProjection incident) {
		return new IncidentResponse(
				incident.getId(),
				incident.getTitle(),
				incident.getDescription(),
				incident.getLatitude(),
				incident.getLongitude(),
				incident.getCategoryId(),
				incident.getCategoryName(),
				IncidentStatus.fromId(incident.getStatusId()),
				incident.getCreatedAt(),
				incident.getPriority() == null ? null : IncidentPriority.valueOf(incident.getPriority()),
				null,
				null);
	}
}
