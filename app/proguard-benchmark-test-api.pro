# Gate 3B: preserve the app API surface exercised by the benchmark instrumentation
# authentication bootstrap. The benchmark variant is R8-minified, so without these keeps
# the white-box test resolves original class/method names against obfuscated code.
#
# Benchmark variant only. The shipping Release build is unaffected.
-keep class sa.hulksa.player.data.HulkRepository { public *; }
-keep class sa.hulksa.player.data.ProfileStore { public *; }
-keep class sa.hulksa.player.data.ProfilePreferencesStore { public *; }
-keep class sa.hulksa.player.data.ProfileRoutingPreferences { *; }
-keep class sa.hulksa.player.data.AccountSessionMetadata { *; }
-keep class sa.hulksa.player.model.Credentials { *; }
-keep class sa.hulksa.player.model.AuthenticatedSession { *; }
-keep class sa.hulksa.player.model.UserProfile { *; }
-keep class sa.hulksa.player.model.ProfileKind { *; }

# The instrumentation runner (androidx.test:runner) links androidx.tracing.Trace, which the
# benchmark app otherwise drops as unused. Keep it so the runner resolves it at runtime.
-keep class androidx.tracing.** { *; }
-dontwarn androidx.tracing.**

# The benchmark instrumentation APK excludes libraries that are provided by the app under
# test and relies on the app's classloader. The app is obfuscated, so keep the shared
# runtime symbols unobfuscated for the runner and the white-box bootstrap.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.annotation.** { *; }
-keep class androidx.collection.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
