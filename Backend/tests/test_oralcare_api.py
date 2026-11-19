import asyncio
import io
import json as _json
import os
from pathlib import Path
import tempfile
import unittest
from urllib.parse import urlsplit
import uuid

import cv2
import numpy as np
from sqlalchemy.orm import sessionmaker


class _SimpleASGIResponse:
    def __init__(self, status_code: int, headers: dict, content: bytes):
        self.status_code = status_code
        self.headers = headers
        self.content = content

    @property
    def text(self) -> str:
        return self.content.decode("utf-8", errors="replace")

    def json(self):
        return _json.loads(self.text)


class _DummyWakeSocket:
    def fileno(self):
        return -1

    def close(self):
        pass


class _NoSocketLoop(asyncio.ProactorEventLoop):
    """ProactorEventLoop that avoids loopback socketpair creation in network-isolated sandboxes."""

    def _make_self_pipe(self):
        self._ssock = _DummyWakeSocket()
        self._csock = _DummyWakeSocket()
        self._internal_fds = 0

    def _close_self_pipe(self):
        self._ssock = None
        self._csock = None

    def _write_to_self(self):
        pass

    def _loop_self_reading(self, f=None):
        pass


class _PureASGITestClient:
    """Zero-dependency ASGI client compatible with Python 3.14+ and network-isolated sandboxes."""

    def __init__(self, asgi_app):
        self.app = asgi_app
        self._loop = _NoSocketLoop()
        asyncio.set_event_loop(self._loop)

    def request(self, method: str, url: str, headers=None, json=None, data=None, files=None):
        split = urlsplit(url)
        raw_headers = []
        hdrs = {k.lower(): v for k, v in (headers or {}).items()}
        body = b""

        if json is not None:
            body = _json.dumps(json).encode("utf-8")
            hdrs.setdefault("content-type", "application/json")
        elif files is not None:
            boundary = f"----OralCareBoundary{uuid.uuid4().hex}"
            parts = []
            for k, v in (data or {}).items():
                parts.append(
                    f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode("utf-8")
                )
            for field_name, file_tuple in files.items():
                fname, fobj, ctype = file_tuple
                fbytes = fobj.read()
                parts.append(
                    (
                        f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field_name}\"; filename=\"{fname}\"\r\n"
                        f"Content-Type: {ctype}\r\n\r\n"
                    ).encode("utf-8")
                    + fbytes
                    + b"\r\n"
                )
            parts.append(f"--{boundary}--\r\n".encode("utf-8"))
            body = b"".join(parts)
            hdrs["content-type"] = f"multipart/form-data; boundary={boundary}"

        hdrs["content-length"] = str(len(body))
        for k, v in hdrs.items():
            raw_headers.append((k.encode("latin-1"), str(v).encode("latin-1")))

        scope = {
            "type": "http",
            "asgi": {"version": "3.0"},
            "http_version": "1.1",
            "method": method.upper(),
            "scheme": "http",
            "path": split.path,
            "raw_path": split.path.encode("utf-8"),
            "query_string": split.query.encode("utf-8"),
            "headers": raw_headers,
            "client": ("127.0.0.1", 50000),
            "server": ("testserver", 80),
        }

        async def _call():
            sent_body = False
            done_event = asyncio.Event()
            status_code = 500
            resp_headers = {}
            chunks = []

            async def receive():
                nonlocal sent_body
                if not sent_body:
                    sent_body = True
                    return {"type": "http.request", "body": body, "more_body": False}
                await done_event.wait()
                return {"type": "http.disconnect"}

            async def send(message):
                nonlocal status_code
                if message["type"] == "http.response.start":
                    status_code = message["status"]
                    for hk, hv in message.get("headers", []):
                        resp_headers[hk.decode("latin-1").lower()] = hv.decode("latin-1")
                elif message["type"] == "http.response.body":
                    chunks.append(message.get("body", b""))
                    if not message.get("more_body", False):
                        done_event.set()

            await self.app(scope, receive, send)
            return _SimpleASGIResponse(status_code, resp_headers, b"".join(chunks))

        return self._loop.run_until_complete(_call())

    def get(self, url, **kwargs):
        return self.request("GET", url, **kwargs)

    def post(self, url, **kwargs):
        return self.request("POST", url, **kwargs)

    def put(self, url, **kwargs):
        return self.request("PUT", url, **kwargs)

    def patch(self, url, **kwargs):
        return self.request("PATCH", url, **kwargs)

    def delete(self, url, **kwargs):
        return self.request("DELETE", url, **kwargs)


