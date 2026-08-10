package sa.hulksa.player.model

enum class ProfileKind {
    STANDARD,
    KIDS,
}

data class UserProfile(
    val id: String,
    val displayName: String,
    val kind: ProfileKind = ProfileKind.STANDARD,
    val avatarKey: String = DEFAULT_AVATAR_KEY,
    val createdAtEpochMs: Long,
    val isPrimary: Boolean = false,
) {
    companion object {
        const val DEFAULT_AVATAR_KEY = "default"
    }
}
