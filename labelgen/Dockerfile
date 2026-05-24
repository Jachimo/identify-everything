FROM python:3.11-slim

WORKDIR /app

# Install python-qrcode and dependencies
RUN pip install --no-cache-dir python-qrcode[full] reportlab pillow

# Copy application code
COPY labelgen/ ./labelgen/
COPY scripts/ ./scripts/

# Create examples directory
RUN mkdir -p examples

# Verify installation
RUN python -m labelgen --help

CMD ["python", "-m", "labelgen", "--help"]
