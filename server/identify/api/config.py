from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "sqlite:///./identify.db"
    upload_dir: str = "./uploads"
    sync_token_secret: str = "dev-secret-change-in-production"
    log_level: str = "INFO"
    max_upload_size: int = 5 * 1024 * 1024  # 5MB

    model_config = {"env_file": ".env"}


settings = Settings()
