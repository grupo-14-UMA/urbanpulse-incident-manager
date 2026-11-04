package es.uma.urbanpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ManagementPanelCorsConfiguration implements WebMvcConfigurer {

    private final String managementPanelOrigin;

    public ManagementPanelCorsConfiguration(
            @Value("${urbanpulse.cors.management-panel-origin}") String managementPanelOrigin) {
        this.managementPanelOrigin = managementPanelOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(managementPanelOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
