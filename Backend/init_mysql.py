"""Initialize MySQL database `oralcare_db`, create all 31 tables, and seed initial data."""

from urllib.parse import urlparse

import pymysql

from app.core.config import settings
from app.core.database import Base, SessionLocal, engine
import app.models.entities  # noqa: F401
from app.main import seed_initial_data


def main() -> None:
    parsed = urlparse(settings.DATABASE_URL.replace("mysql+pymysql://", "http://"))
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or 3306
    user = parsed.username or "root"
    password = parsed.password or ""
    db_name = (parsed.path or "/oralcare_db").lstrip("/")

    print(f"Connecting to MySQL at {host}:{port} as '{user}'...")
    conn = pymysql.connect(host=host, port=port, user=user, password=password, charset="utf8mb4")
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"CREATE DATABASE IF NOT EXISTS `{db_name}` "
                "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
            )
        conn.commit()
        print(f"Database '{db_name}' is ready.")
    finally:
        conn.close()

    print("Creating all OralCare tables in MySQL...")
    Base.metadata.create_all(bind=engine)
    print(f"Tables verified/created: {len(Base.metadata.sorted_tables)}")

    db = SessionLocal()
    try:
        seed_initial_data(db)
        print("Initial Super Admin, English/Tamil manuals, YouTube aids, and awareness content seeded.")
    finally:
        db.close()

    print("\nSetup complete!")
    print(f"Default Super Admin Username : {settings.INITIAL_SUPER_ADMIN_USERNAME}")
    print(f"Default Super Admin Password : {settings.INITIAL_SUPER_ADMIN_PASSWORD}")


if __name__ == "__main__":
    main()
