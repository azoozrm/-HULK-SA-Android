package sa.hulksa.player.data

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.hulkPerfLog
import sa.hulksa.player.model.AuthenticatedSession

/**
 * Optional NEO XMLTV transport.
 *
 * The endpoint is the provider's standard `/xmltv.php` for the exact authenticated session. The
 * load is cancellable, follows no redirects (so credentials cannot be forwarded to another host),
 * accepts gzip transparently through OkHttp and bounds both the declared and decompressed body.
 * No URL, host, username, password or response content is logged.
 */
internal class EpgXmltvClient(
    private val client: OkHttpClient = defaultClient(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val providerHost: String = NeoEpgProviderIdentity.PROVIDER_HOST,
) {
    suspend fun load(
        session: AuthenticatedSession,
        requestedChannelIds: Set<String>,
    ): EpgSnapshot {
        if (!NeoEpgProviderIdentity.isVerifiedSession(session, providerHost)) {
            throw EpgException.ProviderNotVerified
        }
        val channelIds = requestedChannelIds.asSequence()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toSet()
        if (channelIds.isEmpty()) throw EpgException.InvalidResponse

        val base = session.portal.baseUrl.trim().toHttpUrlOrNull()
            ?: throw EpgException.ProviderNotVerified
        val url = base.newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", session.credentials.username)
            .addQueryParameter("password", session.credentials.password)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/xml,text/xml;q=0.9,*/*;q=0.1")
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Android")
            .build()
        val context = currentCoroutineContext()
        val startedNs = System.nanoTime()
        try {
            return client.executeCancellable(request) { response ->
                if (!response.isSuccessful) throw EpgException.Http(response.code)
                val body = response.body ?: throw EpgException.InvalidResponse
                if (body.contentLength() > EPG_MAX_COMPRESSED_BYTES) {
                    throw EpgException.PayloadLimitExceeded
                }
                val snapshot = body.byteStream().use { stream ->
                    EpgXmltvParser(
                        nowEpochSeconds = nowEpochSeconds(),
                        requestedChannelIds = channelIds,
                        providerHost = providerHost,
                        checkCancelled = { context.ensureActive() },
                    ).parse(BoundedEpgInputStream(stream, EPG_MAX_UNCOMPRESSED_BYTES))
                }
                hulkPerfLog {
                    "epgLoad channels=${snapshot.schedule.programmesByChannel.size} " +
                        "programmes=${snapshot.schedule.totalProgrammes} " +
                        "ms=${(System.nanoTime() - startedNs) / 1_000_000}"
                }
                snapshot
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: EpgException) {
            throw error
        } catch (error: IOException) {
            context.ensureActive()
            throw EpgException.Network(error)
        } catch (error: Exception) {
            context.ensureActive()
            throw EpgException.InvalidResponse
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Counts the bytes the XML parser actually consumes after OkHttp has decoded gzip. A malformed or
 * unexpectedly large provider document fails closed instead of growing the heap without a limit.
 */
private class BoundedEpgInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
) : InputStream() {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) account(1L)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = delegate.read(buffer, offset, length)
        if (count > 0) account(count.toLong())
        return count
    }

    override fun skip(byteCount: Long): Long {
        val skipped = delegate.skip(byteCount)
        if (skipped > 0L) account(skipped)
        return skipped
    }

    override fun available(): Int = delegate.available()

    override fun close() = delegate.close()

    private fun account(count: Long) {
        bytesRead += count
        if (bytesRead > maxBytes) throw EpgPayloadLimitException()
    }
}
