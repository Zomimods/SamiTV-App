# SAMI Live TV — release setup

Nothing secret is stored in this repository. Add these under
GitHub → Settings → Secrets and variables → Actions:

| Secret | Required | Content |
|---|---|---|
| `SAMI_KEYSTORE_BASE64` | release | Base64 of your upload keystore (`.jks`) |
| `SAMI_KEYSTORE_PASSWORD` | release | Keystore password |
| `SAMI_KEY_ALIAS` | release | Key alias |
| `SAMI_KEY_PASSWORD` | release | Key password |
| `GOOGLE_SERVICES_JSON_BASE64` | optional | Base64 of `google-services.json`; without it the app runs local-only |

## Create a keystore (on your machine, never in chat or the repo)

```bash
keytool -genkeypair -v -keystore release.jks -alias sami -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # paste the output into SAMI_KEYSTORE_BASE64
```

Back the keystore up; losing it means you cannot update an installed app.

## Run a release

- Tag: `git tag v1.0.0 && git push origin v1.0.0` (versionName from the tag, versionCode = run number), or
- Actions → "Release APK" → Run workflow.

The job fails on missing secrets, runs unit tests, builds, verifies the signature
with `apksigner`, then uploads the APK (and attaches it to the release on tags).

## Local signed build

```bash
export SAMI_KEYSTORE_FILE=/path/release.jks SAMI_KEYSTORE_PASSWORD=... SAMI_KEY_ALIAS=... SAMI_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```
Without these variables the release build is unsigned.
