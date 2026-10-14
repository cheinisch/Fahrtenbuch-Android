package app.fahrtenbuch.mobile.data.api

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** Rücksprungadresse der App nach Google/Microsoft-Anmeldung (Server: OAUTH_APP_REDIRECT_URIS). */
const val OAUTH_REDIRECT_URI = "fahrtenbuch://oauth/callback"

/** TOTP ist aktiv: Ticket mit Zwei-Faktor-Code erneut einlösen. */
class OAuthMfaRequired : Exception("MFA_REQUIRED")

/** PKCE nach RFC 7636 (S256): Verifier bleibt in der App, nur die Challenge geht an den Server. */
internal object Pkce {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /** 64 zufällige Bytes → 86 Zeichen Base64url (erlaubt sind 43–128). */
    fun newVerifier(): String = ByteArray(64).also(SecureRandom()::nextBytes).let { Base64.encodeToString(it, FLAGS) }

    /** BASE64URL(SHA256(verifier)) ohne Padding = 43 Zeichen. */
    fun challenge(verifier: String): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)), FLAGS)
}
