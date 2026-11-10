package es.uma.urbanpulse.incident;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import es.uma.urbanpulse.auth.TokenSubject;
import es.uma.urbanpulse.category.Category;
import es.uma.urbanpulse.category.CategoryRepository;
import es.uma.urbanpulse.incident.api.CreateIncidentRequest;
import es.uma.urbanpulse.incident.api.IncidentBounds;
import es.uma.urbanpulse.incident.api.IncidentMapResponse;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentPhotoNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentPhotoResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;
import es.uma.urbanpulse.incident.api.InvalidIncidentPhotoException;
import es.uma.urbanpulse.user.Role;
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
	private final PhotoStorage photoStorage;

	public IncidentService(
			IncidentRepository incidentRepository,
			AttachmentRepository attachmentRepository,
			CategoryRepository categoryRepository,
			UserRepository userRepository,
			PhotoStorage photoStorage) {
		this.incidentRepository = incidentRepository;
		this.attachmentRepository = attachmentRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.photoStorage = photoStorage;
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

		return toResponse(incidentRepository.save(incident), List.of());
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
		List<IncidentPhotoResponse> photos = attachmentRepository.findByIncidentIdOrderByIdAsc(id).stream()
				.map((attachment) -> toPhotoResponse(id, attachment))
				.toList();
		return toResponse(incident, photos);
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
	public StoredPhotoContent loadPhoto(Integer incidentId, Integer photoId) {
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

	private static void verifyUploadPermission(Incident incident, TokenSubject caller) {
		if (caller.role() != Role.CITIZEN
				|| incident.getReporter() == null
				|| !incident.getReporter().getId().equals(caller.userId())) {
			throw new AccessDeniedException("User is not the reporting citizen of incident " + incident.getId());
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

	private static IncidentResponse toResponse(Incident incident, List<IncidentPhotoResponse> photos) {
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
				incident.getCreatedAt(),
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
				incident.getCreatedAt());
	}

	private record ValidatedUpload(byte[] bytes, PhotoFormat format) {
	}

}
