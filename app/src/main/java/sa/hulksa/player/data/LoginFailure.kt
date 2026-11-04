package sa.hulksa.player.data

import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Login provenance contains no endpoint, credentials, provider payload or localized copy. */
enum class LoginFailureStage { VALIDATION, RESOLVER, PROVIDER, LOCAL_PERSISTENCE }

enum class LoginFailureCategory {
    TRANSPORT, CONNECTION_UNCERTAIN, SERVICE_REJECTED, INVALID_CODE, DISABLED_CODE,
    INVALID_CREDENTIALS, EXPIRED_SUBSCRIPTION, INACTIVE_SUBSCRIPTION, MALFORMED,
    REQUIRED_ACCESS_CODE, REQUIRED_USERNAME, REQUIRED_PASSWORD, LINK_UNAVAILABLE, UNKNOWN,
}

data class LoginFailure(val category: LoginFailureCategory, val stage: LoginFailureStage)

interface LoginFailureSource {
    val loginFailure: LoginFailure
}

internal fun loginFailureFor(
    error: Throwable,
    fallbackStage: LoginFailureStage = LoginFailureStage.LOCAL_PERSISTENCE,
): LoginFailure = when (error) {
    is CancellationException -> throw error
    is LoginFailureSource -> error.loginFailure
    is IOException -> LoginFailure(
        if (fallbackStage == LoginFailureStage.RESOLVER || fallbackStage == LoginFailureStage.PROVIDER) {
            LoginFailureCategory.TRANSPORT
        } else LoginFailureCategory.UNKNOWN,
        fallbackStage,
    )
    else -> LoginFailure(LoginFailureCategory.UNKNOWN, fallbackStage)
}

internal fun loginValidationFailure(accessCode: String, username: String, password: String): LoginFailure? {
    val category = when {
        normalizeResellerAccessCode(accessCode) == null -> LoginFailureCategory.REQUIRED_ACCESS_CODE
        username.trim().isEmpty() -> LoginFailureCategory.REQUIRED_USERNAME
        password.isEmpty() -> LoginFailureCategory.REQUIRED_PASSWORD
        else -> return null
    }
    return LoginFailure(category, LoginFailureStage.VALIDATION)
}
