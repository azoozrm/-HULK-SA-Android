# Gate 3B androidTest APK only. The instrumentation APK does not ship and does not need
# shrinking; keep test-framework entry points and silence optional androidx.test deps that
# R8 cannot resolve on the minified benchmark test variant.
-dontwarn androidx.concurrent.futures.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn com.google.common.**
-keep class androidx.test.** { *; }
-keep class androidx.benchmark.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontobfuscate
