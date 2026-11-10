package es.uma.urbanpulse.incident;

/**
 * Storage contract for incident photos. Backed by a local directory volume in development
 * and replaceable by an S3 implementation when deployed.
 */
public interface PhotoStorage {

	void store(String storageKey, byte[] content, String contentType);

	byte[] read(String storageKey);

	void delete(String storageKey);

}
