import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun String.replaceExactlyOnce(
    oldValue: String,
    newValue: String,
    label: String,
): String {
    val firstMatch = indexOf(oldValue)
    if (firstMatch < 0) {
        throw GradleException("TV UI source patch anchor is missing: $label")
    }
    if (indexOf(oldValue, firstMatch + oldValue.length) >= 0) {
        throw GradleException("TV UI source patch anchor is not unique: $label")
    }
    return replaceRange(firstMatch, firstMatch + oldValue.length, newValue)
}

val productionPortalUrl = "http://3162356.xyz:8080"
val portalUrl = providers.gradleProperty("HULK_PORTAL_URL").orElse(productionPortalUrl)
val configUrl = providers.gradleProperty("HULK_CONFIG_URL").orElse("")

val verifyProductionRuntimeConfig = tasks.register("verifyProductionRuntimeConfig") {
    group = "verification"
    description = "Fails release builds unless the canonical HULK runtime endpoint is compiled."

    doLast {
        if (portalUrl.get().trim() != productionPortalUrl) {
            throw GradleException(
                "Release PORTAL_URL must match the canonical HULK service endpoint.",
            )
        }
        if (configUrl.get().trim().isNotEmpty()) {
            throw GradleException(
                "Release CONFIG_URL must be empty so it cannot override the canonical endpoint.",
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

// Keep the checked-in adaptive/mobile source untouched and compile a deterministic copy
// containing only the approved Android TV corrections. Every anchor is verified exactly
// once so a future source change fails loudly instead of silently applying a stale patch.
val mainShellSource = layout.projectDirectory.file(
    "src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
)
val tvFixedMainShellSourceRoot = layout.buildDirectory.dir("generated/source/tv-fixed-main-shell/main")
val tvFixedMainShellOutput = tvFixedMainShellSourceRoot.map {
    it.file("sa/hulksa/player/ui/screens/TvFixedMainShellScreen.kt")
}
val generateTvFixedMainShellSource = tasks.register("generateTvFixedMainShellSource") {
    group = "build setup"
    description = "Generates MainShell with TV-only rail, logo, and download-card corrections."
    inputs.file(mainShellSource)
    outputs.file(tvFixedMainShellOutput)

    doLast {
        var source = mainShellSource.asFile.readText()
        source = source.replaceExactlyOnce(
            oldValue = "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.size",
            newValue = "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.size",
            label = "offset import",
        )
        source = source.replaceExactlyOnce(
            oldValue = "        BrandLogo(Modifier.size(railLogoSize))",
            newValue = """        if (LocalAdaptiveUi.current.isTelevision) {
            BrandBadge(
                Modifier
                    .size(if (expanded) 76.dp else 50.dp)
                    .offset(x = if (expanded) 0.dp else (-4).dp),
            )
        } else {
            BrandLogo(Modifier.size(railLogoSize))
        }""",
            label = "TV rail logo",
        )
        source = source.replaceExactlyOnce(
            oldValue = """    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator""",
            newValue = """    val isTelevision = adaptiveUi.isTelevision
    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && !isTelevision && adaptiveUi.showKeyboardFocusIndicator""",
            label = "TV rail focus classification",
        )
        source = source.replaceExactlyOnce(
            oldValue = "                if (televisionFocused || keyboardFocused) 2.dp else 0.dp,",
            newValue = "                if (keyboardFocused) 2.dp else 0.dp,",
            label = "TV rail outline",
        )
        source = source.replaceExactlyOnce(
            oldValue = "            .height(if (isTv) 164.dp else 220.dp)",
            newValue = "            .height(220.dp)",
            label = "TV download card height",
        )

        val output = tvFixedMainShellOutput.get().asFile
        output.parentFile.mkdirs()
        output.writeText(source)
    }
}

tasks.configureEach {
    val needsTvFixedSource =
        (name.startsWith("compile") && name.endsWith("Kotlin")) ||
            name.startsWith("lint", ignoreCase = true)
    if (needsTvFixedSource) {
        dependsOn(generateTvFixedMainShellSource)
    }
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

        buildConfigField("String", "PORTAL_URL", portalUrl.get().asBuildConfigString())
        buildConfigField("String", "CONFIG_URL", configUrl.get().asBuildConfigString())
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.exclude("sa/hulksa/player/ui/screens/MainShellScreen.kt")
            java.srcDir(tvFixedMainShellSourceRoot)
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
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
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