import importlib.metadata as _ilm
import anyio
import starlette.concurrency

# Run sync handlers directly on the test loop so no worker threads or loopback sockets are needed
async def _direct_run_in_threadpool(func, *args, **kwargs):
    return func(*args, **kwargs)


async def _direct_run_sync(func, *args, **kwargs):
    return func(*args)


starlette.concurrency.run_in_threadpool = _direct_run_in_threadpool
anyio.to_thread.run_sync = _direct_run_sync

# Always use _PureASGITestClient so tests run cleanly without binding loopback sockets
TestClient = _PureASGITestClient  # type: ignore

# Configure isolated SQLite test database & temp storage before importing app modules
_TEMP_DIR = Path(tempfile.mkdtemp(prefix="oralcare_test_"))
os.environ["DATABASE_URL"] = f"sqlite:///{_TEMP_DIR / 'test_oralcare.db'}"
os.environ["STORAGE_ROOT"] = str(_TEMP_DIR / "storage")
os.environ["ENVIRONMENT"] = "development"
os.environ["AI_MODEL_PATH"] = ""

from app.core.database import Base, build_engine, get_db  # noqa: E402
from app.main import app, seed_initial_data  # noqa: E402
from app.storage.file_storage import file_storage  # noqa: E402


def _make_sample_png_bytes(width: int = 400, height: int = 300) -> bytes:
    """Create a real synthetic image with texture/edges for OpenCV quality validation."""
    img = np.full((height, width, 3), 130, dtype=np.uint8)
    cv2.rectangle(img, (40, 40), (width - 40, height - 40), (180, 120, 140), 4)
    cv2.circle(img, (width // 2, height // 2), 60, (210, 160, 170), -1)
    cv2.putText(img, "ORAL", (110, 160), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (50, 40, 40), 3)
    ok, encoded = cv2.imencode(".png", img)
    assert ok
    return encoded.tobytes()


class OralCareBackendIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.engine = build_engine(f"sqlite:///{_TEMP_DIR / 'suite.db'}")
        cls.TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=cls.engine)
        Base.metadata.drop_all(bind=cls.engine)
        Base.metadata.create_all(bind=cls.engine)
        file_storage.root = (_TEMP_DIR / "storage_suite").resolve()
        file_storage.root.mkdir(parents=True, exist_ok=True)

        db = cls.TestingSessionLocal()
        try:
            seed_initial_data(db)
        finally:
            db.close()

        def override_get_db():
            db_sess = cls.TestingSessionLocal()
            try:
                yield db_sess
            finally:
                db_sess.close()

        app.dependency_overrides[get_db] = override_get_db
        cls.client = TestClient(app)

    @classmethod
    def tearDownClass(cls) -> None:
        app.dependency_overrides.clear()
        cls.engine.dispose()

    def _login(self, username: str, password: str) -> dict:
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": username, "password": password},
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        return resp.json()

    def _auth_header(self, token: str) -> dict:
        return {"Authorization": f"Bearer {token}"}

    def test_01_full_four_role_hierarchy_isolation_and_screening_workflow(self) -> None:
        # 1. Super Admin login & refresh token rotation
        sa_auth = self._login("superadmin", "OralCare@Admin2026!")
        sa_headers = self._auth_header(sa_auth["access_token"])
        self.assertEqual(sa_auth["user"]["role"], "SUPER_ADMIN")

        refreshed = self.client.post(
            "/api/v1/auth/refresh", json={"refresh_token": sa_auth["refresh_token"]}
        )
        self.assertEqual(refreshed.status_code, 200)
        # Reusing old refresh token must fail (rotation protection)
        reuse_attempt = self.client.post(
            "/api/v1/auth/refresh", json={"refresh_token": sa_auth["refresh_token"]}
        )
        self.assertEqual(reuse_attempt.status_code, 401)
        sa_headers = self._auth_header(refreshed.json()["access_token"])

        # 2. Super Admin creates two Dental Colleges + College Admins
        c1_resp = self.client.post(
            "/api/v1/colleges",
            headers=sa_headers,
            json={
                "college_code": "SIMATS01",
                "name": "Saveetha Dental College",
                "city": "Chennai",
                "state": "Tamil Nadu",
                "create_admin_account": True,
                "admin_username": "college_admin_1",
                "admin_full_name": "Dr. Priya College Admin",
            },
        )
        self.assertEqual(c1_resp.status_code, 201, c1_resp.text)
        college_1 = c1_resp.json()["college"]
        ca1_pwd = c1_resp.json()["temporary_password"]
        self.assertTrue(ca1_pwd)

        c2_resp = self.client.post(
            "/api/v1/colleges",
            headers=sa_headers,
            json={
                "college_code": "DENT02",
                "name": "Madras Oral Institute",
                "city": "Madurai",
                "create_admin_account": True,
                "admin_username": "college_admin_2",
                "admin_full_name": "Dr. Kumar College Admin",
            },
        )
        self.assertEqual(c2_resp.status_code, 201, c2_resp.text)
        college_2 = c2_resp.json()["college"]
        ca2_pwd = c2_resp.json()["temporary_password"]

        # 3. College Admin 1 logs in, verifies must_change_password, changes password
        ca1_auth = self._login("college_admin_1", ca1_pwd)
        self.assertTrue(ca1_auth["must_change_password"])
        ca1_headers = self._auth_header(ca1_auth["access_token"])

        chg = self.client.post(
            "/api/v1/auth/change-password",
            headers=ca1_headers,
            json={"current_password": ca1_pwd, "new_password": "NewCollege#2026"},
        )
        self.assertEqual(chg.status_code, 200)
        ca1_auth = self._login("college_admin_1", "NewCollege#2026")
        self.assertFalse(ca1_auth["must_change_password"])
        ca1_headers = self._auth_header(ca1_auth["access_token"])

        ca2_auth = self._login("college_admin_2", ca2_pwd)
        ca2_headers = self._auth_header(ca2_auth["access_token"])

        # Cross-college isolation check: College Admin 1 cannot access College 2
        forbidden_col = self.client.get(f"/api/v1/colleges/{college_2['id']}", headers=ca1_headers)
        self.assertEqual(forbidden_col.status_code, 403)

        # 4. College Admin 1 creates Center 1 & Center 2 with Center Admins
        ct1_resp = self.client.post(
            "/api/v1/centers",
            headers=ca1_headers,
            json={
                "center_code": "CTR-CHN-01",
                "name": "Poonamallee Outreach Center",
                "hub_name": "Chennai West Hub",
                "health_partner": "SIMATS Community Health",
                "create_admin_account": True,
                "admin_username": "center_admin_1",
                "admin_full_name": "Arun Center Admin",
            },
        )
        self.assertEqual(ct1_resp.status_code, 201, ct1_resp.text)
        center_1 = ct1_resp.json()["center"]
        ct1_pwd = ct1_resp.json()["temporary_password"]

        ct2_resp = self.client.post(
            "/api/v1/centers",
            headers=ca1_headers,
            json={
                "center_code": "CTR-CHN-02",
                "name": "Avadi Screening Center",
                "create_admin_account": True,
                "admin_username": "center_admin_2",
                "admin_full_name": "Deepa Center Admin",
            },
        )
        self.assertEqual(ct2_resp.status_code, 201, ct2_resp.text)
        center_2 = ct2_resp.json()["center"]
        ct2_pwd = ct2_resp.json()["temporary_password"]

        # Privilege escalation test: Center Admin 1 cannot create a College Admin
        ct1_headers = self._auth_header(self._login("center_admin_1", ct1_pwd)["access_token"])
        ct2_headers = self._auth_header(self._login("center_admin_2", ct2_pwd)["access_token"])

        esc = self.client.post(
            "/api/v1/users",
            headers=ct1_headers,
            json={
                "username": "illegal_college_admin",
                "full_name": "Illegal User",
                "role": "COLLEGE_ADMIN",
                "college_id": college_1["id"],
            },
        )
        self.assertEqual(esc.status_code, 403)

        # 5. Center Admin 1 creates two Health Organizer Assistants
        org1_resp = self.client.post(
            "/api/v1/organizers",
            headers=ct1_headers,
            json={
                "username": "organizer_1",
                "full_name": "Meena Health Organizer",
                "employee_code": "EMP-HO-001",
                "assigned_area": "Ward 12",
            },
        )
        self.assertEqual(org1_resp.status_code, 201, org1_resp.text)
        org1_pwd = org1_resp.json()["temporary_password"]

        org2_resp = self.client.post(
            "/api/v1/organizers",
            headers=ct1_headers,
            json={
                "username": "organizer_2",
                "full_name": "Ravi Health Organizer",
                "employee_code": "EMP-HO-002",
                "assigned_area": "Ward 15",
            },
        )
        self.assertEqual(org2_resp.status_code, 201, org2_resp.text)
        org2_pwd = org2_resp.json()["temporary_password"]

        org1_headers = self._auth_header(self._login("organizer_1", org1_pwd)["access_token"])
        org2_headers = self._auth_header(self._login("organizer_2", org2_pwd)["access_token"])

        # Organizer cannot create any user account
        org_create_fail = self.client.post(
            "/api/v1/users",
            headers=org1_headers,
            json={"username": "bad_user", "full_name": "Bad", "role": "HEALTH_ORGANIZER"},
        )
        self.assertEqual(org_create_fail.status_code, 403)

        # 6. Health Organizer 1 submits complete 11-step ABNORMAL patient screening (lesion present)
        abnormal_bundle = {
            "idempotency_key": "idem-screening-001",
            "patient": {
                "idempotency_key": "idem-patient-001",
                "prefix": "Mr.",
                "first_name": "Suresh",
                "last_name": "Babu",
                "age": 48,
                "sex": "MALE",
                "religion": "Hindu",
                "marital_status": "Married",
                "education": "Secondary",
                "occupation": "Driver",
                "family_income_per_month": "20000",
                "phone_number": "9876543210",
                "address": "14 Main Road, Poonamallee, Chennai",
            },
            "consent": {
                "hub": "Chennai West Hub",
                "health_partner": "SIMATS Community Health",
                "healthworker_name": "Meena Health Organizer",
                "participant_info_sheet_ack": True,
                "consent_form_signed": True,
                "consent_accepted": True,
                "terms_accepted": True,
                "geolocation_label": "Poonamallee Camp",
                "latitude": 13.0473,
                "longitude": 80.0945,
                "location_permission_granted": True,
            },
            "personal_history": {
                "diet_history": "Mixed",
                "spice_consumption": "High",
                "has_habit_history": True,
            },
            "habits": [
                {
                    "habit_category": "TOBACCO",
                    "habit_type": "Chewing Tobacco",
                    "frequency": "4 times/day",
                    "duration": "12 years",
                    "quantity": "2 packets/day",
                    "usage_status": "CURRENT",
                }
            ],
            "medical_dental_history": {
                "cleaning_aid": "Tooth Brush",
                "material_used": "Tooth Paste",
                "brushing_method": "Horizontal",
                "brushing_frequency": "Once daily",
                "brushing_duration": "1-2 minutes",
                "toothbrush_change_frequency": "Every 3 months",
                "other_hygiene_aids": "None",
                "family_history_conditions": ["Diabetes"],
                "past_medical_conditions": ["Hypertension"],
                "past_dental_history": True,
                "past_dental_details": "Tooth extraction 2 years ago",
                "denture_usage": False,
            },
            "clinical_findings": [
                {
                    "anatomical_region": "Buccal Mucosa",
                    "sub_region": "Right",
                    "status": "ABNORMAL",
                    "remarks": "Non-scrapable white patch noted",
                    "observations": [
                        {"observation_code": "WHITE_PATCH", "observation_label": "White patch / plaque observation"}
                    ],
                },
                {
                    "anatomical_region": "Labial Mucosa",
                    "sub_region": "Upper",
                    "status": "NORMAL",
                },
            ],
            "lesion_present": True,
            "lesions": [
                {
                    "anatomical_location": "Buccal Mucosa - Right",
                    "lesion_type": "Homogeneous White Plaque",
                    "size": "1.5 x 1.0 cm",
                    "appearance": "White, elevated border",
                    "duration": "3 months",
                    "symptoms": "Mild burning on spicy food",
                    "referral_recommendation": "Refer to Oral Medicine Department",
                }
            ],
            "teeth_examinations": [
                {
                    "dentition_type": "PERMANENT",
                    "tooth_number": "16",
                    "status": "CARIES",
                    "clinical_finding": "Occlusal caries",
                    "surfaces": [{"surface_code": "O", "condition": "CARIES"}],
                }
            ],
            "lab_investigation": {
                "haemoglobin": 12.8,
                "rbc": 4.5,
                "total_count": 7600,
                "platelet_count": 2.4,
                "percentage": 41.0,
                "biopsy_report_summary": "Biopsy advised at referral center",
            },
            "submit_immediately": True,
        }

        scr_resp = self.client.post(
            "/api/v1/screenings/complete-bundle", headers=org1_headers, json=abnormal_bundle
        )
        self.assertEqual(scr_resp.status_code, 201, scr_resp.text)
        screening = scr_resp.json()
        self.assertEqual(screening["outcome"], "ABNORMAL")
        self.assertTrue(screening["lesion_present"])
        patient_id = screening["patient_id"]
        screening_id = screening["id"]

        # Duplicate submission protection via idempotency key
        dup_resp = self.client.post(
            "/api/v1/screenings/complete-bundle", headers=org1_headers, json=abnormal_bundle
        )
        self.assertEqual(dup_resp.status_code, 201)
        self.assertEqual(dup_resp.json()["id"], screening_id)

        # 7. Strict role-based patient isolation checks
        # Organizer 2 CANNOT access Organizer 1's patient
        self.assertEqual(self.client.get(f"/api/v1/patients/{patient_id}", headers=org2_headers).status_code, 403)
        # Center Admin 2 (different center) CANNOT access Center 1's patient
        self.assertEqual(self.client.get(f"/api/v1/patients/{patient_id}", headers=ct2_headers).status_code, 403)
        # College Admin 2 (different college) CANNOT access College 1's patient
        self.assertEqual(self.client.get(f"/api/v1/patients/{patient_id}", headers=ca2_headers).status_code, 403)
        # Center Admin 1, College Admin 1, and Super Admin CAN access it
        self.assertEqual(self.client.get(f"/api/v1/patients/{patient_id}", headers=ct1_headers).status_code, 200)
        self.assertEqual(self.client.get(f"/api/v1/patients/{patient_id}", headers=ca1_headers).status_code, 200)
        self.assertEqual(self.client.get(f"/api/v1/patients/{patient_id}", headers=sa_headers).status_code, 200)

        # 8. Submit a NORMAL screening and verify consent enforcement
        no_consent_bundle = dict(abnormal_bundle)
        no_consent_bundle["idempotency_key"] = "idem-no-consent"
        no_consent_bundle["patient"] = dict(abnormal_bundle["patient"], idempotency_key="idem-pat-2", first_name="Anita", sex="FEMALE")
        no_consent_bundle["consent"] = dict(abnormal_bundle["consent"], consent_accepted=False)
        no_consent_resp = self.client.post(
            "/api/v1/screenings/complete-bundle", headers=org1_headers, json=no_consent_bundle
        )
        self.assertEqual(no_consent_resp.status_code, 400)

        normal_bundle = dict(abnormal_bundle)
        normal_bundle["idempotency_key"] = "idem-normal-002"
        normal_bundle["patient"] = dict(
            abnormal_bundle["patient"], idempotency_key="idem-pat-normal", first_name="Kavitha", sex="FEMALE"
        )
        normal_bundle["personal_history"] = {
            "diet_history": "Vegetarian",
            "spice_consumption": "Mild",
            "has_habit_history": False,
        }
        normal_bundle["habits"] = []
        normal_bundle["clinical_findings"] = [
            {"anatomical_region": "Buccal Mucosa", "sub_region": "Right", "status": "NORMAL"}
        ]
        normal_bundle["lesion_present"] = False
        normal_bundle["lesions"] = []
        norm_resp = self.client.post(
            "/api/v1/screenings/complete-bundle", headers=org1_headers, json=normal_bundle
        )
        self.assertEqual(norm_resp.status_code, 201, norm_resp.text)
        self.assertEqual(norm_resp.json()["outcome"], "NORMAL")

        # 9. Upload Clinical Image, Run OpenCV + AI Service (honest MODEL_UNAVAILABLE), Clinician Review
        img_bytes = _make_sample_png_bytes()
        up_resp = self.client.post(
            "/api/v1/images/upload",
            headers=org1_headers,
            data={"screening_id": str(screening_id), "clinical_region": "Buccal Mucosa", "sub_region": "Right"},
            files={"file": ("buccal_right.png", io.BytesIO(img_bytes), "image/png")},
        )
        self.assertEqual(up_resp.status_code, 201, up_resp.text)
        image_id = up_resp.json()["id"]

        # Unauthenticated image fetch blocked; unauthorized organizer blocked; authorized succeeds
        self.assertEqual(self.client.get(f"/api/v1/images/{image_id}/content").status_code, 401)
        self.assertEqual(self.client.get(f"/api/v1/images/{image_id}/content", headers=org2_headers).status_code, 403)
        self.assertEqual(self.client.get(f"/api/v1/images/{image_id}/content", headers=org1_headers).status_code, 200)

        ai_resp = self.client.post(f"/api/v1/ai-analysis/analyze/{image_id}", headers=org1_headers)
        self.assertEqual(ai_resp.status_code, 201, ai_resp.text)
        ai_data = ai_resp.json()
        # Active OpenCV Assistive Screening Analyzer runs real LAB/HSV mucosal feature extraction
        self.assertEqual(ai_data["status"], "COMPLETED")
        self.assertIsNotNone(ai_data["confidence_score"])
        self.assertIn(ai_data["model_output"]["classification"], ("NORMAL", "ABNORMAL"))
        self.assertEqual(ai_data["image_quality_status"], "ACCEPTABLE")
        self.assertEqual(ai_data["review_status"], "PENDING_REVIEW")

        # Finalize screening with AI combines all 11 steps + AI analysis
        fin_resp = self.client.post(f"/api/v1/screenings/{screening_id}/finalize-with-ai", headers=org1_headers)
        self.assertEqual(fin_resp.status_code, 200, fin_resp.text)
        self.assertEqual(fin_resp.json()["outcome"], "ABNORMAL")

        rev_resp = self.client.post(
            f"/api/v1/ai-analysis/{ai_data['id']}/review",
            headers=org1_headers,
            json={"review_status": "REVIEWED", "clinician_notes": "Confirmed right buccal plaque visually."},
        )
        self.assertEqual(rev_resp.status_code, 201, rev_resp.text)

        # 10. Feedback CRUD + 5-star rating average
        fb_resp = self.client.post(
            "/api/v1/feedback",
            headers=org1_headers,
            json={
                "patient_id": patient_id,
                "patient_name": "Suresh Babu",
                "email": "suresh@example.com",
                "rating": 5,
                "message": "Very thorough and caring oral screening team.",
            },
        )
        self.assertEqual(fb_resp.status_code, 201, fb_resp.text)
        fb_list = self.client.get("/api/v1/feedback", headers=org1_headers)
        self.assertEqual(fb_list.status_code, 200)
        self.assertEqual(fb_list.json()["average_rating"], 5.0)

        # 11. Dashboard, Reports & PDF / Excel / CSV Exports
        dash_org1 = self.client.get("/api/v1/reports/dashboard", headers=org1_headers)
        self.assertEqual(dash_org1.status_code, 200)
        self.assertEqual(dash_org1.json()["total_patients"], 2)
        self.assertEqual(dash_org1.json()["abnormal_screenings"], 1)
        self.assertEqual(dash_org1.json()["normal_screenings"], 1)

        # College Admin 2 sees 0 patients (complete cross-college analytics isolation)
        dash_ca2 = self.client.get("/api/v1/reports/dashboard", headers=ca2_headers)
        self.assertEqual(dash_ca2.status_code, 200)
        self.assertEqual(dash_ca2.json()["total_patients"], 0)

        for fmt, expected_ct in [
            ("csv", "text/csv"),
            ("excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            ("pdf", "application/pdf"),
        ]:
            exp = self.client.get(f"/api/v1/reports/export?export_format={fmt}", headers=ca1_headers)
            self.assertEqual(exp.status_code, 200, f"Export failed for {fmt}")
            self.assertIn(expected_ct, exp.headers["content-type"])
            self.assertGreater(len(exp.content), 50)

        # 12. Educational Resources (English + Tamil) & Activity Logs
        edu_res = self.client.get("/api/v1/education/resources", headers=org1_headers)
        self.assertEqual(edu_res.status_code, 200)
        languages = {r["language"] for r in edu_res.json()}
        self.assertIn("English", languages)
        self.assertIn("Tamil", languages)

        # Activity History must be strictly isolated per user (each user sees only their own activity)
        sa_logs = self.client.get("/api/v1/activities", headers=sa_headers)
        self.assertEqual(sa_logs.status_code, 200)
        sa_actions = {a["action"] for a in sa_logs.json()}
        self.assertIn("LOGIN", sa_actions)
        self.assertIn("COLLEGE_CREATED", sa_actions)
        self.assertNotIn("SCREENING_SUBMITTED", sa_actions)
        self.assertNotIn("IMAGE_UPLOADED", sa_actions)

        org1_logs = self.client.get("/api/v1/activities", headers=org1_headers)
        self.assertEqual(org1_logs.status_code, 200)
        org1_actions = {a["action"] for a in org1_logs.json()}
        self.assertIn("LOGIN", org1_actions)
        self.assertIn("SCREENING_SUBMITTED", org1_actions)
        self.assertIn("IMAGE_UPLOADED", org1_actions)
        self.assertNotIn("COLLEGE_CREATED", org1_actions)
        self.assertNotIn("REPORT_DOWNLOADED", org1_actions)

        ca1_logs = self.client.get("/api/v1/activities", headers=ca1_headers)
        self.assertEqual(ca1_logs.status_code, 200)
        ca1_actions = {a["action"] for a in ca1_logs.json()}
        self.assertIn("LOGIN", ca1_actions)
        self.assertIn("REPORT_DOWNLOADED", ca1_actions)
        self.assertNotIn("SCREENING_SUBMITTED", ca1_actions)

    def test_02_lockout_forgot_password_and_ai_never_overrides_clinician(self) -> None:
        from app.ai.model_registry import BaseOralHealthModel, ModelInferenceOutput, model_registry

        # Test forgot/reset password & failed-login lockout using Super Admin creating a test user
        sa_auth = self._login("superadmin", "OralCare@Admin2026!")
        sa_headers = self._auth_header(sa_auth["access_token"])

        colleges = self.client.get("/api/v1/colleges", headers=sa_headers).json()
        self.assertGreaterEqual(len(colleges), 1)
        centers = self.client.get("/api/v1/centers", headers=sa_headers).json()
        self.assertGreaterEqual(len(centers), 1)

        u_resp = self.client.post(
            "/api/v1/users",
            headers=sa_headers,
            json={
                "username": "lockout_tester",
                "email": "lockout@oralcare.org",
                "full_name": "Lockout Tester",
                "role": "HEALTH_ORGANIZER",
                "center_id": centers[0]["id"],
            },
        )
        self.assertEqual(u_resp.status_code, 201)

        # 5 wrong passwords lock the account
        for _ in range(5):
            bad = self.client.post(
                "/api/v1/auth/login", json={"username": "lockout_tester", "password": "WrongPassword!"}
            )
            self.assertEqual(bad.status_code, 401)
        locked = self.client.post(
            "/api/v1/auth/login", json={"username": "lockout_tester", "password": "WrongPassword!"}
        )
        self.assertEqual(locked.status_code, 423)

        # Forgot password + Reset password unlocks account
        fp = self.client.post(
            "/api/v1/auth/forgot-password", json={"username_or_email": "lockout_tester"}
        )
        self.assertEqual(fp.status_code, 200)
        reset_token = fp.json().get("dev_reset_token")
        self.assertTrue(reset_token)
        rp = self.client.post(
            "/api/v1/auth/reset-password",
            json={"reset_token": reset_token, "new_password": "ResetSuccess#2026"},
        )
        self.assertEqual(rp.status_code, 200)
        relogin = self._login("lockout_tester", "ResetSuccess#2026")
        tester_headers = self._auth_header(relogin["access_token"])

        # Step-by-step screening + plugged-in mock AI returning 0.99 confidence must NOT override NORMAL clinician finding
        class _HighConfidenceMockModel(BaseOralHealthModel):
            model_name = "test-validated-mock"
            model_version = "9.9.9-test"
            def is_ready(self) -> bool:
                return True
            def predict(self, normalized_image, clinical_region: str) -> ModelInferenceOutput:
                return ModelInferenceOutput(
                    available=True,
                    model_name=self.model_name,
                    model_version=self.model_version,
                    output={"assistive_flag": "SUSPICIOUS_CANDIDATE", "region": clinical_region},
                    confidence_score=0.99,
                    message="Assistive mock output only.",
                )

        pat = self.client.post(
            "/api/v1/patients",
            headers=tester_headers,
            json={
                "first_name": "Lakshmi",
                "last_name": "Narayanan",
                "age": 34,
                "sex": "FEMALE",
                "phone_number": "9123456789",
                "address": "Chennai",
            },
        )
        self.assertEqual(pat.status_code, 201)
        scr = self.client.post(
            "/api/v1/screenings/start",
            headers=tester_headers,
            json={"patient_id": pat.json()["id"]},
        )
        self.assertEqual(scr.status_code, 201)
        sid = scr.json()["id"]

        self.assertEqual(
            self.client.put(
                f"/api/v1/consent/{sid}",
                headers=tester_headers,
                json={
                    "hub": "Hub A",
                    "health_partner": "Partner A",
                    "healthworker_name": "Lockout Tester",
                    "participant_info_sheet_ack": True,
                    "consent_form_signed": True,
                    "consent_accepted": True,
                    "terms_accepted": True,
                },
            ).status_code,
            200,
        )
        self.assertEqual(
            self.client.put(
                f"/api/v1/clinical-findings/{sid}",
                headers=tester_headers,
                json=[{"anatomical_region": "Tongue", "sub_region": "Dorsal", "status": "NORMAL"}],
            ).status_code,
            200,
        )
        self.assertEqual(
            self.client.put(
                f"/api/v1/lesions/{sid}",
                headers=tester_headers,
                json={"lesion_present": False, "lesions": []},
            ).status_code,
            200,
        )

        img = self.client.post(
            "/api/v1/images/upload",
            headers=tester_headers,
            data={"screening_id": str(sid), "clinical_region": "Tongue", "sub_region": "Dorsal"},
            files={"file": ("tongue.png", io.BytesIO(_make_sample_png_bytes()), "image/png")},
        )
        self.assertEqual(img.status_code, 201)

        model_registry.register_model(_HighConfidenceMockModel())
        try:
            ai_res = self.client.post(
                f"/api/v1/ai-analysis/analyze/{img.json()['id']}", headers=tester_headers
            )
            self.assertEqual(ai_res.status_code, 201)
            self.assertEqual(ai_res.json()["status"], "COMPLETED")
            self.assertEqual(ai_res.json()["confidence_score"], 0.99)

            # Clinician rejects the suspicious AI flag -> clinician decision takes precedence -> NORMAL
            rej = self.client.post(
                f"/api/v1/ai-analysis/{ai_res.json()['id']}/review",
                headers=tester_headers,
                json={"review_status": "REJECTED", "clinician_notes": "Clinician verified mucosa is normal."},
            )
            self.assertEqual(rej.status_code, 201)

            sub = self.client.post(f"/api/v1/screenings/{sid}/submit", headers=tester_headers, json={})
            self.assertEqual(sub.status_code, 200)
            self.assertEqual(sub.json()["outcome"], "NORMAL")
        finally:
            model_registry.clear_custom_model()


if __name__ == "__main__":
    unittest.main()
