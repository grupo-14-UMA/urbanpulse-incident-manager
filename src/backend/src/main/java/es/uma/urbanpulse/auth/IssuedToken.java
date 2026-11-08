package es.uma.urbanpulse.auth;

import java.time.Instant;

/** A signed access token and the instant it stops being valid. */
public record IssuedToken(String token, Instant expiresAt) {
}
