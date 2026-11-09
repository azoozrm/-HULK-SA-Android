package sa.hulksa.player

import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType

/**
 * True while Home has no usable hero and no authoritative settled outcome yet. This is owned by
 * the ViewModel/current session, not by network loadingTypes: cache restore and the first disk
 * publication happen before any network request is admitted.
 */
internal fun homePreparationPending(
    destination: MainDestination,
    isProfileLibraryReady: Boolean,
    hasUsableHomeHero: Boolean,
    cacheRestoreActive: Boolean,
    pendingCacheTypes: Boolean,
    networkLoading: Boolean,
    hasSettledFailure: Boolean,
    hasSettledHomeHeroAbsence: Boolean,
): Boolean {
    if (destination != MainDestination.HOME) return false
    if (!isProfileLibraryReady) return true
    if (hasUsableHomeHero) return false
    if (hasSettledFailure) return false
    if (hasSettledHomeHeroAbsence) return false
    if (cacheRestoreActive || pendingCacheTypes || networkLoading) return true
    // No hero, no settled failure/empty, and no active work: initial preparation is still pending.
    return true
}

/**
 * The Home hero is supplied by Movie/Series, not Live. Hero absence is confirmed when both hero
 * types have a settled complete source and neither has an item. A cache-hit empty type counts as
 * settled; a cache miss counts only after network success/failure. Live may still be loading or
 * contain rows; that does not invalidate the hero-area conclusion.
 */
internal fun settledHomeHeroAbsence(
    loaded: Map<ContentType, Catalog>,
    freshTypes: Set<ContentType>,
    failedTypes: Set<ContentType>,
    cacheAttemptedTypes: Set<ContentType>,
): Boolean {
    val heroTypes = listOf(ContentType.MOVIE, ContentType.SERIES)
    val allSettled = heroTypes.all { type ->
        type in freshTypes ||
            type in failedTypes ||
            (type in cacheAttemptedTypes && loaded[type] != null)
    }
    if (!allSettled) return false
    return heroTypes.all { type -> loaded[type]?.items?.isEmpty() != false }
}

/**
 * The ViewModel may finish source preparation before the remembered Compose presentation adopts
 * the newly published catalog. When a usable hero source exists but the currently presented model
 * has no hero yet, keep the existing loading presentation until the cheap/exact model arrives.
 * A presented hero is retained during background revalidation; settled exact empty is not forced
 * into loading forever.
 */
internal fun homePresentationTransitionPending(
    hasUsableHomeHeroSource: Boolean,
    presentedHasHero: Boolean,
    homeModelSettled: Boolean,
): Boolean = hasUsableHomeHeroSource && !presentedHasHero && !homeModelSettled

internal enum class HomePlaceholderMode {
    ERROR,
    LOADING,
    EMPTY,
}

/**
 * The error owner wins, then genuine preparation loading, and only then the settled-empty message.
 * A pending bootstrap therefore never flashes the empty message/Refresh control.
 */
internal fun homePlaceholderMode(
    hasError: Boolean,
    loading: Boolean,
): HomePlaceholderMode = when {
    hasError -> HomePlaceholderMode.ERROR
    loading -> HomePlaceholderMode.LOADING
    else -> HomePlaceholderMode.EMPTY
}
