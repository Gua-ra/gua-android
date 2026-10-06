/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

// Runs publish-play-gate.sh, the decision step of publish-play.yml, for each event and dispatch
// shape and checks the outputs the publish jobs key on.
//
//   node --test .github/workflows/scripts/publish-play-gate.test.mjs

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), "publish-play-gate.sh");
const REPO = "Gua-ra/gua-android";
const OUTPUT_KEYS = ["qa", "prod", "firebase_only", "firebase_version_code", "track", "status"];

/** Runs the gate with only the variables the workflow passes; THIS_REPO is always the repo itself. */
function gate(env) {
  const output = join(mkdtempSync(join(tmpdir(), "gua-gate-")), "output.txt");
  const run = spawnSync("bash", [SCRIPT], {
    encoding: "utf8",
    env: { PATH: process.env.PATH, GITHUB_OUTPUT: output, THIS_REPO: REPO, ...env },
  });
  let outputs = {};
  try {
    outputs = Object.fromEntries(
      readFileSync(output, "utf8")
        .split("\n")
        .filter(Boolean)
        .map((line) => line.split(/=(.*)/s).slice(0, 2)),
    );
  } catch {
    // The gate exited before writing: outputs stay empty.
  }
  return { status: run.status, stdout: run.stdout, stderr: run.stderr, outputs };
}

const nothing = { qa: "false", prod: "false", firebase_only: "false", firebase_version_code: "", track: "internal", status: "draft" };

function assertDecision(result, expected) {
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.deepEqual(Object.keys(result.outputs).sort(), [...OUTPUT_KEYS].sort());
  assert.deepEqual(result.outputs, expected);
}

function assertRefused(result, message) {
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, message);
  assert.deepEqual(result.outputs, {}, "a refused dispatch writes no outputs");
}

describe("pull_request", () => {
  it("ships a labelled same-repo PR to the QA app, completed on internal", () => {
    assertDecision(gate({ EVENT: "pull_request", HEAD_REPO: REPO, IS_LABELLED: "true" }), {
      ...nothing,
      qa: "true",
      status: "completed",
    });
  });
  it("does nothing for an unlabelled PR", () => {
    assertDecision(gate({ EVENT: "pull_request", HEAD_REPO: REPO, IS_LABELLED: "false" }), nothing);
  });
  it("does nothing for a labelled fork PR", () => {
    assertDecision(gate({ EVENT: "pull_request", HEAD_REPO: "someone/gua-android", IS_LABELLED: "true" }), nothing);
  });
});

describe("push", () => {
  it("stages a draft production publish", () => {
    assertDecision(gate({ EVENT: "push" }), { ...nothing, prod: "true", track: "production" });
  });
});

describe("workflow_dispatch", () => {
  const dispatch = (inputs) => gate({ EVENT: "workflow_dispatch", INPUT_APP: "qa", INPUT_TRACK: "internal", INPUT_STATUS: "draft", ...inputs });

  it("ships app=qa to the chosen testing track", () => {
    assertDecision(dispatch({ INPUT_TRACK: "alpha", INPUT_STATUS: "completed" }), { ...nothing, qa: "true", track: "alpha", status: "completed" });
  });
  it("refuses app=qa on the production track", () => {
    assertRefused(dispatch({ INPUT_TRACK: "production" }), /::error::app=qa cannot publish to the production track/);
  });
  it("stages app=prod on any track behind publish-prod", () => {
    assertDecision(dispatch({ INPUT_APP: "prod", INPUT_TRACK: "alpha", INPUT_STATUS: "draft" }), { ...nothing, prod: "true", track: "alpha" });
    assertDecision(dispatch({ INPUT_APP: "prod", INPUT_TRACK: "production", INPUT_STATUS: "completed" }), {
      ...nothing,
      prod: "true",
      track: "production",
      status: "completed",
    });
  });

  describe("firebase_version_code", () => {
    it("runs only the Firebase publish for that versionCode", () => {
      const result = dispatch({ INPUT_APP: "prod", INPUT_TRACK: "alpha", INPUT_STATUS: "completed", INPUT_FIREBASE_VERSION_CODE: "202606060" });
      assertDecision(result, { ...nothing, firebase_only: "true", firebase_version_code: "202606060" });
      assert.match(result.stdout, /::notice::Firebase-only mode: versionCode 202606060/);
    });
    it("ignores surrounding whitespace in the versionCode", () => {
      assertDecision(dispatch({ INPUT_APP: "prod", INPUT_FIREBASE_VERSION_CODE: " 202606060\n" }), {
        ...nothing,
        firebase_only: "true",
        firebase_version_code: "202606060",
      });
    });
    it("is the regular dispatch when left empty or blank", () => {
      assertDecision(dispatch({ INPUT_APP: "prod", INPUT_FIREBASE_VERSION_CODE: "" }), { ...nothing, prod: "true" });
      assertDecision(dispatch({ INPUT_APP: "prod", INPUT_FIREBASE_VERSION_CODE: "  " }), { ...nothing, prod: "true" });
    });
    it("needs app=prod", () => {
      assertRefused(dispatch({ INPUT_APP: "qa", INPUT_FIREBASE_VERSION_CODE: "202606060" }), /::error::firebase_version_code .* needs app=prod/);
    });
    it("refuses anything but digits", () => {
      assertRefused(dispatch({ INPUT_APP: "prod", INPUT_FIREBASE_VERSION_CODE: "26.06.6" }), /::error::firebase_version_code must be a Play versionCode/);
      assertRefused(dispatch({ INPUT_APP: "prod", INPUT_FIREBASE_VERSION_CODE: "202606060; rm -rf /" }), /digits only/);
    });
  });
});

describe("other events", () => {
  it("decide nothing", () => {
    assertDecision(gate({ EVENT: "schedule" }), nothing);
    assertDecision(gate({}), nothing);
  });
});
