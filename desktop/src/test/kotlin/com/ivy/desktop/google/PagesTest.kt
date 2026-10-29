package com.ivy.desktop.google

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PagesTest {
    @Test
    fun `the PKCE challenge is the SHA-256 of the verifier, as in RFC 7636`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `verifiers are long enough, safe in a web address, and new each time`() {
        val verifier = Pkce.verifier()

        assertEquals(43, verifier.length)
        assertTrue(verifier.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(verifier, Pkce.verifier())
    }

    @Test
    fun `messages show as text`() {
        assertTrue("<p>Fish &amp; chips &lt;b&gt;</p>" in Pages.message("Fish & chips <b>"))
    }
}
