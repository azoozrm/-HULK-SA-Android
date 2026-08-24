package sa.hulksa.player.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sa.hulksa.player.data.GrowthDestination
import sa.hulksa.player.data.GrowthQrPresentation
import sa.hulksa.player.data.OperationsGrowthLinkConfig
import sa.hulksa.player.data.normalizeGrowthCustomQrUrl
import sa.hulksa.player.data.resolveGrowthQrPresentation
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
internal fun GrowthQrDialog(
    destination: GrowthDestination,
    link: OperationsGrowthLinkConfig,
    onDismiss: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val backRequester = remember { FocusRequester() }
    val customUrl = normalizeGrowthCustomQrUrl(link.customQrUrl)
    val newSubscriptionCopy =
        destination == GrowthDestination.RENEWAL && link.title == "اشتراك جديد"
    var customImageFailed by remember(customUrl) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { backRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .82f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Some Android TV launchers expose substantially less vertical dp than the
            // physical 720p/1080p height suggests. Reserve a real bottom budget for the
            // focused Back button instead of letting the QR consume that space.
            val compactHeight = maxHeight < 620.dp
            val qrSize = minOf(
                maxWidth * if (compactHeight) .48f else .55f,
                maxHeight * if (compactHeight) .38f else .44f,
                if (compactHeight) 300.dp else 340.dp,
            ).coerceAtLeast(if (compactHeight) 170.dp else 210.dp)
            val qrPixels = with(LocalDensity.current) { qrSize.roundToPx().coerceIn(256, 1_400) }
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF11120E))
                    .border(1.dp, colors.gold.copy(alpha = .42f), shape)
                    .padding(
                        horizontal = if (compactHeight) 24.dp else 28.dp,
                        vertical = if (compactHeight) 12.dp else 18.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactHeight) 7.dp else 9.dp),
            ) {
                Text(
                    text = "HULK SA",
                    color = colors.goldBright,
                    fontSize = if (compactHeight) 15.sp else 17.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = when {
                        newSubscriptionCopy -> "اشتراك جديد"
                        destination == GrowthDestination.RENEWAL -> "الموقع الالكتروني"
                        else -> "الدعم الفني"
                    },
                    color = colors.text,
                    fontSize = if (compactHeight) 25.sp else 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Text(
                    text = when {
                        newSubscriptionCopy -> "اشترك الان من موقع HULK SA"
                        destination == GrowthDestination.RENEWAL -> "جدد اشتراكك من موقع HULK SA"
                        else -> "تواصل معنا عبر واتساب"
                    },
                    color = colors.textMuted,
                    fontSize = if (compactHeight) 14.sp else 15.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "امسح رمز QR بكاميرا جوالك",
                    color = colors.goldBright,
                    fontSize = if (compactHeight) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.gold.copy(alpha = .10f))
                        .padding(
                            horizontal = if (compactHeight) 12.dp else 14.dp,
                            vertical = if (compactHeight) 5.dp else 7.dp,
                        ),
                )

                GrowthQrImage(
                    content = link.url.orEmpty(),
                    customUrl = customUrl,
                    showCustom = resolveGrowthQrPresentation(link, customImageFailed) ==
                        GrowthQrPresentation.CUSTOM_IMAGE,
                    targetPixels = qrPixels,
                    customImageFailed = { customImageFailed = true },
                    modifier = Modifier.size(qrSize),
                )

                Text(
                    text = "\u2066${link.displayText.ifBlank { link.url.orEmpty() }}\u2069",
                    color = colors.text,
                    fontSize = if (compactHeight) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = TextStyle(textDirection = TextDirection.Ltr),
                    maxLines = 1,
                )
                Spacer(Modifier.height(if (compactHeight) 0.dp else 1.dp))
                FocusButton(
                    text = "رجوع",
                    onClick = onDismiss,
                    primary = false,
                    compact = true,
                    modifier = Modifier
                        .focusRequester(backRequester)
                        .widthIn(min = 160.dp)
                        .padding(bottom = if (compactHeight) 2.dp else 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GrowthQrImage(
    content: String,
    customUrl: String?,
    showCustom: Boolean,
    targetPixels: Int,
    customImageFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val generated by produceState<Bitmap?>(
        initialValue = null,
        key1 = content,
        key2 = targetPixels,
    ) {
        value = withContext(Dispatchers.Default) {
            generateGrowthQrBitmap(content, targetPixels)
        }
    }
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color.White, shape)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        generated?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "رمز QR",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showCustom && customUrl != null) {
            AsyncImage(
                model = customUrl,
                contentDescription = "رمز QR مخصص",
                contentScale = ContentScale.Fit,
                onError = { customImageFailed() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun generateGrowthQrBitmap(content: String, sizePixels: Int): Bitmap? = runCatching {
    require(content.isNotBlank())
    val safeSize = sizePixels.coerceIn(256, 1_400)
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        safeSize,
        safeSize,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ),
    )
    val pixels = IntArray(safeSize * safeSize)
    for (y in 0 until safeSize) {
        val rowOffset = y * safeSize
        for (x in 0 until safeSize) {
            pixels[rowOffset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(pixels, safeSize, safeSize, Bitmap.Config.ARGB_8888)
}.getOrNull()
