#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(relative_path: str, old: str, new: str) -> None:
    path = ROOT / relative_path
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"{relative_path}: expected exactly one match, found {count}: {old[:140]!r}"
        )
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


login = "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
components = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
contract = "quality/compatibility-v2/tests/test_mobile_window_contract.py"

replace_once(
    login,
    """                Row(
                    modifier = Modifier
                        .widthIn(max = 1110.dp)
                        .fillMaxWidth()
                        .heightIn(max = if (compactMobileLandscape) 390.dp else 680.dp),""",
    """                Row(
                    modifier = Modifier
                        .widthIn(max = 1110.dp)
                        .fillMaxWidth()
                        .then(
                            if (compactMobileLandscape) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.heightIn(max = 680.dp)
                            },
                        ),""",
)
replace_once(
    login,
    """                        modifier = Modifier.width(
                            when {
                                compactMobileLandscape -> 520.dp
                                compactWideTv -> 440.dp
                                isTv -> 450.dp
                                else -> 430.dp
                            },
                        ),""",
    """                        modifier = Modifier
                            .width(
                                when {
                                    compactMobileLandscape -> 500.dp
                                    compactWideTv -> 440.dp
                                    isTv -> 450.dp
                                    else -> 430.dp
                                },
                            )
                            .then(
                                if (compactMobileLandscape) Modifier.fillMaxHeight() else Modifier,
                            ),""",
)
replace_once(
    login,
    """    val panelShape = RoundedCornerShape(if (compact) 18.dp else 26.dp)
    val panelHorizontalPadding = if (compact) 18.dp else 28.dp
    val panelVerticalPadding = if (compact) 8.dp else 22.dp
    Column(
        modifier = modifier
            .widthIn(max = 474.dp)
            .clip(panelShape)""",
    """    val panelShape = RoundedCornerShape(if (compact) 18.dp else 26.dp)
    val panelHorizontalPadding = if (compact) 14.dp else 28.dp
    val panelVerticalPadding = if (compact) 6.dp else 22.dp
    val panelScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .widthIn(max = 474.dp)
            .then(if (compact) Modifier.verticalScroll(panelScrollState) else Modifier)
            .clip(panelShape)""",
)
replace_once(login, "fontSize = if (compact) 22.sp else 31.sp,", "fontSize = if (compact) 18.sp else 31.sp,")
replace_once(login, "fontSize = if (compact) 10.sp else 13.sp,", "fontSize = if (compact) 9.sp else 13.sp,")
replace_once(login, "Spacer(Modifier.height(if (compact) 6.dp else 18.dp))", "Spacer(Modifier.height(if (compact) 4.dp else 18.dp))")
replace_once(
    login,
    """                .fillMaxWidth()
                .heightIn(min = if (compact) 42.dp else 55.dp),
            keyboardOptions = KeyboardOptions(""",
    """                .fillMaxWidth()
                .heightIn(min = if (compact) 38.dp else 55.dp),
            compact = compact,
            keyboardOptions = KeyboardOptions(""",
)
replace_once(
    login,
    """            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 42.dp else 55.dp),
            visualTransformation""",
    """            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 38.dp else 55.dp),
            compact = compact,
            visualTransformation""",
)
replace_once(login, "Spacer(Modifier.height(if (compact) 6.dp else 10.dp))", "Spacer(Modifier.height(if (compact) 4.dp else 10.dp))")
replace_once(
    login,
    "Spacer(Modifier.height(if (compact) 4.dp else 8.dp))\n\n        LoginOption(",
    "Spacer(Modifier.height(if (compact) 2.dp else 8.dp))\n\n        LoginOption(",
)
replace_once(login, "Spacer(Modifier.height(if (compact) 6.dp else 13.dp))", "Spacer(Modifier.height(if (compact) 4.dp else 13.dp))")
replace_once(
    login,
    """            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 40.dp else 52.dp),
            onFocused = onNonTextFocus,""",
    """            enabled = !isLoading,
            compact = compact,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 36.dp else 52.dp),
            onFocused = onNonTextFocus,""",
)
replace_once(
    login,
    """            onClick = onOpenWebsite,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 40.dp else 52.dp),
            primary = false,""",
    """            onClick = onOpenWebsite,
            compact = compact,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 36.dp else 52.dp),
            primary = false,""",
)
replace_once(login, ".heightIn(min = if (compact) 36.dp else 48.dp)", ".heightIn(min = if (compact) 32.dp else 48.dp)")
replace_once(
    login,
    ".padding(horizontal = 7.dp, vertical = 6.dp),",
    ".padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 3.dp else 6.dp),",
)
replace_once(login, ".size(20.dp)\n                .clip", ".size(if (compact) 18.dp else 20.dp)\n                .clip")
replace_once(login, "Spacer(Modifier.width(10.dp))", "Spacer(Modifier.width(if (compact) 7.dp else 10.dp))")
replace_once(
    login,
    "fontSize = 12.sp,\n            fontWeight = if (focused)",
    "fontSize = if (compact) 10.sp else 12.sp,\n            fontWeight = if (focused)",
)

replace_once(
    components,
    """    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)""",
    """    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    compact: Boolean = false,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)""",
)
replace_once(
    components,
    ".padding(horizontal = 15.dp, vertical = 13.dp),",
    ".padding(horizontal = if (compact) 12.dp else 15.dp, vertical = if (compact) 8.dp else 13.dp),",
)
replace_once(
    components,
    "textStyle = TextStyle(color = colors.text, fontSize = 15.sp, textAlign = TextAlign.Start),",
    "textStyle = TextStyle(color = colors.text, fontSize = if (compact) 13.sp else 15.sp, textAlign = TextAlign.Start),",
)
replace_once(
    components,
    "if (value.isEmpty()) Text(label, color = colors.textMuted, fontSize = 14.sp)",
    "if (value.isEmpty()) Text(label, color = colors.textMuted, fontSize = if (compact) 12.sp else 14.sp)",
)

replace_once(
    contract,
    '        self.assertIn("min = if (compact) 42.dp else 55.dp", login)\n',
    '        self.assertIn("min = if (compact) 38.dp else 55.dp", login)\n',
)
contract_text = (ROOT / contract).read_text(encoding="utf-8")
anchor = """        self.assertIn(\"if (!compact)\", login)\n"""
addition = """        self.assertIn(\"Modifier.fillMaxHeight()\", login)\n        self.assertIn(\"Modifier.verticalScroll(panelScrollState)\", login)\n        self.assertIn(\"compact = compact\", login)\n"""
if anchor not in contract_text:
    raise RuntimeError(f"{contract}: compact Login contract anchor is missing")
if addition not in contract_text:
    contract_text = contract_text.replace(anchor, anchor + addition, 1)
    (ROOT / contract).write_text(contract_text, encoding="utf-8")

print("Applied compact, full-height and scrollable short-landscape Login correction.")
