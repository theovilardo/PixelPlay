package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

private val MinTopBarHeight = 64.dp
private val MaxTopBarHeight = 160.dp

@Composable
fun LazyColumnWithCollapsibleTopBar(
    listState: LazyListState,
    topBarContent: @Composable () -> Unit,
    collapseFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

    val topBarHeightRange = (MaxTopBarHeight - MinTopBarHeight).value
    val scrollOffset = when {
        firstVisibleItemIndex > 0 -> topBarHeightRange
        else -> firstVisibleItemScrollOffset.toFloat()
    }

    val topBarHeight = (MaxTopBarHeight.value - scrollOffset).coerceIn(MinTopBarHeight.value, MaxTopBarHeight.value).dp

    val fraction = (1 - (scrollOffset / topBarHeightRange)).coerceIn(0f, 1f)
    collapseFraction(fraction)

    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topBarHeight + contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
                start = contentPadding.calculateLeftPadding(layoutDirection),
                end = contentPadding.calculateRightPadding(layoutDirection)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
        val translationOffset = if (firstVisibleItemIndex == 0) {
            -firstVisibleItemScrollOffset.toFloat()
        } else {
            -topBarHeightRange
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight)
                .graphicsLayer {
                    translationY = translationOffset
                }
        ) {
            topBarContent()
        }
    }
}