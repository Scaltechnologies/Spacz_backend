package com.spacz.studyhall.storage;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Uploaded photos are stored as host-agnostic paths ({@code /api/studyhalls/media/...}). Responses turn
 * them into absolute URLs on the host the client actually called — through the gateway's
 * X-Forwarded-* headers (server.forward-headers-strategy=framework) — so a phone on the LAN gets
 * {@code http://<pc-lan-ip>:8080/...}, never {@code localhost}. External URLs (CDN) pass through.
 */
public final class MediaUrls {

    public static final String PREFIX = "/api/studyhalls/media/";

    private MediaUrls() {
    }

    public static String forKey(String key) {
        return PREFIX + key;
    }

    /** The storage key of an uploaded photo's stored path, or null for external URLs. */
    public static String keyOf(String storedUrl) {
        return storedUrl != null && storedUrl.startsWith(PREFIX) ? storedUrl.substring(PREFIX.length()) : null;
    }

    public static String resolve(String storedUrl) {
        if (storedUrl == null || !storedUrl.startsWith("/") || RequestContextHolder.getRequestAttributes() == null) {
            return storedUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(storedUrl).toUriString();
    }
}
