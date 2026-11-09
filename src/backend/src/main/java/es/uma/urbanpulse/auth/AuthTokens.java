package es.uma.urbanpulse.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import es.uma.urbanpulse.user.Role;

/**
 * Issues and verifies the self-issued access tokens (HS256 JWT). The subject is the user id and
 * the {@value #ROLE_CLAIM} claim carries the role.
 */
public class AuthTokens {

	static final String ROLE_CLAIM = "role";

	private static final int MIN_SECRET_BYTES = 32;
	private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Duration lifetime;
	private final Clock clock;

	public AuthTokens(String secret, Duration lifetime, Clock clock) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT_SECRET must be set to a value of at least " + MIN_SECRET_BYTES + " bytes");
		}
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHM).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtTypeValidator.jwt(), timestamps));
		this.lifetime = lifetime;
		this.clock = clock;
	}

	public IssuedToken issue(Integer userId, Role role) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(lifetime);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(userId.toString())
			.claim(ROLE_CLAIM, role.name())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.build();
		JwsHeader header = JwsHeader.with(ALGORITHM).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	/**
	 * Verifies the signature and lifetime of a token.
	 * @throws org.springframework.security.oauth2.jwt.JwtException if the token is not valid
	 */
	public TokenSubject verify(String token) {
		return subjectOf(decoder.decode(token));
	}

	/** Decoder for the resource server filter chain. */
	public JwtDecoder decoder() {
		return decoder;
	}

	public static TokenSubject subjectOf(Jwt jwt) {
		return new TokenSubject(Integer.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)));
	}

}
