from datetime import date
import secrets
from typing import List, Optional, Tuple

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import utc_now
from app.models.entities import (
    Center,
    ClinicalFinding,
    ClinicalObservation,
    ConsentRecord,
    ConsultationSummary,
    DentalHistory,
    HabitHistory,
    LabInvestigation,
    LabReferenceRange,
    LesionDetail,
    MedicalHistory,
    Patient,
    PersonalHistory,
    ScreeningRecord,
    TeethExamination,
    ToothSurface,
    User,
)
from app.models.enums import (
    AIReviewStatus,
    FindingStatus,
    ScreeningOutcome,
    ScreeningWorkflowStatus,
    UserRole,
)
from app.schemas.patient_screening import (
    ClinicalFindingInput,
    CompleteScreeningBundleRequest,
    ConsentStepInput,
    HabitItemInput,
    LabInvestigationInput,
    LesionBatchInput,
    MedicalDentalHistoryInput,
    PatientCreateRequest,
    PersonalHistoryInput,
    ToothExaminationInput,
)
from app.services.activity_service import log_activity


def generate_patient_uid() -> str:
    return f"OC-PAT-{date.today().strftime('%Y%m%d')}-{secrets.token_hex(3).upper()}"


def generate_screening_uid() -> str:
    return f"OC-SCR-{date.today().strftime('%Y%m%d')}-{secrets.token_hex(3).upper()}"


def resolve_patient_scope(db: Session, user: User, requested_center_id: Optional[int]) -> Tuple[int, int, int]:
    """Return (college_id, center_id, assigned_organizer_id) enforcing role scope."""
    if user.role == UserRole.HEALTH_ORGANIZER:
        if not user.college_id or not user.center_id:
            raise HTTPException(status_code=400, detail="Health organizer is not assigned to a center.")
        return user.college_id, user.center_id, user.id

    if user.role == UserRole.CENTER_ADMIN:
        if not user.college_id or not user.center_id:
            raise HTTPException(status_code=400, detail="Center admin is not assigned to a center.")
        return user.college_id, user.center_id, user.id

    if not requested_center_id:
        raise HTTPException(status_code=400, detail="center_id is required when creating a patient as admin.")

    center = db.query(Center).filter(Center.id == requested_center_id, Center.is_deleted.is_(False)).first()
    if not center:
        raise HTTPException(status_code=404, detail="Selected center not found.")
    if user.role == UserRole.COLLEGE_ADMIN and center.college_id != user.college_id:
        raise HTTPException(status_code=403, detail="Cannot register patient for a center in another college.")
    return center.college_id, center.id, user.id


def create_patient_record(
    db: Session,
    user: User,
    payload: PatientCreateRequest,
    ip_address: str = "unknown",
) -> Patient:
    # Idempotency duplicate protection
    if payload.idempotency_key:
        existing = (
            db.query(Patient)
            .filter(Patient.idempotency_key == payload.idempotency_key, Patient.is_deleted.is_(False))
            .first()
        )
        if existing:
            return existing

    college_id, center_id, assigned_organizer_id = resolve_patient_scope(db, user, payload.center_id)

    patient = Patient(
        patient_uid=generate_patient_uid(),
        idempotency_key=payload.idempotency_key,
        college_id=college_id,
        center_id=center_id,
        assigned_organizer_id=assigned_organizer_id,
        created_by_user_id=user.id,
        registration_date=payload.registration_date,
        prefix=payload.prefix,
        first_name=payload.first_name.strip(),
        last_name=payload.last_name.strip(),
        age=payload.age,
        sex=payload.sex,
        religion=payload.religion,
        marital_status=payload.marital_status,
        education=payload.education,
        occupation=payload.occupation,
        family_income_per_month=payload.family_income_per_month,
        phone_number=payload.phone_number.strip(),
        address=payload.address.strip(),
    )
    db.add(patient)
    db.commit()
    db.refresh(patient)

    log_activity(
        db,
        action="PATIENT_CREATED",
        entity_type="PATIENT",
        entity_id=patient.patient_uid,
        user=user,
        college_id=college_id,
        center_id=center_id,
        ip_address=ip_address,
        details={"patient_id": patient.id},
    )
    return patient


