# Local Machine Notes (this machine only)

Not part of the team setup guide ([`SETUP.md`](./SETUP.md)) — this is a record
of the one-off fix-up work done on *this specific machine* (Windows 11, ~8GB
RAM), kept for reference in case something needs to be reproduced, reversed,
or diagnosed later.

## Timeline

1. **Found no MySQL, no Docker, no service on port 3306** — the app failed to
   start with `Connection refused` against `localhost:3306`.
2. **Installed MySQL Server 8.4.9 manually** via `winget install --id
   Oracle.MySQL` as a stop-gap, since Docker wasn't available yet:
   - Data directory: `C:\Users\USER\mysql-data`
   - Initialized with `mysqld --initialize-insecure`, then root password set
     to `root` and `spacz` database created.
   - Run directly as a background process (`Start-Process mysqld.exe
     --datadir=... --port=3306`), **not** installed as a Windows service
     (no admin rights in this shell) — meant it wouldn't survive a reboot.
3. **Installed Docker Desktop 4.91.0** via `winget install --id
   Docker.DockerDesktop`, to switch to the team's standard `docker-compose.yml`
   setup. WSL2 backend was already present on this machine, so no reboot was
   needed.
4. **Stopped the manual MySQL process** (`mysqladmin -u root -proot shutdown`)
   and started the containerized one instead (`docker compose up -d`), using
   the exact same `root`/`root`/`spacz` credentials so nothing else had to
   change.
5. **Hit a JVM out-of-memory error** (`paging file too small`) the first time
   the app ran alongside Docker Desktop — the WSL2 VM was claiming ~3.7GB RAM
   by default on an 8GB machine. Fixed by capping WSL2's memory:

   `C:\Users\USER\.wslconfig`:
   ```ini
   [wsl2]
   memory=2GB
   processors=2
   ```
   then `wsl --shutdown` followed by relaunching Docker Desktop. This is
   machine-level config, outside the repo — anyone else with a similar
   low-RAM machine would need to do this themselves (documented generically
   in `SETUP.md`'s troubleshooting section).

## Current state of this machine

- MySQL runs via `docker compose up -d` from the `spacz/` project folder —
  container name `spacz-mysql`, healthy, same credentials as the team default.
- The old manual `mysqld.exe` process is **stopped**. Its data directory
  (`C:\Users\USER\mysql-data`) is now unused leftover state — safe to delete
  if you want the disk space back, not required otherwise.
- `%USERPROFILE%\.wslconfig` caps WSL2 to 2GB RAM / 2 CPUs.
- Docker Desktop is not configured to launch automatically at login (default
  install behavior) — you may need to manually start it, or `docker compose
  up -d` will fail with a "docker daemon not running" type error until it's
  running.

## Optional cleanup

The manually-installed MySQL Server is no longer used and can be fully
removed if you want to reclaim disk space:

```powershell
Remove-Item -Recurse -Force "C:\Users\USER\mysql-data"
winget uninstall Oracle.MySQL
```

Not required — it's inactive and consumes no resources while stopped.

## If problems come back

- `docker info` — confirms Docker Desktop/engine is actually running.
- `docker compose ps` — confirms the `spacz-mysql` container is `healthy`.
- Still OOMing? Lower the JVM heap for a run:
  `JAVA_TOOL_OPTIONS="-Xmx384m" ./mvnw spring-boot:run` — or lower the
  `memory=` value in `.wslconfig` further.
