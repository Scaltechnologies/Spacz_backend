package com.spacz.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.auth.config.CorrelationIdFilter;
import com.spacz.auth.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes 401/403 responses raised by the security filter chain in the standard {@link ApiError} shape.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            String message = exception instanceof InvalidBearerTokenException
                    ? "Access token is invalid or expired"
                    : "Authentication is required";
            response.setHeader("WWW-Authenticate", "Bearer");
            write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                       String message) throws IOException {
        ApiError error = new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY), null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
