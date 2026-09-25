package com.spacz.user.config;

import com.spacz.user.security.InternalApiKeyFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds {@link RestClient}s for calls to other SPACZ services: timeouts, the internal API key
 * and the propagated correlation ID are applied to every request.
 */
@Component
public class ServiceClientFactory {

    private final RestClient.Builder builder;
    private final SecurityProperties securityProperties;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final String serviceName;

    public ServiceClientFactory(RestClient.Builder builder,
                                SecurityProperties securityProperties,
                                @Value("${spring.application.name}") String serviceName,
                                @Value("${spacz.clients.connect-timeout:2s}") Duration connectTimeout,
                                @Value("${spacz.clients.read-timeout:5s}") Duration readTimeout) {
        this.builder = builder;
        this.securityProperties = securityProperties;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.serviceName = serviceName.replaceFirst("^spacz-", "");
    }

    public RestClient create(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalApiKeyFilter.HEADER, securityProperties.internal().apiKey())
                .defaultHeader("X-Source-Service", serviceName)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
