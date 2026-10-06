# Releasing Gua to Google Play

How Gua is built, signed and published to Google Play, and how a production release reaches the
Android beta testers through Firebase App Distribution. Gua is a fork of Element X Android; this
document covers only the Gua release path.

## 1. One-time setup

### Upload signing key
The release build is signed with an **upload key** (Play App Signing holds the app-signing key).
The signing config in `app/build.gradle.kts` reads, in order:

1. environment variables, then
2. a gitignored `app/keystore.properties` (see `app/keystore.properties.sample`).

If neither is present the release build falls back to the debug keystore, which Play rejects.

Generate an upload key (once):

```bash
keytool -genkeypair -v -keystore ~/gua-upload.jks -alias gua-upload \
  -keyalg RSA -keysize 4096 -validity 10950 -storetype PKCS12 \
  -dname "CN=Gua, O=Gua, C=BR"
```

Then either export `GUA_RELEASE_KEYSTORE`, `GUA_RELEASE_KEYSTORE_PASSWORD`,
`GUA_RELEASE_KEY_ALIAS`, `GUA_RELEASE_KEY_PASSWORD`, or copy
`app/keystore.properties.sample` to `app/keystore.properties` and fill it in.
**Back up the `.jks` and passwords. If lost, Play App Signing lets you reset the upload key,
but never the app-signing key.**

### Firebase
Push notifications and App Distribution use Gua's Firebase project `gua-global` (project number
`511804071315`). The project-wide values are in
`libraries/pushproviders/firebase/src/main/res/values/firebase.xml`; the app id of each package is
`BuildTimeConfig.GOOGLE_APP_ID_{RELEASE,DEV,DEBUG,NIGHTLY}` in `plugins/src/main/kotlin/config/`.

App Distribution must be activated for the `global.gua` app (Firebase console > Release & Monitor >
App Distribution). Testers and groups are project-wide, releases are per app: only the production
app is ever distributed to the beta group.

### Play developer account
- Verify the developer account (identity; **D-U-N-S** for an organization account).
- An **organization** account avoids the personal-account "12 testers / 14 days" pre-production rule.

### CI secrets and variables
`.github/workflows/publish-play.yml` reads these repository secrets:

| Secret | Purpose |
| --- | --- |
| `GUA_RELEASE_KEYSTORE_BASE64` | the upload keystore (`base64 -i gua-upload.jks`) |
| `GUA_RELEASE_KEYSTORE_PASSWORD`, `GUA_RELEASE_KEY_ALIAS`, `GUA_RELEASE_KEY_PASSWORD` | its passwords and alias |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console service account that uploads bundles; app permissions "Release to testing tracks" and "Release to production" on `global.gua` and `global.gua.dev` |
| `GUA_DEV_LOCAL_PROPERTIES` | the `gua.*` dev-host lines of `local.properties`, baked into the QA app |
| `GOOGLE_BETA_SA_JSON` | key of the beta-access service account, the same key `gua-support-inbox` uses for beta invites. Needs Play Console "View app information" on `global.gua` and the role `roles/firebaseappdistro.admin` on the Firebase project |

and these repository variables:

| Variable | Value |
| --- | --- |
| `FIREBASE_PROJECT_NUMBER` | `511804071315` |
| `FIREBASE_ANDROID_APP_ID` | the Firebase app id of `global.gua`: `1:511804071315:android:7a87ae8499f379204e1c66` (`BuildTimeConfig.GOOGLE_APP_ID_RELEASE`) |
| `FIREBASE_BETA_GROUP` | App Distribution group alias of the beta testers; default `android-beta` |
| `PLAY_SIGNING_CERT_SHA256` | SHA-256 of the Play **app signing** certificate (Play Console > Test and release > Setup > App signing), the same fingerprint as in `https://gua.global/.well-known/assetlinks.json`: `05:DF:39:93:8C:C7:E2:AC:A6:C3:A5:F3:3A:DD:F4:D2:91:CF:F4:BA:F9:A3:91:06:FD:09:00:B1:D0:9C:77:92` |

## 2. Build a signed release bundle locally

```bash
./gradlew clean :app:bundleGplayRelease
# -> app/build/outputs/bundle/gplayRelease/app-gplay-release.aab

# confirm it is NOT debug-signed (must show CN=Gua, not CN=Android Debug):
jarsigner -verify -certs app/build/outputs/bundle/gplayRelease/app-gplay-release.aab | grep -i "CN="
```

The bundle's versionCode is `Versions.VERSION_CODE` (the CalVer constants in
`plugins/src/main/kotlin/Versions.kt`, bumped by `tools/release/release.sh`) times 10, for example
`202606060` for 26.06.6. Play refuses a versionCode it has already accepted, so every production
release needs a new one.

For sideload or local QA, the debug APK needs nothing from Play:

```bash
./gradlew :app:assembleGplayDebug
# -> app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk   (applicationId global.gua.debug)
```

The debug build's OIDC redirect scheme is `global.gua.debug`; the MAS client registration must
allow it or debug-APK sign-in fails.

## 3. Play Console (first upload)

1. Create the app `global.gua`; enroll in **Play App Signing**.
2. **App content** declarations (all required before a public track):
   - Privacy policy URL (`https://gua.global/privacy`).
   - Data Safety: phone number, hashed contact identifiers, camera and microphone.
   - Export / encryption compliance (Gua is end-to-end encrypted).
   - Content rating (IARC) questionnaire.
   - Account deletion: in-app path exists; also add a public deletion URL.

   The Play build declares no foreground-service type other than `shortService` and no
   full-screen intent (calls and location sharing are not in the Play build), so neither
   declaration form applies.
3. **Store listing**: filled from `fastlane/metadata/android/en-US/` (title, descriptions,
   icon, feature graphic, screenshots).
4. Upload the AAB to **Closed testing**, add testers, and roll out. Promote to production once
   testing requirements are met.

## 4. Publishing with `publish-play.yml`

- **Label a PR `release-qa`**: builds that branch and ships it to the QA app (`global.gua.dev`,
  "Gua QA") on the internal track, completed. More pushes to the PR re-ship it.
- **Merge to `main`**: stages a production publish (track `production`, status `draft`) that waits
  for a `deploy-approvers` approval on the `production` environment.
- **Actions > Publish to Google Play > Run workflow**: either app, with the track and status picked
  by hand. `app=qa` cannot target the production track.

### Firebase App Distribution
After a production upload succeeds, the `publish-firebase` job hands the same build to the Android
beta testers (`.github/workflows/scripts/publish-firebase-app-distribution.mjs`):

1. asks Play for the APKs it generated for that versionCode and takes the universal APK from the
   signing-key group whose certificate equals `PLAY_SIGNING_CERT_SHA256` (Play needs a few minutes
   after the upload; the job waits up to 15);
2. downloads it and checks the signing certificate again on the runner, with `keytool -printcert
   -jarfile` or, for an APK without a JAR signature, `apksigner verify --max-sdk-version 36`;
3. uploads it to App Distribution, sets the release notes from
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` when that file exists, and
   distributes the release to the `FIREBASE_BETA_GROUP` group, creating the group empty when it
   does not exist yet.

The job prints the tester link and the Firebase console link of the release. Uploading the same
bytes again answers `RELEASE_UNMODIFIED`, so **Re-run failed jobs** on the workflow run repeats only
this job without rebuilding or re-uploading to Play. A QA publish never reaches Firebase. Testers
join the group through the beta-invite workflow in `gua-support-inbox`, not here.
