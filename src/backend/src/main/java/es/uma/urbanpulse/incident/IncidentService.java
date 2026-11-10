package es.uma.urbanpulse.incident;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.uma.urbanpulse.category.Category;
import es.uma.urbanpulse.category.CategoryRepository;
import es.uma.urbanpulse.incident.api.CreateIncidentRequest;
import es.uma.urbanpulse.incident.api.IncidentBounds;
import es.uma.urbanpulse.incident.api.IncidentMapResponse;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentResponse;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;
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

	public IncidentService(IncidentRepository incidentRepository, CategoryRepository categoryRepository,
			UserRepository userRepository) {
		this.incidentRepository = incidentRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
	}

	/** Registers an incident reported by the given citizen, taken from the token. */
	@Transactional
	public IncidentResponse create(CreateIncidentRequest request, Integer reporterId) {
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

		return toResponse(incidentRepository.save(incident));
	}

	@Transactional(readOnly = true)
	public IncidentMapResponse findWithinBounds(IncidentBounds bounds) {
		long queryStartedAt = System.nanoTime();
		List<IncidentMapProjection> candidates = incidentRepository.findWithinBounds(
				bounds.west(), bounds.south(), bounds.east(), bounds.north(),
				IncidentStatus.REPORTED.getId(), MAP_RESULT_LIMIT + 1);
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
	public IncidentResponse findById(Integer id) {
		Incident incident = incidentRepository.findById(id)
				.orElseThrow(() -> new IncidentNotFoundException(id));
		return toResponse(incident);
	}

	private static IncidentResponse toResponse(Incident incident) {
		Point location = incident.getLocation();
		Category category = incident.getCategory();
		return new IncidentResponse(
				incident.getId(),
				incident.getTitle(),
				incident.getDescription(),
				location.getY(),
				location.getX(),
				category == null ? null : category.getId(),
				category == null ? null : category.getName(),
				incident.getStatus(),
				incident.getCreatedAt());
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
				incident.getCreatedAt());
	}
}