def upsert_consent(db: Session, screening: ScreeningRecord, payload: ConsentStepInput) -> ConsentRecord:
    record = db.query(ConsentRecord).filter(ConsentRecord.screening_id == screening.id).first()
    if not record:
        record = ConsentRecord(screening_id=screening.id, patient_id=screening.patient_id)
        db.add(record)

    record.hub = payload.hub
    record.health_partner = payload.health_partner
    record.healthworker_name = payload.healthworker_name
    record.participant_info_sheet_ack = payload.participant_info_sheet_ack
    record.consent_form_signed = payload.consent_form_signed
    record.consent_accepted = payload.consent_accepted
    record.terms_accepted = payload.terms_accepted
    record.location_permission_granted = payload.location_permission_granted
    # Only record coordinates if location permission was granted
    if payload.location_permission_granted:
        record.geolocation_label = payload.geolocation_label
        record.latitude = payload.latitude
        record.longitude = payload.longitude
    else:
        record.geolocation_label = payload.geolocation_label
        record.latitude = None
        record.longitude = None

    screening.current_step = max(screening.current_step, 1)
    db.commit()
    db.refresh(record)
    return record


def upsert_personal_history(
    db: Session, screening: ScreeningRecord, payload: PersonalHistoryInput
) -> PersonalHistory:
    record = db.query(PersonalHistory).filter(PersonalHistory.screening_id == screening.id).first()
    if not record:
        record = PersonalHistory(screening_id=screening.id)
        db.add(record)
    record.diet_history = payload.diet_history
    record.spice_consumption = payload.spice_consumption
    record.has_habit_history = payload.has_habit_history
    record.notes = payload.notes
    if not payload.has_habit_history:
        db.query(HabitHistory).filter(HabitHistory.screening_id == screening.id).delete()
    screening.current_step = max(screening.current_step, 3)
    db.commit()
    db.refresh(record)
    return record


def replace_habit_histories(
    db: Session, screening: ScreeningRecord, habits: List[HabitItemInput]
) -> List[HabitHistory]:
    db.query(HabitHistory).filter(HabitHistory.screening_id == screening.id).delete()
    created: List[HabitHistory] = []
    for item in habits:
        row = HabitHistory(
            screening_id=screening.id,
            habit_category=item.habit_category.upper(),
            habit_type=item.habit_type,
            frequency=item.frequency,
            duration=item.duration,
            quantity=item.quantity,
            usage_status=item.usage_status.upper(),
            remarks=item.remarks,
        )
        db.add(row)
        created.append(row)
    screening.current_step = max(screening.current_step, 4)
    db.commit()
    for row in created:
        db.refresh(row)
    return created


def upsert_medical_dental_history(
    db: Session, screening: ScreeningRecord, payload: MedicalDentalHistoryInput
) -> Tuple[MedicalHistory, DentalHistory]:
    med = db.query(MedicalHistory).filter(MedicalHistory.screening_id == screening.id).first()
    if not med:
        med = MedicalHistory(screening_id=screening.id)
        db.add(med)
    med.family_history_conditions = payload.family_history_conditions
    med.family_history_other = payload.family_history_other
    med.past_medical_conditions = payload.past_medical_conditions
    med.past_medical_other = payload.past_medical_other
    med.current_medications = payload.current_medications
    med.allergies = payload.allergies

    dent = db.query(DentalHistory).filter(DentalHistory.screening_id == screening.id).first()
    if not dent:
        dent = DentalHistory(screening_id=screening.id)
        db.add(dent)
    dent.cleaning_aid = payload.cleaning_aid
    dent.material_used = payload.material_used
    dent.material_other = payload.material_other
    dent.brushing_method = payload.brushing_method
    dent.brushing_frequency = payload.brushing_frequency
    dent.brushing_duration = payload.brushing_duration
    dent.toothbrush_change_frequency = payload.toothbrush_change_frequency
    dent.other_hygiene_aids = payload.other_hygiene_aids
    dent.past_dental_history = payload.past_dental_history
    dent.past_dental_details = payload.past_dental_details if payload.past_dental_history else None
    dent.denture_usage = payload.denture_usage
    dent.denture_details = payload.denture_details if payload.denture_usage else None

    screening.current_step = max(screening.current_step, 5)
    db.commit()
    db.refresh(med)
    db.refresh(dent)
    return med, dent


def replace_clinical_findings(
    db: Session, screening: ScreeningRecord, findings: List[ClinicalFindingInput]
) -> List[ClinicalFinding]:
    existing = db.query(ClinicalFinding).filter(ClinicalFinding.screening_id == screening.id).all()
    for item in existing:
        db.delete(item)
    db.flush()

    created: List[ClinicalFinding] = []
    for f in findings:
        row = ClinicalFinding(
            screening_id=screening.id,
            anatomical_region=f.anatomical_region,
            sub_region=f.sub_region,
            status=f.status,
            remarks=f.remarks,
            reference_image_key=f.reference_image_key,
        )
        for obs in f.observations:
            row.observations.append(
                ClinicalObservation(
                    observation_code=obs.observation_code,
                    observation_label=obs.observation_label,
                    details=obs.details,
                )
            )
        db.add(row)
        created.append(row)

    screening.current_step = max(screening.current_step, 6)
    db.commit()
    for row in created:
        db.refresh(row)
    return created


