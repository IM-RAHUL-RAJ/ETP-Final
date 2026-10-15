#!/usr/bin/env bash
# Source this file to load the repository's .env into the current shell:
#
#     . "$(dirname "${BASH_SOURCE[0]}")/../../scripts/load-env.sh"
#
# KEY=VALUE lines are read as plain text (values may contain spaces, < and >; they are never
# executed), and a variable that is already set in the environment wins over the file.
# Stops with a clear message when the file is missing or a variable listed in `require_env` is empty.

ETP_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

load_env_file() {
    local env_file="$1" line key value
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
        [[ "$line" != *"="* ]] && continue
        key="${line%%=*}"
        value="${line#*=}"
        key="${key#"${key%%[![:space:]]*}"}"
        key="${key%"${key##*[![:space:]]}"}"
        [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
        [ -n "${!key+x}" ] && continue
        export "$key=$value"
    done < "$env_file"
}

require_env() {
    local name
    for name in "$@"; do
        if [ -z "${!name:-}" ]; then
            echo "ERROR: $name is not set. Copy .env.example to .env at the repository root and fill it in." >&2
            return 1
        fi
    done
}

if [ -f "$ETP_ROOT/.env" ]; then
    load_env_file "$ETP_ROOT/.env"
fi
