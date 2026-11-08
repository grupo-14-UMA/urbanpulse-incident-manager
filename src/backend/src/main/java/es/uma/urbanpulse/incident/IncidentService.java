package es.uma.urbanpulse.incident;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.uma.urbanpulse.category.Category;
import es.uma.urbanpulse.category.CategoryRepository;
import es.uma.urbanpulse.incident.api.CreateIncidentRequest;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentResponse;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;
import es.uma.urbanpulse.user.UserRepository;

@Service
public class IncidentService {

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
	public List<IncidentResponse> findAll() {
		return incidentRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(IncidentService::toResponse)
				.toList();
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
}
