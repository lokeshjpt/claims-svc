package com.abc.claims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata + HTTP Basic security scheme so the Swagger UI
 * "Authorize" button works out of the box.
 *
 * <p>UI: http://localhost:8080/swagger-ui.html
 * <p>Spec: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI claimsOpenApi() {
        final String schemeName = "basicAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Claims Processor API")
                        .version("1.0.0")
                        .description("""
                                ABC Health Insurance claims-transaction processing service.
                                Three modes — Batch (CSV), REST, GUI upload — all backed by the same
                                strategy-pattern coverage engine.""")
                        .contact(new Contact().name("Claims Platform Team").email("claims-platform@abc-health.example"))
                        .license(new License().name("Internal / Assessment Only")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Default users: admin/admin123 or processor/claims123")));
    }
}
