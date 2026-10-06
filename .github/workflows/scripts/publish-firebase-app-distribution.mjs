#!/usr/bin/env node
/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

// Gives the Android beta testers the production release that was just uploaded to Google Play:
// downloads the universal APK Play generated and signed for VERSION_CODE, checks that its signing
// certificate is the production one, uploads it to Firebase App Distribution, sets the release
// notes and distributes the release to the beta group. Same bytes uploaded twice give
// RELEASE_UNMODIFIED, so re-running the job is safe.
//
//   node .github/workflows/scripts/publish-firebase-app-distribution.mjs
//
// Env: PLAY_SERVICE_ACCOUNT_JSON (the Play upload service account's key, which can read generated
//      APKs, plus roles/firebaseappdistro.admin on the Firebase project), VERSION_CODE,
//      PLAY_SIGNING_CERT_SHA256 (expected certificate, with or without colons),
//      FIREBASE_PROJECT_NUMBER, FIREBASE_ANDROID_APP_ID, FIREBASE_BETA_GROUP (default android-beta),
//      PLAY_PACKAGE (default global.gua; .dev and .debug packages are refused),
//      RELEASE_NOTES_FILE (used when it exists), WORK_DIR (default RUNNER_TEMP).
// Writes result, release, testing_uri and console_uri to $GITHUB_OUTPUT.
//
// Logs never carry the key, a token or a raw API response: the secret is stored multi-line, so the
// runner masks every line of it, braces included, and a printed JSON body would come out garbled.

