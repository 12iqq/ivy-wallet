package com.ivy.desktop.google

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Proof that the app which asks Google for the tokens is the one that opened the sign-in (PKCE, RFC 7636): the
 * sign-in gets [challenge] of a random [verifier], and only the app knows the verifier.
 */
internal object Pkce {
    private val random = SecureRandom()
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    /** Random letters, digits, - and _, also used as the state that ties Google's answer to the sign-in */
    fun verifier(): String = base64.encodeToString(ByteArray(RandomBytes).also(random::nextBytes))

    fun challenge(verifier: String): String =
        base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    // 43 characters, the shortest verifier allowed
    private const val RandomBytes = 32
}