def replace_lesion_details(
    db: Session, screening: ScreeningRecord, payload: LesionBatchInput
) -> List[LesionDetail]:
    db.query(LesionDetail).filter(LesionDetail.screening_id == screening.id).delete()
    screening.lesion_present = payload.lesion_present
    created: List[LesionDetail] = []
    if payload.lesion_present:
        for item in payload.lesions:
            row = LesionDetail(
                screening_id=screening.id,
                anatomical_location=item.anatomical_location,
                lesion_type=item.lesion_type,
                size=item.size,
                appearance=item.appearance,
                duration=item.duration,
                symptoms=item.symptoms,
                clinical_observations=item.clinical_observations,
                remarks=item.remarks,
                referral_recommendation=item.referral_recommendation,
            )
            db.add(row)
            created.append(row)
    screening.current_step = max(screening.current_step, 7)
    db.commit()
    for row in created:
        db.refresh(row)
    return created


def replace_teeth_examinations(
    db: Session, screening: ScreeningRecord, teeth: List[ToothExaminationInput]
) -> List[TeethExamination]:
    existing = db.query(TeethExamination).filter(TeethExamination.screening_id == screening.id).all()
    for item in existing:
        db.delete(item)
    db.flush()

    created: List[TeethExamination] = []
    for t in teeth:
        row = TeethExamination(
            screening_id=screening.id,
            dentition_type=t.dentition_type,
            tooth_number=t.tooth_number,
            status=t.status,
            clinical_finding=t.clinical_finding,
            remarks=t.remarks,
        )
        for s in t.surfaces:
            row.surfaces.append(
                ToothSurface(
                    surface_code=s.surface_code,
                    condition=s.condition,
                    notes=s.notes,
                )
            )
        db.add(row)
        created.append(row)
    screening.current_step = max(screening.current_step, 8)
    db.commit()
    for row in created:
        db.refresh(row)
    return created


def upsert_lab_investigation(
    db: Session, screening: ScreeningRecord, payload: LabInvestigationInput
) -> LabInvestigation:
    record = db.query(LabInvestigation).filter(LabInvestigation.screening_id == screening.id).first()
    if not record:
        record = LabInvestigation(screening_id=screening.id)
        db.add(record)

    record.test_date = payload.test_date
    record.haemoglobin = payload.haemoglobin
    record.haemoglobin_unit = payload.haemoglobin_unit
    record.rbc = payload.rbc
    record.rbc_unit = payload.rbc_unit
    record.total_count = payload.total_count
    record.total_count_unit = payload.total_count_unit
    record.platelet_count = payload.platelet_count
    record.platelet_count_unit = payload.platelet_count_unit
    record.percentage = payload.percentage
    record.percentage_label = payload.percentage_label
    record.biopsy_report_summary = payload.biopsy_report_summary
    record.laboratory_remarks = payload.laboratory_remarks

    # Snapshot configured lab reference ranges (never hardcoded universal values)
    ranges = db.query(LabReferenceRange).all()
    record.reference_ranges_snapshot = {
        r.parameter_key: {
            "display_name": r.display_name,
            "unit": r.unit,
            "min_value": r.min_value,
            "max_value": r.max_value,
            "standard_source": r.standard_source,
        }
        for r in ranges
    }

    screening.current_step = max(screening.current_step, 9)
    db.commit()
    db.refresh(record)
    return record


