#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_count(path: str, old: str, new: str, expected: int) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path}: expected {expected} matches, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


adaptive = "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt"
replace_once(
    adaptive,
    """    val widthDp = windowSize.width.value.roundToInt().coerceAtLeast(1)
    val heightDp = windowSize.height.value.roundToInt().coerceAtLeast(1)
""",
    """    val widthDp = stableWindowDimensionDp(
        configurationDp = configuration.screenWidthDp,
        containerDp = windowSize.width.value.roundToInt(),
    )
    val heightDp = stableWindowDimensionDp(
        configurationDp = configuration.screenHeightDp,
        containerDp = windowSize.height.value.roundToInt(),
    )
""",
)
replace_once(
    adaptive,
    """fun classifyInputSource(source: Int): HulkInputMode {
""",
    """internal fun stableWindowDimensionDp(configurationDp: Int, containerDp: Int): Int =
    configurationDp.takeIf { it > 0 } ?: containerDp.coerceAtLeast(1)

fun classifyInputSource(source: Int): HulkInputMode {
""",
)
replace_once(
    adaptive,
    """        pageHorizontalPaddingDp = if (windowWidthClass == HulkWindowWidthClass.COMPACT) 12 else 16,
""",
    """        pageHorizontalPaddingDp = when {
            orientation == HulkOrientation.PORTRAIT &&
                windowWidthClass == HulkWindowWidthClass.COMPACT -> 0
            windowWidthClass == HulkWindowWidthClass.COMPACT -> 12
            else -> 16
        },
""",
)

login = "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
replace_once(
    login,
    """import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
""",
    """import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
""",
)
replace_once(
    login,
    """import sa.hulksa.player.ui.components.BrandLogo
""",
    """import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
""",
)
replace_once(
    login,
    """    val colors = LocalHulkColors.current
    val uriHandler = LocalUriHandler.current
""",
    """    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val uriHandler = LocalUriHandler.current
""",
)
replace_once(
    login,
    """    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberAccount by remember { mutableStateOf(true) }
""",
    """    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var rememberAccount by rememberSaveable { mutableStateOf(true) }
""",
)
replace_once(
    login,
    """        val compactHeight = !isTv && maxHeight < 600.dp
        val compactMobileLandscape = !isTv && maxWidth > maxHeight && maxHeight < 520.dp
""",
    """        val stableWindowWidthDp = adaptiveUi.screenWidthDp
        val stableWindowHeightDp = adaptiveUi.screenHeightDp
        val compactHeight = !isTv && stableWindowHeightDp < 600
        val compactMobileLandscape = !isTv &&
            stableWindowWidthDp > stableWindowHeightDp &&
            stableWindowHeightDp < 520
""",
)
replace_once(
    login,
    """            .then(if (compact) Modifier.verticalScroll(panelScrollState) else Modifier)
""",
    """            .then(if (landscapePhone) Modifier.verticalScroll(panelScrollState) else Modifier)
""",
)

