package com.spacz.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.gateway.config.GatewayProperties;
import com.spacz.gateway.filter.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Early rejection at the edge: invalid/expired tokens and obviously wrong roles never reach a
 * service. This is defence in depth only — every service verifies the JWT and ownership again.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private final ObjectMapper objectMapper;

    public GatewaySecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**",
                                "/docs/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/register/user",
                                "/api/auth/register/vendor", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/otp/request", "/api/auth/otp/verify").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/studyhalls", "/api/studyhalls/**", "/api/programs",
                                "/api/programs/**", "/api/amenities", "/api/amenities/**", "/api/vendors/*").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/programs/**", "/api/amenities/**").hasRole("ADMIN")
                        .pathMatchers("/api/owners/**", "/api/properties/**", "/api/blocks/**", "/api/seats/**",
                                "/amenities/**", "/images/**").hasRole("VENDOR")
                        .pathMatchers("/api/vendors/**").hasRole("VENDOR")
                        .pathMatchers("/api/users/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(roleClaimConverter())))
                        .authenticationEntryPoint((exchange, ex) ->
                                writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access token is missing, invalid or expired"))
                        .accessDeniedHandler((exchange, ex) ->
                                writeError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) ->
                                writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required"))
                        .accessDeniedHandler((exchange, ex) ->
                                writeError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action")))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewayProperties properties) {
        SecretKeySpec key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwtIssuer()));
        return decoder;
    }

    /** The SPACZ "role" claim (ADMIN / USER / VENDOR) becomes ROLE_ADMIN / ROLE_USER / ROLE_VENDOR. */
    private static JwtAuthenticationConverter roleClaimConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("userId");
        return converter;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set("WWW-Authenticate", "Bearer");
        }
        try {
            DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            return response.setComplete();
        }
    }
}
