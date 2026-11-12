package es.uma.urbanpulse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.auth.AuthResponse;

/** Gets tokens for the seeded staff accounts and for new citizens. */
public final class TestAccounts {

	public static final String OPERATOR_EMAIL = "operador@urbanpulse.test";
	public static final String TECHNICIAN_EMAIL = "tecnico1@urbanpulse.test";
	public static final String TECHNICIAN_2_EMAIL = "tecnico2@urbanpulse.test";
	public static final String SEEDED_PASSWORD = "urbanpulse-dev";

	private TestAccounts() {
	}

	public static AuthResponse login(RestTestClient client, String email) {
		return body(client.post()
			.uri("/api/auth/login")
			.body(Map.of("email", email, "password", SEEDED_PASSWORD))
			.exchange()
			.expectStatus()
			.isOk());
	}

	public static AuthResponse newCitizen(RestTestClient client) {
		String email = "ciudadano-" + UUID.randomUUID() + "@mail.com";
		return body(client.post()
			.uri("/api/auth/register")
			.body(Map.of("name", "Ciudadano de prueba", "email", email, "password", "contraseña-segura"))
			.exchange()
			.expectStatus()
			.isCreated());
	}

	public static String bearer(AuthResponse auth) {
		return "Bearer " + auth.token();
	}

	private static AuthResponse body(RestTestClient.ResponseSpec response) {
		AuthResponse body = response.expectBody(AuthResponse.class).returnResult().getResponseBody();
		assertThat(body).isNotNull();
		return body;
	}

}
