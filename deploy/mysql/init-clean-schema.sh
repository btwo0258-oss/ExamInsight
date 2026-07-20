#!/usr/bin/env bash
set -Eeo pipefail

echo "Initializing ExamInsight database schema without local test records"

# The repository dump contains local users, conversations and Windows file paths.
# Keep only schema statements for a safe first public deployment.
grep -v '^INSERT INTO `' /seed/llm.sql | docker_process_sql

# These newer feature tables are maintained as incremental scripts.
docker_process_sql < /seed/add_xfyun_media_tables.sql
docker_process_sql < /seed/add_presentation_tables.sql
