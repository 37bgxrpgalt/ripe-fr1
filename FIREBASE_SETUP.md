# Firebase sync — setup & plan (web first, then Android)

Goal: sign in once with Google and see the **same readings and history** on the
web dashboard *and* the Android app — whichever device is actually talking to the
ESP32.

Everything in **Part A** is console work you do once (it needs your Google account
and creates resources in your project). When it's done, send me the two items
marked **→ SEND ME** and I'll wire the code.

---

## Part A · Console setup

### 1 · Create the project
1. <https://console.firebase.google.com> → **Add project**.
2. Name it (e.g. `ripe-fr1`). Google Analytics is optional (not needed).
3. Wait for provisioning, then **Continue**.

### 2 · Register the **Web** app  → SEND ME
1. Project overview → **Add app** → **Web** (`</>`).
2. Nickname: `Ripe web`. **Don't** tick "Also set up Firebase Hosting" yet
   (that's step 7).
3. Copy the whole `firebaseConfig = { … }` snippet and paste it to me.

### 3 · Register the **Android** app  → SEND ME the file
1. **Add app** → **Android**.
2. Android package name: **`com.example.foodmonitor`** (must match exactly — it's
   the `applicationId` in `app/build.gradle.kts`).
3. Nickname: `Ripe Android`. Register.
4. Download **`google-services.json`** → drop it in `D:\FoodMonitorApp\app\`.
5. (Later, when we add the Android SDK.) Google sign-in on Android also wants the
   **SHA-1** of the signing key: run this in `D:\FoodMonitorApp` and add the
   `debug` fingerprint under Project settings → your Android app → *Add
   fingerprint*:
   ```
   .\gradlew signinReport
   ```

### 4 · Enable Google sign-in
1. **Build → Authentication → Get started**.
2. **Sign-in method → Google → Enable**. Pick a support email → **Save**.
3. **Authentication → Settings → Authorized domains**: `localhost` is there by
   default. We'll add the Firebase Hosting domain in step 7.

> **Why this matters:** Google sign-in only works on a *real domain* (or
> `localhost`) — **not** on a bare IP such as `http://192.168.1.57`. The ESP32
> serves its page from an IP, so that page can never sign in. That's why the
> cloud version of the web dashboard is deployed to **Firebase Hosting**
> (`https://<project>.web.app`), while the ESP32 keeps serving the embedded page
> as the local / offline fallback.

### 5 · Create Firestore
**Build → Firestore Database → Create database** → **Production mode** → choose a
region near you.

### 6 · Security rules
Firestore → **Rules** tab → paste → **Publish**:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

Each person can only read/write their own `users/{uid}` tree.

### 7 · Firebase Hosting (do this when the web code is ready)
**Build → Hosting → Get started**. I'll give you the exact `firebase.json` and
`firebase deploy` command then — this is what creates the `https://…web.app`
domain that step 4 needs.

---

## Part B · What I'll build

### Data model (Firestore)
```
users/{uid}/
  state/latest        → the newest snapshot (single doc, real-time)
  readings/{autoId}   → history (one doc per status change)
```

`state/latest` fields mirror `/data` exactly —
`raw, avg, pct, status, thr_warn, thr_spoiled, cal_floor, cal_ceil, t, source,
updatedAt` — so the **same** dashboard renders reads with no special-casing.

### Web (`web/index.html`) additions
- Loads the Firebase **compat/auth/firestore** SDKs from the CDN (only when a
  config file is present, so the ESP32's offline page is unaffected).
- **Settings → Account**: *Sign in with Google* button; avatar + email when
  signed in; *Sign out*.
- **Settings → Sync** (seg, mirrors the other segments):
  - **Off** — today's behaviour (local only).
  - **Publish this device** — keep reading the ESP32 / BLE / mock locally and
    push each snapshot to `users/{uid}/state/latest`, plus a `readings` doc per
    status change. Use this on the device that can reach the sensor.
  - **Follow the cloud** — ignore the local source and render `state/latest` in
    real time; the history panel reads recent `readings`.
- The header connection chip shows the account + sync state.

### Android (after the web side is verified)
- Firebase Auth (`Google` sign-in) + Firestore via the BOM; `google-services.json`
  + the `com.google.gms.google-services` Gradle plugin.
- Same **Account** + **Sync** settings, same data model — so a phone can publish
  to the ESP32 over Wi-Fi/BLE and the web (or another phone) follows, and vice
  versa.

### Why the ESP32 doesn't talk to Firebase directly
The board has no Firebase credentials and is usually on its own AP with no
internet. Publishing is done by a **client** that can reach the board.

---

## What I need from you
1. The **`firebaseConfig`** object (step 2).
2. Confirmation that Google sign-in, Firestore and the rules are done
   (steps 4–6).

Once I have those I'll build and verify the **web** side first, then add the
Android SDK (that needs an online Gradle sync in Android Studio — this PC builds
the app offline and can't fetch the Firebase dependencies).
