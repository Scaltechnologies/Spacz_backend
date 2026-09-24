package com.spacz.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * One access-log line per routed request: method, path, target route, status, duration, correlation ID.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            log.info("{} {} -> {} {} {}ms [{}]",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    route != null ? route.getId() : "-",
                    exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : "-",
                    (System.nanoTime() - start) / 1_000_000,
                    exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER));
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
