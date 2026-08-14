package sa.hulksa.player.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.model.Credentials

class CredentialPayloadCodecTest {
    @Test
    fun rememberAccountPayloadPreservesCaseSensitiveAccessCodeAndCredentials() {
        val credentials = Credentials(
            accessCode = "VUKqm6Z6ZZ",
            username = "subscriber",
            password = "secret",
        )

        assertEquals(
            credentials,
            CredentialPayloadCodec.decode(CredentialPayloadCodec.encode(credentials)),
        )
    }

    @Test
    fun legacyPayloadWithoutAccessCodeFailsClosed() {
        val legacyPayload = "subscriber\u0000secret".toByteArray(Charsets.UTF_8)

        assertNull(CredentialPayloadCodec.decode(legacyPayload))
    }

    @Test
    fun incompletePayloadFailsClosed() {
        val missingAccessCode = "2\u0000\u0000subscriber\u0000secret".toByteArray(Charsets.UTF_8)

        assertNull(CredentialPayloadCodec.decode(missingAccessCode))
    }
}
