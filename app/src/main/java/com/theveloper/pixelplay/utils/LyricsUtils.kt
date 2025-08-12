package com.theveloper.pixelplay.utils

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ProviderText(
    providerText: String,
    uri: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        append(providerText)
        pushStringAnnotation(tag = "URL", annotation = uri)
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append(" LRCLIB")
        }
        pop()
    }

    textAlign?.let { MaterialTheme.typography.bodySmall.copy(textAlign = it) }?.let {
        ClickableText(
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        },
        style = it,
        modifier = modifier
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsTypeSwitch(
    isSynced: Boolean,
    onIsSyncedSwitch: (Boolean) -> Unit,
    enabled: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Synced", "Plain")
    val selectedTabIndex = if (isSynced) 0 else 1

    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.height(48.dp),
        containerColor = Color.Transparent,
        divider = {}
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = {
                    if (enabled) {
                        onIsSyncedSwitch(index == 0)
                    }
                },
                text = { Text(text = title, fontWeight = FontWeight.Medium) },
                selectedContentColor = accentColor,
                unselectedContentColor = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun BubblesLine(
    positionFlow: Flow<Long>,
    time: Int,
    color: Color,
    nextTime: Int,
    modifier: Modifier = Modifier,
) {
    val position by positionFlow.collectAsState(initial = 0L)
    val isCurrent = position in time until nextTime
    val transition = rememberInfiniteTransition(label = "bubbles")

    val animatedValue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "bubble_animation"
    )

    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) {
        show = isCurrent
    }

    if (show) {
        Canvas(modifier = modifier.size(48.dp, 24.dp)) {
            val bubbleColor = color.copy(alpha = 0.5f)
            val bubbleCount = 3
            val bubbleRadius = 4.dp.toPx()

            for (i in 0 until bubbleCount) {
                val progress = (animatedValue + i * (1f / bubbleCount)) % 1f
                val yOffset = sin(progress * 2 * PI).toFloat() * 4.dp.toPx()
                translate(left = (size.width / (bubbleCount + 1)) * (i + 1)) {
                    drawCircle(
                        color = bubbleColor,
                        radius = bubbleRadius,
                        center = Offset(x = 0f, y = size.height / 2 + yOffset)
                    )
                }
            }
        }
    }
}