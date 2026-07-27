#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rep(path: str, old: str, new: str, label: str, count: int = 1) -> None:
    p = root / path
    text = p.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"missing {label}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


rep("app/build.gradle.kts", "versionCode = 59", "versionCode = 60", "versionCode")
rep("app/build.gradle.kts", 'versionName = "0.9.3.15"', 'versionName = "0.9.3.16"', "versionName")

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
rep(
    main,
    "import androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.offset\n",
    "main offset import",
)

rep(
    main,
    '''    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { item ->
        val wasFavorite = isFavorite(item)
        val feedbackName = item.name
        onToggleFavorite(item)
        Toast.makeText(
            context,
            if (wasFavorite) "تمت ازالة $feedbackName من المفضلة" else "تمت اضافة $feedbackName الى المفضلة",
            Toast.LENGTH_SHORT,
        ).show()
    }
''',
    '''    val favoriteOverrides = remember { mutableStateMapOf<String, Boolean>() }
    var favoriteActionLocked by remember { mutableStateOf(false) }
    val favoriteScope = rememberCoroutineScope()
    LaunchedEffect(state.favorites) {
        favoriteOverrides.entries.toList().forEach { (key, optimisticValue) ->
            if ((key in state.favorites) == optimisticValue) favoriteOverrides.remove(key)
        }
    }
    val resolvedIsFavorite: (ContentItem) -> Boolean = { item ->
        val key = "${item.type.name}:${item.id}"
        favoriteOverrides[key] ?: isFavorite(item)
    }
    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { item ->
        if (!favoriteActionLocked) {
            favoriteActionLocked = true
            val key = "${item.type.name}:${item.id}"
            val feedbackName = item.name
            val wasFavorite = resolvedIsFavorite(item)
            val optimisticValue = !wasFavorite
            favoriteOverrides[key] = optimisticValue
            onToggleFavorite(item)
            Toast.makeText(
                context,
                if (wasFavorite) "تمت ازالة $feedbackName من المفضلة" else "تمت اضافة $feedbackName الى المفضلة",
                Toast.LENGTH_SHORT,
            ).show()
            favoriteScope.launch {
                delay(900L)
                favoriteActionLocked = false
                delay(900L)
                if (favoriteOverrides[key] == optimisticValue) favoriteOverrides.remove(key)
            }
        }
    }
''',
    "favorite optimistic state and remote long-press guard",
)
rep(
    main,
    """                        isTv = true,
                        navigationMemory = navigationMemory,
                        isFavorite = isFavorite,
""",
    """                        isTv = true,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
""",
    "rail favorite resolver",
)
rep(
    main,
    """                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = isFavorite,
""",
    """                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
""",
    "mobile favorite resolver",
)

rep(
    main,
    '''    val railWidth by animateDpAsState(if (expanded) 202.dp else 78.dp, label = "railWidth")
''',
    '''    val railWidth by animateDpAsState(if (expanded) 202.dp else 86.dp, label = "railWidth")
''',
    "collapsed rail width",
)
rep(
    main,
    '''        BrandBadge(Modifier.size(if (expanded) 78.dp else 52.dp))
''',
    '''        BrandBadge(
            Modifier
                .size(if (expanded) 76.dp else 44.dp)
                .offset(x = if (expanded) 0.dp else (-7).dp),
        )
''',
    "collapsed rail logo inset",
)

# Remove the asymmetric animated tail after a category is reordered.
rep(
    main,
    """                val targetIndex = to + 3
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
""",
    """                val targetIndex = to + 3
                listState.scrollToItem(targetIndex.coerceAtLeast(0))
""",
    "catalog reorder symmetric follow",
)
rep(
    main,
    """                val targetIndex = to + 1
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
""",
    """                val targetIndex = to + 1
                listState.scrollToItem(targetIndex.coerceAtLeast(0))
""",
    "live reorder symmetric follow",
)

