package com.spacz.notification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Firebase Cloud Messaging (HTTP v1 through the Admin SDK). Sends in batches of 500 tokens, the
 * FCM multicast limit, and reports tokens FCM says are gone so they can be deleted.
 */
@Slf4j
public class FcmPushSender implements PushSender {

    static final int MAX_TOKENS_PER_CALL = 500;
    private static final Set<MessagingErrorCode> TOKEN_GONE = Set.of(
            MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging messaging;

    public FcmPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public String provider() {
        return "FCM";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public PushResult send(PushMessage message, List<String> tokens) {
        PushResult result = PushResult.NONE;
        for (int from = 0; from < tokens.size(); from += MAX_TOKENS_PER_CALL) {
            result = result.plus(sendBatch(message, tokens.subList(from, Math.min(from + MAX_TOKENS_PER_CALL, tokens.size()))));
        }
        return result;
    }

    private PushResult sendBatch(PushMessage message, List<String> tokens) {
        try {
            BatchResponse response = messaging.sendEachForMulticast(build(message, tokens));
            Set<String> invalid = new HashSet<>();
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                FirebaseMessagingException error = responses.get(i).getException();
                if (error != null && TOKEN_GONE.contains(error.getMessagingErrorCode())) {
                    invalid.add(tokens.get(i));
                } else if (error != null) {
                    log.debug("FCM rejected a device: {} {}", error.getMessagingErrorCode(), error.getMessage());
                }
            }
            return new PushResult(response.getSuccessCount(), response.getFailureCount(), invalid);
        } catch (FirebaseMessagingException | RuntimeException ex) {
            log.warn("FCM send of '{}' to {} device(s) failed: {}", message.title(), tokens.size(), ex.getMessage());
            return new PushResult(0, tokens.size(), Set.of());
        }
    }

    static MulticastMessage build(PushMessage message, List<String> tokens) {
        com.google.firebase.messaging.Notification.Builder notification = com.google.firebase.messaging.Notification.builder()
                .setTitle(message.title())
                .setBody(message.body());
        AndroidNotification.Builder android = AndroidNotification.builder()
                .setChannelId(message.androidChannelId())
                .setSound("default");
        if (message.imageUrl() != null) {
            notification.setImage(message.imageUrl());
            android.setImage(message.imageUrl());
        }
        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification.build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(message.highPriority() ? AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                        .setNotification(android.build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .build();
    }
}
