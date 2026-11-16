package com.spacz.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.spacz.notification.config.PushProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Chooses the push provider at startup. Bad or missing credentials never stop the service: push is
 * disabled (and logged as an error), in-app notifications keep working.
 */
@Slf4j
@Configuration
public class PushSenderConfig {

    private static final String APP_NAME = "spacz-notifications";

    @Bean
    public PushSender pushSender(PushProperties properties) {
        PushProperties.Fcm fcm = properties.fcmOrEmpty();
        if (!fcm.configured()) {
            log.warn("Push notifications are DISABLED: set FCM_CREDENTIALS_BASE64 (or FCM_CREDENTIALS_FILE) to the "
                    + "SPACZ Firebase service-account JSON. In-app notifications still work.");
            return new DisabledPushSender();
        }
        try (InputStream json = open(fcm)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(json);
            String projectId = credentials instanceof ServiceAccountCredentials sa ? sa.getProjectId() : null;
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> existing.getName().equals(APP_NAME))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(
                            FirebaseOptions.builder().setCredentials(credentials).build(), APP_NAME));
            log.info("Push notifications enabled through FCM (Firebase project '{}')", projectId);
            if (projectId != null && projectId.toLowerCase().startsWith("strike")) {
                log.error("FCM credentials belong to Firebase project '{}', which looks like the Strike app, "
                        + "not SPACZ. SPACZ devices will not receive these pushes.", projectId);
            }
            return new FcmPushSender(FirebaseMessaging.getInstance(app));
        } catch (IOException | RuntimeException ex) {
            log.error("Push notifications are DISABLED: the FCM credentials could not be loaded ({})", ex.getMessage());
            return new DisabledPushSender();
        }
    }

    private static InputStream open(PushProperties.Fcm fcm) throws IOException {
        if (fcm.credentialsBase64() != null && !fcm.credentialsBase64().isBlank()) {
            return new ByteArrayInputStream(Base64.getMimeDecoder().decode(fcm.credentialsBase64().trim()));
        }
        return Files.newInputStream(Path.of(fcm.credentialsFile().trim()));
    }
}