import { spawnSync } from "node:child_process";
import { createPrivateKey, createSign } from "node:crypto";
import { appendFileSync, createWriteStream, existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";

const PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const FIREBASE_API = "https://firebaseappdistribution.googleapis.com";
const GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const FIREBASE_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

// Play generates the APKs of a bundle minutes after the upload; until then the list answers 404.
const PLAY_APK_ATTEMPTS = 30;
const PLAY_APK_POLL_MS = 30_000;
const OPERATION_ATTEMPTS = 60;
const OPERATION_POLL_MS = 10_000;
// Play signs with an extra ML-DSA v3.2 signer for future Android versions that apksigner cannot
// verify yet; capping the SDK range leaves it out of the check.
const APKSIGNER_MAX_SDK = "36";

const PACKAGE_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;
const APP_ID_RE = /^1:(\d+):android:[0-9a-f]+$/;
const GROUP_ALIAS_RE = /^[a-z0-9-]{4,}$/;
const EMAIL_RE = /[^\s@()<>"',;]+@[^\s@()<>"',;]+/g;

export class PublishError extends Error {}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export const normalizeFingerprint = (value) =>
  String(value ?? "")
    .replace(/[:\s]/g, "")
    .toUpperCase();
export const formatFingerprint = (hex) => hex.match(/.{2}/g)?.join(":") ?? hex;

/** Reads and validates the settings. Error messages name settings, never their values. */
export function readConfig(env) {
  const missing = ["PLAY_SERVICE_ACCOUNT_JSON", "VERSION_CODE", "PLAY_SIGNING_CERT_SHA256", "FIREBASE_PROJECT_NUMBER", "FIREBASE_ANDROID_APP_ID"].filter(
    (name) => !env[name]?.trim(),
  );
  if (missing.length) throw new PublishError(`missing ${missing.join(", ")}`);

  let key;
  try {
    key = JSON.parse(env.PLAY_SERVICE_ACCOUNT_JSON);
  } catch {
    key = undefined;
  }
  if (typeof key?.client_email !== "string" || typeof key?.private_key !== "string") {
    throw new PublishError("PLAY_SERVICE_ACCOUNT_JSON is not a service account key");
  }
  const tokenUri = key.token_uri || GOOGLE_TOKEN_URI;
  try {
    new URL(tokenUri);
  } catch {
    throw new PublishError("PLAY_SERVICE_ACCOUNT_JSON has an invalid token_uri");
  }

  const versionCode = env.VERSION_CODE.trim();
  if (!/^\d+$/.test(versionCode)) throw new PublishError("VERSION_CODE is not a number");

  const pkg = (env.PLAY_PACKAGE || "global.gua").trim();
  if (!PACKAGE_RE.test(pkg)) throw new PublishError("PLAY_PACKAGE is not a package name");
  if (/\.(dev|debug)$/i.test(pkg)) {
    throw new PublishError(`PLAY_PACKAGE is ${pkg}; beta testers get the production app only, never a QA or debug build`);
  }

  const expectedFingerprint = normalizeFingerprint(env.PLAY_SIGNING_CERT_SHA256);
  if (!/^[0-9A-F]{64}$/.test(expectedFingerprint)) throw new PublishError("PLAY_SIGNING_CERT_SHA256 is not a SHA-256 fingerprint");

  const projectNumber = env.FIREBASE_PROJECT_NUMBER.trim();
  if (!/^\d+$/.test(projectNumber)) throw new PublishError("FIREBASE_PROJECT_NUMBER is not a number");
  const appId = env.FIREBASE_ANDROID_APP_ID.trim();
  const appProject = appId.match(APP_ID_RE)?.[1];
  if (!appProject) throw new PublishError("FIREBASE_ANDROID_APP_ID is not an Android Firebase app id");
  if (appProject !== projectNumber) throw new PublishError("FIREBASE_ANDROID_APP_ID belongs to another project than FIREBASE_PROJECT_NUMBER");

  const group = (env.FIREBASE_BETA_GROUP || "android-beta").trim();
  if (!GROUP_ALIAS_RE.test(group)) throw new PublishError("FIREBASE_BETA_GROUP is not a group alias (lowercase letters, digits and hyphens, 4+ characters)");

  return {
    key,
    tokenUri,
    versionCode,
    pkg,
    expectedFingerprint,
    projectNumber,
    appId,
    appName: `projects/${projectNumber}/apps/${appId}`,
    group,
    releaseNotesFile: env.RELEASE_NOTES_FILE?.trim() || "",
    workDir: env.WORK_DIR?.trim() || env.RUNNER_TEMP?.trim() || tmpdir(),
    playApi: env.PLAY_API_BASE?.trim() || PLAY_API,
    firebaseApi: env.FIREBASE_API_BASE?.trim() || FIREBASE_API,
    androidSdk: env.ANDROID_HOME?.trim() || env.ANDROID_SDK_ROOT?.trim() || "",
    apksigner: env.APKSIGNER?.trim() || "",
  };
}

/** The run log is public: the service account names the Cloud project it lives in. */
function maskIdentifiers(cfg, log) {
  const project = cfg.key.client_email.match(/@([a-z][a-z0-9-]*)\.iam\.gserviceaccount\.com$/i)?.[1];
  const values = [cfg.key.client_email, cfg.key.client_id, cfg.key.project_id, project];
  for (const value of new Set(values.filter(Boolean).map(String))) log(`::add-mask::${value}`);
}

const b64url = (buf) => Buffer.from(buf).toString("base64url");

/** Bearer token via the service-account JWT grant (RS256), scoped to one API. Never logged. */
async function googleToken(cfg, scope) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT", ...(cfg.key.private_key_id ? { kid: cfg.key.private_key_id } : {}) };
  const claims = { iss: cfg.key.client_email, scope, aud: cfg.tokenUri, iat: now, exp: now + 3600 };
  const input = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  let signature;
  try {
    signature = createSign("RSA-SHA256").update(input).sign(createPrivateKey(cfg.key.private_key));
  } catch {
    throw new PublishError("the private_key in PLAY_SERVICE_ACCOUNT_JSON could not be used for signing");
  }
  const res = await fetch(cfg.tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${input}.${b64url(signature)}` }),
  });
  const json = await readJson(res);
  if (!res.ok || typeof json.access_token !== "string") {
    throw new PublishError(`Google sign-in for the service account failed (${describeError(res.status, json)})`);
  }
  return json.access_token;
}

async function readJson(res) {
  const text = await res.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return {};
  }
}

async function google(token, method, url, body) {
  const res = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  return { ok: res.ok, status: res.status, json: await readJson(res) };
}

/**
 * One line for a Google error: status, error.status and, when present, the ErrorInfo reason, which
 * is what tells a disabled API (SERVICE_DISABLED) from missing IAM (both are HTTP 403). Addresses
 * in the message are replaced; nothing else of the body is printed.
 */
export function describeError(status, json) {
  const error = json?.error ?? {};
  const reason = (Array.isArray(error.details) ? error.details : []).find((d) => d?.reason)?.reason;
  const parts = [`HTTP ${status}`];
  if (error.status) parts.push(error.status);
  if (reason) parts.push(`reason ${reason}`);
  const message =
    typeof error.message === "string"
      ? error.message
          .replace(/[\r\n]+/g, " ")
          .replace(EMAIL_RE, "<address>")
          .trim()
      : "";
  return message ? `${parts.join(" ")}: ${message}` : parts.join(" ");
}

/**
 * The universal APK to ship, from generatedapks.list: { downloadId } of the universal APK in the
 * signing-key group whose certificate is the expected one. Only the certificate and the presence of
 * generatedUniversalApk decide; targetingInfo is not required. Play's archived APK group (a lone base
 * split under the same certificate) has no universal APK and a group signed with another key is
 * never used. Returns { certificates } (each certificate listed, once) when Play lists groups but
 * none qualifies: that listing is Play's answer for the versionCode, so the caller fails at once
 * naming them instead of polling on. Returns {} while Play lists nothing.
 */
export function pickUniversalApk(generatedApks, expectedFingerprint) {
  const groups = Array.isArray(generatedApks) ? generatedApks : [];
  const certificate = (g) => normalizeFingerprint(g?.certificateSha256Hash);
  const group = groups.find((g) => certificate(g) === expectedFingerprint && g.generatedUniversalApk?.downloadId);
  if (group) return { downloadId: group.generatedUniversalApk.downloadId };
  if (!groups.length) return {};
  return { certificates: [...new Set(groups.map((g) => formatFingerprint(certificate(g)) || "(none)"))] };
}

async function waitForUniversalApk(cfg, token, log, deps) {
  const url = `${cfg.playApi}/applications/${cfg.pkg}/generatedApks/${cfg.versionCode}`;
  for (let attempt = 1; attempt <= PLAY_APK_ATTEMPTS; attempt++) {
    const res = await google(token, "GET", url);
    if (res.status === 401 || res.status === 403) {
      throw new PublishError(`Play refused generatedapks.list for ${cfg.pkg} ${cfg.versionCode} (${describeError(res.status, res.json)})`);
    }
    if (res.ok) {
      const pick = pickUniversalApk(res.json.generatedApks, cfg.expectedFingerprint);
      if (pick.downloadId) return pick.downloadId;
      if (pick.certificates) {
        throw new PublishError(
          `Play lists APKs for ${cfg.pkg} ${cfg.versionCode} signed with ${pick.certificates.join(", ")}, ` +
            `but none signed with the expected production certificate ${formatFingerprint(cfg.expectedFingerprint)} has a universal APK`,
        );
      }
      log(`attempt ${attempt}/${PLAY_APK_ATTEMPTS}: Play lists no APKs for versionCode ${cfg.versionCode} yet`);
    } else if (res.status === 404) {
      log(`attempt ${attempt}/${PLAY_APK_ATTEMPTS}: Play has not generated APKs for versionCode ${cfg.versionCode} yet`);
    } else {
      log(`attempt ${attempt}/${PLAY_APK_ATTEMPTS}: generatedapks.list answered ${describeError(res.status, res.json)}`);
    }
    if (attempt < PLAY_APK_ATTEMPTS) await deps.sleep(PLAY_APK_POLL_MS);
  }
  throw new PublishError(`Play did not list a universal APK for ${cfg.pkg} ${cfg.versionCode} signed with the production certificate in time`);
}

async function downloadApk(cfg, token, downloadId, dest) {
  const url = `${cfg.playApi}/applications/${cfg.pkg}/generatedApks/${cfg.versionCode}/downloads/${downloadId}:download?alt=media`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}`, "Accept-Encoding": "identity" } });
  if (!res.ok) throw new PublishError(`downloading the universal APK failed (${describeError(res.status, await readJson(res))})`);
  await pipeline(Readable.fromWeb(res.body), createWriteStream(dest));
  const size = statSync(dest).size;
  const announced = Number(res.headers.get("content-length")) || 0;
  if (!size || (announced && announced !== size)) {
    throw new PublishError(`the universal APK download is incomplete (${size} of ${announced || "unknown"} bytes)`);
  }
  return size;
}

