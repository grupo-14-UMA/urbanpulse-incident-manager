package es.uma.urbanpulse.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import es.uma.urbanpulse.user.Role;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	static final Duration TOKEN_LIFETIME = Duration.ofDays(7);

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorHandlers errorHandlers) throws Exception {
		http
			// Stateless API authenticated with bearer tokens: no session and no cookies, so no CSRF.
			.csrf((csrf) -> csrf.disable())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Uses the Spring MVC CORS mappings of ManagementPanelCorsConfiguration.
			.cors(Customizer.withDefaults())
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/incidents").hasRole(Role.CITIZEN.name())
				.requestMatchers(HttpMethod.POST, "/api/incidents/*/photos").hasRole(Role.CITIZEN.name())
				.requestMatchers("/api/technicians/**").hasRole(Role.OPERATOR.name())
				.anyRequest().authenticated())
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(roleFromTokenConverter()))
				.authenticationEntryPoint(errorHandlers)
				.accessDeniedHandler(errorHandlers))
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(errorHandlers)
				.accessDeniedHandler(errorHandlers));
		return http.build();
	}

	@Bean
	AuthTokens authTokens(@Value("${urbanpulse.security.jwt-secret:}") String secret) {
		return new AuthTokens(secret, TOKEN_LIFETIME, Clock.systemUTC());
	}

	@Bean
	JwtDecoder jwtDecoder(AuthTokens authTokens) {
		return authTokens.decoder();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private static JwtAuthenticationConverter roleFromTokenConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(AuthTokens.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
