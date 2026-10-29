package com.ivy.desktop.google

import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `the picker page can't be broken by what's put in it`() {
        val config = GoogleConfig("client-1", "secret-1", "key</script><script>alert(1)", "1234")

        val page = Pages.picker(config, AccessToken("access-1", Instant.EPOCH), "/chosen/state-1")

        assertFalse("key</script>" in page)
        assertTrue(""""answer":"/chosen/state-1"""" in page)
        assertEquals(2, Regex("</script>").findAll(page).count())
    }

    @Test
    fun `messages show as text`() {
        assertTrue("<p>Fish &amp; chips &lt;b&gt;</p>" in Pages.message("Fish & chips <b>"))
    }
}
