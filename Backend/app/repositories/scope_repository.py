from sqlalchemy.orm import Query

from app.models.entities import (
    ActivityLog,
    Center,
    College,
    Feedback,
    Organizer,
    Patient,
    ScreeningRecord,
    User,
)
from app.models.enums import UserRole


def scope_colleges_query(query: Query, user: User) -> Query:
    query = query.filter(College.is_deleted.is_(False))
    if user.role == UserRole.SUPER_ADMIN:
        return query
    return query.filter(College.id == user.college_id)


def scope_centers_query(query: Query, user: User) -> Query:
    query = query.filter(Center.is_deleted.is_(False))
    if user.role == UserRole.SUPER_ADMIN:
        return query
    if user.role == UserRole.COLLEGE_ADMIN:
        return query.filter(Center.college_id == user.college_id)
    return query.filter(Center.id == user.center_id)


def scope_organizers_query(query: Query, user: User) -> Query:
    query = query.filter(Organizer.is_deleted.is_(False))
    if user.role == UserRole.SUPER_ADMIN:
        return query
    if user.role == UserRole.COLLEGE_ADMIN:
        return query.filter(Organizer.college_id == user.college_id)
    if user.role == UserRole.CENTER_ADMIN:
        return query.filter(Organizer.center_id == user.center_id)
    return query.filter(Organizer.user_id == user.id)


def scope_patients_query(query: Query, user: User) -> Query:
    query = query.filter(Patient.is_deleted.is_(False))
    if user.role == UserRole.SUPER_ADMIN:
        return query
    if user.role == UserRole.COLLEGE_ADMIN:
        return query.filter(Patient.college_id == user.college_id)
    if user.role == UserRole.CENTER_ADMIN:
        return query.filter(Patient.center_id == user.center_id)
    return query.filter(
        Patient.center_id == user.center_id,
        Patient.assigned_organizer_id == user.id,
    )


def scope_screenings_query(query: Query, user: User) -> Query:
    query = query.filter(ScreeningRecord.is_deleted.is_(False))
    if user.role == UserRole.SUPER_ADMIN:
        return query
    if user.role == UserRole.COLLEGE_ADMIN:
        return query.filter(ScreeningRecord.college_id == user.college_id)
    if user.role == UserRole.CENTER_ADMIN:
        return query.filter(ScreeningRecord.center_id == user.center_id)
    return query.filter(
        ScreeningRecord.center_id == user.center_id,
        ScreeningRecord.organizer_user_id == user.id,
    )


def scope_feedback_query(query: Query, user: User) -> Query:
    query = query.filter(Feedback.is_deleted.is_(False))
    if user.role == UserRole.SUPER_ADMIN:
        return query
    if user.role == UserRole.COLLEGE_ADMIN:
        return query.filter(Feedback.college_id == user.college_id)
    if user.role == UserRole.CENTER_ADMIN:
        return query.filter(Feedback.center_id == user.center_id)
    return query.filter(
        Feedback.center_id == user.center_id,
        Feedback.created_by_user_id == user.id,
    )


def scope_activities_query(query: Query, user: User) -> Query:
    """Scope activity history according to role hierarchy."""
    if user.role == UserRole.SUPER_ADMIN:
        return query
    if user.role == UserRole.COLLEGE_ADMIN:
        return query.filter(ActivityLog.college_id == user.college_id)
    if user.role == UserRole.CENTER_ADMIN:
        return query.filter(ActivityLog.center_id == user.center_id)
    return query.filter(ActivityLog.user_id == user.id)
