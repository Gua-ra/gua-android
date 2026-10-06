/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

// Runs publish-firebase-app-distribution.mjs against a fake Google HTTP layer and APKs signed with
// a throwaway key. JDK tools (keytool, jarsigner, jar) come from PATH; aapt2 and apksigner from the
// Android SDK when one is installed, otherwise the APK-signature-scheme cases are skipped.
//
//   node --test .github/workflows/scripts/publish-firebase-app-distribution.test.mjs

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash, generateKeyPairSync } from "node:crypto";
import { copyFileSync, existsSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import { before, describe, it } from "node:test";

import {
  describeError,
  main,
  normalizeFingerprint,
  parseApksignerFingerprints,
  parseKeytoolFingerprints,
  pickUniversalApk,
  PublishError,
  readConfig,
} from "./publish-firebase-app-distribution.mjs";

const PROJECT = "511804071315";
const APP_ID = `1:${PROJECT}:android:7a87ae8499f379204e1c66`;
const APP = `projects/${PROJECT}/apps/${APP_ID}`;
const VERSION_CODE = "202606060";
const OTHER_CERT = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99";

const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const SA = {
  type: "service_account",
  project_id: "fake-project",
  private_key_id: "k1",
  private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  client_email: "beta@fake-project.iam.gserviceaccount.com",
  client_id: "123",
};

const run = (command, args) => spawnSync(command, args, { encoding: "utf8" });
const available = (command) => !run(command, ["-help"]).error;

function androidTools() {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || join(homedir(), "Library/Android/sdk");
  const newest = (dir) => {
    try {
      return readdirSync(dir).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    } catch {
      return [];
    }
  };
  const buildTools = newest(join(sdk, "build-tools"))
    .map((v) => join(sdk, "build-tools", v))
    .find((d) => existsSync(join(d, "aapt2")) && existsSync(join(d, "apksigner")));
  const platform = newest(join(sdk, "platforms"))
    .map((v) => join(sdk, "platforms", v, "android.jar"))
    .find((f) => existsSync(f));
  return buildTools && platform ? { aapt2: join(buildTools, "aapt2"), apksigner: join(buildTools, "apksigner"), androidJar: platform } : null;
}

/** Throwaway key plus APKs signed with it: v1 (JAR signature) always, v2 when the Android SDK is available. */
function buildFixtures() {
  if (!available("keytool") || !available("jarsigner") || !available("jar")) return null;
  const dir = mkdtempSync(join(tmpdir(), "gua-fad-"));
  const keystore = join(dir, "ks.p12");
  const storeArgs = ["-keystore", keystore, "-storetype", "PKCS12", "-storepass", "changeit"];
  assert.equal(
    run("keytool", [
      "-genkeypair",
      ...storeArgs,
      "-keypass",
      "changeit",
      "-alias",
      "t",
      "-keyalg",
      "RSA",
      "-keysize",
      "2048",
      "-validity",
      "30",
      "-dname",
      "CN=Fixture",
    ]).status,
    0,
  );
  const der = join(dir, "cert.der");
  assert.equal(run("keytool", ["-exportcert", ...storeArgs, "-alias", "t", "-file", der]).status, 0);
  const fingerprint = createHash("sha256").update(readFileSync(der)).digest("hex").toUpperCase();

  const unsigned = join(dir, "unsigned.apk");
  const android = androidTools();
  if (android) {
    const manifest = join(dir, "AndroidManifest.xml");
    writeFileSync(
      manifest,
      '<?xml version="1.0" encoding="utf-8"?>' +
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="global.gua.fixture" ' +
        'android:versionCode="1" android:versionName="1">' +
        '<uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36" /></manifest>',
    );
    assert.equal(run(android.aapt2, ["link", "-o", unsigned, "--manifest", manifest, "-I", android.androidJar]).status, 0);
  } else {
    writeFileSync(join(dir, "payload.txt"), "fixture");
    assert.equal(run("jar", ["cf", unsigned, "-C", dir, "payload.txt"]).status, 0);
  }

  const v1 = join(dir, "v1.apk");
  copyFileSync(unsigned, v1);
  assert.equal(run("jarsigner", ["-keystore", keystore, "-storepass", "changeit", v1, "t"]).status, 0);

  let v2 = null;
  if (android) {
    v2 = join(dir, "v2.apk");
    copyFileSync(unsigned, v2);
    assert.equal(
      run(android.apksigner, ["sign", "--ks", keystore, "--ks-pass", "pass:changeit", "--ks-key-alias", "t", "--v1-signing-enabled", "false", v2]).status,
      0,
    );
  }
  return { dir, fingerprint, unsigned, v1, v2, apksigner: android?.apksigner ?? "" };
}

/**
 * Fake Google endpoints. `scenario.list` is the sequence of generatedapks.list answers, `scenario.polls`
 * the sequence of operation answers; the rest are single answers. Every request is recorded.
 */
function startFake(scenario) {
  const calls = [];
  const bodies = [];
  const server = createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = Buffer.concat(chunks);
    const url = new URL(req.url, "http://127.0.0.1");
    calls.push({ method: req.method, path: url.pathname + url.search, headers: req.headers, length: body.length });
    bodies.push(body);
    const auth = req.headers.authorization;
    const send = (status, json) => {
      res.writeHead(status, { "Content-Type": "application/json" });
      res.end(json === undefined ? "" : JSON.stringify(json));
    };
    const route = `${req.method} ${url.pathname}`;
    if (route === "POST /token") return send(200, { access_token: `tok-${calls.length}`, expires_in: 3600 });
    if (!auth?.startsWith("Bearer tok-")) return send(401, { error: { status: "UNAUTHENTICATED", message: "no token" } });
    if (route === `GET /play/applications/global.gua/generatedApks/${VERSION_CODE}`) {
      const next = scenario.list.length > 1 ? scenario.list.shift() : scenario.list[0];
      return send(next.status, next.json);
    }
    if (route === `GET /play/applications/global.gua/generatedApks/${VERSION_CODE}/downloads/dl-universal:download`) {
      res.writeHead(200, { "Content-Type": "application/octet-stream", "Content-Length": scenario.apk.length });
      return res.end(scenario.apk);
    }
    if (route === `POST /fb/upload/v1/${APP}/releases:upload`) return send(200, { name: `${APP}/releases/-/operations/op1` });
    if (route === `GET /fb/v1/${APP}/releases/-/operations/op1`) {
      const next = scenario.polls.length > 1 ? scenario.polls.shift() : scenario.polls[0];
      return send(200, next);
    }
    if (route === `PATCH /fb/v1/${APP}/releases/r1`) return send(200, { name: `${APP}/releases/r1` });
    if (route === `GET /fb/v1/projects/${PROJECT}/groups/android-beta`) {
      return scenario.groupExists
        ? send(200, { name: "g", testerCount: 3, releaseCount: 2 })
        : send(404, { error: { status: "NOT_FOUND", message: "group not found" } });
    }
    if (route === `POST /fb/v1/projects/${PROJECT}/groups`) return send(200, { name: `projects/${PROJECT}/groups/android-beta` });
    if (route === `POST /fb/v1/${APP}/releases/r1:distribute`) return send(200, {});
    send(500, { error: { status: "INTERNAL", message: `unexpected ${route}` } });
  });
  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => {
      const base = `http://127.0.0.1:${server.address().port}`;
      resolve({ base, calls, bodies, close: () => new Promise((done) => server.close(done)) });
    });
  });
}

