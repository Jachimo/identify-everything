from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ...database import get_db
from ...schemas.item import ItemOut
from ...services import item_service

router = APIRouter(prefix="/api/v1/search", tags=["search"])


@router.get("", response_model=list[ItemOut])
def search_items(q: str = Query(..., min_length=1), db: Session = Depends(get_db)):
    items = item_service.search_items(db, q)
    return [ItemOut.model_validate(item) for item in items]