shell = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
replace_once(shell, "import androidx.compose.foundation.layout.BoxWithConstraints\n", "")
replace_once(shell, "import androidx.compose.foundation.layout.offset\n", "")
replace_once(
    shell,
    """private val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp
""",
    """private val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp

@Composable
private fun resolvedPageHorizontalGutter(isTv: Boolean) =
    if (isTv) TV_PAGE_GUTTER else LocalAdaptiveUi.current.layoutPolicy.pageHorizontalPaddingDp.dp
""",
)
replace_once(
    shell,
    """    val portraitEdgeInset = if (
        !isTv &&
        !useNavigationRail &&
        adaptiveUi.screenHeightDp > adaptiveUi.screenWidthDp
    ) {
        when (state.destination) {
            MainDestination.HOME -> 25.dp
            MainDestination.LIVE -> 12.dp
            MainDestination.MOVIES,
            MainDestination.SERIES,
            MainDestination.FAVORITES,
            MainDestination.SEARCH,
            MainDestination.DOWNLOADS,
            -> 13.dp
            MainDestination.SETTINGS -> 15.dp
        }
    } else {
        0.dp
    }
""",
    "",
)
replace_once(
    shell,
    """                BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
                    Box(
                        Modifier
                            .width(maxWidth + portraitEdgeInset + portraitEdgeInset)
                            .fillMaxHeight()
                            .offset(x = -portraitEdgeInset),
                    ) {
                        DestinationContent(
                            state = state,
                            isTv = false,
                            navigationMemory = navigationMemory,
                            isFavorite = resolvedIsFavorite,
                            onSelectCategory = onSelectCategory,
                            onSearch = onSearch,
                            onOpen = onOpen,
                            onOpenHistory = onOpenHistory,
                            onToggleFavorite = toggleFavoriteWithFeedback,
                            onRefresh = onRefresh,
                            onSelectDestination = onSelectDestination,
                            onClearHistory = onClearHistory,
                            onPlayDownload = onPlayDownload,
                            onDeleteDownload = onDeleteDownload,
                            onRetryDownload = onRetryDownload,
                            onToggleWifiOnly = onToggleWifiOnly,
                            onToggleDownloadSchedule = onToggleDownloadSchedule,
                            onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                            onCycleDownloadPriority = onCycleDownloadPriority,
                            onRunDiagnostics = onRunDiagnostics,
                            onLogout = onLogout,
                        )
                    }
                }
""",
    """                Box(Modifier.weight(1f).clipToBounds()) {
                    DestinationContent(
                        state = state,
                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onToggleDownloadSchedule = onToggleDownloadSchedule,
                        onCycleConcurrentDownloads = onCycleConcurrentDownloads,
                        onCycleDownloadPriority = onCycleDownloadPriority,
                        onRunDiagnostics = onRunDiagnostics,
                        onLogout = onLogout,
                    )
                }
""",
)
replace_once(
    shell,
    "Modifier.padding(horizontal = if (isTv) 25.dp else 14.dp)",
    "Modifier.padding(horizontal = resolvedPageHorizontalGutter(isTv))",
)
replace_once(
    shell,
    "Box(Modifier.fillMaxWidth().padding(horizontal = if (isTv) TV_PAGE_GUTTER else 25.dp)) { content() }",
    "Box(Modifier.fillMaxWidth().padding(horizontal = resolvedPageHorizontalGutter(isTv))) { content() }",
)
replace_once(
    shell,
    "horizontal = if (isTv) TV_PAGE_GUTTER else 13.dp,",
    "horizontal = resolvedPageHorizontalGutter(isTv),",
)
replace_once(
    shell,
    "horizontal = if (isTv) TV_PAGE_GUTTER else 12.dp,",
    "horizontal = resolvedPageHorizontalGutter(isTv),",
)
replace_count(
    shell,
    ".padding(if (isTv) TV_PAGE_GUTTER else 13.dp)",
    ".padding(resolvedPageHorizontalGutter(isTv))",
    3,
)
replace_once(
    shell,
    "PaddingValues(15.dp)",
    "PaddingValues(horizontal = resolvedPageHorizontalGutter(false), vertical = 15.dp)",
)
replace_once(
    shell,
    "contentPadding = PaddingValues(horizontal = if (isTv) 8.dp else 24.dp, vertical = 8.dp),",
    "contentPadding = PaddingValues(horizontal = if (isTv) 8.dp else 3.dp, vertical = 8.dp),",
)

