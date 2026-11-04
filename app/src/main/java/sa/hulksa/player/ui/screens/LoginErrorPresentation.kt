package sa.hulksa.player.ui.screens

import sa.hulksa.player.data.LoginFailure
import sa.hulksa.player.data.LoginFailureCategory
import sa.hulksa.player.data.LoginFailureStage

internal const val LOGIN_OFFLINE_MESSAGE = "لا يوجد اتصال بالانترنت"
internal const val LOGIN_EXPIRY_MESSAGE = "انتهت صلاحية اشتراكك"
internal const val LOGIN_WEBSITE_FAILURE_MESSAGE = "تعذر فتح الرابط"

enum class LoginConnectivityEvidence { ONLINE, OFFLINE, UNKNOWN }
internal enum class LoginErrorTarget { ACCESS_CODE, USERNAME, PASSWORD, NONE }
internal enum class LoginNoticeOwner { AUTHENTICATION, WEBSITE }

internal data class LoginErrorPresentation(
    val displayText: String?,
    val confirmedOffline: Boolean = false,
    val target: LoginErrorTarget = LoginErrorTarget.NONE,
    val owner: LoginNoticeOwner = LoginNoticeOwner.AUTHENTICATION,
)

/** Missing services/capabilities and unvalidated transitions do not prove an internet outage. */
internal fun validatedConnectivityEvidence(
    hasDefaultNetwork: Boolean?,
    capabilitiesKnown: Boolean,
    internet: Boolean,
    validated: Boolean,
): LoginConnectivityEvidence = when {
    hasDefaultNetwork == null -> LoginConnectivityEvidence.UNKNOWN
    !hasDefaultNetwork -> LoginConnectivityEvidence.OFFLINE
    !capabilitiesKnown -> LoginConnectivityEvidence.UNKNOWN
    internet && validated -> LoginConnectivityEvidence.ONLINE
    else -> LoginConnectivityEvidence.UNKNOWN
}

/** Semantic API/entitlement causes are immutable; only a transport failure can become offline. */
internal fun loginErrorPresentation(
    failure: LoginFailure?,
    connectivity: LoginConnectivityEvidence,
): LoginErrorPresentation {
    if (failure == null) return LoginErrorPresentation(null)
    val offline = failure.category == LoginFailureCategory.TRANSPORT &&
        failure.stage in setOf(LoginFailureStage.RESOLVER, LoginFailureStage.PROVIDER) &&
        connectivity == LoginConnectivityEvidence.OFFLINE
    val text = when (failure.category) {
        LoginFailureCategory.TRANSPORT -> if (offline) LOGIN_OFFLINE_MESSAGE else "تعذر الاتصال بالخدمة"
        LoginFailureCategory.CONNECTION_UNCERTAIN -> "تعذر الاتصال بالخدمة"
        LoginFailureCategory.SERVICE_REJECTED -> "الخدمة رفضت الاتصال"
        LoginFailureCategory.INVALID_CODE -> "الكود غير صحيح"
        LoginFailureCategory.DISABLED_CODE -> "الكود غير مفعل"
        LoginFailureCategory.INVALID_CREDENTIALS -> "بيانات الدخول غير صحيحة"
        LoginFailureCategory.EXPIRED_SUBSCRIPTION -> LOGIN_EXPIRY_MESSAGE
        LoginFailureCategory.INACTIVE_SUBSCRIPTION -> "الاشتراك غير فعال"
        LoginFailureCategory.REQUIRED_ACCESS_CODE -> "ادخل كود الدخول"
        LoginFailureCategory.REQUIRED_USERNAME -> "ادخل اسم المستخدم"
        LoginFailureCategory.REQUIRED_PASSWORD -> "ادخل كلمة المرور"
        LoginFailureCategory.LINK_UNAVAILABLE -> LOGIN_WEBSITE_FAILURE_MESSAGE
        LoginFailureCategory.MALFORMED, LoginFailureCategory.UNKNOWN -> "تعذر تسجيل الدخول"
    }
    val target = when (failure.category) {
        LoginFailureCategory.INVALID_CODE, LoginFailureCategory.DISABLED_CODE,
        LoginFailureCategory.REQUIRED_ACCESS_CODE -> LoginErrorTarget.ACCESS_CODE
        LoginFailureCategory.INVALID_CREDENTIALS, LoginFailureCategory.REQUIRED_USERNAME -> LoginErrorTarget.USERNAME
        LoginFailureCategory.REQUIRED_PASSWORD -> LoginErrorTarget.PASSWORD
        else -> LoginErrorTarget.NONE
    }
    return LoginErrorPresentation(text, offline, target, owner = if (failure.category == LoginFailureCategory.LINK_UNAVAILABLE) {
        LoginNoticeOwner.WEBSITE
    } else LoginNoticeOwner.AUTHENTICATION)
}

/** One current authentication outcome outranks a failed website launch; rows never stack. */
internal fun loginNoticePresentation(
    authentication: LoginErrorPresentation,
    websiteFailed: Boolean,
): LoginErrorPresentation = when {
    authentication.displayText != null -> authentication
    websiteFailed -> LoginErrorPresentation(LOGIN_WEBSITE_FAILURE_MESSAGE, owner = LoginNoticeOwner.WEBSITE)
    else -> authentication
}
