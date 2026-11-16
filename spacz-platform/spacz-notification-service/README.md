# spacz-notification-service

Port **8085**, database **`spacz_notification`**. In-app notifications, FCM push devices and push
preferences for the student app, the Partner app and the admin panel, plus admin announcements.

* API, notification catalogue, app checklist and push setup: [docs/NOTIFICATIONS.md](../docs/NOTIFICATIONS.md)
* Other services send through `POST /internal/notifications` (`X-Internal-Api-Key`), from their `notify` package.
* Swagger: http://localhost:8085/swagger-ui.html (or the gateway's aggregated UI)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local `spacz_notification` | database |
| `JWT_SECRET`, `JWT_ISSUER`, `INTERNAL_API_KEY` | dev values | shared with every service |
| `FCM_CREDENTIALS_BASE64` / `FCM_CREDENTIALS_FILE` | empty | SPACZ Firebase service account; empty = push disabled |
| `NOTIFICATION_RETENTION` | `120d` | delete in-app notifications older than this |
| `DEVICE_TOKEN_TTL` | `90d` | delete device tokens not refreshed for this long |

Run alone: `../mvnw spring-boot:run` (needs PostgreSQL with the `spacz_notification` database, e.g.
from `docker compose up -d postgres`).
