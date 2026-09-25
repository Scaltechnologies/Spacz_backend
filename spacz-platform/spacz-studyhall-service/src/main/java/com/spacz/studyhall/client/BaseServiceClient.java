package com.spacz.studyhall.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.studyhall.exception.ApiError;
import com.spacz.studyhall.exception.ServiceUnavailableException;
import com.spacz.studyhall.exception.SpaczException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

/**
 * Error translation shared by all REST clients:
 * <ul>
 *   <li>4xx from the downstream service (404, 409, 422 ...) is passed on with its code and message</li>
 *   <li>401/403 means the internal API key is misconfigured → 502</li>
 *   <li>5xx, timeouts and connection failures → 503</li>
 * </ul>
 */
@Slf4j
public abstract class BaseServiceClient {

    private final String serviceName;
    private final ObjectMapper objectMapper;

    protected BaseServiceClient(String serviceName, ObjectMapper objectMapper) {
        this.serviceName = serviceName;
        this.objectMapper = objectMapper;
    }

    protected <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException(serviceName, ex);
        }
    }

    protected void run(Runnable call) {
        execute(() -> {
            call.run();
            return null;
        });
    }

    protected RestClient.ResponseSpec.ErrorHandler errorHandler() {
        return (request, response) -> {
            int status = response.getStatusCode().value();
            ApiError error = null;
            try {
                error = objectMapper.readValue(response.getBody(), ApiError.class);
            } catch (Exception ignored) {
                // body is not an ApiError; fall back to a generic message
            }
            if (status == 401 || status == 403) {
                log.error("{} rejected internal credentials for {} {}", serviceName, request.getMethod(), request.getURI());
                throw new SpaczException(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_AUTH_ERROR",
                        serviceName + " rejected the internal service credentials");
            }
            if (status >= 500) {
                throw new ServiceUnavailableException(serviceName, "HTTP " + status);
            }
            HttpStatus httpStatus = HttpStatus.resolve(status);
            throw new SpaczException(httpStatus != null ? httpStatus : HttpStatus.BAD_GATEWAY,
                    error != null && error.error() != null ? error.error() : "DOWNSTREAM_ERROR",
                    error != null && error.message() != null ? error.message() : serviceName + " returned HTTP " + status);
        };
    }
}
