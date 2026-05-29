#!/bin/bash

# Create backup with timestamp
BACKUP_FILE="backup_$(date +%Y%m%d_%H%M%S).sql"; 
docker exec floor21_postgres pg_dump -U floor21_user -d floor21_db -F p --encoding=UTF8 > "$BACKUP_FILE" && echo "Backup created: $BACKUP_FILE"

# Convert DOS to UNIX
# dos2unix "$BACKUP_FILE"

# Authenticate GCP
gcloud auth activate-service-account --key-file./trusty-solution-405810-11ba61e62531.json

# Upload backup to GCS
gcloud storage cp "$BACKUP_FILE" gs://floor21_bkp/

# Restore latest backup
#LATEST_FILE=$(ls -t backup_*.sql | head -1);
#docker exec -i floor21_postgres psql -U floor21_user -d floor21_db < "$LATEST_FILE"