/** SHA-256 fingerprints of the JAR (v1) signers, from `keytool -printcert -jarfile`. */
export function parseKeytoolFingerprints(output) {
  return [...String(output).matchAll(/^\s*SHA256:\s*([0-9A-Fa-f:]{95})\s*$/gm)].map((m) => normalizeFingerprint(m[1]));
}

/**
 * SHA-256 fingerprints of the APK signature scheme signers, from `apksigner verify --print-certs`.
 * The signer label varies by apksigner version ("Signer #1", "Signer (minSdkVersion=24, ...)").
 */
export function parseApksignerFingerprints(output) {
  return [...String(output).matchAll(/certificate SHA-256 digest:\s*([0-9A-Fa-f]{64})\b/g)].map((m) => normalizeFingerprint(m[1]));
}

/** The newest apksigner under the Android SDK's build-tools, or "" when there is none. */
export function findApksigner(cfg) {
  if (cfg.apksigner) return cfg.apksigner;
  if (!cfg.androidSdk) return "";
  const dir = join(cfg.androidSdk, "build-tools");
  let versions;
  try {
    versions = readdirSync(dir);
  } catch {
    return "";
  }
  const byVersion = (a, b) => b.localeCompare(a, undefined, { numeric: true });
  return (
    versions
      .sort(byVersion)
      .map((v) => join(dir, v, "apksigner"))
      .find((p) => existsSync(p)) ?? ""
  );
}

