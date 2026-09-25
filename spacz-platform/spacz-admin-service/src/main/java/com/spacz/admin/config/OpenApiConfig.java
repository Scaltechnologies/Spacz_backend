package com.spacz.admin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI spaczOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SPACZ Admin Service")
                        .version("1.0.0")
                        .description("""
                                Platform administration: dashboard statistics, users, vendor and study-hall approval,
                                exam/amenity catalogs, bookings overview and audit logs.

                                **Every endpoint requires ROLE_ADMIN** (`Authorization: Bearer <accessToken>`).
                                Every state-changing call is recorded in the audit log with actor, IP and correlation ID.
                                Data is fetched from the owning services over REST; admin-service never reads their databases.
                                **Errors:** `ApiError` schema; `503 SERVICE_UNAVAILABLE` when an owning service is down.
                                **Pagination:** `page` (0-based), `size` (max 100), `sort`."""))
                .servers(List.of(new Server().url("/").description("Current host")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