def evaluate_screening_outcome(screening: ScreeningRecord) -> Tuple[ScreeningOutcome, str, dict]:
    """
    Holistic Clinical + Assistive AI Screening Evaluator.
    Combines:
      1. Step 6 Clinical Findings (abnormal anatomical regions)
      2. Step 7 Lesion Details (lesion presence)
      3. Step 4 High-Risk Habits (tobacco / arecanut / alcohol risk scoring)
      4. Step 9 Biopsy / Lab flags
      5. Step 10 OpenCV / AI Image Analysis results (assistive flag)
    Returns a clear NORMAL or ABNORMAL screening outcome (or clinician-overridden status)
    with a transparent breakdown of all contributing factors.
    """
    abnormal_findings = [
        f for f in screening.clinical_findings if f.status == FindingStatus.ABNORMAL
    ]
    examined_findings = [
        f for f in screening.clinical_findings if f.status != FindingStatus.NOT_EXAMINED
    ]
    lesions_count = len(screening.lesion_details)

    # Check biopsy keywords
    biopsy_text = (
        screening.lab_investigation.biopsy_report_summary.lower()
        if screening.lab_investigation and screening.lab_investigation.biopsy_report_summary
        else ""
    )
    biopsy_abnormal = any(
        kw in biopsy_text
        for kw in ("dysplasia", "malignant", "carcinoma", "positive", "abnormal", "leukoplakia", "osmf", "suspicious")
    )

    # Check high-risk habits (informational risk score)
    high_risk_habits = [
        h for h in screening.habit_histories if h.habit_category.upper() in ("TOBACCO", "ARECANUT")
    ]

    # Collect Step 10 AI Image Analysis outcomes
    ai_abnormal_images = []
    ai_normal_images = []
    ai_confidences = []
    for img in screening.patient_images:
        if img.ai_analyses:
            latest_ai = img.ai_analyses[0]
            if latest_ai.confidence_score is not None:
                ai_confidences.append(latest_ai.confidence_score)
            out = latest_ai.model_output or {}
            cls_label = str(out.get("classification", "")).upper()
            # Respect clinician rejection of an AI flag
            if latest_ai.review_status == AIReviewStatus.REJECTED:
                continue
            if cls_label == "ABNORMAL":
                ai_abnormal_images.append(f"{img.clinical_region} ({img.sub_region or 'General'})")
            elif cls_label == "NORMAL":
                ai_normal_images.append(f"{img.clinical_region} ({img.sub_region or 'General'})")

    reasons = []
    if screening.lesion_present or lesions_count > 0:
        reasons.append(f"{max(lesions_count, 1)} oral lesion(s) documented in Step 7")
    if abnormal_findings:
        regions = sorted({f.anatomical_region for f in abnormal_findings})
        reasons.append(f"abnormal clinical finding(s) in {', '.join(regions)}")
    if biopsy_abnormal:
        reasons.append("suspicious/abnormal biopsy report summary in Step 9")
    if ai_abnormal_images:
        reasons.append(f"AI image analysis flagged candidate mucosal anomaly in {', '.join(ai_abnormal_images)}")

    avg_ai_conf = round(sum(ai_confidences) / len(ai_confidences), 4) if ai_confidences else None

    if reasons:
        outcome = ScreeningOutcome.ABNORMAL
        explanation = (
            "ABNORMAL: " + "; ".join(reasons) + ". Prompt dental specialist evaluation is recommended."
        )
    else:
        outcome = ScreeningOutcome.NORMAL
        normal_notes = []
        if examined_findings:
            normal_notes.append(f"{len(examined_findings)} anatomical site(s) examined normal")
        else:
            normal_notes.append("no abnormal clinical findings recorded")
        normal_notes.append("no oral lesions reported")
        if ai_normal_images:
            normal_notes.append(f"{len(ai_normal_images)} clinical image(s) analyzed as NORMAL by AI")
        if high_risk_habits:
            normal_notes.append(
                f"note: {len(high_risk_habits)} tobacco/arecanut habit(s) noted — routine preventive counseling advised"
            )
        explanation = "NORMAL: " + "; ".join(normal_notes) + "."

    ai_report = {
        "ai_classification": outcome.value,
        "images_analyzed": len(ai_abnormal_images) + len(ai_normal_images),
        "ai_abnormal_regions": ai_abnormal_images,
        "ai_normal_regions": ai_normal_images,
        "average_ai_confidence": avg_ai_conf,
        "clinical_abnormal_regions": sorted({f.anatomical_region for f in abnormal_findings}),
        "lesions_count": lesions_count,
        "high_risk_habit_count": len(high_risk_habits),
        "biopsy_flagged": biopsy_abnormal,
        "contributing_factors": reasons,
    }
    return outcome, explanation, ai_report