function runTool(command, args) {
  const run = spawnSync(command, args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (run.error) throw new PublishError(`${command} could not be run: ${run.error.message}`);
  return run;
}

/**
 * Fingerprints of the APK's signing certificates. A JAR signature is read with keytool; an APK
 * signed with the APK signature scheme only (minSdk 24 and above gets no JAR signature) is read
 * with apksigner, SDK range capped so Play's ML-DSA signer is left out.
 */
function signingFingerprints(cfg, apkPath, log) {
  const keytool = runTool("keytool", ["-printcert", "-jarfile", apkPath]);
  if (keytool.status !== 0) throw new PublishError(`keytool -printcert failed (exit ${keytool.status}): ${firstLine(keytool.stderr || keytool.stdout)}`);
  const fromKeytool = parseKeytoolFingerprints(keytool.stdout);
  if (fromKeytool.length) {
    log(`signing certificate read by keytool (JAR signature): ${fromKeytool.map(formatFingerprint).join(", ")}`);
    return fromKeytool;
  }

  const apksigner = findApksigner(cfg);
  if (!apksigner) throw new PublishError("the APK has no JAR signature keytool can read and no apksigner was found under the Android SDK");
  const verify = runTool(apksigner, ["verify", "--print-certs", "--max-sdk-version", APKSIGNER_MAX_SDK, apkPath]);
  if (verify.status !== 0) throw new PublishError(`apksigner verify failed (exit ${verify.status}): ${firstLine(verify.stderr || verify.stdout)}`);
  const fromApksigner = parseApksignerFingerprints(verify.stdout);
  if (!fromApksigner.length) {
    throw new PublishError(
      `apksigner printed no certificate digest for the APK; it said: ${firstLine(verify.stdout) || firstLine(verify.stderr) || "(nothing)"}`,
    );
  }
  log(`signing certificate read by apksigner (APK signature scheme): ${fromApksigner.map(formatFingerprint).join(", ")}`);
  return fromApksigner;
}

const firstLine = (text) =>
  String(text ?? "")
    .split(/\r?\n/)
    .find((line) => line.trim()) ?? "";

async function uploadRelease(cfg, token, apkPath, log, deps) {
  const url = `${cfg.firebaseApi}/upload/v1/${cfg.appName}/releases:upload`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/octet-stream",
      "X-Goog-Upload-Protocol": "raw",
      "X-Goog-Upload-File-Name": `${cfg.pkg}-${cfg.versionCode}.apk`,
    },
    body: readFileSync(apkPath),
  });
  const operation = await readJson(res);
  if (!res.ok || typeof operation.name !== "string") {
    throw new PublishError(`Firebase App Distribution refused the upload (${describeError(res.status, operation)})`);
  }
  log("upload accepted; waiting for Firebase to process the release");

  for (let attempt = 1; attempt <= OPERATION_ATTEMPTS; attempt++) {
    const poll = await google(token, "GET", `${cfg.firebaseApi}/v1/${operation.name}`);
    if (poll.ok && poll.json.done) {
      if (poll.json.error) throw new PublishError(`Firebase could not process the release (${describeError(poll.status, { error: poll.json.error })})`);
      const { result, release } = poll.json.response ?? {};
      if (!release?.name) throw new PublishError("Firebase finished the upload without returning a release");
      return { result: result ?? "UNKNOWN", release };
    }
    if (!poll.ok && (poll.status === 401 || poll.status === 403 || poll.status === 404)) {
      throw new PublishError(`Firebase refused the operation poll (${describeError(poll.status, poll.json)})`);
    }
    await deps.sleep(OPERATION_POLL_MS);
  }
  throw new PublishError("Firebase did not finish processing the release in time");
}

