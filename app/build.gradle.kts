import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val resellerApiUrl = "https://hulksa.com"
val operationsConfigUrl = "https://hulksa.com/hulk-operations/api/app/v1/config/"

val verifyProductionRuntimeConfig = tasks.register("verifyProductionRuntimeConfig") {
    group = "verification"
    description = "Fails release builds unless the reviewed HTTPS HULK endpoints are configured."

    doLast {
        val value = resellerApiUrl.trim()
        val parsed = runCatching { URI(value) }.getOrNull()
        if (
            value.isEmpty() ||
            parsed == null ||
            !parsed.scheme.equals("https", ignoreCase = true) ||
            parsed.host.isNullOrBlank() ||
            parsed.userInfo != null ||
            parsed.query != null ||
            parsed.fragment != null
        ) {
            throw GradleException(
                "The HULK reseller API must be a public HTTPS base URL without credentials, query, or fragment.",
            )
        }
        if (value.contains("3162356.xyz", ignoreCase = true)) {
            throw GradleException(
                "The legacy IPTV host must never be compiled as the HULK reseller API.",
            )
        }

        val operationsValue = operationsConfigUrl.trim()
        val operationsParsed = runCatching { URI(operationsValue) }.getOrNull()
        if (
            operationsParsed == null ||
            !operationsParsed.scheme.equals("https", ignoreCase = true) ||
            !operationsParsed.host.equals("hulksa.com", ignoreCase = true) ||
            operationsParsed.userInfo != null ||
            operationsParsed.query != null ||
            operationsParsed.fragment != null ||
            operationsParsed.path != "/hulk-operations/api/app/v1/config/"
        ) {
            throw GradleException(
                "The HULK Operations config endpoint must be the reviewed public HTTPS URL.",
            )
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(verifyProductionRuntimeConfig)
    }
}

val releaseSigningProperties = linkedMapOf(
    "HULK_RELEASE_KEYSTORE_FILE" to providers.gradleProperty("HULK_RELEASE_KEYSTORE_FILE"),
    "HULK_RELEASE_KEY_ALIAS" to providers.gradleProperty("HULK_RELEASE_KEY_ALIAS"),
    "HULK_RELEASE_STORE_PASSWORD" to providers.gradleProperty("HULK_RELEASE_STORE_PASSWORD"),
    "HULK_RELEASE_KEY_PASSWORD" to providers.gradleProperty("HULK_RELEASE_KEY_PASSWORD"),
)
val releaseSigningRequested = releaseSigningProperties.values.any { it.isPresent }
val releaseSigningConfigured = releaseSigningProperties.values.all { it.isPresent }

if (releaseSigningRequested && !releaseSigningConfigured) {
    val missing = releaseSigningProperties
        .filterValues { !it.isPresent }
        .keys
        .joinToString(", ")
    throw GradleException("Incomplete release signing configuration. Missing: $missing")
}

val releaseKeystoreFile = if (releaseSigningConfigured) {
    file(releaseSigningProperties.getValue("HULK_RELEASE_KEYSTORE_FILE").get()).also { keystore ->
        if (!keystore.isFile) {
            throw GradleException("Release keystore file does not exist: ${keystore.absolutePath}")
        }
    }
} else {
    null
}

android {
    namespace = "sa.hulksa.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "sa.hulksa.player"
        minSdk = 23
        targetSdk = 36
        versionCode = 64
        versionName = "0.9.3.20"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        buildConfigField("String", "RESELLER_API_URL", resellerApiUrl.asBuildConfigString())
        buildConfigField("String", "OPERATIONS_CONFIG_URL", operationsConfigUrl.asBuildConfigString())
        vectorDrawables.useSupportLibrary = true

        // Phase 3.1: preserve qualified ABIs while polishing responsive mobile UI.
        // x86 is intentionally excluded; x86_64 remains for emulators/tests.
        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
            )
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseSigningProperties.getValue("HULK_RELEASE_STORE_PASSWORD").get()
                keyAlias = releaseSigningProperties.getValue("HULK_RELEASE_KEY_ALIAS").get()
                keyPassword = releaseSigningProperties.getValue("HULK_RELEASE_KEY_PASSWORD").get()
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-beta"
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestUtil("androidx.test:orchestrator:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
