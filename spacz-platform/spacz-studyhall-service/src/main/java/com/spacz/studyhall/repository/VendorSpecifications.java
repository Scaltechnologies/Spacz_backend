package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.entity.VendorStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class VendorSpecifications {

    private VendorSpecifications() {
    }

    public static Specification<VendorProfile> matching(String search, VendorStatus status, String city) {
        Specification<VendorProfile> spec = Specification.where(null);
        if (StringUtils.hasText(search)) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("businessName")), pattern),
                    cb.like(cb.lower(root.get("contactName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (StringUtils.hasText(city)) {
            String normalized = city.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("city")), normalized));
        }
        return spec;
    }
}
