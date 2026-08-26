package sa.hulksa.player.data

import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode

/**
 * Stable deterministic identities for provider-owned models.
 *
 * Provider IDs are only unique within their logical model scope. Never use a list index or random
 * value as identity because Compose focus/state restoration depends on stable keys across refreshes.
 */
internal fun providerContentIdentity(type: ContentType, id: Int): String = "${type.name}:$id"

internal fun ContentItem.providerStableIdentity(): String = providerContentIdentity(type, id)

internal fun Category.providerStableIdentity(): String = "${type.name}:$id"

internal fun Episode.providerStableIdentity(seriesId: Int): String = "SERIES:$seriesId:$id"
