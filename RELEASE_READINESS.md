# SAMI Live TV — Part 6 release-readiness review

## Current decision: not yet release-verified

The review added unit coverage for the local/cloud library merge and checked
the Firestore rule boundaries. A signed APK and a live Firebase project could
not be tested from this workspace, so this is not a release approval.

## Automated checks added

`app/src/test/java/com/sami/livetv/data/cloud/CloudLibraryMergeTest.kt` covers:

- first sign-in merging local and remote favorites/history without duplicates;
- a local favorite removal surviving a sync against an unchanged remote copy;
- concurrent favorite changes on two devices;
- history ordering and the ten-item cap.

Run these tests after installing the Android SDK:

```bash
./gradlew :app:testDebugUnitTest
```

## Firestore and privacy review

- Library documents are owner-only, limited to 500 favorite IDs and 10 history
  entries, and require a server timestamp on writes.
- Usage documents are owner-only, but clients can write their own counters. Treat
  these values as approximate display statistics only—not billing or audit data.
- Channel comments are publicly readable. Posting requires Firebase
  Authentication; the rules limit text to 500 characters and require the server
  timestamp. New comments contain only text and timestamp: no Firebase UID or
  email is stored. Editing and client-side deletion are denied.
- A public comment board still needs moderation and server-enforced
  rate-limiting before launch. Consider Firebase App Check as an additional
  abuse-control measure.
- Passwords are passed to Firebase Authentication and are not written into
  DataStore or Firestore. Account email is managed by Firebase Authentication;
  comment text is public.
- The catch-all Firestore rule denies all paths not explicitly allowed above.

## Required verification before release

1. Install Android SDK Platform 35 and Build-Tools 35.0.0; then run
   `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.
2. Configure Firebase locally as described in `firebase/FIREBASE_SETUP.md`.
   Do not commit `app/google-services.json`.
3. Run Firestore Security Rules tests in the Firebase Emulator Suite. Verify
   cross-user library reads/writes are denied, anonymous comment writes are
   denied, comments over 500 characters are denied, and malformed/extra fields
   are denied.
4. With two test accounts/devices, verify initial sync, favorite add/remove,
   recent-history merge, offline changes, and account switching.
5. Verify no-config launch and local favorites/history, email/password sign-up,
   sign-in/out, comment display/posting, and usage counters on a real emulator or
   device.
6. Decide whether email verification and password recovery are required for the
   intended release; neither flow is implemented in this part.
7. Publish a privacy notice for public comments and Firebase Authentication
   data. Add moderation/rate-limits before enabling comments for an open
   audience.

## Environment checks performed for Part 6

- XML resources parse successfully and Arabic/English string names match.
- The version catalog parses successfully.
- The Part 6 ZIP passes `unzip -t` and contains only the listed source/test/docs
  files (no Firebase config).
- Android SDK Platform 35, Build-Tools, Firebase CLI, and Firebase Emulator
  Suite are not installed in this workspace. `ANDROID_HOME` and
  `ANDROID_SDK_ROOT` are unset, so Gradle test/build and emulator rule tests
  remain unverified here.