test_file = "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt"
replace_once(
    test_file,
    """    @Test
    fun phonePortraitOrientationRestoresAfterLandscapePlayback() {
""",
    """    @Test
    fun phonePortraitLoginFieldsAcceptTypingWithoutCrash() {
        assumeFalse("Portrait login typing test is not applicable to television UI mode", isTelevision())
        assumeTrue("Portrait login typing test must start in portrait", device.displayHeight > device.displayWidth)

        launchScenario().use { scenario ->
            assertTrue(
                "Application package did not become visible",
                device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 15_000L),
            )
            instrumentation.waitForIdleSync()
            SystemClock.sleep(1_200L)

            var fields = device.findObjects(By.clazz("android.widget.EditText"))
            if (fields.size < 2) {
                val usernameLabel = device.wait(Until.findObject(By.text("اسم المستخدم")), 6_000L)
                assertNotNull("Username field was not exposed", usernameLabel)
                usernameLabel?.click()
            } else {
                fields[0].click()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(500L)
            device.executeShellCommand("input text portraituser")
            instrumentation.waitForIdleSync()
            SystemClock.sleep(700L)

            scenario.onActivity { activity ->
                assertFalse("Application finished after username input", activity.isFinishing)
                assertTrue("Application window disappeared after username input", activity.window.decorView.isShown)
            }
            assertTrue(
                "Application package left the foreground after username input",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )

            fields = device.findObjects(By.clazz("android.widget.EditText"))
            if (fields.size >= 2) {
                fields[1].click()
            } else {
                val passwordLabel = device.wait(Until.findObject(By.text("كلمة المرور")), 6_000L)
                assertNotNull("Password field was not exposed", passwordLabel)
                passwordLabel?.click()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(500L)
            device.executeShellCommand("input text portraitpass")
            instrumentation.waitForIdleSync()
            SystemClock.sleep(900L)

            scenario.onActivity { activity ->
                assertFalse("Application finished after password input", activity.isFinishing)
                assertTrue("Application window disappeared after password input", activity.window.decorView.isShown)
            }
            assertTrue(
                "Application package left the foreground after password input",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
            assertFalse(
                "Android crash recovery dialog appeared after portrait login typing",
                device.hasObject(By.textContains("مسح ذاكرة التخزين المؤقت")),
            )

            val output = File(targetContext.getExternalFilesDir(null), "compatibility-v2").apply { mkdirs() }
            assertTrue(
                "Portrait login evidence screenshot failed",
                device.takeScreenshot(File(output, "portrait-login-ime-stable.png")),
            )
            device.dumpWindowHierarchy(File(output, "portrait-login-ime-stable.xml"))

            device.pressBack()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(700L)
            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
        }
    }

    @Test
    fun phonePortraitOrientationRestoresAfterLandscapePlayback() {
""",
)

unit_test = "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt"
replace_once(
    unit_test,
    """    @Test
    fun windowWidthBreakpointsUseContainerDimensions() {
""",
    """    @Test
    fun configurationDimensionsRemainStableWhenImeShrinksTheComposeContainer() {
        assertEquals(411, stableWindowDimensionDp(configurationDp = 411, containerDp = 411))
        assertEquals(891, stableWindowDimensionDp(configurationDp = 891, containerDp = 430))
        assertEquals(430, stableWindowDimensionDp(configurationDp = 0, containerDp = 430))
    }

    @Test
    fun compactPortraitPolicyUsesTheFullAvailableWidth() {
        val policy = resolveAdaptiveLayoutPolicy(
            deviceClass = HulkDeviceClass.MOBILE,
            windowWidthClass = HulkWindowWidthClass.COMPACT,
            windowHeightClass = HulkWindowHeightClass.MEDIUM,
            orientation = HulkOrientation.PORTRAIT,
            inputMode = HulkInputMode.TOUCH,
        )

        assertEquals(0, policy.pageHorizontalPaddingDp)
    }

    @Test
    fun windowWidthBreakpointsUseContainerDimensions() {
""",
)

shell_text = Path(shell).read_text(encoding="utf-8")
for token in ("portraitEdgeInset", "BoxWithConstraints", "maxWidth + portraitEdgeInset"):
    if token in shell_text:
        raise SystemExit(f"MainShell regression token still present: {token}")

login_text = Path(login).read_text(encoding="utf-8")
if ".then(if (compact) Modifier.verticalScroll(panelScrollState) else Modifier)" in login_text:
    raise SystemExit("Nested portrait Login scroll regression still present")
