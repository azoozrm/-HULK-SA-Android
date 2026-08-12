package sa.hulksa.player.data

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.CapabilityFinding
import sa.hulksa.player.model.CapabilityStatus
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.FeatureRecommendation

const val KIDS_SERVER_CATALOG_CAPABILITY_ID = "kids_server_catalog"
const val LEGACY_PARENTAL_RECOMMENDATION_TITLE = "رقابة ابوية وملفات شخصية"

internal fun isExplicitKidsCategoryName(raw: String): Boolean {
    val normalized = raw
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ٱ', 'ا')
        .trim()
    if (normalized.isBlank()) return false

    val explicitTokens = setOf(
        "kid",
        "kids",
        "child",
        "children",
        "اطفال",
        "الاطفال",
        "طفل",
        "الطفل",
        "للاطفال",
        "صغار",
        "الصغار",
        "للصغار",
    )
    return Regex("[^\\p{L}\\p{N}]+")
        .split(normalized)
        .filter(String::isNotBlank)
        .any(explicitTokens::contains)
}

internal fun isServerCategoryScopeVerified(
    returnedCategoryIds: List<String>,
    expectedCategoryId: String,
): Boolean {
    val expected = expectedCategoryId.trim()
    if (expected.isBlank()) return false
    return returnedCategoryIds.all { it.trim() == expected }
}

data class VerifiedKidsCatalogSnapshot(
    val catalogs: Map<ContentType, Catalog>,
    val blockedTypes: Map<ContentType, String>,
) {
    val availableTypes: Set<ContentType>
        get() = catalogs
            .filterValues { it.items.isNotEmpty() }
            .keys

    val isAvailable: Boolean
        get() = availableTypes.isNotEmpty()

    val totalItems: Int
        get() = catalogs.values.sumOf { it.items.size }

    val totalExplicitCategories: Int
        get() = catalogs.values.sumOf { it.categories.size }

    fun catalog(type: ContentType): Catalog = catalogs[type] ?: Catalog(emptyList(), emptyList())

    fun capabilityFinding(): CapabilityFinding {
        val typeSummary = ContentType.entries.mapNotNull { type ->
            val catalog = catalogs[type] ?: return@mapNotNull null
            if (catalog.categories.isEmpty() && catalog.items.isEmpty()) return@mapNotNull null
            "${type.arabicLabel()}: ${catalog.categories.size} فئة / ${catalog.items.size} عنصر"
        }
        val blockedSummary = blockedTypes.entries.joinToString(" • ") { (type, reason) ->
            "${type.arabicLabel()}: $reason"
        }
        val status = when {
            isAvailable && blockedTypes.isEmpty() -> CapabilityStatus.SUPPORTED
            isAvailable -> CapabilityStatus.PARTIAL
            blockedTypes.isNotEmpty() -> CapabilityStatus.UNSTABLE
            else -> CapabilityStatus.UNSUPPORTED
        }
        val details = when {
            isAvailable -> buildString {
                append("تم العثور على مصدر أطفال صريح من فئات السيرفر، وجلب المحتوى بطلبات category_id فقط.")
                if (typeSummary.isNotEmpty()) append(" ").append(typeSummary.joinToString(" • "))
                if (blockedSummary.isNotBlank()) append(" • غير متاح: ").append(blockedSummary)
            }
            blockedTypes.isNotEmpty() ->
                "لم يتم اعتماد مصدر أطفال آمن لأن تحقق فلترة السيرفر لم يكتمل. $blockedSummary"
            else ->
                "لم نجد فئات سيرفر صريحة باسم أطفال/Kids/Children. وضع الأطفال سيبقى غير مفعّل."
        }
        val evidence = when {
            isAvailable -> "تم التحقق من category_id لكل عنصر مسترجع • $totalItems عنصر"
            blockedTypes.isNotEmpty() -> "Fail-closed: لم يتم قبول محتوى غير قابل للتحقق"
            else -> "0 عناصر أطفال مؤكدة من السيرفر"
        }
        return CapabilityFinding(
            id = KIDS_SERVER_CATALOG_CAPABILITY_ID,
            title = "مصدر محتوى الأطفال من السيرفر",
            status = status,
            details = details,
            evidence = evidence,
        )
    }

    fun recommendation(): FeatureRecommendation = when {
        isAvailable -> FeatureRecommendation(
            title = "وضع الأطفال المقيد بمحتوى السيرفر",
            readiness = "جاهزة للمرحلة التالية",
            priority = 1,
            reason = "تم التحقق من فئات أطفال صريحة وفلترة category_id من السيرفر. يمكن بناء Kids Profile فوق هذا المصدر فقط.",
        )
        blockedTypes.isNotEmpty() -> FeatureRecommendation(
            title = "وضع الأطفال المقيد بمحتوى السيرفر",
            readiness = "متوقفة - فلترة السيرفر غير مؤكدة",
            priority = 3,
            reason = "لن يتم تفعيل Kids Mode حتى ينجح تحقق المصدر وفلترة category_id بدون أي تسريب لمحتوى آخر.",
        )
        else -> FeatureRecommendation(
            title = "وضع الأطفال المقيد بمحتوى السيرفر",
            readiness = "متوقفة - لا توجد فئات أطفال صريحة",
            priority = 3,
            reason = "السيرفر لم يعلن فئات أطفال صريحة، لذلك لن ننشئ وضع أطفال يعتمد على تخمينات مثل Animation أو Family.",
        )
    }
}

class KidsServerCatalogClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Builds a fail-closed Kids snapshot directly from server category filters.
     *
     * The client never downloads the full VOD/series/live catalog for Kids mode. It first reads
     * category metadata, keeps only explicit Kids/Children/أطفال categories, then requests each
     * matching category with category_id. Every returned item must echo the requested category id;
     * otherwise that content type is blocked instead of being exposed to a Kids profile.
     */
    suspend fun loadVerified(session: AuthenticatedSession): VerifiedKidsCatalogSnapshot =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val results = ContentType.entries.map { type ->
                    async {
                        val result = try {
                            KidsTypeLoadResult.Loaded(loadTypeVerified(session, type))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            KidsTypeLoadResult.Blocked(error.safeKidsFailureReason())
                        }
                        type to result
                    }
                }.awaitAll()

                val catalogs = linkedMapOf<ContentType, Catalog>()
                val blocked = linkedMapOf<ContentType, String>()
                results.forEach { (type, result) ->
                    when (result) {
                        is KidsTypeLoadResult.Loaded -> catalogs[type] = result.catalog
                        is KidsTypeLoadResult.Blocked -> blocked[type] = result.reason
                    }
                }
                VerifiedKidsCatalogSnapshot(catalogs = catalogs, blockedTypes = blocked)
            }
        }

    private suspend fun loadTypeVerified(
        session: AuthenticatedSession,
        type: ContentType,
    ): Catalog = coroutineScope {
        val categories = parseCategories(
            requestArray(
                session = session,
                action = type.categoryAction(),
            ),
            type,
        ).filter { isExplicitKidsCategoryName(it.name) }
            .distinctBy(Category::id)

        if (categories.isEmpty()) return@coroutineScope Catalog(emptyList(), emptyList())

        val scopedItems = categories.map { category ->
            async {
                val items = parseItems(
                    requestArray(
                        session = session,
                        action = type.contentAction(),
                        extra = mapOf("category_id" to category.id),
                    ),
                    type = type,
                    portalBaseUrl = session.portal.baseUrl,
                )
                if (!isServerCategoryScopeVerified(items.map(ContentItem::categoryId), category.id)) {
                    throw KidsCatalogException.ServerFilterNotEnforced(type, category.id)
                }
                items
            }
        }.awaitAll().flatten()
            .distinctBy { "${it.type.name}:${it.id}" }

        val allowedIds = categories.mapTo(linkedSetOf(), Category::id)
        val guardedItems = scopedItems.filter { it.categoryId in allowedIds }
        Catalog(categories = categories, items = guardedItems)
    }

    private suspend fun requestArray(
        session: AuthenticatedSession,
        action: String,
        extra: Map<String, String> = emptyMap(),
    ): JSONArray {
        val url = (session.portal.baseUrl.trimEnd('/') + "/player_api.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("username", session.credentials.username)
            .addQueryParameter("password", session.credentials.password)
            .addQueryParameter("action", action)
            .apply {
                extra.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", JSON.toString())
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} KidsCatalog")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw KidsCatalogException.Http(response.code)
                if (body.isBlank() || body.trimStart().startsWith('<')) {
                    throw KidsCatalogException.InvalidResponse
                }
                return JSONArray(body)
            }
        } catch (error: KidsCatalogException) {
            throw error
        } catch (error: IOException) {
            throw KidsCatalogException.Network(error)
        } catch (error: Exception) {
            throw KidsCatalogException.InvalidResponse
        }
    }

    private fun parseCategories(array: JSONArray, type: ContentType): List<Category> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("category_id").trim().takeIf(String::isNotBlank) ?: continue
            val name = item.optString("category_name").trim().takeIf(String::isNotBlank) ?: continue
            add(Category(id = id, name = name, type = type))
        }
    }

    private fun parseItems(
        array: JSONArray,
        type: ContentType,
        portalBaseUrl: String,
    ): List<ContentItem> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val idKey = if (type == ContentType.SERIES) "series_id" else "stream_id"
            val id = item.optString(idKey).toIntOrNull() ?: continue
            val categoryId = item.optString("category_id").trim()
            add(
                ContentItem(
                    id = id,
                    name = item.optString("name", "بدون اسم"),
                    categoryId = categoryId,
                    type = type,
                    posterUrl = normalizeArtworkUrl(
                        item.optNullableString(if (type == ContentType.SERIES) "cover" else "stream_icon"),
                        portalBaseUrl,
                    ),
                    rating = item.optNullableString("rating") ?: item.optNullableString("rating_5based"),
                    year = item.optNullableString("year"),
                    containerExtension = item.optNullableString("container_extension"),
                    nowPlaying = item.optNullableString("epg_channel_id"),
                    addedAtEpochSeconds = when (type) {
                        ContentType.SERIES -> item.optNullableString("last_modified")
                            ?: item.optNullableString("added")
                        else -> item.optNullableString("added")
                    }?.toLongOrNull(),
                    plot = item.optNullableString("plot"),
                    genre = item.optNullableString("genre"),
                    backdropUrl = normalizeArtworkUrl(item.firstImageUrl("backdrop_path"), portalBaseUrl),
                ),
            )
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeUnless { it.isEmpty() || it.equals("null", true) }
    }

    private fun JSONObject.firstImageUrl(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is JSONArray -> (0 until value.length())
                .asSequence()
                .mapNotNull { value.optString(it).trim().takeUnless(String::isBlank) }
                .firstOrNull()
            is String -> {
                val clean = value.trim()
                if (clean.startsWith("[")) {
                    runCatching { JSONArray(clean).optString(0).trim().takeUnless(String::isBlank) }
                        .getOrNull()
                } else {
                    clean.takeUnless { it.isBlank() || it.equals("null", true) }
                }
            }
            else -> null
        }
    }

    private fun ContentType.categoryAction(): String = when (this) {
        ContentType.LIVE -> "get_live_categories"
        ContentType.MOVIE -> "get_vod_categories"
        ContentType.SERIES -> "get_series_categories"
    }

    private fun ContentType.contentAction(): String = when (this) {
        ContentType.LIVE -> "get_live_streams"
        ContentType.MOVIE -> "get_vod_streams"
        ContentType.SERIES -> "get_series"
    }

    private sealed interface KidsTypeLoadResult {
        data class Loaded(val catalog: Catalog) : KidsTypeLoadResult
        data class Blocked(val reason: String) : KidsTypeLoadResult
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

private sealed class KidsCatalogException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidResponse : KidsCatalogException("invalid response")
    data class Http(val statusCode: Int) : KidsCatalogException("HTTP $statusCode")
    data class Network(val error: IOException) : KidsCatalogException("network", error)
    data class ServerFilterNotEnforced(
        val type: ContentType,
        val categoryId: String,
    ) : KidsCatalogException("server category filter not enforced")
}

private fun Throwable.safeKidsFailureReason(): String = when (this) {
    is KidsCatalogException.ServerFilterNotEnforced ->
        "السيرفر لم يلتزم بفلترة category_id للفئة ${categoryId.take(12)}"
    is KidsCatalogException.Http -> "فشل API بكود $statusCode"
    is KidsCatalogException.Network -> "تعذر الاتصال بالسيرفر"
    is KidsCatalogException.InvalidResponse -> "استجابة الفئات غير صالحة"
    else -> "تعذر التحقق من فلترة السيرفر"
}

private fun ContentType.arabicLabel(): String = when (this) {
    ContentType.LIVE -> "قنوات"
    ContentType.MOVIE -> "أفلام"
    ContentType.SERIES -> "مسلسلات"
}
