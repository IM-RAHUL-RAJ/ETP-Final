#!/usr/bin/env bash
# Shared by apply.sh and check.sh: loads the repository's .env into the environment.

# Load KEY=VALUE entries from .env without word-splitting values (supports spaces and <> in values).
# A variable that is already set in the environment wins over the file.
load_env_file() {
    local env_file="$1"
    local line key value
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
