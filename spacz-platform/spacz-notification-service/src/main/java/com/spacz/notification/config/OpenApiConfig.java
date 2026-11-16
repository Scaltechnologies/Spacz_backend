package com.spacz.notification.config;

import com.spacz.notification.security.InternalApiKeyFilter;
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
                        .title("SPACZ Notification Service")
                        .version("1.0.0")
                        .description("""
                                In-app notifications, push devices (Firebase Cloud Messaging) and push preferences for
                                every SPACZ app: student app (USER), partner app (VENDOR) and admin panel (ADMIN).
                                `/api/notifications/**` acts on the signed-in account; `/api/notifications/admin/**` is ADMIN only.

                                **Authentication:** `Authorization: Bearer <accessToken>` from auth-service.
                                **Errors:** `ApiError` schema. **Pagination:** `page` (0-based), `size` (max 100).
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
