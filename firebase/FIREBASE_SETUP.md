# Firebase setup for SAMI Live TV

Part 5 contains an optional Firebase Auth + Cloud Firestore adapter. The app
remains usable with its existing local DataStore when Firebase is not configured.
In that state, the account screen explicitly reports that cloud features are off;
it does not create a pretend session or report a successful sync.

## Enable the cloud integration

1. Create or choose a Firebase project and register an Android app whose package
   name is exactly `com.sami.livetv`.
2. Enable **Authentication → Email/Password** in the Firebase console.
3. Create a Cloud Firestore database.
4. Download that app's `google-services.json` and place it at
   `app/google-services.json` on your machine. This file is ignored by Git and is
   intentionally not included in this deliverable. Do not paste credentials or
   configuration values into chat.
5. Publish `firebase/firestore.rules` to the project's Firestore database.
6. Sync Gradle and build again. The Google Services plugin is applied only when
   `app/google-services.json` exists. Without it, the Firebase SDK dependencies
   compile but the app stays in local-only mode.

The implementation uses the Firebase Android BoM and Google Services plugin
versions listed in the Gradle files. It stores account library data under
`users/{uid}`, per-user usage counters under `users/{uid}/usage/summary`, and
channel comments under `channels/{channelId}/comments/{commentId}`.

Favorites and watch history remain in DataStore for offline use. On sign-in, the
app merges the local and remote library, records a per-account sync baseline,
then saves subsequent local changes (including favorite removals) to the
signed-in account. Comment posting and usage counters require a signed-in
Firebase user. Public channel comments can be read when the database rules allow
it. Comments contain only their text and server timestamp; the app does not
write the author's Firebase UID or email into a comment.

The usage counters are client-reported convenience statistics, not tamper-proof
analytics. Do not use them for billing, security decisions, or audited reporting.
Comments are public and the sample rules do not provide moderation or
rate-limiting. Add moderation and a server-enforced posting limit before opening
a production community. Consider enabling Firebase App Check before public
release.
