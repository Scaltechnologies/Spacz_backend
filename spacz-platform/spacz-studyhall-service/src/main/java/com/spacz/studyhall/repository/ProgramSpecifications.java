package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Program;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class ProgramSpecifications {

    private ProgramSpecifications() {
    }

    public static Specification<Program> matching(String search, String category, Boolean active) {
        Specification<Program> spec = Specification.where(null);
        if (StringUtils.hasText(search)) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern)));
        }
        if (StringUtils.hasText(category)) {
            String normalized = category.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("category")), normalized));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return spec;
    }
}
