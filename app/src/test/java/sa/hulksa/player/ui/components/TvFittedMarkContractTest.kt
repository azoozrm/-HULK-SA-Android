package sa.hulksa.player.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFittedMarkContractTest {
    @Test
    fun `fitted TV mark preserves the exact NEO reference path and square viewport`() {
        val reference = readDrawable("hulk_sa_mark_reference.xml")
        val fitted = readDrawable("hulk_sa_tv_mark_fitted.xml")

        assertEquals(pathData(reference), pathData(fitted))
        assertTrue(fitted.contains("android:width=\"108dp\""))
        assertTrue(fitted.contains("android:height=\"108dp\""))
        assertTrue(fitted.contains("android:viewportWidth=\"1000\""))
        assertTrue(fitted.contains("android:viewportHeight=\"1000\""))
        assertTrue(fitted.contains("android:fillType=\"evenOdd\""))
    }

    @Test
    fun `fitted TV mark transform is uniform centered and tightly bounded`() {
        val fitted = readDrawable("hulk_sa_tv_mark_fitted.xml")
        val scaleX = attr(fitted, "scaleX").toFloat()
        val scaleY = attr(fitted, "scaleY").toFloat()
        val translateX = attr(fitted, "translateX").toFloat()
        val translateY = attr(fitted, "translateY").toFloat()

        assertEquals(scaleX, scaleY, 0.000001f)

        // Rendered visible bounds of the reference path in its 1000-unit viewport, measured from
        // the exact compiled path. The fitted group maps those bounds to a 940-unit-high centered
        // box with a 30-unit safety margin on the limiting dimension.
        val pathMinX = 15.917168f
        val pathMaxX = 304.212457f
        val pathMinY = 46.298434f
        val pathMaxY = 370.646845f
        val minX = pathMinX * scaleX + translateX
        val maxX = pathMaxX * scaleY + translateX
        val minY = pathMinY * scaleX + translateY
        val maxY = pathMaxY * scaleY + translateY

        assertEquals(500f, (minX + maxX) / 2f, 0.01f)
        assertEquals(500f, (minY + maxY) / 2f, 0.01f)
        assertEquals(940f, maxY - minY, 0.05f)
        assertTrue(minX >= 40f && maxX <= 960f)
        assertTrue(minY >= 20f && maxY <= 980f)
    }

    @Test
    fun `rail explicitly selects the fitted mark in the shared slot`() {
        val shell = readSource("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt")

        assertTrue(shell.contains("TvRailBrandTile(Modifier.size(metrics.logoSizeDp.dp))"))
    }

    @Test
    fun `rail brand tile draws directly on the rail surface without a separate backing`() {
        val source = readSource("src/main/java/sa/hulksa/player/ui/components/TvRailBrandLogo.kt")
        val tile = source.substringAfter("fun TvRailBrandTile").substringBefore("/**", source)

        assertFalse(tile.contains(".background("))
        assertTrue(tile.contains(".clip(shape)"))
        assertTrue(tile.contains("contentAlignment = Alignment.Center"))
        assertTrue(tile.contains("painterResource(R.drawable.hulk_sa_tv_mark_fitted)"))
        assertTrue(tile.contains("Modifier.fillMaxSize().padding(3.dp)"))
    }

    @Test
    fun `category chips gate the fitted mark to television and keep the phone mark`() {
        val shell = readSource("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt")
        val badgeBlock = shell
            .substringAfter("val badgeShape = RoundedCornerShape")
            .substringBefore("Text(")

        assertTrue(badgeBlock.contains("if (isTv)"))
        assertTrue(badgeBlock.contains("TvFittedBrandMark"))
        assertTrue(badgeBlock.contains("BrandLogo"))
    }

    @Test
    fun `player badge keeps the fitted mark television only`() {
        val player = readSource("src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt")

        assertTrue(player.contains("fittedTvMark = adaptiveUi.isTelevision"))
    }

    private fun readDrawable(name: String): String =
        File("src/main/res/drawable/$name").readText()

    private fun readSource(path: String): String = File(path).readText()

    private fun pathData(text: String): String =
        Regex("android:pathData=\"([^\"]+)\"").find(text)!!.groupValues[1]

    private fun attr(text: String, name: String): String =
        Regex("android:$name=\"([^\"]+)\"").find(text)!!.groupValues[1]
}
