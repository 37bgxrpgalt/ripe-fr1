/* Ripe FR-1 — Firebase project config.
 *
 * This file is safe to commit and to serve publicly: a Firebase *web* config is
 * an identifier, not a secret. Access is controlled entirely by the Firestore
 * security rules (users can only touch users/{their-own-uid}) plus the
 * Authorized-domains list in Firebase Authentication — not by hiding these keys.
 *
 * Everything here is public information from:
 *   Firebase console → Project settings → General → Your apps → Ripe web
 */
export const firebaseConfig = {
  apiKey: "AIzaSyAvN293adqAgtp_EDdja9kLdu7YZpHgXOA",
  authDomain: "ripe-fr1.firebaseapp.com",
  projectId: "ripe-fr1",
  storageBucket: "ripe-fr1.firebasestorage.app",
  messagingSenderId: "361077385781",
  appId: "1:361077385781:web:624f31ffe34aed8f3adf7c"
};
