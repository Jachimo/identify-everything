import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from ..database import init_db
from .routers import items, sync, search
from .config import settings

logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting up — initializing database…")
    init_db()
    logger.info("Database ready.")
    yield
    logger.info("Shutting down.")


app = FastAPI(
    title="Identify Everything API",
    description="REST API for the Identify Everything item-tracking system.",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,  # credentials not supported with wildcard origins per Fetch spec
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(items.router)
app.include_router(sync.router)
app.include_router(search.router)


@app.get("/", include_in_schema=False)
def root():
    return JSONResponse({"message": "Identify Everything API", "docs": "/docs"})


@app.get("/health", tags=["health"])
def health():
    return {"status": "ok"}
