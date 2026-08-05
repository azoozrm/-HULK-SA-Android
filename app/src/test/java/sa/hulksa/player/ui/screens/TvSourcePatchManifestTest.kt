package sa.hulksa.player.ui.screens

import java.io.File
import java.util.Base64
import java.util.Properties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSourcePatchManifestTest {
    private fun repositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/build.gradle.kts").isFile }
            ?: error("Unable to locate repository root from ${System.getProperty("user.dir")}")

    private fun manifest(root: File): Properties = Properties().apply {
        File(root, "gradle/tv-source-patches.properties").inputStream().use(::load)
    }

    private fun required(properties: Properties, key: String): String =
        properties.getProperty(key) ?: error("Missing TV patch property: $key")

    private fun String.countOccurrences(needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var cursor = 0
        while (true) {
            val index = indexOf(needle, cursor)
            if (index < 0) return count
            count += 1
            cursor = index + needle.length
        }
    }

    @Test
    fun `TV source patch manifest is ordered and complete`() {
        val root = repositoryRoot()
        val properties = manifest(root)
        val patchOrder = required(properties, "patch.order")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

        assertTrue(
            patchOrder == listOf(
                "offset-import",
                "tv-rail-logo",
                "tv-rail-focus-classification",
                "tv-rail-outline",
                "tv-download-card-height",
            ),
        )
        assertTrue(patchOrder.size == patchOrder.distinct().size)
        patchOrder.forEach { patchId ->
            assertTrue(required(properties, "$patchId.label").isNotBlank())
            assertTrue(required(properties, "$patchId.old.base64").isNotBlank())
            assertTrue(required(properties, "$patchId.new.base64").isNotBlank())
        }
    }

    @Test
    fun `manifest reproduces the qualified TV source deterministically`() {
        val root = repositoryRoot()
        val properties = manifest(root)
        val patchOrder = required(properties, "patch.order")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val decoder = Base64.getDecoder()
        val original = File(
            root,
            "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
        ).readText()
        var materialized = original
        val applied = mutableListOf<Pair<String, String>>()

        patchOrder.forEach { patchId ->
            val oldValue = String(decoder.decode(required(properties, "$patchId.old.base64")), Charsets.UTF_8)
            val newValue = String(decoder.decode(required(properties, "$patchId.new.base64")), Charsets.UTF_8)
            assertTrue(materialized.countOccurrences(oldValue) == 1)
            materialized = materialized.replace(oldValue, newValue)
            applied += oldValue to newValue
        }

        assertTrue(materialized.contains("import androidx.compose.foundation.layout.offset"))
        assertTrue(materialized.contains("if (LocalAdaptiveUi.current.isTelevision)"))
        assertTrue(materialized.contains(".size(if (expanded) 76.dp else 50.dp)"))
        assertTrue(materialized.contains("val isTelevision = adaptiveUi.isTelevision"))
        assertTrue(materialized.contains("focused && !isTelevision && adaptiveUi.showKeyboardFocusIndicator"))
        assertTrue(materialized.contains(".height(220.dp)"))
        assertFalse(materialized.contains(".height(if (isTv) 164.dp else 220.dp)"))

        var restored = materialized
        applied.asReversed().forEach { (oldValue, newValue) ->
            assertTrue(restored.countOccurrences(newValue) == 1)
            restored = restored.replace(newValue, oldValue)
        }
        assertTrue(original == restored)
    }

    @Test
    fun `build script declares patch manifest as an input`() {
        val root = repositoryRoot()
        val buildScript = File(root, "app/build.gradle.kts").readText()

        assertTrue(buildScript.contains("inputs.file(rootProject.file(\"gradle/tv-source-patches.properties\"))"))
        assertTrue(buildScript.contains("patchProperties.getProperty(\"patch.order\")"))
        assertFalse(buildScript.contains("label = \"TV rail logo\""))
        assertFalse(buildScript.contains("label = \"TV rail outline\""))
    }
}
