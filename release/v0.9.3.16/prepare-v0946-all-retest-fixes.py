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
    '''    val hideKeyboard: () -> Unit = {
        keyboardController?.hide()
        val hidePlatformIme: () -> Unit = {
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            Unit
        }
        view.post(hidePlatformIme)
        view.postDelayed(hidePlatformIme, 120L)
        view.postDelayed(hidePlatformIme, 320L)
        Unit
    }
    val dismissKeyboard: () -> Unit = {
        hideKeyboard()
        focusManager.clearFocus(force = true)
        Unit
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
    "hide top controls behind next prompt",
)
rep(
    player,
    '''        if (controlsVisible && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerBottomControls(
''',
    '''        if (controlsVisible && nextCountdown < 0 && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerBottomControls(
''',
    "hide bottom controls behind next prompt",
)
rep(
    player,
    '''                NextEpisodePrompt(
                    seconds = nextCountdown,
                    nextTitle = nextTitle,
                    onPlay = { playNextEpisode() },
                    onCancel = { nextCountdown = -1 },
                )
''',
    '''                NextEpisodePrompt(
                    seconds = nextCountdown,
                    nextTitle = nextTitle,
                    playFocusRequester = nextEpisodePlayFocus,
                    cancelFocusRequester = nextEpisodeCancelFocus,
                    onPlay = { playNextEpisode() },
                    onCancel = { nextCountdown = -1 },
                )
''',
    "next prompt requester wiring",
)
rep(
    player,
    '''    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
''',
    '''    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isTv) 32.dp else 24.dp,
                end = if (isTv) 32.dp else 24.dp,
                top = if (isTv) 28.dp else 10.dp,
                bottom = 10.dp,
            ),
''',
    "player top TV safe area",
)
rep(
    player,
    '''            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
''',
    '''            .padding(
                start = if (adaptiveUi.isTelevision) 32.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 32.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 28.dp else 20.dp,
            ),
''',
    "vod bottom TV safe area",
)
rep(
    player,
    '''            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
''',
    '''            .padding(
                start = if (adaptiveUi.isTelevision) 32.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 32.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 30.dp else 24.dp,
            ),
''',
    "live bottom TV safe area",
)
rep(
    player,
    '''    val colors = LocalHulkColors.current
    val density = LocalDensity.current
''',
    '''    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val density = LocalDensity.current
''',
    "live adaptive context",
)
rep(
    player,
    '''                Text("اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
''',
    '''                Text(
                    if (adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE) {
                        "السهم لاعلى: القناة التالية  •  السهم لاسفل: القناة السابقة"
                    } else {
                        "اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة"
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                )
''',
    "remote-aware live hint",
)
rep(
    player,
    '''private fun NextEpisodePrompt(
    seconds: Int,
    nextTitle: String,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
) {
''',
    '''private fun NextEpisodePrompt(
    seconds: Int,
    nextTitle: String,
    playFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
) {
''',
    "next prompt parameters",
)
rep(
    player,
    '''    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .border(1.dp, colors.accent.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
            .padding(18.dp),
''',
    '''    Column(
        Modifier
            .fillMaxWidth()
            .focusGroup()
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .border(1.dp, colors.accent.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
            .padding(18.dp),
''',
    "next prompt focus group",
)
rep(
    player,
    '''            FocusButton("تشغيل الان", onPlay, primary = true, compact = true)
            FocusButton("الغاء", onCancel, primary = false, compact = true)
''',
    '''            FocusButton(
                "تشغيل الان",
                onPlay,
                modifier = Modifier
                    .focusRequester(playFocusRequester)
                    .focusProperties {
                        left = cancelFocusRequester
                        right = cancelFocusRequester
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
                primary = true,
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
''',
    "next prompt focus trap",
)

print("Prepared v0.9.3.16 full retest fixes")