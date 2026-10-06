#!/usr/bin/env bash
#
# Copyright 2026 Gua
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.
#
# The decision step of publish-play.yml: turns the event and the dispatch inputs into the outputs
# the publish jobs key on. Everything it reads comes in through the environment rather than being
# interpolated into the script, so a branch or fork name can never be evaluated as shell.
# Exercised by publish-play-gate.test.mjs.
#
# Env: EVENT (github.event_name), HEAD_REPO and THIS_REPO (repository full names), IS_LABELLED
#      ("true" when the pull request carries the deploy label), INPUT_APP, INPUT_TRACK, INPUT_STATUS
#      and INPUT_FIREBASE_VERSION_CODE (the workflow_dispatch inputs), GITHUB_OUTPUT.
# Outputs: qa, prod, firebase_only (each true/false; at most one is true), firebase_version_code
#      (set only with firebase_only), track, status.

set -euo pipefail

qa=false
prod=false
firebase_only=false
firebase_version_code=""
track=internal
status=draft

case "${EVENT:-}" in
  pull_request)
    # Same-repo check: this is a public repo, so anyone can open a PR, and a fork PR must never be
    # able to reach the upload keystore or the Play service account. GitHub already withholds
    # secrets from fork PRs; asserting it here means the rule still holds if someone later widens
    # the trigger, and a labelled fork PR fails closed instead of failing halfway through a signed
    # build.
    if [ "${HEAD_REPO:-}" = "${THIS_REPO:-}" ] && [ "${IS_LABELLED:-}" = "true" ]; then
      qa=true
      # A QA ship exists to be installed, so it goes out completed rather than sitting as a draft
      # that someone has to promote in the console before a tester sees it.
      track=internal
      status=completed
    fi
    ;;
  push)
    # Merge to main. This stages prod; the `production` environment is what holds it.
    prod=true
    track=production
    # Uploaded but not rolled out: approval says "this build is the release", the roll-out
    # percentage is still a decision someone makes in Play Console. Dispatch with status=completed
    # when you want the workflow itself to roll it out.
    status=draft
    ;;
  workflow_dispatch)
    # Firebase-only: the versionCode named is already on Play, so nothing is built and nothing is
    # uploaded to Play; publish-firebase-only hands that existing build to the beta group. It is
    # a production action (the beta group gets the production app and nothing else), so it needs
    # app=prod and the job waits on the `production` environment.
    requested_code="${INPUT_FIREBASE_VERSION_CODE:-}"
    requested_code="${requested_code//[[:space:]]/}"
    if [ -n "$requested_code" ]; then
      if [ "${INPUT_APP:-}" != "prod" ]; then
        echo "::error::firebase_version_code publishes an existing Play build of the production app to the beta group, so it needs app=prod. The QA app never reaches Firebase."
        exit 1
      fi
      case "$requested_code" in
        *[!0-9]*)
          echo "::error::firebase_version_code must be a Play versionCode (digits only), not '$requested_code'."
          exit 1 ;;
      esac
      firebase_only=true
      firebase_version_code="$requested_code"
    elif [ "${INPUT_APP:-}" = "qa" ]; then
      qa=true
      # The QA job runs under the unprotected `dev` environment on purpose: shipping a branch to
      # internal testers should not need an approver. That only holds while a QA ship stays a
      # testing artefact. `production` is a public Play track, so app=qa + track=production would
      # put global.gua.dev in front of the store with no approval gate at all, precisely the hole
      # this file was split out of release.yml to close. Anything aimed at a production track goes
      # through publish-prod and its `production` environment.
      if [ "${INPUT_TRACK:-}" = "production" ]; then
        echo "::error::app=qa cannot publish to the production track: the QA job has no approval gate. Dispatch app=prod (which waits on the production environment), or pick internal/alpha/beta."
        exit 1
      fi
    else
      prod=true
    fi
    if [ "$firebase_only" != "true" ]; then
      track="${INPUT_TRACK:-$track}"
      status="${INPUT_STATUS:-$status}"
    fi
    ;;
esac

{
  echo "qa=$qa"
  echo "prod=$prod"
  echo "firebase_only=$firebase_only"
  echo "firebase_version_code=$firebase_version_code"
  echo "track=$track"
  echo "status=$status"
} >> "$GITHUB_OUTPUT"

if [ "$firebase_only" = "true" ]; then
  echo "::notice::Firebase-only mode: versionCode $firebase_version_code is published from Play to Firebase App Distribution; nothing is built or uploaded to Play. track and status are ignored."
  echo "qa=false prod=false firebase_only=true firebase_version_code=$firebase_version_code"
else
  echo "qa=$qa prod=$prod track=$track status=$status"
fi
