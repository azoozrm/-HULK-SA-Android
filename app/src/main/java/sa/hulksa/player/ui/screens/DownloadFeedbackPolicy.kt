package sa.hulksa.player.ui.screens

/**
 * Semantic glyph role of one download feedback event. The UI maps each role to the accepted
 * existing vector; the model layer never imports a UI icon.
 */
enum class DownloadFeedbackGlyph {
    PREPARING,
    QUEUED,
    COMPLETED,
    PAUSE,
    RESUME,
    RETRY,
    CANCEL,
    DELETE,
    WIFI,
    SCHEDULE,
    CONCURRENCY,
    PRIORITY,
    ERROR,
}

data class DownloadFeedback(
    val message: String,
    val glyph: DownloadFeedbackGlyph,
)

internal fun downloadFeatureDisabledFeedback(): DownloadFeedback =
    DownloadFeedback("التنزيلات متوقفة مؤقتا", DownloadFeedbackGlyph.ERROR)

internal fun downloadSessionRequiredFeedback(): DownloadFeedback =
    DownloadFeedback("سجل الدخول لبدء التحميل", DownloadFeedbackGlyph.ERROR)

internal fun downloadOwnerChangedFeedback(): DownloadFeedback =
    DownloadFeedback("تغير المستخدم ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR)

internal fun downloadGenericFailureFeedback(): DownloadFeedback =
    DownloadFeedback("تعذر التحميل ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR)

internal fun downloadOwnerFixFailedFeedback(): DownloadFeedback =
    DownloadFeedback("تعذر تثبيت مالك التحميل", DownloadFeedbackGlyph.ERROR)

internal fun downloadTargetUnresolvedFeedback(isMovie: Boolean): DownloadFeedback =
    DownloadFeedback(
        if (isMovie) "تعذر تحديد الفلم" else "تعذر تحديد المسلسل",
        DownloadFeedbackGlyph.ERROR,
    )

internal fun downloadPreparingFeedback(): DownloadFeedback =
    DownloadFeedback("جاري تجهيز التحميل", DownloadFeedbackGlyph.PREPARING)

internal fun downloadDuplicatePendingFeedback(): DownloadFeedback =
    DownloadFeedback("جاري بدء هذا التحميل", DownloadFeedbackGlyph.PREPARING)

internal fun downloadAlreadyQueuedFeedback(): DownloadFeedback =
    DownloadFeedback("المحتوى موجود في التنزيلات", DownloadFeedbackGlyph.QUEUED)

internal fun downloadAlreadyCompletedFeedback(): DownloadFeedback =
    DownloadFeedback("المحتوى محمل بالفعل", DownloadFeedbackGlyph.COMPLETED)

internal fun downloadPausePersistedFeedback(): DownloadFeedback =
    DownloadFeedback("تم ايقاف التحميل مؤقتا", DownloadFeedbackGlyph.PAUSE)

internal fun downloadPauseFailedFeedback(): DownloadFeedback =
    DownloadFeedback("تعذر حفظ ايقاف التحميل ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR)

internal fun downloadResumeAcceptedFeedback(): DownloadFeedback =
    DownloadFeedback("جاري استئناف التحميل", DownloadFeedbackGlyph.RESUME)

internal fun downloadRetryAcceptedFeedback(): DownloadFeedback =
    DownloadFeedback("جاري اعادة المحاولة", DownloadFeedbackGlyph.RETRY)

internal fun downloadCancelConfirmedFeedback(): DownloadFeedback =
    DownloadFeedback("تم الغاء التحميل", DownloadFeedbackGlyph.CANCEL)

internal fun downloadDeletionConfirmedFeedback(): DownloadFeedback =
    DownloadFeedback("تم حذف التنزيل", DownloadFeedbackGlyph.DELETE)

internal fun downloadDeletionFailedFeedback(): DownloadFeedback =
    DownloadFeedback("تعذر حذف التنزيل ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR)

internal fun downloadWifiFeedback(wifiOnly: Boolean): DownloadFeedback =
    DownloadFeedback(
        if (wifiOnly) "تم تفعيل التحميل عبر واي فاي فقط" else "تم السماح بالتحميل عبر جميع الشبكات",
        DownloadFeedbackGlyph.WIFI,
    )

internal fun downloadScheduleFeedback(nightMode: Boolean): DownloadFeedback =
    DownloadFeedback(
        if (nightMode) {
            "تمت جدولة التحميلات الجديدة والقائمة للساعة 2 ليلا"
        } else {
            "تم الغاء الجدولة وبدء التحميلات القائمة الان"
        },
        DownloadFeedbackGlyph.SCHEDULE,
    )

internal fun downloadConcurrencyFeedback(concurrentDownloads: Int): DownloadFeedback =
    DownloadFeedback(
        "عدد التحميلات المتزامنة الان $concurrentDownloads",
        DownloadFeedbackGlyph.CONCURRENCY,
    )

internal fun downloadPriorityFeedback(previousPriority: Int): DownloadFeedback =
    DownloadFeedback(
        when (previousPriority) {
            1 -> "تم خفض اولوية التحميل"
            -1 -> "تم ضبط اولوية التحميل على عادية"
            else -> "تم رفع اولوية التحميل"
        },
        DownloadFeedbackGlyph.PRIORITY,
    )
