package com.example.foodmonitor

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Android half of the cloud sync (see web/firebase.js).
 *
 *   users/{uid}/prefs/main      look & feel shared by every signed-in device
 *   users/{uid}/state/latest    newest snapshot (one doc, real time)
 *   users/{uid}/readings/{id}   history, one doc per status change
 *
 * The web dashboard is the source of truth for the data shape; the field names
 * here are kept byte-for-byte identical to it. Everything runs through the
 * default FirebaseApp that the google-services Gradle plugin installs.
 */
data class CloudPrefs(
    val theme: String?,
    val font: String?,
    val mode: String?,
    val pollingMs: Long?
)

/** A snapshot pulled from Firestore — mapped to the app's [Reading]. */
data class CloudReading(
    val raw: Int, val avg: Int, val pct: Int, val status: String,
    val warn: Int, val spoiled: Int, val floor: Int, val ceil: Int,
    val mode: String, val t: String
) {
    fun toReading(): Reading = Reading(raw, avg, pct, status, warn, spoiled, floor, ceil)
}

object RipeCloud {
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var lastPub = 0L
    private var lastStatus: String? = null

    /** Touch the singletons early so a missing/misconfigured project fails fast. */
    fun init() {
        FirebaseAuth.getInstance()
        FirebaseFirestore.getInstance()
    }

    val currentUser: FirebaseUser? get() = FirebaseAuth.getInstance().currentUser

    /* ---------------------------------------------------------------- auth -- */

    fun watchAuth(cb: (FirebaseUser?) -> Unit): FirebaseAuth.AuthStateListener {
        val l = FirebaseAuth.AuthStateListener { cb(it.currentUser) }
        FirebaseAuth.getInstance().addAuthStateListener(l)
        return l
    }

    fun unwatchAuth(l: FirebaseAuth.AuthStateListener) {
        FirebaseAuth.getInstance().removeAuthStateListener(l)
    }

