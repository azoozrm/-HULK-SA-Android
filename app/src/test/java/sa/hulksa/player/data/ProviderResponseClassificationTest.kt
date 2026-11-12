package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R32 shared body classification: JSON wins over the word "cloudflare". */
class ProviderResponseClassificationTest {
    @Test
    fun `json object and array are never challenge pages`() {
        val objectBody = """{"name":"Cloudflare Sports","error":"none"}"""
        val arrayBody = """[{"stream_id":1,"name":"cloudflare channel"}]"""
        assertNull(providerSuccessfulBodyChallengeCategory(objectBody))
        assertNull(providerSuccessfulBodyChallengeCategory(arrayBody))
        assertNull(providerFailureChallengeCategory(objectBody))
        assertNull(providerFailureChallengeCategory(arrayBody))
    }

    @Test
    fun `bom json is still treated as json`() {
        val body = "\uFEFF{\"name\":\"cloudflare\"}"
        assertNull(providerSuccessfulBodyChallengeCategory(body))
    }

    @Test
    fun `html and plain challenge markers remain blocked`() {
        assertEquals(
            "html-doctype",
            providerSuccessfulBodyChallengeCategory("<!doctype html><html>cloudflare</html>"),
        )
        assertEquals(
            "cloudflare",
            providerSuccessfulBodyChallengeCategory("Attention: cloudflare challenge"),
        )
        assertEquals(
            "blocked-page",
            providerFailureChallengeCategory("Sorry, you have been blocked"),
        )
    }

    @Test
    fun `diagnostic classifier keeps its raw marker behavior`() {
        // The diagnostics classifier is intentionally unchanged; production helpers gate it by JSON.
        assertEquals("cloudflare", providerChallengeCategory("{\"name\":\"cloudflare\"}"))
    }
}
