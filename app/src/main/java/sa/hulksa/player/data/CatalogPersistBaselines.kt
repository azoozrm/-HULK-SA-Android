package sa.hulksa.player.data

import java.util.EnumMap
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType

/**
 * Tracks the catalog content currently known to be on disk without pinning a second complete
 * object graph. After an identical revalidation the baseline is canonicalized to the object already
 * held by the loaded-catalog map; a failed/obsolete write clears it so the previous graph can be
 * collected and a later retry cannot skip a needed write.
 */
internal class CatalogPersistBaselines {
    private val baselines = EnumMap<ContentType, Catalog>(ContentType::class.java)

    fun get(type: ContentType): Catalog? = baselines[type]

    fun onRestored(type: ContentType, catalog: Catalog) {
        baselines[type] = catalog
    }

    fun onIdentical(type: ContentType, catalog: Catalog) {
        baselines[type] = catalog
    }

    fun onWriteSucceeded(type: ContentType, catalog: Catalog) {
        baselines[type] = catalog
    }

    fun onWriteFailed(type: ContentType) {
        baselines.remove(type)
    }

    fun clear() {
        baselines.clear()
    }
}
