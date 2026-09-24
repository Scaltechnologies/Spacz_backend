package com.spacz.studyhall.security;

import com.spacz.studyhall.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * URL rules are coarse (who may call at all); controllers add @PreAuthorize for roles and the
 * services check ownership.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] DOCS_AND_HEALTH = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**", "/actuator/info"
    };
    private static final String[] LEGACY_PARTNER_API = {
            "/api/owners", "/api/owners/**", "/api/properties", "/api/properties/**", "/api/blocks", "/api/blocks/**",
            "/api/seats", "/api/seats/**", "/amenities", "/amenities/**", "/images", "/images/**"
    };

    private final SecurityProperties securityProperties;
    private final SecurityErrorHandlers errorHandlers;

    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(securityProperties.internal().apiKey()),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(InternalApiKeyFilter.ROLE_INTERNAL))
                .exceptionHandling(e -> e.authenticationEntryPoint(errorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(DOCS_AND_HEALTH).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/studyhalls", "/api/studyhalls/**",
                                "/api/programs", "/api/programs/**", "/api/amenities", "/api/amenities/**",
                                "/api/vendors/*").permitAll()
                        .requestMatchers("/api/programs/**", "/api/amenities/**").hasRole("ADMIN")
                        // Role checks before body validation: a wrong role gets 403, not 400.
                        .requestMatchers(HttpMethod.POST, "/api/studyhalls").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.PUT, "/api/studyhalls/**").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/studyhalls/**").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/studyhalls/**").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.POST, "/api/studyhalls/**").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("USER")
                        .requestMatchers(LEGACY_PARTNER_API).hasRole("VENDOR")
                        .requestMatchers("/api/vendors", "/api/vendors/**").hasRole("VENDOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtClaimsConverter()))
                        .authenticationEntryPoint(errorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .exceptionHandling(e -> e.authenticationEntryPoint(errorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .build();
    }
}
