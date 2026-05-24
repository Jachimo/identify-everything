import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = os.environ.get("DATABASE_URL", "sqlite:///./identify.db")
    upload_dir: str = os.environ.get("UPLOAD_DIR", "./uploads")
    sync_token_secret: str = os.environ.get("SYNC_TOKEN_SECRET", "dev-secret-change-in-production")
    log_level: str = os.environ.get("LOG_LEVEL", "INFO")
    max_upload_size: int = 5 * 1024 * 1024  # 5MB

    class Config:
        env_file = ".env"


settings = Settings()
