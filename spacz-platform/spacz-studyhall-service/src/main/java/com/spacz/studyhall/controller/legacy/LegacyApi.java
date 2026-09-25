package com.spacz.studyhall.controller.legacy;

import com.spacz.studyhall.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the controllers of the legacy Partner-app contract (migrated from spacz-partner-service).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "Legacy Partner API", description = """
        The old spacz-partner-service contract (same paths, fields and status codes) for the existing Partner app.
        Now requires a VENDOR token and is scoped to the caller. IDs: ownerId = vendor profile, propertyId = study hall,
        amenityId = block. New clients should use /api/vendors and /api/studyhalls.""")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public @interface LegacyApi {
}
