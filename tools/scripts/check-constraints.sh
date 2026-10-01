#!/usr/bin/env bash
#
# Copyright (c) 2025 Element Creations Ltd.
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
#
# GUA FORK constraint guard: fails on forbidden identifiers, AI attribution in commit messages or a committed keystore.
# Set GUA_CONSTRAINTS_BASE_REF=<ref> to scan only the commit messages in <ref>..HEAD.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[0;33m'
RESET=$'\033[0m'

violations=0

fail() {
    echo "${RED}✗ CONSTRAINT VIOLATION:${RESET} $1"
    violations=$((violations + 1))
}

ok() {
    echo "${GREEN}✓${RESET} $1"
}

# 1. Forbidden identifier strings in tracked files. This script is excluded: it contains the patterns.
FORBIDDEN_STRINGS=(
    "sarahlacerda"
    "me.sarahlacerda.gua"
    "dev.gua.sarahlacerda.me"
    "canada-goose"
)

SELF_REL="tools/scripts/check-constraints.sh"

echo "→ Scanning tracked file content for forbidden identifiers..."
for needle in "${FORBIDDEN_STRINGS[@]}"; do
    matches="$(git ls-files -z \
        | grep -zZv "^${SELF_REL}\$" \
        | xargs -0 grep -F -I -n -- "$needle" 2>/dev/null || true)"
    if [ -n "$matches" ]; then
        fail "forbidden string '${needle}' found in tracked files:"
        echo "$matches" | sed 's/^/    /'
    else
        ok "no occurrences of '${needle}'"
    fi
done

# 2. AI authorship attribution in commit messages.
echo "→ Scanning commit messages for AI attribution..."
if [ -n "${GUA_CONSTRAINTS_SKIP_HISTORY:-}" ]; then
    # Set when the caller has no meaningful range to scan.
    LOG_RANGE=""
elif [ -n "${GUA_CONSTRAINTS_BASE_REF:-}" ]; then
    LOG_RANGE="${GUA_CONSTRAINTS_BASE_REF}..HEAD"
else
    LOG_RANGE="HEAD"
fi

if [ -z "$LOG_RANGE" ]; then
    echo "${YELLOW}-${RESET} commit-message scan skipped (no range to scan)"
else
    ai_attr="$(git log "$LOG_RANGE" --format='%H%n%B' 2>/dev/null \
        | grep -i -E 'Co-Authored-By:[[:space:]]*Claude|Generated with .*(Claude|AI|Anthropic)|🤖 Generated with' || true)"
    if [ -n "$ai_attr" ]; then
        fail "AI authorship attribution found in commit messages (range: ${LOG_RANGE}):"
        echo "$ai_attr" | sed 's/^/    /'
    else
        ok "no AI attribution in commit messages (range: ${LOG_RANGE})"
    fi
fi

# 3. Committed keystores or signing secrets, except the two upstream keystores under app/signature.
echo "→ Scanning for committed keystores / signing secrets..."
ALLOWED_KEYSTORES=(
    "app/signature/debug.keystore"
    "app/signature/nightly.keystore"
)

is_allowed() {
    local f="$1"
    for allowed in "${ALLOWED_KEYSTORES[@]}"; do
        [ "$f" = "$allowed" ] && return 0
    done
    return 1
}

secret_hits=0
while IFS= read -r f; do
    [ -z "$f" ] && continue
    if is_allowed "$f"; then
        continue
    fi
    fail "committed signing secret / keystore: ${f}"
    secret_hits=$((secret_hits + 1))
done < <(git ls-files | grep -E '\.(keystore|jks|p12)$|keystore\.properties$' || true)

if [ "$secret_hits" -eq 0 ]; then
    ok "no unexpected committed keystores/secrets (only known upstream debug & nightly keystores present)"
fi

echo
if [ "$violations" -ne 0 ]; then
    echo "${RED}Constraint guard FAILED with ${violations} violation(s).${RESET}"
    exit 1
fi
echo "${GREEN}Constraint guard passed: tree is clean.${RESET}"
echo "${YELLOW}(scanned tracked files + commit messages in range ${LOG_RANGE})${RESET}"
exit 0
