package es.uma.urbanpulse.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.PostgisContainerConfiguration;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class AuthApiTests {

	@Autowired
	private RestTestClient client;

	@Test
	void citizenSignsUpAndGetsAToken() {
		String email = uniqueEmail();

		this.client.post()
			.uri("/api/auth/register")
			.body(Map.of("name", "Ana García", "email", email, "password", "contraseña-segura"))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.token").isNotEmpty()
			.jsonPath("$.expiresAt").isNotEmpty()
			.jsonPath("$.user.id").isNumber()
			.jsonPath("$.user.name").isEqualTo("Ana García")
			.jsonPath("$.user.email").isEqualTo(email)
			.jsonPath("$.user.role").isEqualTo("CITIZEN")
			.jsonPath("$.user.password").doesNotExist()
			.jsonPath("$.user.passwordHash").doesNotExist();
	}

	@Test
	void signUpIsRejectedWhenTheEmailAlreadyHasAnAccountIgnoringCase() {
		String email = uniqueEmail();
		register("Ana García", email, "contraseña-segura").expectStatus().isCreated();

		register("Otra Ana", email.toUpperCase(), "otra-contraseña")
			.expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.message").isEqualTo("Ya existe una cuenta con este email.");
	}

	@Test
	void signUpIsRejectedWhenThePasswordIsTooShortOrTooLong() {
		register("Ana García", uniqueEmail(), "corta")
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.password").isEqualTo("La contraseña debe tener al menos 8 caracteres.");

		register("Ana García", uniqueEmail(), "x".repeat(73))
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.password").isEqualTo("La contraseña es demasiado larga. Elige una más corta.");

		register("Ana García", uniqueEmail(), "ñ".repeat(72))
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.password").isEqualTo("La contraseña es demasiado larga. Elige una más corta.");
	}

	@Test
	void signUpIsRejectedWhenThePasswordIsOnlySpaces() {
		register("Ana García", uniqueEmail(), " ".repeat(8))
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.password").isEqualTo("La contraseña no puede estar formada solo por espacios.");
		// A no-break space and a tab are whitespace too.
		register("Ana García", uniqueEmail(), " \t".repeat(4)).expectStatus().isBadRequest();
	}

	@Test
	void spacesAroundTheEmailAreIgnored() {
		String email = uniqueEmail();

		register("Ana García", " " + email + " ", "contraseña-segura")
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.user.email").isEqualTo(email);
		login(email + " ", "contraseña-segura").expectStatus().isOk();

		// Some keyboards add a no-break space instead.
		String other = uniqueEmail();
		register("Ana García", other + " ", "contraseña-segura")
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.user.email").isEqualTo(other);
		login(other + " ", "contraseña-segura").expectStatus().isOk();
	}

	@Test
	void citizenLogsInWithTheEmailInAnyCase() {
		String email = uniqueEmail();
		register("Ana García", email, "contraseña-segura").expectStatus().isCreated();

		login(email.toUpperCase(), "contraseña-segura")
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.token").isNotEmpty()
			.jsonPath("$.user.email").isEqualTo(email)
			.jsonPath("$.user.role").isEqualTo("CITIZEN");
	}

	@Test
	void wrongPasswordAndUnknownEmailGetTheSameMessage() {
		String email = uniqueEmail();
		register("Ana García", email, "contraseña-segura").expectStatus().isCreated();

		login(email, "contraseña-equivocada")
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.message").isEqualTo("El email o la contraseña no son correctos.");
		login(uniqueEmail(), "contraseña-segura")
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.message").isEqualTo("El email o la contraseña no son correctos.");
		login(email, "x".repeat(100)).expectStatus().isUnauthorized();
	}

	@Test
	void loginComparesThePasswordExactlyAsTyped() {
		String email = uniqueEmail();
		register("Ana García", email, "  contraseña-segura  ").expectStatus().isCreated();

		login(email, "  contraseña-segura  ").expectStatus().isOk();
		login(email, "contraseña-segura").expectStatus().isUnauthorized();
		login(email, " ".repeat(8))
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.message").isEqualTo("El email o la contraseña no son correctos.");
	}

	@Test
	void passwordMatchesWhetherTheKeyboardSendsTheAccentComposedOrDecomposed() {
		String composed = "contraseña-segura";
		String decomposed = "contraseña-segura";
		String signedUpComposed = uniqueEmail();
		String signedUpDecomposed = uniqueEmail();
		register("Ana García", signedUpComposed, composed).expectStatus().isCreated();
		register("Ana García", signedUpDecomposed, decomposed).expectStatus().isCreated();

		login(signedUpComposed, decomposed).expectStatus().isOk();
		login(signedUpDecomposed, composed).expectStatus().isOk();
	}

	@Test
	void loginRejectsALongerPasswordThatStartsLikeTheRightOne() {
		String email = uniqueEmail();
		// Exactly BCrypt's 72 bytes: the ñ takes two.
		String password = "ñ" + "a".repeat(70);
		register("Ana García", email, password).expectStatus().isCreated();

		login(email, password).expectStatus().isOk();
		login(email, password + "-y-algo-más")
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.message").isEqualTo("El email o la contraseña no son correctos.");
	}

	@Test
	void seededStaffAccountsLogIn() {
		login("operador@urbanpulse.test", "urbanpulse-dev")
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.user.role").isEqualTo("OPERATOR");
		login("tecnico1@urbanpulse.test", "urbanpulse-dev")
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.user.role").isEqualTo("TECHNICIAN");
	}

	@Test
	void requestWithoutTokenGetsUnauthorized() {
		this.client.get()
			.uri("/api/incidents")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
			.expectBody()
			.jsonPath("$.message").isEqualTo("Tu sesión ha caducado. Vuelve a iniciar sesión.")
			.jsonPath("$.errors").isMap();
	}

	@Test
	void requestWithAnInvalidTokenGetsUnauthorized() {
		this.client.get()
			.uri("/api/users/me")
			.header("Authorization", "Bearer not-a-valid-token")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.message").isEqualTo("Tu sesión ha caducado. Vuelve a iniciar sesión.");
	}

	@Test
	void currentUserIsTheOwnerOfTheToken() {
		String email = uniqueEmail();
		String token = tokenOf(register("Ana García", email, "contraseña-segura"));

		this.client.get()
			.uri("/api/users/me")
			.header("Authorization", "Bearer " + token)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.name").isEqualTo("Ana García")
			.jsonPath("$.email").isEqualTo(email)
			.jsonPath("$.role").isEqualTo("CITIZEN");
	}

	@Test
	void stillValidTokenIsRenewed() {
		String email = uniqueEmail();
		String token = tokenOf(register("Ana García", email, "contraseña-segura"));

		String renewed = tokenOf(this.client.post()
			.uri("/api/auth/refresh")
			.header("Authorization", "Bearer " + token)
			.exchange()
			.expectStatus()
			.isOk());

		this.client.get()
			.uri("/api/users/me")
			.header("Authorization", "Bearer " + renewed)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.email").isEqualTo(email);
	}

	@Test
	void refreshWithoutTokenGetsUnauthorized() {
		this.client.post().uri("/api/auth/refresh").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void managementPanelPreflightAllowsTheAuthorizationHeader() {
		this.client.options()
			.uri("/api/incidents")
			.header("Origin", "http://localhost:5173")
			.header("Access-Control-Request-Method", "GET")
			.header("Access-Control-Request-Headers", "authorization")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
			.expectHeader().value("Access-Control-Allow-Headers",
					(headers) -> assertThat(headers).containsIgnoringCase("authorization"));
	}

	static String tokenOf(RestTestClient.ResponseSpec response) {
		AuthResponse body = response.expectBody(AuthResponse.class).returnResult().getResponseBody();
		assertThat(body).isNotNull();
		return body.token();
	}

	private RestTestClient.ResponseSpec register(String name, String email, String password) {
		return this.client.post()
			.uri("/api/auth/register")
			.body(Map.of("name", name, "email", email, "password", password))
			.exchange();
	}

	private RestTestClient.ResponseSpec login(String email, String password) {
		return this.client.post()
			.uri("/api/auth/login")
			.body(Map.of("email", email, "password", password))
			.exchange();
	}

	static String uniqueEmail() {
		return "ciudadano-" + UUID.randomUUID() + "@mail.com";
	}

}
