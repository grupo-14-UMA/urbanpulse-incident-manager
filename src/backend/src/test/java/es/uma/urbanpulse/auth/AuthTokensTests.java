package es.uma.urbanpulse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import es.uma.urbanpulse.user.Role;

class AuthTokensTests {

	private static final String SECRET = "unit-test-secret-with-at-least-32-bytes";
	private static final Duration LIFETIME = Duration.ofDays(7);

	@Test
	void issuedTokenCarriesTheUserIdAndRole() {
		AuthTokens tokens = new AuthTokens(SECRET, LIFETIME, Clock.systemUTC());

		IssuedToken issued = tokens.issue(42, Role.TECHNICIAN);

		assertThat(tokens.verify(issued.token())).isEqualTo(new TokenSubject(42, Role.TECHNICIAN));
	}

	@Test
	void tokenExpiresAfterItsLifetime() {
		Instant issuedAt = Instant.parse("2026-01-01T10:00:00Z");
		AuthTokens tokens = new AuthTokens(SECRET, LIFETIME, Clock.fixed(issuedAt, ZoneOffset.UTC));

		IssuedToken issued = tokens.issue(7, Role.CITIZEN);

		assertThat(issued.expiresAt()).isEqualTo(Instant.parse("2026-01-08T10:00:00Z"));
	}

	@Test
	void rejectsAnExpiredToken() {
		Clock eightDaysAgo = Clock.offset(Clock.systemUTC(), Duration.ofDays(-8));
		String expired = new AuthTokens(SECRET, LIFETIME, eightDaysAgo).issue(7, Role.CITIZEN).token();

		AuthTokens tokens = new AuthTokens(SECRET, LIFETIME, Clock.systemUTC());

		assertThatThrownBy(() -> tokens.verify(expired)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsATokenSignedWithAnotherSecret() {
		String foreign = new AuthTokens("another-secret-that-is-also-32-bytes-long", LIFETIME, Clock.systemUTC())
			.issue(7, Role.OPERATOR)
			.token();

		AuthTokens tokens = new AuthTokens(SECRET, LIFETIME, Clock.systemUTC());

		assertThatThrownBy(() -> tokens.verify(foreign)).isInstanceOf(JwtException.class);
	}

	@Test
	void refusesAMissingOrShortSecret() {
		assertThatIllegalStateException().isThrownBy(() -> new AuthTokens(null, LIFETIME, Clock.systemUTC()))
			.withMessageContaining("JWT_SECRET");
		assertThatIllegalStateException().isThrownBy(() -> new AuthTokens("", LIFETIME, Clock.systemUTC()))
			.withMessageContaining("JWT_SECRET");
		assertThatIllegalStateException()
			.isThrownBy(() -> new AuthTokens("only-31-bytes-long-secret-value", LIFETIME, Clock.systemUTC()))
			.withMessageContaining("JWT_SECRET");
	}

}
