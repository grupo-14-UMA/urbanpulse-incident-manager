package es.uma.urbanpulse.incident;

import java.util.Locale;

import es.uma.urbanpulse.incident.api.InvalidIncidentPhotoException;

enum PhotoFormat {

	JPEG("image/jpeg", ".jpg") {
		@Override
		boolean matchesHeader(byte[] bytes) {
			return bytes.length >= 3
					&& (bytes[0] & 0xFF) == 0xFF
					&& (bytes[1] & 0xFF) == 0xD8
					&& (bytes[2] & 0xFF) == 0xFF;
		}
	},

	PNG("image/png", ".png") {
		@Override
		boolean matchesHeader(byte[] bytes) {
			return bytes.length >= 8
					&& (bytes[0] & 0xFF) == 0x89
					&& bytes[1] == 0x50
					&& bytes[2] == 0x4E
					&& bytes[3] == 0x47
					&& bytes[4] == 0x0D
					&& bytes[5] == 0x0A
					&& bytes[6] == 0x1A
					&& bytes[7] == 0x0A;
		}
	},

	WEBP("image/webp", ".webp") {
		@Override
		boolean matchesHeader(byte[] bytes) {
			return bytes.length >= 12
					&& bytes[0] == 'R'
					&& bytes[1] == 'I'
					&& bytes[2] == 'F'
					&& bytes[3] == 'F'
					&& bytes[8] == 'W'
					&& bytes[9] == 'E'
					&& bytes[10] == 'B'
					&& bytes[11] == 'P';
		}
	};

	static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

	static final String UNSUPPORTED_TYPE_MESSAGE =
			"Solo se permiten fotografías en formato JPEG, PNG o WebP.";

	static final String EMPTY_FILE_MESSAGE =
			"El archivo de la fotografía está vacío.";

	static final String MAX_SIZE_MESSAGE =
			"Cada fotografía puede ocupar como máximo 5 MB.";

	private final String mediaType;
	private final String extension;

	PhotoFormat(String mediaType, String extension) {
		this.mediaType = mediaType;
		this.extension = extension;
	}

	String mediaType() {
		return mediaType;
	}

	String extension() {
		return extension;
	}

	abstract boolean matchesHeader(byte[] bytes);

	static PhotoFormat validate(String declaredContentType, byte[] content) {
		if (content == null || content.length == 0) {
			throw new InvalidIncidentPhotoException(EMPTY_FILE_MESSAGE);
		}
		if (content.length > MAX_PHOTO_SIZE_BYTES) {
			throw new InvalidIncidentPhotoException(MAX_SIZE_MESSAGE);
		}
		if (declaredContentType == null || declaredContentType.isBlank()) {
			throw new InvalidIncidentPhotoException(UNSUPPORTED_TYPE_MESSAGE);
		}
		String normalized = normalizeContentType(declaredContentType);
		PhotoFormat byHeader = detectByHeader(content);
		if (byHeader == null || !normalized.equals(byHeader.mediaType)) {
			throw new InvalidIncidentPhotoException(UNSUPPORTED_TYPE_MESSAGE);
		}
		return byHeader;
	}

	private static PhotoFormat detectByHeader(byte[] content) {
		for (PhotoFormat format : values()) {
			if (format.matchesHeader(content)) {
				return format;
			}
		}
		return null;
	}

	private static String normalizeContentType(String contentType) {
		String base = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		return base.equals("image/jpg") ? "image/jpeg" : base;
	}

}
