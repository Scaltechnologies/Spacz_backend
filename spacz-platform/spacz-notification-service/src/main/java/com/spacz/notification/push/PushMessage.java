package com.spacz.notification.push;

import java.util.Map;

/**
 * What a device shows (title, body, image) plus the data the app uses to open the right screen.
 */
public record PushMessage(String title, String body, String imageUrl, Map<String, String> data,
                          String androidChannelId, boolean highPriority) {
}
