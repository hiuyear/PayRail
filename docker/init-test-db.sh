#!/bin/bash
# creates a second, empty database for the test suite to use, with the same schema.
#
# tests need a database they can wipe between cases. pointing them at the dev database
# would mean `mvn test` deletes whatever you were just looking at, so they get their own.
#
# postgres runs everything in /docker-entrypoint-initdb.d once, in alphabetical order, when
# the data volume is first created. the zz- prefix on the mounted name makes this run after
# schema.sql has already set up the dev database.
set -e

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "CREATE DATABASE ${POSTGRES_DB}_test;"
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${POSTGRES_DB}_test" \
    -f /docker-entrypoint-initdb.d/schema.sql

echo "test database ${POSTGRES_DB}_test created"
