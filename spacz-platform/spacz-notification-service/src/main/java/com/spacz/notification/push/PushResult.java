package com.spacz.notification.push;

import java.util.HashSet;
import java.util.Set;

/**
 * @param invalidTokens tokens FCM reported as no longer registered; they are deleted
 */
public record PushResult(int success, int failure, Set<String> invalidTokens) {

    public static final PushResult NONE = new PushResult(0, 0, Set.of());

    public PushResult plus(PushResult other) {
        Set<String> invalid = new HashSet<>(invalidTokens);
        invalid.addAll(other.invalidTokens);
        return new PushResult(success + other.success, failure + other.failure, invalid);
    }
}