login = "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
rep(login, "package sa.hulksa.player.ui.screens\n\n", "package sa.hulksa.player.ui.screens\n\nimport android.content.Context\nimport android.view.inputmethod.InputMethodManager\n", "login ime imports")
rep(
    login,
    "import androidx.compose.ui.platform.LocalUriHandler\n",
    "import androidx.compose.ui.platform.LocalUriHandler\nimport androidx.compose.ui.platform.LocalView\n",
    "login LocalView import",
)
rep(
    login,
    '''    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
''',
    '''    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
''',
    "login view",
)
rep(
    login,
    '''    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
''',
    '''    val hideKeyboard = {
        keyboardController?.hide()
        val hidePlatformIme = {
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        view.post(hidePlatformIme)
        view.postDelayed(hidePlatformIme, 120L)
        view.postDelayed(hidePlatformIme, 320L)
    }
    val dismissKeyboard = {
        hideKeyboard()
        focusManager.clearFocus(force = true)
    }
''',
    "login robust ime hide",
)
rep(
    login,
    '''                        onOpenWebsite = openWebsite,
                        modifier = Modifier.width(if (isTv) 474.dp else 430.dp),
''',
    '''                        onOpenWebsite = openWebsite,
                        onNonTextFocus = hideKeyboard,
                        modifier = Modifier.width(if (isTv) 474.dp else 430.dp),
''',
    "wide login non text focus",
)
rep(
    login,
    '''                    onOpenWebsite = openWebsite,
                    modifier = Modifier.fillMaxWidth(),
''',
    '''                    onOpenWebsite = openWebsite,
                    onNonTextFocus = hideKeyboard,
                    modifier = Modifier.fillMaxWidth(),
''',
    "compact login non text focus",
)
rep(
    login,
    '''    onSubmit: () -> Unit,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier,
''',
    '''    onSubmit: () -> Unit,
    onOpenWebsite: () -> Unit,
    onNonTextFocus: () -> Unit,
    modifier: Modifier = Modifier,
''',
    "login panel focus callback",
)
rep(
    login,
    """    val colors = LocalHulkColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val panelShape = RoundedCornerShape(26.dp)
""",
    """    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(26.dp)
""",
    "remove panel keyboard controller",
)
rep(
    login,
    '''        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
        )
''',
    '''        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocused = onNonTextFocus,
        )
''',
    "show password ime hide",
)
rep(
    login,
    '''        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
        )
''',
    '''        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocused = onNonTextFocus,
        )
''',
    "remember account ime hide",
)
rep(
    login,
    '''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged { if (it.isFocused) keyboardController?.hide() },
''',
    '''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            onFocused = onNonTextFocus,
''',
    "login button ime hide",
)
rep(
    login,
    '''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            primary = false,
''',
    '''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            primary = false,
            onFocused = onNonTextFocus,
''',
    "website button ime hide",
)
rep(
    login,
    '''private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
''',
    '''private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
''',
    "login option callback",
)
rep(
    login,
    '''            .onFocusChanged { focused = it.isFocused }
''',
    '''            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
''',
    "login option focus handler",
)

