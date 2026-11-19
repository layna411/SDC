from contextlib import asynccontextmanager
import secrets

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from app.api.v1 import (
    activities,
    ai_analysis,
    auth,
    centers,
    clinical_findings,
    colleges,
    consent,
    education,
    feedback,
    histories,
    images,
    laboratory,
    lesions,
    organizers,
    patients,
    reports,
    screenings,
    settings as settings_router,
    teeth,
    users,
)
from app.core.config import settings
from app.core.database import Base, SessionLocal, engine
from app.core.security import hash_password
from app.models.entities import AwarenessContent, EducationalResource, LabReferenceRange, User
from app.models.enums import AwarenessContentType, EducationResourceType, UserRole


def seed_initial_data(db: Session) -> None:
    """Seed initial Super Admin and baseline bilingual educational content if DB is empty."""
    admin = db.query(User).filter(User.role == UserRole.SUPER_ADMIN).first()
    if not admin:
        admin = User(
            user_uid=f"OC-SA-{secrets.token_hex(4).upper()}",
            username=settings.INITIAL_SUPER_ADMIN_USERNAME,
            email=settings.INITIAL_SUPER_ADMIN_EMAIL,
            full_name="OralCare Super Administrator",
            password_hash=hash_password(settings.INITIAL_SUPER_ADMIN_PASSWORD),
            role=UserRole.SUPER_ADMIN,
            is_active=True,
            must_change_password=False,
        )
        db.add(admin)
        db.commit()
        db.refresh(admin)

    if db.query(EducationalResource).count() == 0:
        defaults = [
            EducationalResource(
                title="OralCare Field Screening User Manual (English)",
                description="Step-by-step guide for Health Organizer Assistants on consent capture, 11-step oral screening, clinical photography, and offline sync.",
                category="USER_MANUAL",
                language="English",
                resource_type=EducationResourceType.MANUAL_PDF,
                external_url="https://oralcare.health/manuals/oralcare-user-manual-en.pdf",
                created_by_user_id=admin.id,
            ),
            EducationalResource(
                title="ஓரல்கேர் வாய்வழி சுகாதார பரிசோதனை கையேடு (தமிழ்)",
                description="சுகாதார ஒருங்கிணைப்பாளர்களுக்கான 11-படி நோயாளி பதிவு மற்றும் வாய்வழி பரிசோதனை வழிகாட்டி.",
                category="USER_MANUAL",
                language="Tamil",
                resource_type=EducationResourceType.MANUAL_PDF,
                external_url="https://oralcare.health/manuals/oralcare-user-manual-ta.pdf",
                created_by_user_id=admin.id,
            ),
            EducationalResource(
                title="Proper Brushing & Oral Hygiene Technique",
                description="Educational video demonstrating modified Bass brushing technique and interdental cleaning aids.",
                category="ORAL_HEALTH_AIDS",
                language="English",
                resource_type=EducationResourceType.YOUTUBE_VIDEO,
                external_url="https://www.youtube.com/watch?v=xm9c5fWzQcY",
                created_by_user_id=admin.id,
            ),
            EducationalResource(
                title="Early Detection of Oral Mucosal Lesions",
                description="Clinical awareness video on recognizing white/red patches, non-healing ulcers, and tobacco risk factors.",
                category="ORAL_HEALTH_AIDS",
                language="English",
                resource_type=EducationResourceType.YOUTUBE_VIDEO,
                external_url="https://www.youtube.com/watch?v=3mG5yV9qJ3U",
                created_by_user_id=admin.id,
            ),
        ]
        db.add_all(defaults)
        db.commit()

    if db.query(AwarenessContent).count() == 0:
        awareness_items = [
            AwarenessContent(
                title="Tobacco & Arecanut: Impact on Oral Health",
                summary="Understanding how smokeless tobacco, betel quid, and arecanut elevate risk for oral potentially malignant disorders.",
                body=(
                    "Regular consumption of smoked or smokeless tobacco, pan masala, and arecanut significantly increases the risk "
                    "of oral submucous fibrosis, leukoplakia, erythroplakia, and oral cancer. Early periodic oral screening allows "
                    "qualified dental clinicians to detect mucosal changes early and initiate timely intervention."
                ),
                category="TOBACCO_CESSATION",
                language="English",
                content_type=AwarenessContentType.ARTICLE,
                created_by_user_id=admin.id,
            ),
            AwarenessContent(
                title="Daily Oral Hygiene & Warning Signs",
                summary="Five practical oral hygiene habits and red-flag symptoms requiring dental consultation.",
                body=(
                    "Brush twice daily using a soft-bristled toothbrush and fluoridated toothpaste, replace your brush every 3 months, "
                    "and rinse after meals. Seek prompt evaluation by a dental surgeon if you notice any ulcer lasting more than two weeks, "
                    "persistent white or red patches, restricted mouth opening, or unexplained oral bleeding."
                ),
                category="ORAL_HYGIENE",
                language="English",
                content_type=AwarenessContentType.POSTER,
                created_by_user_id=admin.id,
            ),
            AwarenessContent(
                title="வாய்வழி ஆரோக்கியம் மற்றும் ஆரம்பகால பரிசோதனையின் முக்கியத்துவம்",
                summary="புகையிலை மற்றும் பாக்கு பழக்கத்தால் ஏற்படும் பாதிப்புகள் மற்றும் தினசரி பல் பராமரிப்பு குறிப்புகள்.",
                body=(
                    "தினமும் இருமுறை மென்மையான பல் துலக்கியால் பல் துலக்கவும். வாயில் இரண்டு வாரங்களுக்கு மேல் ஆறாத புண், "
                    "வெள்ளை அல்லது சிவப்பு திட்டுகள், வாய் திறப்பதில் சிரமம் இருந்தால் உடனடியாக பல் மருத்துவரை அணுகவும்."
                ),
                category="ORAL_HYGIENE",
                language="Tamil",
                content_type=AwarenessContentType.ARTICLE,
                created_by_user_id=admin.id,
            ),
        ]
        db.add_all(awareness_items)
        db.commit()

    if db.query(LabReferenceRange).count() == 0:
        # Configurable laboratory parameter catalog (min/max intentionally left configurable by local lab standards)
        db.add_all(
            [
                LabReferenceRange(
                    parameter_key="haemoglobin",
                    display_name="Haemoglobin (Hb)",
                    unit="g/dL",
                    standard_source="Configure per local accredited laboratory standard",
                ),
                LabReferenceRange(
                    parameter_key="rbc",
                    display_name="Red Blood Cell Count (RBC)",
                    unit="million/uL",
                    standard_source="Configure per local accredited laboratory standard",
                ),
                LabReferenceRange(
                    parameter_key="total_count",
                    display_name="Total Leukocyte Count (TC)",
                    unit="cells/uL",
                    standard_source="Configure per local accredited laboratory standard",
                ),
                LabReferenceRange(
                    parameter_key="platelet_count",
                    display_name="Platelet Count",
                    unit="lakh/uL",
                    standard_source="Configure per local accredited laboratory standard",
                ),
            ]
        )
        db.commit()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    settings.storage_path.mkdir(parents=True, exist_ok=True)
    # Create tables automatically in development/sqlite; use Alembic migrations in production MySQL
    if settings.ENVIRONMENT == "development" or settings.DATABASE_URL.startswith("sqlite"):
        Base.metadata.create_all(bind=engine)
        db = SessionLocal()
        try:
            seed_initial_data(db)
        finally:
            db.close()
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.APP_NAME,
        version=settings.APP_VERSION,
        description=(
            "Production-oriented REST API for ORALCARE – Oral Health Screening & Clinical Management System. "
            "Supports four-tier RBAC (Super Admin, College Admin, Center Admin, Health Organizer Assistant), "
            "11-step clinical screening workflow, modular AI image analysis with clinician review, reports, and immutable audit logs."
        ),
        docs_url="/docs",
        redoc_url="/redoc",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    api_prefix = "/api/v1"
    routers = [
        auth.router,
        users.router,
        colleges.router,
        centers.router,
        organizers.router,
        patients.router,
        consent.router,
        histories.router,
        clinical_findings.router,
        lesions.router,
        teeth.router,
        laboratory.router,
        images.router,
        ai_analysis.router,
        screenings.router,
        feedback.router,
        reports.router,
        education.router,
        activities.router,
        settings_router.router,
    ]
    for r in routers:
        app.include_router(r, prefix=api_prefix)

    @app.get("/health", tags=["System"])
    def health_check():
        return {"status": "healthy", "service": settings.APP_NAME, "version": settings.APP_VERSION}

    return app


app = create_app()
