package com.studyhouse.spacz.partner.config;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.studyhouse.spacz.partner.dto.response.ErrorResponse;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

@Configuration
public class OpenApiConfig {

    private static final Map<String, String> ERROR_RESPONSES = Map.of(
            "400", "Invalid request",
            "404", "Resource or referenced parent not found",
            "409", "Conflicts with existing data",
            "500", "Unexpected server error");

    @Bean
    public OpenAPI partnerServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SPACZ Partner Service API")
                .version("v1")
                .description("Partner/Vendor side of SPACZ: owners and their properties, blocks, seats, "
                        + "amenities and images. Same endpoints and fields as the legacy spacz backend. "
                        + "Error responses share one JSON body (timestamp, status, error, message, path)."));
    }

    // Documents the shared error body on every operation, while leaving the success
    // responses to springdoc (which derives them from the controller return types).
    @Bean
    public OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class));
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas("ErrorResponse", resolved.schema);
            resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);

            Content errorContent = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    ERROR_RESPONSES.forEach((code, description) -> operation.getResponses()
                            .putIfAbsent(code, new ApiResponse().description(description).content(errorContent)))));
        };
    }
}