player = "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
rep(player, "import androidx.compose.foundation.focusable\n", "import androidx.compose.foundation.focusable\nimport androidx.compose.foundation.focusGroup\n", "player focusGroup import")
rep(
    player,
    "import androidx.compose.ui.focus.FocusRequester\n",
    "import androidx.compose.ui.focus.FocusRequester\nimport androidx.compose.ui.focus.focusProperties\n",
    "player focus properties import",
)
rep(
    player,
    "import sa.hulksa.player.ui.components.BrandBadge\n",
    "import sa.hulksa.player.ui.adaptive.HulkInputMode\nimport sa.hulksa.player.ui.adaptive.LocalAdaptiveUi\nimport sa.hulksa.player.ui.components.BrandBadge\n",
    "player adaptive imports",
)
rep(
    player,
    '''    val unlockFocus = remember { FocusRequester() }
''',
    '''    val unlockFocus = remember { FocusRequester() }
    val nextEpisodePlayFocus = remember { FocusRequester() }
    val nextEpisodeCancelFocus = remember { FocusRequester() }
''',
    "next episode focus requesters",
)
rep(
    player,
    '''                    nextCountdown = NEXT_EPISODE_SECONDS
                    controlsVisible = true
''',
    '''                    nextCountdown = NEXT_EPISODE_SECONDS
                    controlsVisible = false
''',
    "hide controls under next episode prompt",
)
rep(
    player,
    '''    LaunchedEffect(controlsVisible, activePanel, browserVisible, resumePromptVisible, unlockVisible, controlsLocked) {
        if (browserVisible || activePanel != null || resumePromptVisible || unlockVisible) return@LaunchedEffect
''',
    '''    LaunchedEffect(controlsVisible, activePanel, browserVisible, resumePromptVisible, unlockVisible, controlsLocked, nextCountdown) {
        if (browserVisible || activePanel != null || resumePromptVisible || unlockVisible || nextCountdown >= 0) return@LaunchedEffect
''',
    "next prompt focus isolation",
)
rep(
    player,
    '''    LaunchedEffect(resumePromptVisible, request.historyKey) {
''',
    '''    LaunchedEffect(nextCountdown, request.historyKey) {
        if (nextCountdown >= 0) {
            repeat(4) {
                delay(if (it == 0) 70L else 130L)
                runCatching { nextEpisodePlayFocus.requestFocus() }
            }
        }
    }

    LaunchedEffect(resumePromptVisible, request.historyKey) {
''',
    "next prompt focus request",
)
rep(
    player,
    '''        if (controlsVisible && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerTopBar(
''',
    '''        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerTopBar(
''',
    "top controls hidden under next prompt",
)
rep(
    player,
    '''        if (controlsVisible && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
''',
    '''        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
''',
    "bottom controls hidden under next prompt",
)
rep(
    player,
    '''                onPlayNow = { nextCountdown = -1; saveAndPlayNext() },
                onCancel = { nextCountdown = -1 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(28.dp),
''',
    '''                onPlayNow = { nextCountdown = -1; saveAndPlayNext() },
                onCancel = { nextCountdown = -1; controlsVisible = true },
                playFocusRequester = nextEpisodePlayFocus,
                cancelFocusRequester = nextEpisodeCancelFocus,
                modifier = Modifier.align(Alignment.BottomEnd).padding(36.dp),
''',
    "next prompt focus wiring",
)
rep(
    player,
    '''    val colors = LocalHulkColors.current
    Row(
''',
    '''    val colors = LocalHulkColors.current
    val isTv = LocalAdaptiveUi.current.isTelevision
    Row(
''',
    "player top adaptive state",
)
rep(
    player,
    '''            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
''',
    '''            .statusBarsPadding()
            .padding(
                start = if (isTv) 34.dp else 14.dp,
                end = if (isTv) 34.dp else 14.dp,
                top = if (isTv) 28.dp else 10.dp,
                bottom = if (isTv) 18.dp else 10.dp,
            ),
''',
    "player top overscan safe area",
)
rep(
    player,
    '''    val colors = LocalHulkColors.current
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
''',
    '''    val colors = LocalHulkColors.current
    val isTv = LocalAdaptiveUi.current.isTelevision
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
''',
    "vod controls adaptive state",
)
rep(
    player,
    '''            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
''',
    '''            .navigationBarsPadding()
            .padding(
                start = if (isTv) 34.dp else 24.dp,
                end = if (isTv) 34.dp else 24.dp,
                top = 12.dp,
                bottom = if (isTv) 30.dp else 20.dp,
            ),
''',
    "vod controls overscan safe area",
)
rep(
    player,
    '''    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
''',
    '''    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val isRemoteUi = adaptiveUi.isTelevision || adaptiveUi.inputMode != HulkInputMode.TOUCH
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(
                start = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 30.dp else 24.dp,
            ),
''',
    "live controls adaptive safe area",
)
rep(
    player,
    '''            Text("اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة", color = colors.textMuted, fontSize = 10.sp)
''',
    '''            Text(
                if (isRemoteUi) "السهم لاعلى: القناة التالية  •  السهم لاسفل: القناة السابقة"
                else "اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة",
                color = colors.textMuted,
                fontSize = 10.sp,
            )
''',
    "remote live channel hint",
)
rep(
    player,
    '''private fun NextEpisodePrompt(
    title: String,
    seconds: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
''',
    '''private fun NextEpisodePrompt(
    title: String,
    seconds: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    playFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
''',
    "next prompt focus params",
)
rep(
    player,
    '''        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("تشغيل الان", onPlayNow, compact = true)
            FocusButton("الغاء", onCancel, primary = false, compact = true)
        }
''',
    '''        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusButton(
                "تشغيل الان",
                onPlayNow,
                modifier = Modifier
                    .focusRequester(playFocusRequester)
                    .focusProperties {
                        left = cancelFocusRequester
                        right = cancelFocusRequester
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
                compact = true,
            )
            FocusButton(
                "الغاء",
                onCancel,
                modifier = Modifier
                    .focusRequester(cancelFocusRequester)
                    .focusProperties {
                        left = playFocusRequester
                        right = playFocusRequester
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
                primary = false,
                compact = true,
            )
        }
''',
    "next prompt focus trap",
)

print("Prepared v0.9.3.16 full retest fixes")
