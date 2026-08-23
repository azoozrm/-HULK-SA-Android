package sa.hulksa.player.data

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_728_000_000L

    @Test
    fun growthMissingUsesDisabledSafeDefault() {
        val parsed = parseOperationsConfig(configJson())

        assertNotNull(parsed)
        assertFalse(parsed!!.growth.enabled)
        assertFalse(parsed.growth.renewal.enabled)
        assertFalse(parsed.growth.support.enabled)
    }

    @Test
    fun growthDisabledIsPreserved() {
        val parsed = parseOperationsConfig(configJson(growthJson(enabled = false)))

        assertNotNull(parsed)
        assertFalse(parsed!!.growth.enabled)
    }

    @Test
    fun renewalEnabledWithTrustedUrl() {
        val parsed = parseOperationsConfig(configJson(growthJson()))

        assertTrue(parsed!!.growth.renewal.enabled)
        assertEquals("https://hulksa.com/", parsed.growth.renewal.url)
    }

    @Test
    fun supportEnabledWithOfficialWhatsAppUrl() {
        val parsed = parseOperationsConfig(configJson(growthJson()))

        assertTrue(parsed!!.growth.support.enabled)
        assertEquals("https://wa.me/966506349935", parsed.growth.support.url)
    }

    @Test
    fun renewalAllowsTrustedSubdomain() {
        assertEquals(
            "https://renew.hulksa.com/path",
            normalizeGrowthRenewalUrl("https://renew.hulksa.com/path"),
        )
    }

    @Test
    fun httpRenewalUrlIsRejected() {
        val parsed = parseOperationsConfig(
            configJson(growthJson(renewalUrl = "http://hulksa.com/")),
        )

        assertFalse(parsed!!.growth.renewal.enabled)
        assertNull(parsed.growth.renewal.url)
    }

    @Test
    fun dangerousSchemesAreRejected() {
        for (url in listOf(
            "javascript:alert(1)",
            "file:///tmp/qr.png",
            "content://sa.hulksa/qr",
            "intent://hulksa.com/#Intent;end",
            "ftp://hulksa.com/qr",
        )) {
            assertNull(normalizeGrowthRenewalUrl(url))
        }
    }

    @Test
    fun renewalCredentialsAndQueryAreRejected() {
        assertNull(normalizeGrowthRenewalUrl("https://user:pass@hulksa.com/"))
        assertNull(normalizeGrowthRenewalUrl("https://hulksa.com/?accountId=1"))
    }

    @Test
    fun whatsappDotComSendUrlIsAccepted() {
        assertEquals(
            "https://api.whatsapp.com/send?phone=966506349935&app_absent=0",
            normalizeGrowthSupportUrl(
                "https://api.whatsapp.com/send?phone=966506349935&app_absent=0",
            ),
        )
    }

    @Test
    fun invalidWhatsAppUrlsAreRejected() {
        assertNull(normalizeGrowthSupportUrl("https://example.com/966506349935"))
        assertNull(normalizeGrowthSupportUrl("https://wa.me/not-a-number"))
        assertNull(normalizeGrowthSupportUrl("http://wa.me/966506349935"))
        assertNull(normalizeGrowthSupportUrl("https://api.whatsapp.com/send?phone=966506349935&token=x"))
    }

    @Test
    fun autoQrModeIsParsed() {
        val parsed = parseOperationsConfig(configJson(growthJson(renewalMode = "AUTO")))

        assertEquals(GrowthQrMode.AUTO, parsed!!.growth.renewal.qrMode)
    }

    @Test
    fun customQrModeAndTrustedImageAreParsed() {
        val custom = validCustomQr("renewal")
        val parsed = parseOperationsConfig(
            configJson(growthJson(renewalMode = "CUSTOM", renewalCustomQr = custom)),
        )

        assertEquals(GrowthQrMode.CUSTOM, parsed!!.growth.renewal.qrMode)
        assertEquals(custom, parsed.growth.renewal.customQrUrl)
    }

    @Test
    fun invalidCustomQrHostIsIgnored() {
        val parsed = parseOperationsConfig(
            configJson(
                growthJson(
                    renewalMode = "CUSTOM",
                    renewalCustomQr = "https://example.com/growth-renewal.png",
                ),
            ),
        )

        assertNull(parsed!!.growth.renewal.customQrUrl)
        assertEquals(
            GrowthQrPresentation.GENERATED_QR,
            resolveGrowthQrPresentation(parsed.growth.renewal),
        )
    }

    @Test
    fun missingCustomQrFallsBackToGenerated() {
        val parsed = parseOperationsConfig(
            configJson(growthJson(renewalMode = "CUSTOM", renewalCustomQr = null)),
        )

        assertEquals(GrowthQrMode.CUSTOM, parsed!!.growth.renewal.qrMode)
        assertEquals(
            GrowthQrPresentation.GENERATED_QR,
            resolveGrowthQrPresentation(parsed.growth.renewal),
        )
    }

    @Test
    fun failedCustomImageFallsBackToGenerated() {
        val link = enabledGrowth().renewal.copy(
            qrMode = GrowthQrMode.CUSTOM,
            customQrUrl = validCustomQr("renewal"),
        )

        assertEquals(GrowthQrPresentation.CUSTOM_IMAGE, resolveGrowthQrPresentation(link))
        assertEquals(
            GrowthQrPresentation.GENERATED_QR,
            resolveGrowthQrPresentation(link, customImageFailed = true),
        )
    }

    @Test
    fun daysBeforeExpiryLowerBoundIsClamped() {
        val parsed = parseOperationsConfig(configJson(growthJson(days = 0)))

        assertEquals(1, parsed!!.growth.renewalBanner.daysBeforeExpiry)
    }

    @Test
    fun daysBeforeExpiryUpperBoundIsClamped() {
        val parsed = parseOperationsConfig(configJson(growthJson(days = 31)))

        assertEquals(30, parsed!!.growth.renewalBanner.daysBeforeExpiry)
    }

    @Test
    fun malformedGrowthDoesNotBreakOperationsConfig() {
        val parsed = parseOperationsConfig(configJson("\"not-an-object\""))

        assertNotNull(parsed)
        assertFalse(parsed!!.growth.enabled)
        assertEquals(64, parsed.update.latestVersionCode)
    }

    @Test
    fun tvRenewalActionOpensQr() {
        assertEquals(
            GrowthAction.OPEN_QR,
            resolveGrowthAction(enabledGrowth(), GrowthDestination.RENEWAL, isTv = true),
        )
    }

    @Test
    fun tvSupportActionOpensQr() {
        assertEquals(
            GrowthAction.OPEN_QR,
            resolveGrowthAction(enabledGrowth(), GrowthDestination.SUPPORT, isTv = true),
        )
    }

    @Test
    fun mobileRenewalActionOpensUrl() {
        assertEquals(
            GrowthAction.OPEN_URL,
            resolveGrowthAction(enabledGrowth(), GrowthDestination.RENEWAL, isTv = false),
        )
    }

    @Test
    fun mobileSupportActionOpensUrl() {
        assertEquals(
            GrowthAction.OPEN_URL,
            resolveGrowthAction(enabledGrowth(), GrowthDestination.SUPPORT, isTv = false),
        )
    }

    @Test
    fun invalidUrlProducesNoAction() {
        val growth = enabledGrowth().copy(
            renewal = enabledGrowth().renewal.copy(url = "https://evil.example/"),
        )

        assertEquals(
            GrowthAction.NO_ACTION,
            resolveGrowthAction(growth, GrowthDestination.RENEWAL, isTv = true),
        )
    }

    @Test
    fun remainingTenDaysHidesBanner() {
        assertNull(bannerAt(10))
    }

    @Test
    fun remainingEightDaysHidesBanner() {
        assertNull(bannerAt(8))
    }

    @Test
    fun remainingSevenDaysShowsBanner() {
        assertEquals(7, bannerAt(7)!!.daysRemaining)
    }

    @Test
    fun remainingThreeDaysShowsBanner() {
        assertEquals("اشتراكك ينتهي خلال 3 أيام", bannerAt(3)!!.title)
    }

    @Test
    fun remainingOneDayShowsTomorrowCopy() {
        assertEquals("اشتراكك ينتهي غدًا", bannerAt(1)!!.title)
    }

    @Test
    fun expiryTodayShowsTodayCopy() {
        val banner = evaluateRenewalBanner(
            growth = enabledGrowth(),
            expiresAtEpochSeconds = now + 3_600L,
            nowEpochSeconds = now,
            timeZone = utc,
        )

        assertEquals("اشتراكك ينتهي اليوم", banner!!.title)
    }

    @Test
    fun expiredAccountShowsRenewalCopy() {
        val banner = evaluateRenewalBanner(
            growth = enabledGrowth(),
            expiresAtEpochSeconds = now - 1L,
            nowEpochSeconds = now,
            timeZone = utc,
        )

        assertEquals("انتهى اشتراكك", banner!!.title)
        assertEquals("جدد اشتراكك للمتابعة", banner.subtitle)
    }

    @Test
    fun expiredEarlierOnSameLocalDayShowsExpiredCopy() {
        val midday = now + 43_200L
        val banner = evaluateRenewalBanner(
            growth = enabledGrowth(),
            expiresAtEpochSeconds = midday - 1L,
            nowEpochSeconds = midday,
            timeZone = utc,
        )

        assertEquals("انتهى اشتراكك", banner!!.title)
    }

    @Test
    fun missingExpiryHidesBanner() {
        assertNull(
            evaluateRenewalBanner(enabledGrowth(), null, nowEpochSeconds = now, timeZone = utc),
        )
    }

    @Test
    fun implausibleExpiryHidesBanner() {
        assertNull(
            evaluateRenewalBanner(
                enabledGrowth(),
                Long.MAX_VALUE,
                nowEpochSeconds = now,
                timeZone = utc,
            ),
        )
    }

    @Test
    fun growthDisabledHidesBanner() {
        assertNull(
            evaluateRenewalBanner(
                enabledGrowth().copy(enabled = false),
                now + 86_400L,
                now,
                utc,
            ),
        )
    }

    @Test
    fun renewalDisabledHidesBanner() {
        val growth = enabledGrowth().copy(
            renewal = enabledGrowth().renewal.copy(enabled = false),
        )

        assertNull(evaluateRenewalBanner(growth, now + 86_400L, now, utc))
    }

    @Test
    fun bannerFlagDisabledHidesBanner() {
        val growth = enabledGrowth().copy(
            renewalBanner = OperationsRenewalBannerConfig(enabled = false, daysBeforeExpiry = 7),
        )

        assertNull(evaluateRenewalBanner(growth, now + 86_400L, now, utc))
    }

    private fun bannerAt(days: Int): RenewalBannerContent? = evaluateRenewalBanner(
        growth = enabledGrowth(),
        expiresAtEpochSeconds = now + (days * 86_400L),
        nowEpochSeconds = now,
        timeZone = utc,
    )

    private fun enabledGrowth() = OperationsGrowthConfig(
        enabled = true,
        renewal = OperationsGrowthLinkConfig(
            enabled = true,
            title = "التجديد والموقع",
            url = "https://hulksa.com/",
            displayText = "hulksa.com",
        ),
        support = OperationsGrowthLinkConfig(
            enabled = true,
            title = "الدعم الفني",
            url = "https://wa.me/966506349935",
            displayText = "0506349935",
        ),
        renewalBanner = OperationsRenewalBannerConfig(enabled = true, daysBeforeExpiry = 7),
    )

    private fun validCustomQr(slot: String): String =
        "https://hulksa.com/hulk-operations/growth-media/growth-$slot-${"a".repeat(32)}.png"

    private fun growthJson(
        enabled: Boolean = true,
        renewalUrl: String = "https://hulksa.com/",
        renewalMode: String = "AUTO",
        renewalCustomQr: String? = null,
        days: Int = 7,
    ): String {
        val customQr = renewalCustomQr?.let { "\"$it\"" } ?: "null"
        return """
            {
              "enabled":$enabled,
              "renewal":{
                "enabled":true,
                "title":"التجديد والموقع",
                "url":"$renewalUrl",
                "displayText":"hulksa.com",
                "qrMode":"$renewalMode",
                "customQrUrl":$customQr
              },
              "support":{
                "enabled":true,
                "title":"الدعم الفني",
                "url":"https://wa.me/966506349935",
                "displayText":"0506349935",
                "qrMode":"AUTO",
                "customQrUrl":null
              },
              "renewalBanner":{"enabled":true,"daysBeforeExpiry":$days}
            }
        """.trimIndent()
    }

    private fun configJson(growth: String? = null): String {
        val growthField = growth?.let { ",\n  \"growth\":$it" }.orEmpty()
        return """
            {
              "schemaVersion":1,
              "generatedAt":1770000000,
              "service":{"status":"OPERATIONAL"},
              "update":{
                "latestVersionCode":64,
                "latestVersionName":"0.9.3.20",
                "minimumSupportedVersionCode":64,
                "apkUrl":null,
                "apkSha256":null
              },
              "announcements":[],
              "features":{}$growthField
            }
        """.trimIndent()
    }
}
