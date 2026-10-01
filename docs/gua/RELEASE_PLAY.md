# Releasing Gua to Google Play

How Gua builds reach Google Play. The mechanics live in `.github/workflows/publish-play.yml`.

## 1. The two apps

| | QA app | Production app |
|---|---|---|
| Application id | `global.gua.dev` ("Gua QA") | `global.gua` ("Gua") |
| Backend | dev stack (`-Pgua.deployment=dev`) | gua.global |
| Ships on | the `release-qa` label on a pull request | a merge to `main` |
| Play track | internal, status completed | production, uploaded as a draft |
| Approval | none (the `dev` environment is unprotected) | the `production` environment's required reviewers |

## 2. Ship a QA build

Put the `release-qa` label on the pull request. The workflow builds that branch, signs it with the
upload key and publishes it to the QA app's internal track, then comments on the PR with the track
and commit. Every later push to a labelled PR ships again. Only people with write access can add the
label. The QA versionCode adds the workflow run number (`-Pgua.versionCodeOffset`) so one label can
ship many times without Play rejecting a reused code.

## 3. Ship a production build

1. Open a release pull request from `develop` to `main` and merge it.
2. The push to `main` starts the `publish-prod` job, which waits on the `production` environment.
   Nothing reaches Play until a required reviewer approves the deployment. The environment accepts
   deployments from `main` only.
3. On approval the job checks that the Play service account can open an edit on `global.gua`,
   builds `bundleGplayRelease` with no `-Pgua.deployment` (the production app, pointing at
   gua.global) and uploads the bundle to the production track as a draft. Rolling out is a Play
   Console decision.

`workflow_dispatch` runs either path by hand from the Actions tab, with the app (`qa` or `prod`),
track and release status as inputs. A dispatched `prod` publish still waits on the `production`
environment. `app=qa` with `track=production` is refused because the QA job has no approval gate.
To put a production build in front of internal testers, dispatch `app=prod`, `track=internal`,
`status=completed`; testers opt in through the production app's internal-testing link in Play
Console.

## 4. Version numbers

`plugins/src/main/kotlin/Versions.kt` holds the CalVer version: `VERSION_NAME` is `YY.MM.N` and
`VERSION_CODE` is `(2000 + YY) * 10000 + MM * 100 + N`. `app/build.gradle.kts` adds the QA offset,
multiplies by 10 and adds the ABI index (0 for the bundle), so release 26.06.5 uploads as
versionCode 202606050.

Play refusing a versionCode as already used means `versionReleaseNumber` in `Versions.kt` needs a
bump. That is the intended signal, not a CI fault.

## 5. Signing and secrets

The release build is signed with an **upload key**; Play App Signing holds the app-signing key.
`app/build.gradle.kts` reads the signing config from environment variables first, then from a
gitignored `app/keystore.properties` (template: `app/keystore.properties.sample`). Without either,
the release build falls back to the debug keystore, which Play rejects.

Repository secrets the workflow reads (names only):

| Secret | Used for |
|---|---|
| `GUA_RELEASE_KEYSTORE_BASE64` | the upload keystore, decoded to a file at build time |
| `GUA_RELEASE_KEYSTORE_PASSWORD`, `GUA_RELEASE_KEY_ALIAS`, `GUA_RELEASE_KEY_PASSWORD` | keystore and key credentials |
| `PLAY_SERVICE_ACCOUNT_JSON` | the Play Developer API service account |
| `GUA_DEV_LOCAL_PROPERTIES` | QA only: the `gua.*` dev host properties |

Back up the upload keystore and its passwords. Play App Signing can reset a lost upload key, never
the app-signing key.

A local signed build, for checking the signing configuration:

```bash
./gradlew clean :app:bundleGplayRelease
# -> app/build/outputs/bundle/gplayRelease/app-gplay-release.aab
# must show CN=Gua, not CN=Android Debug:
jarsigner -verify -certs app/build/outputs/bundle/gplayRelease/app-gplay-release.aab | grep -i "CN="
```

The debug APK (`./gradlew :app:assembleGplayDebug`, application id `global.gua.debug`) needs nothing
from Play. Its OIDC redirect scheme is `global.gua.debug`, which the auth service's client
registration must allow or debug sign-in fails.

## 6. Push notifications

Push is configured. The Firebase values for the release, QA and debug application ids are committed
in `libraries/pushproviders/firebase/src/main/res/values/firebase.xml` and
`BuildTimeConfig.GOOGLE_APP_ID_*`; there is no `google-services.json` in the tree. The Gua push
gateway serves the production pusher (`PushConfig.PUSHER_APP_ID`, `global.gua.android`).

## 7. Play Console checklist

Required before a closed or production track; the internal track accepts an unreviewed app:

- Privacy policy URL: `https://gua.global/privacy`.
- Data Safety: phone number, hashed contact identifiers, camera and microphone.
- Export and encryption compliance (Gua is end-to-end encrypted).
- Content rating (IARC) questionnaire.
- Foreground-service type (microphone) justification.
- `USE_FULL_SCREEN_INTENT` and `READ_CONTACTS` permission declarations.
- Account deletion: the in-app path plus the public URL `https://gua.global/delete-account`.
- Store listing from `fastlane/metadata/android/en-US/`.

A personal developer account needs a closed test with 12 testers for 14 days before production or
open testing; the internal track is not affected.
