# ORALCARE – Development Status & Verification Tracker

## System Overview
**ORALCARE – Oral Health Screening & Clinical Management System** consists of:
- **Android Application** (`SDC/`): Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt DI, Retrofit + OkHttp, Room offline drafts, DataStore session management, CameraX + Photo Picker, WorkManager background sync.
- **FastAPI Backend & Modular AI Service** (`Backend/`): Python FastAPI, SQLAlchemy 2.0, Pydantic v2, Alembic migrations + MySQL 8+ DDL (`31` normalized tables), JWT + Refresh Token Rotation, Argon2id/PBKDF2 password hashing, strict 4-tier RBAC & organizational data isolation, OpenCV image quality validation, pluggable validated AI model registry, PDF/Excel/CSV report exports, and immutable audit logs.

---

## Module Completion Matrix

| Module | Backend API | MySQL Schema / Migrations | Android Frontend | Automated Tests | Status |
|---|---|---|---|---|---|
| 1. Blue & White Medical Design System + 15 Reusable Components | N/A | N/A | Complete (Light & Dark themes) | Unit tested | Complete |
| 2. Authentication, JWT Refresh Rotation, Lockout, Password Reset | Complete | `users`, `refresh_tokens`, `password_reset_tokens` | Complete (Login, Forced/Manual Change Password, Forgot/Reset) | Verified passing | Complete |
| 3. Four-Tier RBAC & Hierarchy (`SUPER_ADMIN`, `COLLEGE_ADMIN`, `CENTER_ADMIN`, `HEALTH_ORGANIZER`) | Complete | `colleges`, `centers`, `organizers`, `users` | Complete (Role-routed Dashboards & Admin CRUD) | Cross-college, cross-center, organizer isolation & privilege escalation verified | Complete |
| 4. Health Organizer 6-Tab Bottom Nav & Home Dashboard | Complete | Dashboard & analytics queries | Complete (Home, Create, Patients, Feedback, Reports, Activity) | Verified passing | Complete |
| 5. 11-Step Patient Registration & Oral Health Screening | Complete (step-by-step + atomic offline-sync bundle) | All clinical tables (`patients`, `screening_records`, `consent_records`, histories, findings, lesions, teeth, lab, summary) | Complete (All 11 steps, auto-drafts, resume, offline queue, Normal/Abnormal/Pending result screen) | Verified passing (including consent block & idempotency) | Complete |
| 6. Step 6 – Image-Guided 11-Region Clinical Findings | Complete | `clinical_findings`, `clinical_observations` | Complete (Custom Compose anatomical diagrams + 11 regions & all sub-regions) | Verified passing | Complete |
| 7. Step 8 – Interactive FDI Dental Chart | Complete | `teeth_examinations`, `tooth_surfaces` | Complete (Permanent 32 + Primary 20 teeth, M/D/O/B/L/I surfaces) | Verified passing | Complete |
| 8. Step 9 – Laboratory Investigation | Complete (configurable ref ranges + biopsy attachment) | `lab_investigations`, `lab_reference_ranges` | Complete | Verified passing | Complete |
| 9. Step 10 – Clinical Image Capture, Secure Storage & AI Integration | Complete (FastAPI + standalone `ai_service/main.py`) | `patient_images`, `ai_analysis`, `ai_review_records` | Complete (CameraX + Photo Picker + AI status + clinician review) | Verified passing (honest `MODEL_UNAVAILABLE`, AI never overrides clinician) | Complete (Model validation pending – see note below) |
| 10. Patient Management (Search, Filters, Detail Profile, Follow-Up & Referrals) | Complete | `patients`, `follow_up_records`, `referral_records` | Complete | Verified passing | Complete |
| 11. Feedback Management (5-Star Rating) | Complete | `feedback` | Complete | Verified passing | Complete |
| 12. Reports & Analytics (PDF, Excel, CSV Exports) | Complete | Scoped analytics queries | Complete (Charts, Breakdowns, Export & Share) | Verified passing (PDF/Excel/CSV bytes validated) | Complete |
| 13. Activity & Immutable Audit Logs | Complete | `activity_logs` | Complete | Verified passing | Complete |
| 14. Educational Resources (English & Tamil Manuals, YouTube Aids, Awareness) | Complete | `educational_resources`, `awareness_content` | Complete (Search, Language filter, Admin Publish/Delete) | Verified passing | Complete |
| 15. Offline Synchronization (Room + WorkManager) | Idempotency keys & atomic bundle API | `idempotency_key` unique indexes | Complete (`OralCareDatabase`, `OfflineSyncWorker`) | Backend idempotency verified | Complete |

---

## AI Model Validation Status (Important Clinical Safety Disclosure)
- The complete AI integration architecture is implemented and verified (`Backend/app/ai/preprocessor.py`, `model_registry.py`, `analyzer.py`, and standalone microservice `Backend/ai_service/main.py`).
- **OpenCV preprocessing & objective image quality checks** (resolution, Laplacian sharpness, illumination/brightness, contrast, CLAHE normalization) execute on every clinical image.
- **No clinically validated oral-lesion model weights are bundled by default.** When `AI_MODEL_PATH` is unset, the backend and Android app explicitly report `MODEL_UNAVAILABLE` and **never fabricate predictions or diagnoses**.
- Once a clinically validated oral-health TorchScript model artifact is approved by dental specialists, set `AI_MODEL_PATH`, `AI_MODEL_NAME`, and `AI_MODEL_VERSION` in `Backend/.env` (or register an adapter in `model_registry.py`) without modifying any Android screens or patient workflows.
- AI results remain strictly **assistive** and require clinician review (`PENDING_REVIEW`, `REVIEWED`, `ACCEPTED`, `REJECTED`, `NEEDS_FURTHER_EXAMINATION`); AI confidence alone never sets `NORMAL` or `ABNORMAL` screening status.

## Known Environment Notes
- On Python 3.14 environments running legacy `anyio 3.x`, upgrade to `anyio>=4.6.0` and install `argon2-cffi`, `alembic`, and `pytest` from `Backend/requirements.txt` before production deployment.
- Android Gradle build uses AGP `9.5.1` and Kotlin `2.2.10` (`SDC/gradle/libs.versions.toml`). Ensure Android Studio / Gradle has network access on first sync to fetch Hilt, Room, Retrofit, CameraX, and WorkManager artifacts.
