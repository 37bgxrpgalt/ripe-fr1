/* Ripe FR-1 — optional cloud sync (Firebase Auth + Firestore).
 *
 * Loaded on demand by index.html through a guarded dynamic import, so the
 * ESP32's embedded offline page — which has neither this file nor internet —
 * is completely unaffected.
 *
 * The modular SDK is pulled from the Firebase CDN at the exact version the
 * console gave us. Everything is scoped per signed-in uid:
 *
 *   users/{uid}/state/latest      one doc: the newest snapshot (real-time)
 *   users/{uid}/readings/{autoId} one doc per status change (history)
 *
 * The household model: everyone signs in with the SAME Google account, so they
 * all get the same uid and therefore the same data.
 */

import { initializeApp }
  from "https://www.gstatic.com/firebasejs/13.0.0/firebase-app.js";
import {
  getAuth, onAuthStateChanged, GoogleAuthProvider, signInWithPopup,
  signInWithRedirect, getRedirectResult, signOut
} from "https://www.gstatic.com/firebasejs/13.0.0/firebase-auth.js";
import {
  getFirestore, doc, setDoc, onSnapshot, collection, addDoc, query,
  orderBy, limit, getDocs, serverTimestamp
} from "https://www.gstatic.com/firebasejs/13.0.0/firebase-firestore.js";

import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const provider = new GoogleAuthProvider();

/* finish a redirect sign-in if a previous popup was blocked by the browser */
getRedirectResult(auth).catch(function () {});

/* --------------------------------------------------------------- identity -- */

export function onUser(cb) { return onAuthStateChanged(auth, cb); }

export function signIn() {
  return signInWithPopup(auth, provider).catch(function (e) {
    if (e && e.code === 'auth/popup-blocked') {
      return signInWithRedirect(auth, provider);
    }
    throw e;
  });
}

export function signOutNow() { return signOut(auth); }

/* ------------------------------------------------------------------ data -- */

function docState(uid) {
  return doc(db, 'users', uid, 'state', 'latest');
}
function colReadings(uid) {
  return collection(db, 'users', uid, 'readings');
}

/* snapshot() = the /data JSON shape → one Firestore doc the dashboard can
   render unchanged. */
export function publish(uid, d) {
  return setDoc(docState(uid), {
    raw: d.raw,
    avg: d.avg,
    pct: d.pct,
    status: d.status,
    thr_warn: d.thr_warn,
    thr_spoiled: d.thr_spoiled,
    cal_floor: d.cal_floor,
    cal_ceil: d.cal_ceil,
    t: d.t || '',
    mode: d.mode || 'ESP32',
    ip: d.ip || '',
    rssi: (typeof d.rssi === 'number' ? d.rssi : 0),
    uptime_s: (typeof d.uptime_s === 'number' ? d.uptime_s : 0),
    updatedAt: serverTimestamp()
  });
}

export function addReading(uid, e) {
  return addDoc(colReadings(uid), {
    t: e.t || '',
    status: e.status,
    avg: e.avg,
    ts: Date.now()
  });
}

/* real-time newest snapshot; cb(null) while nothing has been published yet */
export function follow(uid, cb, onErr) {
  return onSnapshot(docState(uid), function (snap) {
    cb(snap.exists() ? snap.data() : null);
  }, function (err) { if (onErr) onErr(err); });
}

/* newest-first history for the status panel */
export function recentReadings(uid, n) {
  const q = query(colReadings(uid), orderBy('ts', 'desc'), limit(n || 30));
  return getDocs(q).then(function (out) {
    const rows = [];
    out.forEach(function (d) { rows.push(d.data()); });
    return rows;
  });
}

/* ------------------------------------------------------------ settings -- */
/* Look-and-feel settings that follow the account across every signed-in
   device (theme, typeface, Basic/Advanced, poll interval). Per-device things
   like the data source and the ESP32 address deliberately stay local. */

function docPrefs(uid) {
  return doc(db, 'users', uid, 'prefs', 'main');
}

/* cb(null) the first time — nothing has been stored for this account yet */
export function watchPrefs(uid, cb, onErr) {
  return onSnapshot(docPrefs(uid), function (snap) {
    cb(snap.exists() ? snap.data() : null);
  }, function (err) { if (onErr) onErr(err); });
}

export function pushPrefs(uid, p) {
  return setDoc(docPrefs(uid), {
    theme: p.theme,
    font: p.font,
    mode: p.mode,
    pollMs: p.pollMs,
    updatedAt: serverTimestamp()
  }, { merge: true });
}
