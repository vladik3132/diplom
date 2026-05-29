# TeacherLicence — Deployment Guide

## Prerequisites
- Docker 24+ & Docker Compose v2
- 2GB+ RAM on server
- Domain or IP address

## Quick Start

### 1. Clone & configure
```bash
git clone <repo-url> TeacherLicence
cd TeacherLicence
cp .env.example .env
```

### 2. Edit `.env`
```bash
nano .env
```
**Mandatory:**
- `DB_PASSWORD` — strong password for PostgreSQL
- `JWT_SECRET` — random string, minimum 32 characters

**Optional:**
- `MISTRAL_API_KEY` — for AI features (leave empty to disable)
- `AI_ENABLED=false` — disable AI completely
- `GOOGLE_DRIVE_ENABLED=true` + credentials — for cloud file storage

### 3. Google Drive (optional)
If using Google Drive for file storage:
```bash
# Place service account JSON at project root
cp /path/to/service-account.json ./google-credentials.json
```
Then uncomment the volume mount in `docker-compose.yml`:
```yaml
- ./google-credentials.json:/app/config/google-credentials.json:ro
```

### 4. DOCX Templates
Templates are in `./templates/`. They are auto-mounted into the backend container.

### 5. Build & Run
```bash
docker compose up -d --build
```

### 6. Access
- **Frontend:** http://your-server (port 80)
- **Backend API:** http://your-server:8081/api
- Default login: `admin/admin`

## Architecture

```
                    :80                  :8081
 Browser ──→ [Nginx/Frontend] ──/api/──→ [Backend] ──→ [PostgreSQL :5432]
                                  │
                                  └──/ws/──→ WebSocket
```

| Container | Image | Port | Description |
|-----------|-------|------|-------------|
| tl-frontend | nginx + React SPA | 80 | Static files + API reverse proxy |
| tl-backend | Java 21 + Spring Boot | 8081 | REST API + WebSocket |
| tl-postgres | PostgreSQL 16 | 5432 | Database |

## Commands

```bash
# Start
docker compose up -d

# Stop
docker compose down

# Rebuild after code changes
docker compose up -d --build

# View logs
docker compose logs -f backend
docker compose logs -f frontend

# Database backup
docker exec tl-postgres pg_dump -U postgres teacher_licence > backup.sql

# Database restore
cat backup.sql | docker exec -i tl-postgres psql -U postgres teacher_licence

# Reset database (WARNING: deletes all data!)
docker compose down -v
docker compose up -d --build
```

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USERNAME` | `postgres` | PostgreSQL username |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `DB_NAME` | `teacher_licence` | Database name |
| `DB_PORT` | `5432` | PostgreSQL exposed port |
| `JWT_SECRET` | dev default | JWT signing key (min 32 chars) |
| `AI_ENABLED` | `true` | Enable/disable AI features |
| `MISTRAL_API_KEY` | — | Mistral API key |
| `MISTRAL_CLIENT_SERVER_URL` | `https://api.mistral.ai` | Mistral API endpoint |
| `MISTRAL_MODEL` | `mistral-medium-latest` | AI model name |
| `GOOGLE_DRIVE_ENABLED` | `false` | Enable Google Drive storage |
| `GOOGLE_DRIVE_ROOT_FOLDER_ID` | — | Google Drive root folder |
| `DOCX_TEMPLATES_DIR` | `/app/templates` | DOCX template directory |

## HTTPS (Production)

For HTTPS, use a reverse proxy (Caddy, Traefik, or nginx with certbot):

### Option A: Caddy (simplest)
```Caddyfile
your-domain.com {
    reverse_proxy localhost:80
}
```

### Option B: nginx + Let's Encrypt
```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

## Troubleshooting

**Backend won't start:**
```bash
docker compose logs backend
# Check DB connection, env vars, migrations
```

**AI not working:**
- Verify `AI_ENABLED=true` in `.env`
- Check `MISTRAL_API_KEY` is set
- Backend logs: `docker compose logs backend | grep -i mistral`

**File uploads fail:**
- Check `uploads` volume: `docker volume inspect teacherlicence_uploads`
- If using Google Drive: verify credentials mount and `GOOGLE_DRIVE_ENABLED=true`

**Database migration errors:**
```bash
# Check Flyway status
docker compose exec backend java -jar app.jar --spring.flyway.repair
```
