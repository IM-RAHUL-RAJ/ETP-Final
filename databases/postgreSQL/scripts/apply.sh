#!/usr/bin/env bash
#
# Builds the PostgreSQL database for the platform: one database (DB_NAME) with the schemas
# "trade" and "auth". Connection settings come from the repository's .env (or the environment).
#
#   scripts/apply.sh                 create the database if missing, run schema.sql, then seed-data.sql
#   scripts/apply.sh --reset         drop both schemas first (reset.sql), then rebuild and re-seed
#   scripts/apply.sh --migrations    build from migrations/*.sql instead of schema.sql
#   scripts/apply.sh --no-seed       build the schema only, no baseline data
#   scripts/apply.sh --queries       afterwards, also run the sample queries in queries/
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$DB_DIR/../.." && pwd)"
MIGRATIONS_DIR="$DB_DIR/migrations"
QUERIES_DIR="$DB_DIR/queries"

RESET=false
USE_MIGRATIONS=false
SEED=true
RUN_QUERIES=false
for arg in "$@"; do
    case "$arg" in
        --reset)      RESET=true ;;
        --migrations) USE_MIGRATIONS=true ;;
        --no-seed)    SEED=false ;;
        --queries)    RUN_QUERIES=true ;;
        -h|--help)    sed -n '2,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "ERROR: unknown option $arg"; exit 1 ;;
    esac
done

# shellcheck source=lib-env.sh
. "$SCRIPT_DIR/lib-env.sh"

if [ -f "$REPO_ROOT/.env" ]; then
    load_env_file "$REPO_ROOT/.env"
elif [ -f ".env" ]; then
    load_env_file ".env"
fi

for required in DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD; do
    if [ -z "${!required:-}" ]; then
        echo "ERROR: $required is not set. Copy .env.example to .env at the repository root and fill it in."
        exit 1
    fi
done

export PGHOST="$DB_HOST"
export PGPORT="$DB_PORT"
export PGUSER="$DB_USER"
export PGPASSWORD="$DB_PASSWORD"

echo "Database: $DB_NAME on $DB_HOST:$DB_PORT as $DB_USER"

# Create the database when it does not exist yet (connects to the maintenance database "postgres").
exists="$(psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")"
if [ "$exists" != "1" ]; then
    echo "Creating database $DB_NAME"
    psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$DB_NAME\""
fi
export PGDATABASE="$DB_NAME"

run_sql() {
    echo "Applying: $1"
    psql -v ON_ERROR_STOP=1 -q -f "$1"
}

if [ "$RESET" = true ]; then
    run_sql "$DB_DIR/reset.sql"
fi

if [ "$USE_MIGRATIONS" = true ]; then
    shopt -s nullglob
    migrations=("$MIGRATIONS_DIR"/*.sql)
    if [ "${#migrations[@]}" -eq 0 ]; then
        echo "ERROR: no migration SQL files found in $MIGRATIONS_DIR"
        exit 1
    fi
    for migration in "${migrations[@]}"; do
        run_sql "$migration"
    done
else
    run_sql "$DB_DIR/schema.sql"
fi

if [ "$SEED" = true ]; then
    run_sql "$DB_DIR/seed-data.sql"
fi

if [ "$RUN_QUERIES" = true ] && [ -d "$QUERIES_DIR" ]; then
    export PGOPTIONS="-c search_path=trade,auth,public"
    for query_file in "$QUERIES_DIR"/*.sql; do
        echo "===================================="
        echo "Executing: $query_file"
        echo "===================================="
        psql -v ON_ERROR_STOP=1 -a -f "$query_file"
        echo ""
    done
fi

echo "Database setup completed successfully."