async function setReleaseNotes(cfg, token, release, log) {
  if (!cfg.releaseNotesFile || !existsSync(cfg.releaseNotesFile)) {
    log(`::notice::No release notes: ${cfg.releaseNotesFile || "RELEASE_NOTES_FILE unset"} does not exist.`);
    return false;
  }
  const text = readFileSync(cfg.releaseNotesFile, "utf8").trim();
  if (!text) {
    log(`::notice::No release notes: ${cfg.releaseNotesFile} is empty.`);
    return false;
  }
  const res = await google(token, "PATCH", `${cfg.firebaseApi}/v1/${release.name}?updateMask=releaseNotes.text`, { releaseNotes: { text } });
  if (!res.ok) throw new PublishError(`setting the release notes failed (${describeError(res.status, res.json)})`);
  log(`release notes set from ${cfg.releaseNotesFile}`);
  return true;
}

/** The beta group must exist before a release can be distributed to it; a missing one is created empty. */
async function ensureGroup(cfg, token, log) {
  const url = `${cfg.firebaseApi}/v1/projects/${cfg.projectNumber}/groups/${cfg.group}`;
  const existing = await google(token, "GET", url);
  if (existing.ok) {
    log(`group ${cfg.group}: ${existing.json.testerCount ?? 0} tester(s), ${existing.json.releaseCount ?? 0} release(s)`);
    return;
  }
  if (existing.status !== 404) throw new PublishError(`reading group ${cfg.group} failed (${describeError(existing.status, existing.json)})`);
  const created = await google(token, "POST", `${cfg.firebaseApi}/v1/projects/${cfg.projectNumber}/groups?groupId=${cfg.group}`, {
    displayName: "Android beta",
  });
  if (!created.ok) throw new PublishError(`creating group ${cfg.group} failed (${describeError(created.status, created.json)})`);
  log(`::notice::Created the empty Firebase App Distribution group ${cfg.group}; testers are added by the beta-invite workflow.`);
}