const RELEASE = {
  name: `${APP}/releases/r1`,
  displayVersion: "26.6.6",
  buildVersion: VERSION_CODE,
  testingUri: "https://appdistribution.firebase.dev/i/abc",
  firebaseConsoleUri: "https://console.firebase.google.com/project/gua-global/appdistribution/app/android:global.gua/releases",
};

const doneOperation = (result) => ({ name: "op1", done: true, response: { result, release: RELEASE } });

const universalGroup = (cert) => ({
  certificateSha256Hash: cert,
  generatedSplitApks: [{ moduleName: "base", variantId: 0, downloadId: "dl-base" }],
  generatedUniversalApk: { downloadId: "dl-universal" },
  targetingInfo: { packageName: "global.gua", variant: [{ variantId: 0 }] },
});
const archivedGroup = (cert) => ({ certificateSha256Hash: cert, generatedSplitApks: [{ moduleName: "base", downloadId: "dl-archived" }] });

function envFor(fake, fixtures, overrides = {}) {
  return {
    GOOGLE_BETA_SA_JSON: JSON.stringify({ ...SA, token_uri: `${fake.base}/token` }),
    VERSION_CODE,
    PLAY_SIGNING_CERT_SHA256: fixtures.fingerprint,
    FIREBASE_PROJECT_NUMBER: PROJECT,
    FIREBASE_ANDROID_APP_ID: APP_ID,
    WORK_DIR: fixtures.dir,
    PLAY_API_BASE: `${fake.base}/play`,
    FIREBASE_API_BASE: `${fake.base}/fb`,
    ANDROID_HOME: "",
    APKSIGNER: "",
    ...overrides,
  };
}

