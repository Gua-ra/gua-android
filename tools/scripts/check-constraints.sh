#!/usr/bin/env bash
#
# Copyright (c) 2025 Element Creations Ltd.
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
#
# GUA FORK constraint guard.
#
# Fails the build if forbidden content has leaked into the tree or git history:
#   * scrubbed personal / infra identifiers
#   * co-author trailers or generated-by notices in commit messages
#   * a committed keystore / signing secret (other than the known upstream debug & nightly keystores)
#
# Runnable locally:  ./tools/scripts/check-constraints.sh
# Exit code 0 = clean, 1 = at least one violation found.
#
# By default it scans the tracked working tree (git ls-files) plus the commit-message history
# reachable from HEAD. Set GUA_CONSTRAINTS_BASE_REF=<ref> to scan only the commit messages in
# <ref>..HEAD.

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
    # -F fixed string, -I skip binary, -n line numbers. Tracked files only, via git ls-files.
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

# 2. No AI co-author trailers or generated-by notices in commit messages.
echo "→ Scanning commit messages for AI attribution..."
if [ -n "${GUA_CONSTRAINTS_SKIP_HISTORY:-}" ]; then
    # Set when the caller has no meaningful range to scan (a new branch, a force push, a manual
    # run). Scanning the whole history instead would fail on commits that are already merged.
    LOG_RANGE=""
elif [ -n "${GUA_CONSTRAINTS_BASE_REF:-}" ]; then
    LOG_RANGE="${GUA_CONSTRAINTS_BASE_REF}..HEAD"
else
    LOG_RANGE="HEAD"
fi

if [ -z "$LOG_RANGE" ]; then
    echo "${YELLOW}-${RESET} commit-message scan skipped (no range to scan)"
else
    # -i case-insensitive, -E extended regex over the full commit message bodies.
    ai_attr="$(git log "$LOG_RANGE" --format='%H%n%B' 2>/dev/null \
        | grep -i -E 'Co-Authored-By:[[:space:]]*Claude|Generated with .*(Claude|AI|Anthropic)|🤖 Generated with' || true)"
    if [ -n "$ai_attr" ]; then
        fail "AI authorship attribution found in commit messages (range: ${LOG_RANGE}):"
        echo "$ai_attr" | sed 's/^/    /'
    else
        ok "no AI attribution in commit messages (range: ${LOG_RANGE})"
    fi
fi

# 3. Committed keystores or signing secrets. Any tracked *.keystore, *.jks, *.p12 or
#    keystore.properties is a violation, except the two upstream keystores:
#      - app/signature/debug.keystore   (the public Android debug key)
#      - app/signature/nightly.keystore (upstream nightly key; passwords come from env)
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
