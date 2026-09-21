# Spacz Backend — Developer Setup Guide

This is the quick-start guide to get the backend running on your machine. For
architecture, API endpoints, and how the code works, see
[`BACKEND_DOCUMENTATION.md`](./BACKEND_DOCUMENTATION.md) instead.

## Prerequisites

- **Java 17** — check with `java -version`.
- **Docker Desktop** — used to run MySQL identically on every machine. [Download here](https://www.docker.com/products/docker-desktop/).
- **Git**.
- You do **not** need Maven installed globally — this repo ships the `mvnw`/`mvnw.cmd` wrapper.

## Quick start

```bash
git clone https://github.com/Scaltechnologies/Spacz_backend.git
cd Spacz_backend/spacz

# 1. Start MySQL (same DB, same credentials, on every machine)
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run        # macOS/Linux
mvnw.cmd spring-boot:run      # Windows
```

Then check:
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

No config edits needed — `docker-compose.yml` creates a MySQL 8.4 container with
database `spacz` and user `root`/password `root`, which is exactly what
`application.properties` expects by default. Data persists in a Docker volume
across restarts (`docker compose down`), and is only wiped by
`docker compose down -v`.

## Everyday commands

| Command | What it does |
|---|---|
| `docker compose up -d` | Start MySQL in the background |
| `docker compose ps` | Check container status (wait for `healthy`) |
| `docker compose logs -f mysql` | Tail MySQL's logs |
| `docker compose exec mysql mysql -uroot -proot spacz` | Open a MySQL shell inside the container |
| `docker compose down` | Stop MySQL, keep the data |
| `docker compose down -v` | Stop MySQL and **delete all data** (fresh start) |

## Overriding DB credentials (optional)

If you need to point the app at a different database (a different container,
a remote instance, different credentials), set these environment variables
before running — no need to edit any tracked file:

```bash
export DB_URL="jdbc:mysql://localhost:3306/spacz?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export DB_USERNAME=myuser
export DB_PASSWORD=mypassword
./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
$env:DB_USERNAME = "myuser"
$env:DB_PASSWORD = "mypassword"
mvnw.cmd spring-boot:run
```

## No Docker? Manual MySQL fallback

1. Install MySQL 8.x yourself.
2. Create a database named `spacz`, with a `root`/`root` login (to match the
   defaults) — or use the env var overrides above to point at your own setup.
3. Run `./mvnw spring-boot:run` / `mvnw.cmd spring-boot:run` as normal.

## Troubleshooting

- **`Communications link failure` / `Connection refused` on startup** — MySQL
  isn't running or isn't ready yet. Run `docker compose up -d` and wait until
  `docker compose ps` shows `healthy` before starting the app.
- **`Web server failed to start. Port 8080 was already in use.`** — another
  instance of the app is already running; stop it first.
- **Port 3306 already in use** (when running `docker compose up -d`) —
  something else (a local MySQL install, another container) is already using
  that port. Stop it, or change the `3306:3306` port mapping in
  `docker-compose.yml`.
- **JVM fails to start with an out-of-memory / "paging file too small" error**
  — on machines with limited RAM (8GB or less), Docker Desktop's WSL2 VM can
  claim several GB by default, starving the JVM. Fix by capping it: create
  `%USERPROFILE%\.wslconfig` with:
  ```ini
  [wsl2]
  memory=2GB
  processors=2
  ```
  then run `wsl --shutdown` and restart Docker Desktop. You can also cap the
  JVM heap directly for a run: `JAVA_TOOL_OPTIONS="-Xmx512m" ./mvnw spring-boot:run`.