const deps = (logs) => ({ log: (line) => logs.push(line), sleep: async () => {} });

async function runMain(env, logs = []) {
  try {
    return { outcome: await main(env, deps(logs)), logs };
  } catch (error) {
    return { error, logs };
  }
}

describe("readConfig", () => {
  const base = {
    GOOGLE_BETA_SA_JSON: JSON.stringify(SA),
    VERSION_CODE,
    PLAY_SIGNING_CERT_SHA256: OTHER_CERT,
    FIREBASE_PROJECT_NUMBER: PROJECT,
    FIREBASE_ANDROID_APP_ID: APP_ID,
  };
  it("names missing settings without values", () => {
    assert.throws(
      () => readConfig({ VERSION_CODE }),
      /missing GOOGLE_BETA_SA_JSON, PLAY_SIGNING_CERT_SHA256, FIREBASE_PROJECT_NUMBER, FIREBASE_ANDROID_APP_ID/,
    );
  });
  it("refuses QA and debug packages", () => {
    assert.throws(() => readConfig({ ...base, PLAY_PACKAGE: "global.gua.dev" }), /production app only/);
    assert.throws(() => readConfig({ ...base, PLAY_PACKAGE: "global.gua.debug" }), /production app only/);
  });
  it("ties the app id to the project number", () => {
    assert.throws(() => readConfig({ ...base, FIREBASE_PROJECT_NUMBER: "1" }), /another project/);
    assert.throws(() => readConfig({ ...base, FIREBASE_ANDROID_APP_ID: "1:511804071315:ios:abc" }), /not an Android Firebase app id/);
  });
  it("accepts a fingerprint with or without colons and defaults the group", () => {
    assert.equal(readConfig(base).expectedFingerprint, normalizeFingerprint(OTHER_CERT));
    assert.equal(
      readConfig({ ...base, PLAY_SIGNING_CERT_SHA256: OTHER_CERT.replace(/:/g, "").toLowerCase() }).expectedFingerprint,
      normalizeFingerprint(OTHER_CERT),
    );
    assert.equal(readConfig(base).group, "android-beta");
    assert.throws(() => readConfig({ ...base, PLAY_SIGNING_CERT_SHA256: "abc" }), /not a SHA-256 fingerprint/);
    assert.throws(() => readConfig({ ...base, FIREBASE_BETA_GROUP: "Beta Group" }), /not a group alias/);
  });
});

describe("pickUniversalApk", () => {
  const expected = normalizeFingerprint(OTHER_CERT);
  it("skips the archived variant and picks the targeted universal APK", () => {
    assert.deepEqual(pickUniversalApk([archivedGroup(OTHER_CERT), universalGroup(OTHER_CERT)], expected), { downloadId: "dl-universal" });
  });
  it("reports a certificate mismatch", () => {
    const other = "05:DF:39:93:8C:C7:E2:AC:A6:C3:A5:F3:3A:DD:F4:D2:91:CF:F4:BA:F9:A3:91:06:FD:09:00:B1:D0:9C:77:92";
    assert.deepEqual(pickUniversalApk([universalGroup(other)], expected), { mismatch: [other] });
  });
  it("waits while nothing shippable is listed", () => {
    assert.deepEqual(pickUniversalApk([], expected), {});
    assert.deepEqual(pickUniversalApk(undefined, expected), {});
    assert.deepEqual(pickUniversalApk([archivedGroup(OTHER_CERT)], expected), {});
  });
});

