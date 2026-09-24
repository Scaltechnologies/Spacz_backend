# spacz-gateway-service (port 8080, no database)

Spring Cloud Gateway. The single entry point for every client.

| Path | Routed to |
|---|---|
| `/api/auth/**` | auth-service |
| `/api/users/**` | user-service |
| `/api/vendors/**`, `/api/studyhalls/**`, `/api/programs/**`, `/api/amenities/**`, `/api/bookings/**` | studyhall-service |
| `/api/owners/**`, `/api/properties/**`, `/api/blocks/**`, `/api/seats/**`, `/amenities/**`, `/images/**` | studyhall-service (legacy Partner API) |
| `/api/admin/**` | admin-service |
| `/internal/**` | **blocked** |
| `/swagger-ui.html`, `/docs/{service}/v3/api-docs` | aggregated Swagger UI |

It also handles CORS (`CORS_ALLOWED_ORIGINS`) and adds a correlation ID (`X-Correlation-Id`,
generated or passed through). It rejects invalid or expired JWTs and obviously wrong roles early,
but every service still enforces its own security. It strips any client-sent `X-Internal-Api-Key`,
logs each request, and caps request bodies at 2 MB.

Env: `JWT_SECRET`, `JWT_ISSUER`, `CORS_ALLOWED_ORIGINS`, `AUTH_SERVICE_URL`, `USER_SERVICE_URL`,
`STUDYHALL_SERVICE_URL`, `ADMIN_SERVICE_URL`. **Tests:** 9 (edge security, public routes, internal
block, correlation ID, CORS).