async function distribute(cfg, token, release) {
  const res = await google(token, "POST", `${cfg.firebaseApi}/v1/${release.name}:distribute`, { groupAliases: [cfg.group] });
  if (!res.ok) throw new PublishError(`distributing the release to group ${cfg.group} failed (${describeError(res.status, res.json)})`);
}

function writeOutputs(env, values) {
  if (!env.GITHUB_OUTPUT) return;
  appendFileSync(
    env.GITHUB_OUTPUT,
    Object.entries(values)
      .map(([k, v]) => `${k}=${v}\n`)
      .join(""),
  );
}

function writeSummary(env, cfg, outcome) {
  if (!env.GITHUB_STEP_SUMMARY) return;
  const lines = [
    "## Firebase App Distribution",
    "",
    `- ${cfg.pkg} ${outcome.release.displayVersion ?? ""} (${outcome.release.buildVersion ?? cfg.versionCode}): ${outcome.result}`,
    `- Group: ${cfg.group}`,
    `- Tester link: ${outcome.release.testingUri ?? ""}`,
    `- Console: ${outcome.release.firebaseConsoleUri ?? ""}`,
    "",
  ];
  appendFileSync(env.GITHUB_STEP_SUMMARY, lines.join("\n"));
}

export async function main(env, deps = {}) {
  const log = deps.log ?? console.log;
  const wait = deps.sleep ?? sleep;
  const cfg = readConfig(env);
  maskIdentifiers(cfg, log);
  log(`publishing ${cfg.pkg} versionCode ${cfg.versionCode} to Firebase App Distribution group ${cfg.group}`);

  const playToken = await googleToken(cfg, PLAY_SCOPE);
  const downloadId = await waitForUniversalApk(cfg, playToken, log, { sleep: wait });
  const apkPath = join(cfg.workDir, `${cfg.pkg}-${cfg.versionCode}.apk`);
  const size = await downloadApk(cfg, playToken, downloadId, apkPath);
  log(`downloaded the universal APK (${size} bytes)`);

  const fingerprints = signingFingerprints(cfg, apkPath, log);
  if (!fingerprints.includes(cfg.expectedFingerprint)) {
    throw new PublishError(`the downloaded APK is not signed with the expected production certificate ${formatFingerprint(cfg.expectedFingerprint)}`);
  }
  log("signing certificate matches PLAY_SIGNING_CERT_SHA256");

  const firebaseToken = await googleToken(cfg, FIREBASE_SCOPE);
  const outcome = await uploadRelease(cfg, firebaseToken, apkPath, log, { sleep: wait });
  log(`release ${outcome.release.name?.split("/").pop()}: ${outcome.result}`);
  await setReleaseNotes(cfg, firebaseToken, outcome.release, log);
  await ensureGroup(cfg, firebaseToken, log);
  await distribute(cfg, firebaseToken, outcome.release);
  log(`distributed to group ${cfg.group}`);

  const links = `Tester link: ${outcome.release.testingUri ?? "(none)"}. Console: ${outcome.release.firebaseConsoleUri ?? "(none)"}`;
  log(`::notice::Firebase App Distribution: ${outcome.result}. ${links}`);
  writeOutputs(env, {
    result: outcome.result,
    release: outcome.release.name ?? "",
    testing_uri: outcome.release.testingUri ?? "",
    console_uri: outcome.release.firebaseConsoleUri ?? "",
  });
  writeSummary(env, cfg, outcome);
  return outcome;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main(process.env).catch((error) => {
    const message = error instanceof PublishError ? error.message : `unexpected failure: ${error?.message ?? error}`;
    console.log(`::error::Firebase App Distribution publish failed: ${message}`);
    process.exit(1);
  });
}