describe("parsers", () => {
  it("reads keytool and apksigner fingerprints", () => {
    const keytool =
      "Signer #1:\n\nCertificate #1:\nOwner: CN=Probe\nCertificate fingerprints:\n\t SHA1: 1D:45\n\t SHA256: " +
      OTHER_CERT +
      "\nSignature algorithm name: SHA384withRSA\n";
    assert.deepEqual(parseKeytoolFingerprints(keytool), [normalizeFingerprint(OTHER_CERT)]);
    assert.deepEqual(parseKeytoolFingerprints("Not a signed jar file\n"), []);
    const digest = OTHER_CERT.replace(/:/g, "").toLowerCase();
    const apksigner = `Signer #1 certificate DN: CN=Probe\nSigner #1 certificate SHA-256 digest: ${digest}\nSigner #1 certificate SHA-1 digest: 1d45\n`;
    assert.deepEqual(parseApksignerFingerprints(apksigner), [normalizeFingerprint(OTHER_CERT)]);
  });
  it("describes Google errors by status, reason and message without addresses", () => {
    const json = {
      error: {
        code: 403,
        message: "Firebase App Distribution API has not been used in project 123 before or it is disabled. Ask someone@example.com",
        status: "PERMISSION_DENIED",
        details: [{ "@type": "type.googleapis.com/google.rpc.ErrorInfo", reason: "SERVICE_DISABLED", domain: "googleapis.com" }],
      },
    };
    assert.equal(
      describeError(403, json),
      "HTTP 403 PERMISSION_DENIED reason SERVICE_DISABLED: " +
        "Firebase App Distribution API has not been used in project 123 before or it is disabled. Ask <address>",
    );
    assert.equal(describeError(502, {}), "HTTP 502");
  });
});

