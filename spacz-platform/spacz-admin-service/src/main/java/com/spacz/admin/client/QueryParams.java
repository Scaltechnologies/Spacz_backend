package com.spacz.admin.client;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriBuilder;

import java.util.Optional;

/**
 * Forwards paging/sorting and optional filters to downstream services.
 */
final class QueryParams {

    private QueryParams() {
    }

    static UriBuilder page(UriBuilder builder, Pageable pageable) {
        builder.queryParam("page", pageable.getPageNumber()).queryParam("size", pageable.getPageSize());
        for (Sort.Order order : pageable.getSort()) {
            builder.queryParam("sort", order.getProperty() + "," + order.getDirection().name().toLowerCase());
        }
        return builder;
    }

    static Optional<Object> optional(Object value) {
        if (value instanceof String s && s.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(value);
    }
}
