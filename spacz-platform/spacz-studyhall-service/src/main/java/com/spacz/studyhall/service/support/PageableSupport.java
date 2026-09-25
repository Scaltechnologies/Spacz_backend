package com.spacz.studyhall.service.support;

import com.spacz.studyhall.exception.SpaczException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Only whitelisted properties may be used in {@code sort=}, so clients cannot sort on internal
 * fields or trigger errors with unknown property names.
 */
public final class PageableSupport {

    private PageableSupport() {
    }

    public static Pageable restrictSort(Pageable pageable, Set<String> allowed, Sort defaultSort) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new SpaczException(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + allowed);
            }
        }
        return pageable;
    }
}