describe("publish flow", () => {
  let fixtures;
  before(() => {
    fixtures = buildFixtures();
  });

  it("downloads the Play APK, checks its certificate, uploads, sets notes, creates the group and distributes", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const notes = join(fixtures.dir, `${VERSION_CODE}.txt`);
    writeFileSync(notes, "Beta build.\n");
    const output = join(fixtures.dir, "output.txt");
    writeFileSync(output, "");
    const summary = join(fixtures.dir, "summary.md");
    writeFileSync(summary, "");
    const apk = readFileSync(fixtures.v1);
    const fake = await startFake({
      apk,
      list: [
        { status: 404, json: { error: { status: "NOT_FOUND", message: "not found" } } },
        { status: 200, json: { generatedApks: [archivedGroup(fixtures.fingerprint)] } },
        { status: 200, json: { generatedApks: [archivedGroup(fixtures.fingerprint), universalGroup(fixtures.fingerprint)] } },
      ],
      polls: [{ name: "op1", done: false }, doneOperation("RELEASE_CREATED")],
      groupExists: false,
    });
    try {
      const { outcome, error, logs } = await runMain(
        envFor(fake, fixtures, { RELEASE_NOTES_FILE: notes, GITHUB_OUTPUT: output, GITHUB_STEP_SUMMARY: summary }),
      );
      assert.equal(error, undefined, error?.message);
      assert.equal(outcome.result, "RELEASE_CREATED");

      const routes = fake.calls.map((c) => `${c.method} ${c.path}`);
      assert.deepEqual(routes, [
        "POST /token",
        `GET /play/applications/global.gua/generatedApks/${VERSION_CODE}`,
        `GET /play/applications/global.gua/generatedApks/${VERSION_CODE}`,
        `GET /play/applications/global.gua/generatedApks/${VERSION_CODE}`,
        `GET /play/applications/global.gua/generatedApks/${VERSION_CODE}/downloads/dl-universal:download?alt=media`,
        "POST /token",
        `POST /fb/upload/v1/${APP}/releases:upload`,
        `GET /fb/v1/${APP}/releases/-/operations/op1`,
        `GET /fb/v1/${APP}/releases/-/operations/op1`,
        `PATCH /fb/v1/${APP}/releases/r1?updateMask=releaseNotes.text`,
        `GET /fb/v1/projects/${PROJECT}/groups/android-beta`,
        `POST /fb/v1/projects/${PROJECT}/groups?groupId=android-beta`,
        `POST /fb/v1/${APP}/releases/r1:distribute`,
      ]);
      const upload = fake.calls[6];
      assert.equal(upload.headers["x-goog-upload-protocol"], "raw");
      assert.equal(upload.headers["x-goog-upload-file-name"], `global.gua-${VERSION_CODE}.apk`);
      assert.equal(upload.headers["content-type"], "application/octet-stream");
      assert.equal(upload.length, apk.length);
      assert.ok(fake.bodies[6].equals(apk));
      assert.ok(fake.calls.slice(1).every((c) => c.path === "/token" || c.headers.authorization?.startsWith("Bearer tok-")));
      assert.deepEqual(JSON.parse(fake.bodies[9]), { releaseNotes: { text: "Beta build." } });
      assert.deepEqual(JSON.parse(fake.bodies[11]), { displayName: "Android beta" });
      assert.deepEqual(JSON.parse(fake.bodies[12]), { groupAliases: ["android-beta"] });

      assert.ok(logs.some((l) => l.includes("read by keytool")));
      assert.ok(logs.includes(`::add-mask::${SA.client_email}`));
      assert.ok(logs.some((l) => l.startsWith("::notice::Created the empty Firebase App Distribution group android-beta")));
      assert.ok(logs.at(-1).includes(RELEASE.testingUri) && logs.at(-1).includes(RELEASE.firebaseConsoleUri));
      assert.ok(!logs.some((l) => l.includes("tok-") || l.includes("PRIVATE KEY")));
      assert.equal(
        readFileSync(output, "utf8"),
        `result=RELEASE_CREATED\nrelease=${RELEASE.name}\ntesting_uri=${RELEASE.testingUri}\nconsole_uri=${RELEASE.firebaseConsoleUri}\n`,
      );
      assert.match(readFileSync(summary, "utf8"), /RELEASE_CREATED[\s\S]*appdistribution\.firebase\.dev/);
    } finally {
      await fake.close();
    }
  });

  it("treats RELEASE_UNMODIFIED as success, keeps an existing group and skips absent notes", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const fake = await startFake({
      apk: readFileSync(fixtures.v1),
      list: [{ status: 200, json: { generatedApks: [universalGroup(fixtures.fingerprint)] } }],
      polls: [doneOperation("RELEASE_UNMODIFIED")],
      groupExists: true,
    });
    try {
      const { outcome, error, logs } = await runMain(envFor(fake, fixtures, { RELEASE_NOTES_FILE: join(fixtures.dir, "missing.txt") }));
      assert.equal(error, undefined, error?.message);
      assert.equal(outcome.result, "RELEASE_UNMODIFIED");
      const routes = fake.calls.map((c) => `${c.method} ${c.path}`);
      assert.ok(!routes.some((r) => r.startsWith("PATCH") || r.includes("groups?groupId")));
      assert.ok(routes.includes(`POST /fb/v1/${APP}/releases/r1:distribute`));
      assert.ok(logs.some((l) => l.startsWith("::notice::No release notes")));
      assert.ok(logs.some((l) => l === "group android-beta: 3 tester(s), 2 release(s)"));
    } finally {
      await fake.close();
    }
  });

  it("fails before downloading when Play's certificate is not the production one", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const fake = await startFake({ apk: readFileSync(fixtures.v1), list: [{ status: 200, json: { generatedApks: [universalGroup(OTHER_CERT)] } }], polls: [] });
    try {
      const { error } = await runMain(envFor(fake, fixtures));
      assert.ok(error instanceof PublishError);
      assert.match(error.message, /signed with AA:BB:CC.*not the expected production certificate/);
      assert.ok(!fake.calls.some((c) => c.path.includes(":download")));
    } finally {
      await fake.close();
    }
  });

  it("fails when the downloaded APK is signed with another key", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const fake = await startFake({ apk: readFileSync(fixtures.v1), list: [{ status: 200, json: { generatedApks: [universalGroup(OTHER_CERT)] } }], polls: [] });
    try {
      const { error } = await runMain(envFor(fake, fixtures, { PLAY_SIGNING_CERT_SHA256: OTHER_CERT }));
      assert.ok(error instanceof PublishError);
      assert.match(error.message, /downloaded APK is not signed with the expected production certificate/);
      assert.ok(!fake.calls.some((c) => c.path.includes("releases:upload")));
    } finally {
      await fake.close();
    }
  });

  it("fails on an unsigned APK when no apksigner is available", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const fake = await startFake({
      apk: readFileSync(fixtures.unsigned),
      list: [{ status: 200, json: { generatedApks: [universalGroup(fixtures.fingerprint)] } }],
      polls: [],
    });
    try {
      const { error } = await runMain(envFor(fake, fixtures));
      assert.ok(error instanceof PublishError);
      assert.match(error.message, /no JAR signature keytool can read and no apksigner/);
    } finally {
      await fake.close();
    }
  });

  it("reads an APK-signature-scheme-only APK with apksigner", async (t) => {
    if (!fixtures?.v2) return t.skip("Android SDK build-tools not available");
    const fake = await startFake({
      apk: readFileSync(fixtures.v2),
      list: [{ status: 200, json: { generatedApks: [universalGroup(fixtures.fingerprint)] } }],
      polls: [doneOperation("RELEASE_UPDATED")],
      groupExists: true,
    });
    try {
      const { outcome, error, logs } = await runMain(envFor(fake, fixtures, { APKSIGNER: fixtures.apksigner }));
      assert.equal(error, undefined, error?.message);
      assert.equal(outcome.result, "RELEASE_UPDATED");
      assert.ok(logs.some((l) => l.includes("read by apksigner")));
    } finally {
      await fake.close();
    }
  });

  it("stops on a 403 and names the reason", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const denied = {
      status: 403,
      json: { error: { status: "PERMISSION_DENIED", message: "The caller does not have permission", details: [{ reason: "IAM_PERMISSION_DENIED" }] } },
    };
    const fake = await startFake({ apk: Buffer.alloc(0), list: [denied], polls: [] });
    try {
      const { error } = await runMain(envFor(fake, fixtures));
      assert.ok(error instanceof PublishError);
      assert.match(error.message, /Play refused generatedapks\.list.*HTTP 403 PERMISSION_DENIED reason IAM_PERMISSION_DENIED/);
      assert.equal(fake.calls.filter((c) => c.path.includes("generatedApks")).length, 1);
    } finally {
      await fake.close();
    }
  });

  it("gives up when Play never lists the APK", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const fake = await startFake({ apk: Buffer.alloc(0), list: [{ status: 404, json: {} }], polls: [] });
    try {
      const { error } = await runMain(envFor(fake, fixtures));
      assert.ok(error instanceof PublishError);
      assert.match(error.message, /did not list a universal APK/);
      assert.equal(fake.calls.filter((c) => c.path.includes("generatedApks")).length, 30);
    } finally {
      await fake.close();
    }
  });

  it("fails when Firebase reports an operation error", async (t) => {
    if (!fixtures) return t.skip("JDK tools not available");
    const fake = await startFake({
      apk: readFileSync(fixtures.v1),
      list: [{ status: 200, json: { generatedApks: [universalGroup(fixtures.fingerprint)] } }],
      polls: [{ name: "op1", done: true, error: { code: 3, message: "The binary's package name does not match the app" } }],
    });
    try {
      const { error } = await runMain(envFor(fake, fixtures));
      assert.ok(error instanceof PublishError);
      assert.match(error.message, /Firebase could not process the release.*package name does not match/);
      assert.ok(!fake.calls.some((c) => c.path.includes(":distribute")));
    } finally {
      await fake.close();
    }
  });
});
