package com.spacz.studyhall.config;

import com.spacz.studyhall.security.InternalApiKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String INTERNAL_KEY = "internalApiKey";

    @Bean
    public OpenAPI spaczOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SPACZ Study Hall Service")
                        .version("1.0.0")
                        .description("""
                                Vendors / institutes, study halls (branches), blocks and seats, amenities, the exam/course
                                catalog, bookings (daily and monthly), enrollments (a hall's students) and the legacy
                                Partner-app API.

                                * `GET /api/studyhalls/**`, `/api/programs`, `/api/amenities`, `GET /api/vendors/{id}`: public
                                * `/api/vendors/**` and writes on `/api/studyhalls/**`: ROLE_VENDOR, scoped to the caller's own halls
                                * `/api/bookings/**`: ROLE_USER (the hall's vendor and admins may read a booking)
                                * writes on `/api/programs` and `/api/amenities`: ROLE_ADMIN
                                * `/api/owners`, `/api/properties`, `/api/blocks`, `/api/seats`, `/amenities`, `/images`:
                                  legacy Partner API (ROLE_VENDOR)

                                **Authentication:** `Authorization: Bearer <accessToken>`.
                                **Errors:** `ApiError` schema; booking conflicts return `409 SEAT_UNAVAILABLE`.
                                **Pagination:** `page` (0-based), `size` (max 100), `sort=field,asc|desc`.
                                `/internal/**` is service-to-service only (`X-Internal-Api-Key`)."""))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(INTERNAL_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name(InternalApiKeyFilter.HEADER)));
    }
}
