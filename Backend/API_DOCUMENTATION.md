# ORALCARE REST API v1 Reference

Base URL: `/api/v1`  
Interactive OpenAPI / Swagger UI: `/docs` | ReDoc: `/redoc`  
Authentication: `Authorization: Bearer <access_token>`

## 1. Authentication (`/api/v1/auth`)
- `POST /auth/login` – Authenticate by username, email, or user ID; returns JWT access token, refresh token, `must_change_password`, and verified user profile.
- `POST /auth/refresh` – Rotate refresh token (revokes entire token family on reuse detection).
- `POST /auth/logout` – Revoke active refresh tokens and record `LOGOUT` audit log.
- `GET /auth/me` – Return authenticated user profile.
- `POST /auth/change-password` – Change current/temporary password and revoke old refresh tokens.
- `POST /auth/forgot-password` – Generate time-limited password reset token.
- `POST /auth/reset-password` – Complete password reset and clear failed-login lockout.

## 2. Role Hierarchy & Organizations
- `GET/POST /users`, `PATCH /users/{id}/status` – Hierarchical account creation & activation/deactivation (`SUPER_ADMIN → COLLEGE_ADMIN → CENTER_ADMIN → HEALTH_ORGANIZER`).
- `GET/POST /colleges`, `GET/PUT /colleges/{id}`, `PATCH /colleges/{id}/status` – Super Admin dental college management + optional immediate College Admin provisioning.
- `GET/POST /centers`, `GET/PUT /centers/{id}`, `PATCH /centers/{id}/status` – Screening center management + Center Admin provisioning.
- `GET/POST /organizers`, `PUT /organizers/{id}` – Health Organizer Assistant management & center assignment.

## 3. Patients & 11-Step Clinical Screening
- `GET/POST /patients`, `GET/PUT /patients/{id}`, `GET /patients/{id}/screenings` – Role-scoped patient search, filter, sorting, pagination, and screening history.
- `POST /screenings/start` – Begin a draft screening.
- `POST /screenings/complete-bundle` – Atomic 11-step submission with `idempotency_key` for offline sync.
- `GET /screenings`, `GET /screenings/{id}`, `POST /screenings/{id}/submit`, `POST /screenings/{id}/clinician-review` – Screening retrieval, finalization, and clinician sign-off.
- `PUT/GET /consent/{screening_id}` – Step 1 Consent & Geolocation.
- `PUT /histories/{screening_id}/personal`, `/habits`, `/medical-dental` – Steps 3–5 Histories.
- `GET /clinical-findings/taxonomy`, `PUT/GET /clinical-findings/{screening_id}` – Step 6 11-region clinical findings.
- `PUT/GET /lesions/{screening_id}` – Step 7 Lesion details.
- `GET /teeth/chart-schema`, `PUT/GET /teeth/{screening_id}` – Step 8 FDI permanent/primary teeth & surfaces.
- `GET/PUT /laboratory/reference-ranges`, `PUT /laboratory/{screening_id}`, `POST/GET /laboratory/{screening_id}/biopsy-attachment` – Step 9 Lab investigations & configurable ranges.
- `POST /images/upload`, `GET /images/screening/{screening_id}`, `GET /images/{image_id}/content` – Step 10 Authenticated-only clinical image upload & secure retrieval.
- `GET /ai-analysis/status`, `POST /ai-analysis/analyze/{image_id}`, `GET /ai-analysis/screening/{screening_id}`, `POST /ai-analysis/{analysis_id}/review` – Step 10 OpenCV quality + pluggable AI model inference + clinician review.
- `POST /screenings/{id}/follow-ups`, `PATCH /screenings/follow-ups/{id}`, `POST /screenings/{id}/referrals` – Follow-up & referral tracking.

## 4. Feedback, Reports, Education, Activities & Settings
- `GET/POST /feedback`, `PUT/DELETE /feedback/{id}` – 5-star patient feedback & average rating.
- `GET /reports/dashboard` – Role-scoped dashboard quick stats, weekly series, monthly overview, and recent activities.
- `GET /reports/analytics` – Filterable analytics with college/center/organizer breakdowns.
- `GET /reports/export?export_format=pdf|excel|csv` – Downloadable backend-generated PDF, Excel, and CSV reports.
- `GET/POST /education/resources`, `POST /education/resources/{id}/file`, `GET /education/resources/{id}/download`, `PUT/DELETE /education/resources/{id}` – English/Tamil manuals & YouTube Oral Health Aids.
- `GET/POST /education/awareness`, `PUT/DELETE /education/awareness/{id}` – Awareness articles & posters.
- `GET /activities` – Immutable role-scoped audit logs.
- `GET/PUT /settings` – Super Admin application settings.