    fun signIn(email: String, pass: String, onDone: (String?) -> Unit) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { t ->
                onDone(if (t.isSuccessful) null else friendly(t.exception))
            }
    }

    fun createAccount(email: String, pass: String, onDone: (String?) -> Unit) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { t ->
                onDone(if (t.isSuccessful) null else friendly(t.exception))
            }
    }

    fun signOutNow() {
        FirebaseAuth.getInstance().signOut()
    }

    /** Plain-language message, mirroring the web dashboard's acctError(). */
    private fun friendly(e: Exception?): String {
        val code = (e as? FirebaseAuthException)?.errorCode
            ?: return e?.message ?: "Sign-in failed."
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "That email address looks wrong."
            "ERROR_MISSING_PASSWORD" -> "Enter the password."
            "ERROR_USER_NOT_FOUND" -> "No account with that email — tap Create account."
            "ERROR_WRONG_PASSWORD" -> "Wrong password."
            "ERROR_INVALID_CREDENTIAL" -> "Email or password is incorrect."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has an account — tap Sign in."
            "ERROR_WEAK_PASSWORD" -> "Use a password with at least 6 characters."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Enable Email/Password in Firebase Authentication."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts — wait a minute and try again."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error — check your connection."
            else -> e.message ?: "Sign-in failed."
        }
    }

    /* -------------------------------------------------------- Google sign-in -- */

    /** The OAuth *web* client ID the google-services plugin writes from the JSON. */
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }

    /**
     * Runs the Credential Manager "Sign in with Google" flow. Its bottom sheet is
     * the account chooser and appears even when the device has a single Google
     * account. Calls back with a plain-language error, or null on success.
     */
    suspend fun googleSignIn(context: Context, onDone: (String?) -> Unit) {
        val web = webClientId(context)
        if (web == null) {
            onDone("Google sign-in isn't configured yet - add this app's SHA-1 in Firebase and re-download google-services.json.")
            return
        }
        val cm = CredentialManager.create(context)
        try {
            // Prefer accounts already used with this app; if there are none, fall
            // back to every Google account on the device (the documented flow).
            val idToken = try {
                requestGoogleToken(cm, context, web, authorizedOnly = true)
            } catch (e: NoCredentialException) {
                requestGoogleToken(cm, context, web, authorizedOnly = false)
            }
            FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener { r -> onDone(if (r.isSuccessful) null else friendly(r.exception)) }
        } catch (e: GetCredentialCancellationException) {
            onDone("Google sign-in cancelled.")
        } catch (e: NoCredentialException) {
            onDone("No Google account available on this device.")
        } catch (e: GoogleIdTokenParsingException) {
            onDone("Couldn't read the Google token - try again.")
        } catch (e: GetCredentialException) {
            onDone(e.message ?: "Google sign-in failed.")
        } catch (e: Exception) {
            onDone(e.message ?: "Google sign-in failed.")
        }
    }

    private suspend fun requestGoogleToken(
        cm: CredentialManager,
        context: Context,
        serverClientId: String,
        authorizedOnly: Boolean
    ): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setAutoSelectEnabled(false) // always show the chooser
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = cm.getCredential(context, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected Google credential type: ${credential.type}")
    }

    /** Best effort - clear the cached Google account state so the sheet reappears. */
    suspend fun googleSignOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Firebase sign-out still happens; the cache is simply not cleared.
        }
    }

    /* --------------------------------------------------------------- prefs -- */

    private fun userDoc(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid)

    private fun prefsDoc(uid: String) =
        userDoc(uid).collection("prefs").document("main")

    /** "dark" → "Dark" — the web stores lowercase, the app's segments are capitalised. */
    private fun cap(s: String?): String? =
        if (s.isNullOrEmpty()) null else s.substring(0, 1).uppercase() + s.substring(1)

    /** onData(null) the first time — nothing stored for this account yet. */
    fun watchPrefs(
        uid: String,
        onData: (CloudPrefs?) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = prefsDoc(uid).addSnapshotListener { snap, err ->
        if (err != null) {
            onError(err.message ?: "Cloud read failed.")
            return@addSnapshotListener
        }
        if (snap == null || !snap.exists()) {
            onData(null)
            return@addSnapshotListener
        }
        onData(
            CloudPrefs(
                theme = cap(snap.getString("theme")),
                font = cap(snap.getString("font")),
                mode = cap(snap.getString("mode")),
                pollingMs = snap.getLong("pollMs")
            )
        )
    }

    fun pushPrefs(uid: String, theme: String, font: String, mode: String, pollingMs: Long) {
        prefsDoc(uid).set(
            mapOf(
                // The web stores lowercase ("dark", "editorial", "basic"); the app
                // uses "Dark", "Editorial", "Basic" — normalise at the wire.
                "theme" to theme.lowercase(),
                "font" to font.lowercase(),
                "mode" to mode.lowercase(),
                "pollMs" to pollingMs,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
    }

    /* ------------------------------------------------------------ readings -- */

    private fun stateDoc(uid: String) =
        userDoc(uid).collection("state").document("latest")

    /**
     * Mirror one locally-read snapshot to the account. Throttled to one write
     * every 10 s, except a status change writes immediately and also appends a
     * history row — identical to the web's cloudMaybePublish().
     */
    fun publish(uid: String, r: Reading) {
        val now = System.currentTimeMillis()
        val changed = r.status != lastStatus
        if (!changed && now - lastPub < 10_000L) return
        lastPub = now
        lastStatus = r.status
        val t = clock.format(Date())
        stateDoc(uid).set(
            mapOf(
                "raw" to r.raw, "avg" to r.avg, "pct" to r.pct, "status" to r.status,
                "thr_warn" to r.warn, "thr_spoiled" to r.spoiled,
                "cal_floor" to r.floor, "cal_ceil" to r.ceil,
                "mode" to "APP", "t" to t, "ip" to "", "rssi" to 0, "uptime_s" to 0,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (changed) {
            // `ts` is a plain epoch-millis number, exactly like web/firebase.js
            // addReading() — the history query orders by it.
            userDoc(uid).collection("readings").add(
                mapOf(
                    "t" to t, "status" to r.status, "avg" to r.avg,
                    "ts" to System.currentTimeMillis()
                )
            )
        }
    }

    fun watchLatest(
        uid: String,
        onData: (CloudReading?) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = stateDoc(uid).addSnapshotListener { snap, err ->
        if (err != null) {
            onError(err.message ?: "Cloud read failed.")
            return@addSnapshotListener
        }
        if (snap == null || !snap.exists()) {
            onData(null)
            return@addSnapshotListener
        }
        onData(
            CloudReading(
                raw = snap.getLong("raw")?.toInt() ?: 0,
                avg = snap.getLong("avg")?.toInt() ?: 0,
                pct = snap.getLong("pct")?.toInt() ?: 0,
                status = snap.getString("status") ?: "FRESH",
                warn = snap.getLong("thr_warn")?.toInt() ?: 1800,
                spoiled = snap.getLong("thr_spoiled")?.toInt() ?: 2800,
                floor = snap.getLong("cal_floor")?.toInt() ?: 1000,
                ceil = snap.getLong("cal_ceil")?.toInt() ?: 3800,
                mode = snap.getString("mode") ?: "",
                t = snap.getString("t") ?: ""
            )
        )
    }

    /** Newest-first history rows for the Status panel, mapped to [LogEntry]. */
    fun recentReadings(uid: String, n: Int, onData: (List<LogEntry>) -> Unit) {
        userDoc(uid).collection("readings")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(n.toLong())
            .get()
            .addOnSuccessListener { out ->
                val rows = ArrayList<LogEntry>()
                out.forEach { d ->
                    rows.add(
                        LogEntry(
                            time = d.getString("t") ?: "--:--:--",
                            status = d.getString("status") ?: "?",
                            rawValue = d.getLong("avg")?.toInt() ?: 0
                        )
                    )
                }
                onData(rows)
            }
    }
}
