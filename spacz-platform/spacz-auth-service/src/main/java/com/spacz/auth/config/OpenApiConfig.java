package com.spacz.auth.config;

import com.spacz.auth.security.InternalApiKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
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
                        .title("SPACZ Auth Service")
                        .version("1.0.0")
                        .description("""
                                Identity for the SPACZ platform: email/password and phone-OTP registration and login,
                                JWT access tokens (15 min),
                                rotating refresh tokens (7 days) and account status.

                                **Authentication:** send `Authorization: Bearer <accessToken>`.
                                **Errors:** every error uses the `ApiError` schema (`status`, `error` code, `message`, `path`).
                                **Pagination:** `page` (0-based), `size` (max 100), `sort=field,asc|desc`.
                                Endpoints under `/internal/**` are service-to-service only and require `X-Internal-Api-Key`.""")
                        .contact(new Contact().name("SPACZ Platform")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(INTERNAL_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name(InternalApiKeyFilter.HEADER)));
    }
}
