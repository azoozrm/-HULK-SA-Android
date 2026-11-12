package sa.hulksa.player.data

/**
 * R32 shared response classification.
 *
 * A successful provider response that is JSON must reach normal parsing even when a field or a
 * channel name contains the word "cloudflare". Challenge markers classify only non-JSON bodies
 * (HTML/plain challenge pages). A non-success JSON body (for example a JSON 429) is a normal
 * provider failure with its real status, not a challenge page.
 */
internal fun providerBodyLooksLikeJson(body: String): Boolean {
    val trimmed = body.trimStart().removePrefix("\uFEFF").trimStart()
    return trimmed.startsWith("{") || trimmed.startsWith("[")
}

/** Successful non-JSON challenge page, e.g. an HTTP 200 Cloudflare interstitial. */
internal fun providerSuccessfulBodyChallengeCategory(body: String): String? =
    providerNonJsonChallengeCategory(body)

/** Non-success body that is a challenge page, never a JSON provider error/429. */
internal fun providerFailureChallengeCategory(body: String): String? =
    providerNonJsonChallengeCategory(body)

private fun providerNonJsonChallengeCategory(body: String): String? =
    if (providerBodyLooksLikeJson(body)) null else providerChallengeCategory(body)
