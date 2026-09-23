# Gate 3B androidTest APK only. The instrumentation APK does not ship; keep it fully
# intact so JUnit can discover and run the bootstrap test on the minified benchmark
# variant, and silence optional androidx.test deps that R8 cannot resolve.
-dontshrink
-dontoptimize
-dontobfuscate
-dontwarn androidx.concurrent.futures.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn com.google.common.**
-keep class sa.hulksa.player.** { *; }
-keep class androidx.test.** { *; }
-keep class androidx.benchmark.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepattributes *Annotation*
