package es.uma.urbanpulse.incident;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import es.uma.urbanpulse.auth.TokenSubject;
import es.uma.urbanpulse.category.Category;
import es.uma.urbanpulse.category.CategoryRepository;
import es.uma.urbanpulse.incident.api.AssignIncidentRequest;
import es.uma.urbanpulse.incident.api.ChangePriorityRequest;
import es.uma.urbanpulse.incident.api.CreateIncidentRequest;
import es.uma.urbanpulse.incident.api.IncidentAccessDeniedException;
import es.uma.urbanpulse.incident.api.IncidentBounds;
import es.uma.urbanpulse.incident.api.IncidentMapResponse;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentPageResponse;
import es.uma.urbanpulse.incident.api.IncidentPhotoNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentPhotoResponse;
import es.uma.urbanpulse.incident.api.IncidentQueryParameters;
import es.uma.urbanpulse.incident.api.IncidentResponse;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;
import es.uma.urbanpulse.incident.api.InvalidIncidentPhotoException;
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
	static final int MAX_PHOTOS_PER_INCIDENT = 3;

	private static final GeometryFactory WGS84_GEOMETRY_FACTORY =
			new GeometryFactory(new PrecisionModel(), 4326);

	private final IncidentRepository incidentRepository;
	private final AttachmentRepository attachmentRepository;
	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;
	private final StatusChangeRepository statusChangeRepository;
	private final PhotoStorage photoStorage;

	public IncidentService(
			IncidentRepository incidentRepository,
			AttachmentRepository attachmentRepository,
			CategoryRepository categoryRepository,
			UserRepository userRepository,
			StatusChangeRepository statusChangeRepository,
			PhotoStorage photoStorage) {
		this.incidentRepository = incidentRepository;
		this.attachmentRepository = attachmentRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.statusChangeRepository = statusChangeRepository;
		this.photoStorage = photoStorage;
	}

	/** Registers an incident reported by the given citizen, taken from the token. */
	@Transactional
	public IncidentResponse create(CreateIncidentRequest request, TokenSubject caller) {
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
				userRepository.getReferenceById(caller.userId()));

		Incident saved = incidentRepository.save(incident);
		statusChangeRepository.save(new StatusChange(
				saved, null, IncidentStatus.REPORTED, saved.getReporter(), saved.getCreatedAt(), null));
		return toResponse(saved, caller);
	}

	@Transactional(readOnly = true)
	public IncidentMapResponse findWithinBounds(IncidentQueryParameters query, TokenSubject caller) {
		IncidentBounds bounds = query.toBounds();
		long queryStartedAt = System.nanoTime();
		List<IncidentMapProjection> candidates = caller.role() == Role.CITIZEN
				? incidentRepository.findWithinBoundsForReporter(
						bounds.west(), bounds.south(), bounds.east(), bounds.north(),
						IncidentStatus.activeIds(), caller.userId(), statusId(query), query.getCategoryId(),
						query.isUncategorized(), priorityName(query), query.getCreatedAfter(), query.getCreatedBefore(),
						MAP_RESULT_LIMIT + 1)
				: incidentRepository.findWithinBounds(
						bounds.west(), bounds.south(), bounds.east(), bounds.north(),
						IncidentStatus.activeIds(), statusId(query), query.getCategoryId(), query.isUncategorized(),
						priorityName(query), query.getCreatedAfter(), query.getCreatedBefore(), MAP_RESULT_LIMIT + 1);
		long queryDurationMs = (System.nanoTime() - queryStartedAt) / 1_000_000;
		boolean truncated = candidates.size() > MAP_RESULT_LIMIT;
		List<IncidentResponse> incidents = candidates.stream()
				.limit(MAP_RESULT_LIMIT)
				.map(IncidentService::toResponse)
				.toList();
		LOGGER.info("Loaded {} map incidents for role {} (truncated={}) in {} ms",
				incidents.size(), caller.role(), truncated, queryDurationMs);
		return new IncidentMapResponse(incidents, truncated);
	}

	/** Returns one bounded page, scoped to the caller's role before optional filters are applied. */
	@Transactional(readOnly = true)
	public IncidentPageResponse findPage(IncidentQueryParameters query, TokenSubject caller) {
		long queryStartedAt = System.nanoTime();
		Sort.Direction direction = "asc".equals(query.getSortDirection())
				? Sort.Direction.ASC : Sort.Direction.DESC;
		Sort sort = "priority".equals(query.getSortBy())
				? Sort.by(new Sort.Order(direction, "priorityRank"),
						new Sort.Order(Sort.Direction.DESC, "createdAt"),
						new Sort.Order(Sort.Direction.DESC, "id"))
				: Sort.by(new Sort.Order(direction, "createdAt"),
						new Sort.Order(Sort.Direction.DESC, "id"));
		PageRequest pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
		Page<Incident> result = incidentRepository.findAll(IncidentListSpecification.forQuery(query, caller), pageable);
		List<IncidentResponse> incidents = result.getContent().stream()
				.map(incident -> toResponse(incident, caller))
				.toList();
		long queryDurationMs = (System.nanoTime() - queryStartedAt) / 1_000_000;
		LOGGER.info("Loaded incident page for role {} user {} (page={}, size={}, results={}, total={}) in {} ms",
				caller.role(), caller.userId(), query.getPage(), query.getSize(), incidents.size(),
				result.getTotalElements(), queryDurationMs);
		return new IncidentPageResponse(incidents, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	private static Integer statusId(IncidentQueryParameters query) {
		return query.getStatus() == null ? null : query.getStatus().getId();
	}

	private static String priorityName(IncidentQueryParameters query) {
		return query.getPriority() == null ? null : query.getPriority().name();
	}

	@Transactional(readOnly = true)
	public IncidentResponse findById(Integer id, TokenSubject caller) {
		Incident incident = load(id);
		verifyReadPermission(incident, caller);
		List<IncidentPhotoResponse> photos = attachmentRepository.findByIncidentIdOrderByIdAsc(id).stream()
				.map(attachment -> toPhotoResponse(id, attachment))
				.toList();
		return toResponse(incident, caller, photos);
	}

	@Transactional
	public List<IncidentPhotoResponse> uploadPhotos(
			Integer incidentId,
			List<MultipartFile> files,
			TokenSubject caller) {
		Incident incident = incidentRepository.findByIdForUpdate(incidentId)
				.orElseThrow(() -> new IncidentNotFoundException(incidentId));
		verifyUploadPermission(incident, caller);

		if (files == null || files.isEmpty()) {
			throw new InvalidIncidentPhotoException("Debes adjuntar al menos una fotografía.");
		}
		long existingPhotos = attachmentRepository.countByIncidentId(incidentId);
		if (existingPhotos + files.size() > MAX_PHOTOS_PER_INCIDENT) {
			throw new InvalidIncidentPhotoException(
					"No se pueden adjuntar más de " + MAX_PHOTOS_PER_INCIDENT + " fotografías a una incidencia.");
		}

		List<ValidatedUpload> validatedUploads = new ArrayList<>(files.size());
		for (MultipartFile file : files) {
			if (file == null) {
				throw new InvalidIncidentPhotoException("Debes adjuntar al menos una fotografía.");
			}
			byte[] bytes = readBytes(file);
			PhotoFormat format = PhotoFormat.validate(file.getContentType(), bytes);
			validatedUploads.add(new ValidatedUpload(bytes, format));
		}

		List<String> storedKeys = new ArrayList<>(validatedUploads.size());
		List<IncidentPhotoResponse> responses = new ArrayList<>(validatedUploads.size());
		try {
			for (ValidatedUpload upload : validatedUploads) {
				String storageKey = "incidents/" + incidentId + "/" + UUID.randomUUID() + upload.format().extension();
				photoStorage.store(storageKey, upload.bytes(), upload.format().mediaType());
				storedKeys.add(storageKey);

				Attachment saved = attachmentRepository.saveAndFlush(new Attachment(
						incident,
						storageKey,
						upload.format().mediaType(),
						upload.bytes().length));
				LOGGER.info("Stored incident photo: incidentId={}, photoId={}, contentType={}, sizeBytes={}",
						incidentId, saved.getId(), saved.getFileType(), saved.getFileSize());
				responses.add(toPhotoResponse(incidentId, saved));
			}
		}
		catch (RuntimeException exception) {
			for (String key : storedKeys) {
				photoStorage.delete(key);
			}
			throw exception;
		}
		return responses;
	}

	@Transactional(readOnly = true)
	public StoredPhotoContent loadPhoto(Integer incidentId, Integer photoId, TokenSubject caller) {
		Incident incident = incidentRepository.findById(incidentId)
				.orElseThrow(() -> new IncidentNotFoundException(incidentId));
		verifyReadPermission(incident, caller);
		Attachment attachment = attachmentRepository.findByIdAndIncidentId(photoId, incidentId)
				.orElseThrow(() -> new IncidentPhotoNotFoundException(incidentId, photoId));
		try {
			byte[] content = photoStorage.read(attachment.getStorageKey());
			return new StoredPhotoContent(content, attachment.getFileType());
		}
		catch (UncheckedIOException exception) {
			if (exception.getCause() instanceof NoSuchFileException) {
				throw new IncidentPhotoNotFoundException(incidentId, photoId);
			}
			throw exception;
		}
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
		return toResponse(incident, caller, List.of());
	}

	private static IncidentResponse toResponse(
			Incident incident, TokenSubject caller, List<IncidentPhotoResponse> photos) {
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
				assignee == null ? null : TechnicianResponse.from(assignee),
				photos);
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
				null,
				List.of());
	}

	private static void verifyUploadPermission(Incident incident, TokenSubject caller) {
		if (caller.role() == Role.CITIZEN) {
			if (incident.getReporter() == null || !incident.getReporter().getId().equals(caller.userId())) {
				throw new AccessDeniedException("Citizen is not the reporter of incident " + incident.getId());
			}
			return;
		}
		if (caller.role() == Role.TECHNICIAN) {
			return;
		}
		throw new AccessDeniedException("Role " + caller.role() + " cannot upload incident photos");
	}

	private static void verifyReadPermission(Incident incident, TokenSubject caller) {
		if (caller.role() == Role.CITIZEN
				&& (incident.getReporter() == null || !incident.getReporter().getId().equals(caller.userId()))) {
			throw new AccessDeniedException("Citizen is not the reporter of incident " + incident.getId());
		}
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Could not read uploaded photo bytes", exception);
		}
	}

	private static IncidentPhotoResponse toPhotoResponse(Integer incidentId, Attachment attachment) {
		return new IncidentPhotoResponse(
				attachment.getId(),
				"/api/incidents/" + incidentId + "/photos/" + attachment.getId(),
				attachment.getFileType(),
				attachment.getFileSize(),
				attachment.getCreatedAt());
	}

	private record ValidatedUpload(byte[] bytes, PhotoFormat format) {
	}
}
