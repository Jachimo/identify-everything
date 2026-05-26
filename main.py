import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "server"))

import uvicorn

if __name__ == "__main__":
    uvicorn.run(
        "identify.api.main:app",
        host="0.0.0.0",
        port=8000,
        reload=False,
        log_level="info",
    )
