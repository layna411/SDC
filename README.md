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


Here is how the AI system in OralCare works across the entire pipeline:

[ Upload Clinical Photo ]
           │
           ▼
[ 1. Image Quality & Preprocessing (OpenCV) ]
   • Resolution verification (min 300x300)
   • Motion blur detection (Laplacian variance)
   • Lighting & contrast checks (Mean brightness 35–235)
   • CLAHE local adaptive lighting normalization
           │
           ▼
[ 2. Feature Extraction & AI Analysis ]
   • White plaque / Leukoplakia detection (LAB/HSV)
   • Erythematous / Red patch detection (LAB a* > 168)
   • Ulcerated / Dark mucosal irregularity
   • Texture heterogeneity (Canny edge density)
   (Or PyTorch TorchScript model via AI_MODEL_PATH)
           │
           ▼
[ 3. Holistic Clinical + AI Outcome Integration ]
   Combines:
   • Step 6 Clinical region findings
   • Step 7 Lesion documentation
   • Step 9 Biopsy & lab keywords
   • Step 10 AI image anomalies
           │
           ▼
[ 4. Unified Outcome: NORMAL or ABNORMAL ]
   + Diagnostic reasons & recommendations
   + Clinician governance (Confirm / Override)
1. Image Quality & Preprocessing (preprocessor.py)

Before any AI model evaluates an image, it passes through an objective OpenCV quality gate:

Sharpness / Blur Detection: Computes the Laplacian variance across the image. If 
Var
<
25.0
Var<25.0, the image is flagged as BLURRY (potential motion blur).
Illumination & Glare Check: Evaluates mean grayscale illumination. Values 
<
35
<35 or 
>
235
>235 are flagged as POOR_LIGHTING.
Contrast & Resolution: Checks that resolution meets minimum clinical standards (
≥
300
×
300
≥300×300).
Color Normalization: Converts BGR to RGB and applies CLAHE (Contrast Limited Adaptive Histogram Equalization) in LAB color space to normalize localized camera flash shadows without distorting tissue color, resizing to standard 
224
×
224
224×224.
2. Anomaly Detection Engine (model_registry.py)

The system analyzes the central 70% region-of-interest (where mucosal tissue is framed) for 4 primary clinical markers:

Clinical Marker	Detection Logic	Target Oral Lesions
Keratotic / White Plaque	High luminance (
𝐿
∗
>
205
L
∗
>205) + low saturation (
𝑆
<
48
S<48) excluding specular reflections	Leukoplakia, Candidiasis, Lichen planus
Erythema / Red Lesion	High red chrominance (
𝑎
∗
>
168
a
∗
>168) + high saturation (
𝑆
>
115
S>115)	Erythroplakia, inflammation, erosions
Ulcer / Necrotic Area	Low luminance (
𝐿
∗
<
52
L
∗
<52) + elevated redness	Aphthous / traumatic ulcers, malignant margins
Texture Heterogeneity	Canny edge density 
>
0.24
>0.24 inside mucosal ROI	Mucosal roughness, induration, cobblestoning
Scoring: Computes a weighted anomaly_score. If 
Score
≥
0.36
Score≥0.36 or any flagged threshold is crossed, the image is classified as ABNORMAL; otherwise NORMAL.
Deep Learning Support: If a validated deep-learning model (.pt or .pts TorchScript) is configured in .env (AI_MODEL_PATH), the registry seamlessly switches from the OpenCV analyzer to the deep neural network.
3. Holistic Outcome Integration (screening_service.py)

The AI image outcome is never isolated from medical reality. The final patient screening result combines:

AI Image Analysis: Any uploaded photo flagged as abnormal.
Clinical Findings: Any anatomical region observed as abnormal during examination.
Lesion Records: Size, site, and characteristics of any recorded oral lesions.
Biopsy / Lab Keywords: Detection of pathology terms (dysplasia, carcinoma, OSMF, etc.).
High-Risk Habits: Tobacco, arecanut, or alcohol consumption risk weighting.

If any factor indicates pathology, the screening outcome is assigned ABNORMAL with transparent contributing reasons. If all checks are clear, it is NORMAL.

4. Human-in-the-Loop Clinician Governance
The AI acts as an assistive diagnostic aid, not an autonomous decision maker.
Qualified dental clinicians can inspect AI confidence scores, detected flags, and visual metrics on the screening detail view.
Clinicians have full ability to Confirm or Override the AI flag, and rejected AI flags are discounted from subsequent clinical evaluations.
5. When Does the AI Run?
Initial Screening: When completing Step 10 & Step 11 of the screening wizard, the app automatically calls finalize-with-ai upon bundle submission.
"Examine Again" (New): When re-examining a patient from their profile, uploading clinical images automatically triggers finalize-with-ai, immediately updating the patient's diagnostic outcome, timeline, and downloadable PDF report.
On Demand: Individual images can also be re-analyzed at any time from the clinical image view.