def submit_screening(
    db: Session,
    screening: ScreeningRecord,
    user: User,
    clinical_recommendations: Optional[str] = None,
    ip_address: str = "unknown",
) -> ScreeningRecord:
    # Enforce mandatory consent prior to submission
    consent = screening.consent_record
    if not consent or not (consent.consent_accepted and consent.terms_accepted and consent.consent_form_signed):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Screening cannot be submitted without signed consent form, consent acceptance, and terms acceptance.",
        )

    outcome, reason, ai_report = evaluate_screening_outcome(screening)
    screening.outcome = outcome
    screening.workflow_status = ScreeningWorkflowStatus.SUBMITTED
    screening.current_step = 11
    screening.submitted_at = utc_now()

    summary_payload = {
        "screening_uid": screening.screening_uid,
        "patient_uid": screening.patient.patient_uid if screening.patient else None,
        "patient_name": (
            f"{screening.patient.first_name} {screening.patient.last_name}"
            if screening.patient
            else None
        ),
        "consent_accepted": consent.consent_accepted,
        "terms_accepted": consent.terms_accepted,
        "habit_count": len(screening.habit_histories),
        "clinical_findings_total": len(screening.clinical_findings),
        "abnormal_findings_count": sum(
            1 for f in screening.clinical_findings if f.status == FindingStatus.ABNORMAL
        ),
        "lesion_present": screening.lesion_present,
        "lesions_count": len(screening.lesion_details),
        "teeth_examined_count": len(screening.teeth_examinations),
        "has_lab_investigation": screening.lab_investigation is not None,
        "uploaded_images_count": len(screening.patient_images),
        "outcome": outcome.value,
        "outcome_reason": reason,
        "ai_report": ai_report,
    }

    summary = (
        db.query(ConsultationSummary)
        .filter(ConsultationSummary.screening_id == screening.id)
        .first()
    )
    if not summary:
        summary = ConsultationSummary(
            screening_id=screening.id,
            generated_by_user_id=user.id,
        )
        db.add(summary)

    summary.outcome = outcome
    summary.outcome_reason = reason
    summary.clinical_recommendations = clinical_recommendations or summary.clinical_recommendations
    summary.summary_payload = summary_payload
    summary.generated_at = utc_now()

    db.commit()
    db.refresh(screening)

    log_activity(
        db,
        action="SCREENING_SUBMITTED",
        entity_type="SCREENING",
        entity_id=screening.screening_uid,
        user=user,
        college_id=screening.college_id,
        center_id=screening.center_id,
        ip_address=ip_address,
        details={"outcome": outcome.value, "lesion_present": screening.lesion_present},
    )
    return screening


def create_complete_screening_bundle(
    db: Session,
    user: User,
    bundle: CompleteScreeningBundleRequest,
    ip_address: str = "unknown",
) -> ScreeningRecord:
    """Atomic 11-step creation & submission with idempotency support for offline sync."""
    if bundle.submit_immediately and not (
        bundle.consent.consent_accepted
        and bundle.consent.terms_accepted
        and bundle.consent.consent_form_signed
    ):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Screening cannot be submitted without signed consent form, consent acceptance, and terms acceptance.",
        )

    if bundle.idempotency_key:
        existing_scr = (
            db.query(ScreeningRecord)
            .filter(
                ScreeningRecord.idempotency_key == bundle.idempotency_key,
                ScreeningRecord.is_deleted.is_(False),
            )
            .first()
        )
        if existing_scr:
            return existing_scr

    if bundle.existing_patient_id:
        patient = (
            db.query(Patient)
            .filter(Patient.id == bundle.existing_patient_id, Patient.is_deleted.is_(False))
            .first()
        )
        if not patient:
            raise HTTPException(status_code=404, detail="Existing patient not found.")
    elif bundle.patient:
        patient = create_patient_record(db, user, bundle.patient, ip_address=ip_address)
    else:
        raise HTTPException(
            status_code=400, detail="Either existing_patient_id or patient details must be provided."
        )

    screening = ScreeningRecord(
        screening_uid=generate_screening_uid(),
        idempotency_key=bundle.idempotency_key,
        patient_id=patient.id,
        college_id=patient.college_id,
        center_id=patient.center_id,
        organizer_user_id=user.id,
        screening_date=bundle.screening_date,
        workflow_status=ScreeningWorkflowStatus.DRAFT,
        current_step=1,
    )
    db.add(screening)
    db.commit()
    db.refresh(screening)

    upsert_consent(db, screening, bundle.consent)
    upsert_personal_history(db, screening, bundle.personal_history)
    if bundle.personal_history.has_habit_history:
        replace_habit_histories(db, screening, bundle.habits)
    upsert_medical_dental_history(db, screening, bundle.medical_dental_history)
    replace_clinical_findings(db, screening, bundle.clinical_findings)
    replace_lesion_details(
        db,
        screening,
        LesionBatchInput(lesion_present=bundle.lesion_present, lesions=bundle.lesions),
    )
    replace_teeth_examinations(db, screening, bundle.teeth_examinations)
    if bundle.lab_investigation:
        upsert_lab_investigation(db, screening, bundle.lab_investigation)

    db.refresh(screening)
    if bundle.submit_immediately:
        return submit_screening(
            db,
            screening,
            user,
            clinical_recommendations=bundle.clinical_recommendations,
            ip_address=ip_address,
        )
    return screening
