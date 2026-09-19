# Releasing Gua to Google Play

How to build a signed Gua release bundle and publish it to a Google Play testing track.
Gua is a fork of Element X Android; this document covers only the Gua-specific release path.

## 1. One-time setup

### Upload signing key
The release build is signed with an **upload key** (Play App Signing holds the real
app-signing key). The signing config in `app/build.gradle.kts` reads, in order:

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

### Push notifications (Firebase / FCM) — optional for the first beta
Push is wired but ships with **no credentials** (Element's project + key were removed). Until a
Gua Firebase project is configured, push is inert (the app still runs; notifications only arrive
while the app is foregrounded). To enable:

1. Create a Firebase project. Add Android apps for `global.gua`, `global.gua.debug`,
   `global.gua.nightly`.
2. Paste the values from each `google-services.json` into
   `libraries/pushproviders/firebase/src/main/res/values/firebase.xml`.
3. Set `BuildTimeConfig.GOOGLE_APP_ID_{RELEASE,DEBUG,NIGHTLY}` to the matching
   `mobilesdk_app_id` values.
4. Register the FCM sender with the Gua push gateway.

### Play developer account
- Verify the developer account (identity; **D-U-N-S** for an organization account).
- An **organization** account avoids the personal-account "12 testers / 14 days" pre-production rule.

## 2. Build a signed release bundle

```bash
./gradlew clean :app:bundleGplayRelease
# -> app/build/outputs/bundle/gplayRelease/app-gplay-release.aab   (versionCode 202606040)

# confirm it is NOT debug-signed (must show CN=Gua, not CN=Android Debug):
jarsigner -verify -certs app/build/outputs/bundle/gplayRelease/app-gplay-release.aab | grep -i "CN="
```

For sideload / internal QA, the debug APK needs nothing from Play:

```bash
./gradlew :app:assembleGplayDebug
# -> app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk   (applicationId global.gua.debug)
```

Note: the debug build's OIDC redirect scheme is `global.gua.debug`; the MAS client
registration must whitelist it or debug-APK sign-in fails.

## 3. Play Console (first upload)

1. Create the app `global.gua`; enroll in **Play App Signing**.
2. **App content** declarations (all required before a public track):
   - Privacy policy URL (`https://gua.global/privacy`).
   - Data Safety: phone number, hashed contact identifiers, camera/microphone.
   - Export / encryption compliance (Gua is end-to-end encrypted).
   - Content rating (IARC) questionnaire.
   - Foreground-service types (microphone) justification.
   - `USE_FULL_SCREEN_INTENT` and `READ_CONTACTS` permission declarations.
   - Account deletion: in-app path exists; also add a public deletion URL.
3. **Store listing**: filled from `fastlane/metadata/android/en-US/` (title, descriptions,
   icon, feature graphic, screenshots).
4. Upload the AAB to **Closed testing**, add testers (email list), and roll out.
   Promote to production once testing requirements are met.

## 4. Automating the upload (later)

Add a `workflow_dispatch` job that builds `bundleGplayRelease` with the `GUA_RELEASE_*`
secrets, then uploads with `r0adkll/upload-google-play` (a Play service-account JSON secret,
`packageName: global.gua`, `track: internal`). `.github/workflows/release.yml` already builds
the bundle; it needs the signing secrets and the upload step wired.
