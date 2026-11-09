package es.uma.urbanpulse.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Every operation needs a bearer token unless it declares an empty {@code @SecurityRequirements}. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH))
@SecurityScheme(name = OpenApiConfiguration.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
		bearerFormat = "JWT")
public class OpenApiConfiguration {

	static final String BEARER_AUTH = "bearerAuth";

}
