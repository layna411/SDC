# ORALCARE – Oral Health Screening & Clinical Management System

A production-oriented, multi-role healthcare platform built with **Kotlin Jetpack Compose** (Android), **Python FastAPI + SQLAlchemy 2.0** (Backend), **MySQL 8+** (Relational Database), and a **Modular OpenCV + PyTorch-ready AI Service**.

---

## Repository Structure

```text
SDC/
├── Backend/                        # FastAPI REST API, MySQL models, Alembic migrations, AI service, Tests
│   ├── app/
│   │   ├── main.py                 # FastAPI entrypoint, CORS, lifespan seed, /api/v1 router mounting
│   │   ├── core/                   # Config, SQLAlchemy 2.0 DB engine, Argon2/JWT security, 4-tier RBAC
│   │   ├── models/                 # 31 normalized relational entities (users, colleges, centers, patients, 11-step clinical records, audit logs)
│   │   ├── schemas/                # Pydantic v2 validation schemas
│   │   ├── repositories/           # Strict organizational scope query filters
│   │   ├── services/               # Auth, 11-step screening & clinical rules, PDF/Excel/CSV reporting, audit logs
│   │   ├── ai/                     # OpenCV image quality & preprocessing + pluggable validated model registry
│   │   ├── storage/                # Secure private file storage with magic-byte & size validation
│   │   └── api/v1/                 # 20 modular versioned REST routers
│   ├── ai_service/main.py          # Standalone AI FastAPI microservice wrapper
│   ├── migrations/                 # Alembic migrations + mysql_schema.sql (MySQL 8+ DDL)
│   ├── tests/test_oralcare_api.py  # End-to-end 4-role isolation, screening, AI & export test suite
│   ├── requirements.txt
│   └── .env.example
├── SDC/                            # Android Studio Kotlin + Jetpack Compose project (com.simats.sdc)
│   └── app/src/main/java/com/simats/sdc/
│       ├── core/                   # Blue & White healthcare theme, Room DB, DataStore, Network, WorkManager Sync
│       ├── data/                   # Retrofit API service, DTOs, Repository implementation
│       ├── domain/                 # Repository interfaces & domain contracts
│       ├── di/                     # Hilt DI modules
│       └── presentation/           # Reusable components, Auth, 4 Role Dashboards, 11-Step Wizard, Patients, Feedback, Reports, Activity, Education, Navigation
├── DEVELOPMENT_STATUS.md
└── README.md
```

---

## 1. MySQL 8+ Database Setup

1. Create the MySQL database and dedicated user:
```sql
CREATE DATABASE oralcare_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'oralcare_user'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_DB_PASSWORD';
GRANT ALL PRIVILEGES ON oralcare_db.* TO 'oralcare_user'@'localhost';
FLUSH PRIVILEGES;
```

2. Initialize schema using **Alembic** (recommended) or the generated SQL DDL:
```bash
cd Backend
cp .env.example .env
# Edit .env with your MySQL credentials and a 64-character JWT_SECRET_KEY
pip install -r requirements.txt
alembic upgrade head
```
Or directly import `Backend/migrations/mysql_schema.sql`:
```bash
mysql -u oralcare_user -p oralcare_db < migrations/mysql_schema.sql
```

---

## 2. Running the FastAPI Backend & AI Service

```bash
cd Backend
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
- **Swagger UI**: `http://127.0.0.1:8000/docs`
- **ReDoc**: `http://127.0.0.1:8000/redoc`
- **Health Check**: `http://127.0.0.1:8000/health`

Optional standalone AI microservice:
```bash
uvicorn ai_service.main:ai_app --host 0.0.0.0 --port 8001
```

### Bootstrap Super Admin Account
On first startup against an empty database, the backend creates the initial Super Admin configured in `.env` (`INITIAL_SUPER_ADMIN_USERNAME` / `INITIAL_SUPER_ADMIN_PASSWORD`).
- Super Admin logs in → creates Dental Colleges & College Admin credentials.
- College Admin logs in (prompted to change temporary password) → creates Screening Centers & Center Admin credentials.
- Center Admin logs in → creates Health Organizer Assistant accounts.
- Health Organizer Assistant logs in → accesses the 6-tab bottom navigation (`Home`, `Create`, `Patients`, `Feedback`, `Reports`, `Activity`).

---

## 3. Running Backend Automated Tests

```bash
cd Backend
python -m unittest discover -s tests -v
```

---

## 4. Android Studio Setup & Execution

1. Open `SDC/` in Android Studio.
2. Sync Gradle (`SDC/gradle/libs.versions.toml` and `SDC/app/build.gradle.kts`).
3. Ensure the backend is running on port `8000`:
   - On Android Emulator, the default API URL is `http://10.0.2.2:8000/api/v1/`.
   - On a physical device on the same LAN, tap the top-right **Server Settings icon** on the Login screen and enter `http://<YOUR_PC_LAN_IP>:8000/api/v1/` (or your production `https://` endpoint).
4. Run the `app` configuration or execute Android unit tests:
```bash
cd SDC
./gradlew testDebugUnitTest assembleDebug
```

---

## 5. Production Deployment Checklist

- **TLS / HTTPS**: Terminate TLS via Nginx/Caddy/ALB in front of Uvicorn/Gunicorn (`gunicorn -k uvicorn.workers.UvicornWorker -w 4 app.main:app`). Android `network_security_config.xml` enforces HTTPS for all non-local domains.
- **Secrets**: Generate a strong random `JWT_SECRET_KEY`, set `ENVIRONMENT=production`, `DEBUG=false`, and restrict `CORS_ORIGINS`.
- **File Storage**: Store `STORAGE_ROOT` on an encrypted volume outside web roots (or swap `SecureFileStorage` with S3/GCS signed access).
- **Backups & Retention**: Schedule encrypted daily `mysqldump --single-transaction` backups and configure retention in Super Admin Settings.
- **Clinical & Regulatory Sign-off**: Complete local healthcare privacy/consent review and clinically validate any oral-health AI model before setting `AI_MODEL_PATH`.
