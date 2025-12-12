package com.theveloper.pixelplay.presentation.components.brickbreaker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class BrickState(
    val rect: Rect,
    val hitsRemaining: Int,
    val color: Color
)

@Composable
fun BrickBreakerOverlay(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = colorScheme.primary
    val surface = colorScheme.surface
    val density = LocalDensity.current

    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var paddleX by remember { mutableFloatStateOf(0f) }
    val paddleWidthPx = with(density) { 140.dp.toPx() }
    val paddleHeightPx = with(density) { 14.dp.toPx() }
    val paddleBottomInset = with(density) { 42.dp.toPx() }
    val ballRadius = with(density) { 9.dp.toPx() }

    var ballPosition by remember { mutableStateOf(Offset.Zero) }
    var ballVelocity by remember { mutableStateOf(Offset(280f, -320f)) }
    var ballLaunched by remember { mutableStateOf(false) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    var hasWon by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    val bricks = remember { mutableStateListOf<BrickState>() }

    fun centerPaddle() {
        if (areaSize == IntSize.Zero) return
        paddleX = ((areaSize.width.toFloat() - paddleWidthPx) / 2f).coerceAtLeast(0f)
    }

    fun attachBallToPaddle() {
        if (areaSize == IntSize.Zero) return
        val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
        ballPosition = Offset(paddleX + paddleWidthPx / 2f, paddleTop - ballRadius - 4f)
        ballVelocity = ballVelocity.copy(y = -abs(ballVelocity.y))
    }

    fun rebuildBricks() {
        if (areaSize == IntSize.Zero) return
        bricks.clear()
        val padding = 8.dp.value
        val rows = 5
        val cols = 8
        val brickHeight = 20.dp.value
        val startY = 36.dp.value
        val totalPaddingX = padding * (cols + 1)
        val brickWidth = max((areaSize.width - totalPaddingX) / cols, 24f)

        repeat(rows) { row ->
            repeat(cols) { col ->
                val left = padding + col * (brickWidth + padding)
                val top = startY + row * (brickHeight + padding)
                val rect = Rect(left, top, left + brickWidth, top + brickHeight)
                val colorStep = 0.08f * row
                val color = colorScheme.tertiary.copy(alpha = 0.85f - colorStep)
                bricks.add(
                    BrickState(
                        rect = rect,
                        hitsRemaining = 1,
                        color = color
                    )
                )
            }
        }
    }

    fun resetGame() {
        hasWon = false
        isGameOver = false
        lives = 3
        score = 0
        ballVelocity = Offset(280f, -340f)
        centerPaddle()
        attachBallToPaddle()
        rebuildBricks()
    }

    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero) {
            resetGame()
        }
    }

    LaunchedEffect(areaSize, ballLaunched, lives, hasWon, isGameOver) {
        if (areaSize == IntSize.Zero) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val deltaTime = (frameNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameNanos

            if (ballLaunched && !isGameOver && !hasWon) {
                var nextPos = ballPosition + ballVelocity * deltaTime
                var nextVelocity = ballVelocity

                // Walls
                if (nextPos.x - ballRadius <= 0) {
                    nextPos = nextPos.copy(x = ballRadius)
                    nextVelocity = nextVelocity.copy(x = abs(nextVelocity.x))
                }
                if (nextPos.x + ballRadius >= areaSize.width) {
                    nextPos = nextPos.copy(x = areaSize.width - ballRadius)
                    nextVelocity = nextVelocity.copy(x = -abs(nextVelocity.x))
                }
                if (nextPos.y - ballRadius <= 0) {
                    nextPos = nextPos.copy(y = ballRadius)
                    nextVelocity = nextVelocity.copy(y = abs(nextVelocity.y))
                }

                val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
                val paddleRect = Rect(
                    paddleX,
                    paddleTop,
                    paddleX + paddleWidthPx,
                    paddleTop + paddleHeightPx
                )

                // Paddle collision
                if (nextVelocity.y > 0 && circleIntersectsRect(nextPos, ballRadius, paddleRect)) {
                    val influence = ((nextPos.x - paddleRect.center.x) / (paddleWidthPx / 2f)).coerceIn(-1f, 1f)
                    val newX = (max(220f, abs(nextVelocity.x)) * influence)
                    nextVelocity = Offset(newX, -abs(nextVelocity.y)).copy(y = -abs(nextVelocity.y))
                    nextPos = nextPos.copy(y = paddleRect.top - ballRadius - 1f)
                }

                // Brick collisions
                var hitIndex = -1
                var shouldInvertX = false
                var shouldInvertY = false
                bricks.forEachIndexed { index, brick ->
                    if (hitIndex != -1 || brick.hitsRemaining <= 0) return@forEachIndexed
                    if (circleIntersectsRect(nextPos, ballRadius, brick.rect)) {
                        hitIndex = index
                        val dx = min(abs(nextPos.x - brick.rect.left), abs(nextPos.x - brick.rect.right))
                        val dy = min(abs(nextPos.y - brick.rect.top), abs(nextPos.y - brick.rect.bottom))
                        shouldInvertX = dx < dy
                        shouldInvertY = dy <= dx
                    }
                }

                if (hitIndex != -1) {
                    val brick = bricks[hitIndex]
                    bricks[hitIndex] = brick.copy(hitsRemaining = brick.hitsRemaining - 1)
                    score += 50
                    val remaining = bricks.count { it.hitsRemaining > 0 }
                    if (remaining == 0) {
                        hasWon = true
                        ballLaunched = false
                    }

                    if (shouldInvertX) {
                        nextVelocity = nextVelocity.copy(x = -nextVelocity.x)
                    }
                    if (shouldInvertY) {
                        nextVelocity = nextVelocity.copy(y = -nextVelocity.y)
                    }
                    nextVelocity = nextVelocity * 1.02f
                }

                // Bottom collision / life loss
                if (nextPos.y - ballRadius > areaSize.height) {
                    lives -= 1
                    if (lives <= 0) {
                        isGameOver = true
                        ballLaunched = false
                    } else {
                        ballLaunched = false
                        attachBallToPaddle()
                    }
                } else {
                    ballPosition = nextPos
                    ballVelocity = nextVelocity
                }
            }

        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = surface.copy(alpha = 0.96f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surface)
                .padding(horizontal = 12.dp)
        ) {
            // Close button
            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp)
                    .padding(WindowInsets.statusBars.asPaddingValues()),
                onClick = onClose,
                shape = RoundedStarShape(sides = 7, curve = 0.12, rotation = 6f),
                containerColor = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.onSurface
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close easter egg")
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp)
                    .padding(WindowInsets.statusBars.asPaddingValues()),
                horizontalAlignment = Alignment.Start
            ) {
                ElevatedAssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(text = "PixelPlay Brick Breaker") },
                    shape = RoundedStarShape(sides = 6, curve = 0.14),
                    colors = androidx.compose.material3.AssistChipDefaults.elevatedAssistChipColors(
                        disabledContainerColor = colorScheme.secondaryContainer,
                        disabledLabelColor = colorScheme.onSecondaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatPill(label = "Score", value = score.toString(), color = accent)
                    StatPill(label = "Lives", value = lives.toString(), color = colorScheme.error)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp)
                    .pointerInput(paddleWidthPx, areaSize) {
                        detectDragGestures(
                            onDragStart = {
                                if (!ballLaunched && !isGameOver && !hasWon) {
                                    ballLaunched = true
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (areaSize != IntSize.Zero) {
                                    val newX = (paddleX + dragAmount.x)
                                        .coerceIn(0f, areaSize.width - paddleWidthPx)
                                    paddleX = newX
                                    if (!ballLaunched) {
                                        attachBallToPaddle()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(hasWon, isGameOver, ballLaunched) {
                        detectTapGestures(
                            onTap = {
                                when {
                                    isGameOver || hasWon -> resetGame()
                                    !ballLaunched -> ballLaunched = true
                                }
                            }
                        )
                    },
                onDraw = {
                    val newArea = size.toSize().toIntSize()
                    if (areaSize != newArea) {
                        areaSize = newArea
                    }

                    val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
                    val paddleRect = Rect(
                        paddleX,
                        paddleTop,
                        paddleX + paddleWidthPx,
                        paddleTop + paddleHeightPx
                    )

                    // Background area
                    drawRoundRect(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                    )

                    // Bricks
                    bricks.forEach { brick ->
                        if (brick.hitsRemaining > 0) {
                            drawRoundRect(
                                color = brick.color,
                                topLeft = brick.rect.topLeft,
                                size = brick.rect.size,
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                            )
                        }
                    }

                    // Paddle
                    drawRoundRect(
                        color = colorScheme.primaryContainer,
                        topLeft = paddleRect.topLeft,
                        size = paddleRect.size,
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )

                    // Ball
                    drawCircle(
                        color = colorScheme.onPrimaryContainer,
                        radius = ballRadius,
                        center = ballPosition
                    )
                }
            )

            if (isGameOver || hasWon) {
                val message = if (hasWon) "You Win" else "Game Over"
                val caption = if (hasWon) "Tap to level up" else "Tap to retry"
                GameMessage(
                    modifier = Modifier.align(Alignment.Center),
                    title = message,
                    subtitle = caption,
                    accent = accent
                )
            }
        }
    }
}

@Composable
private fun GameMessage(modifier: Modifier = Modifier, title: String, subtitle: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedStarShape(sides = 6, curve = 0.12),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = accent)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        tonalElevation = 6.dp,
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.material3.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = label.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun circleIntersectsRect(center: Offset, radius: Float, rect: Rect): Boolean {
    val closestX = center.x.coerceIn(rect.left, rect.right)
    val closestY = center.y.coerceIn(rect.top, rect.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}

private operator fun Offset.times(scalar: Float) = Offset(x * scalar, y * scalar)

private fun androidx.compose.ui.geometry.Size.toIntSize(): IntSize = IntSize(width.toInt(), height.toInt())
