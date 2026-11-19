from datetime import date
from typing import Optional

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import Response
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_client_ip, get_current_user
from app.models.entities import User
from app.models.enums import ScreeningOutcome
from app.schemas.operations import DashboardStatsResponse, ReportAnalyticsResponse
from app.services.activity_service import log_activity
from app.services.report_service import (
    build_dashboard_stats,
    build_report_analytics,
    export_report_csv,
    export_report_excel,
    export_report_pdf,
)

router = APIRouter(prefix="/reports", tags=["Dashboard, Reports & Exports"])


@router.get("/dashboard", response_model=DashboardStatsResponse)
def get_dashboard(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return build_dashboard_stats(db, current_user)


@router.get("/analytics", response_model=ReportAnalyticsResponse)
def get_analytics_report(
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    organizer_user_id: Optional[int] = None,
    outcome: Optional[ScreeningOutcome] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return build_report_analytics(
        db,
        current_user,
        start_date=start_date,
        end_date=end_date,
        college_id=college_id,
        center_id=center_id,
        organizer_user_id=organizer_user_id,
        outcome=outcome,
    )


@router.get("/export")
def export_report(
    request: Request,
    export_format: str = Query(default="pdf", pattern="^(pdf|excel|csv)$"),
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    organizer_user_id: Optional[int] = None,
    outcome: Optional[ScreeningOutcome] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    analytics = build_report_analytics(
        db,
        current_user,
        start_date=start_date,
        end_date=end_date,
        college_id=college_id,
        center_id=center_id,
        organizer_user_id=organizer_user_id,
        outcome=outcome,
    )

    log_activity(
        db,
        action="REPORT_DOWNLOADED",
        entity_type="REPORT",
        entity_id=export_format.upper(),
        user=current_user,
        ip_address=get_client_ip(request),
        details={"format": export_format},
    )

    if export_format == "csv":
        data = export_report_csv(analytics)
        return Response(
            content=data,
            media_type="text/csv",
            headers={"Content-Disposition": "attachment; filename=oralcare_report.csv"},
        )
    if export_format == "excel":
        data = export_report_excel(analytics)
        return Response(
            content=data,
            media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            headers={"Content-Disposition": "attachment; filename=oralcare_report.xlsx"},
        )
    data = export_report_pdf(analytics)
    return Response(
        content=data,
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=oralcare_report.pdf"},
    )
