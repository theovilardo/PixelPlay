package com.theveloper.pixelplay.presentation.components.brickbreaker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// --- Data Models ---

enum class BrickType {
    Normal,
    Hard,
    Solid // Unbreakable
}

private data class BrickState(
    val rect: Rect,
    val hitsRemaining: Int,
    val type: BrickType,
    val maxHits: Int,
    val color: Color
)

// --- Composable ---

@Composable
fun BrickBreakerOverlay(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // Game Physics Constants (Scaled by Density usually, but here dynamic)
    // Aumentamos la velocidad base significativamente (antes 380f)
    val baseBallVelocity = 500f

    // Game State
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var paddleX by remember { mutableFloatStateOf(0f) }

    // Dynamic Difficulty Props
    var level by remember { mutableIntStateOf(1) }
    var currentSpeedMult by remember { mutableFloatStateOf(1f) }
    var paddleWidthPx by remember { mutableFloatStateOf(0f) } // Will be set based on screen

    val paddleHeightPx = with(density) { 16.dp.toPx() }
    val paddleBottomInset = with(density) { 32.dp.toPx() }
    val ballRadius = with(density) { 10.dp.toPx() }

    var ballPosition by remember { mutableStateOf(Offset.Zero) }
    var ballVelocity by remember { mutableStateOf(Offset(baseBallVelocity, -baseBallVelocity)) }
    var ballLaunched by remember { mutableStateOf(false) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    var hasWon by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    val bricks = remember { mutableStateListOf<BrickState>() }

    // Initialization of dimensions
    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero && paddleWidthPx == 0f) {
            paddleWidthPx = areaSize.width * 0.25f // Paddle is 25% of screen width initially
        }
    }

    fun centerPaddle() {
        if (areaSize == IntSize.Zero) return
        paddleX = ((areaSize.width.toFloat() - paddleWidthPx) / 2f).coerceAtLeast(0f)
    }

    fun attachBallToPaddle() {
        if (areaSize == IntSize.Zero) return
        val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
        ballPosition = Offset(paddleX + paddleWidthPx / 2f, paddleTop - ballRadius - 4f)
        // Reset vertical direction but keep horizontal momentum feels weird, so strict reset:
        val speed = baseBallVelocity * currentSpeedMult
        // Launch at a slight random angle to prevent boring straight vertical loops
        val randomX = Random.nextDouble(-0.5, 0.5).toFloat() * speed
        ballVelocity = Offset(randomX, -speed)
    }

    fun generateLevel(lvl: Int) {
        if (areaSize == IntSize.Zero) return
        bricks.clear()

        // Difficulty scaling
        // Cada nivel aumenta la velocidad base un 10%
        currentSpeedMult = 1f + (lvl - 1) * 0.1f
        val widthFactor = (0.25f - (lvl * 0.02f)).coerceAtLeast(0.10f) // Shrink paddle
        paddleWidthPx = areaSize.width * widthFactor

        val padding = 8.dp.value * density.density
        val topOffset = 20.dp.value * density.density
        val rows = min(5 + lvl, 10) // More rows on higher levels
        val cols = 8 // Keep even for symmetry
        val brickHeight = 24.dp.value * density.density
        val totalPaddingX = padding * (cols + 1)
        val brickWidth = max((areaSize.width - totalPaddingX) / cols, 10f)

        // Generate grid
        for (row in 0 until rows) {
            // Symmetry logic: Only decide for half the columns, then mirror
            for (col in 0 until cols / 2) {

                // Randomness factors
                val shouldSkip = Random.nextFloat() < (0.1f * (lvl * 0.5f)).coerceAtMost(0.3f) // Holes in grid
                val isHard = Random.nextFloat() < (0.1f * lvl).coerceAtMost(0.4f)
                val isSolid = lvl > 2 && Random.nextFloat() < 0.05f && row > 1 // Rare solid blocks, not at bottom

                if (!shouldSkip) {
                    val type = when {
                        isSolid -> BrickType.Solid
                        isHard -> BrickType.Hard
                        else -> BrickType.Normal
                    }

                    val hits = if (type == BrickType.Hard) 2 else 1

                    // Colors
                    val baseColor = when(type) {
                        BrickType.Solid -> Color(0xFF555555)
                        BrickType.Hard -> colorScheme.error
                        else -> {
                            // Gradient from top to bottom based on Tertiary
                            val hueShift = row * 0.1f
                            colorScheme.tertiary.copy(alpha = 1f - hueShift.coerceAtMost(0.5f))
                        }
                    }

                    // Create Left Side Brick
                    val leftX = padding + col * (brickWidth + padding)
                    val topY = topOffset + row * (brickHeight + padding)

                    bricks.add(BrickState(
                        rect = Rect(leftX, topY, leftX + brickWidth, topY + brickHeight),
                        hitsRemaining = hits,
                        maxHits = hits,
                        type = type,
                        color = baseColor
                    ))

                    // Create Right Side Mirror Brick
                    val mirrorCol = cols - 1 - col
                    val mirrorLeftX = padding + mirrorCol * (brickWidth + padding)

                    bricks.add(BrickState(
                        rect = Rect(mirrorLeftX, topY, mirrorLeftX + brickWidth, topY + brickHeight),
                        hitsRemaining = hits,
                        maxHits = hits,
                        type = type,
                        color = baseColor
                    ))
                }
            }
        }
    }

    fun resetGame(fullReset: Boolean = false) {
        isGameOver = false
        hasWon = false
        ballLaunched = false

        if (fullReset) {
            score = 0
            lives = 3
            level = 1
        }

        generateLevel(level)
        centerPaddle()
        attachBallToPaddle()
    }

    fun nextLevel() {
        level++
        ballLaunched = false
        hasWon = false
        generateLevel(level)
        centerPaddle()
        attachBallToPaddle()
    }

    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero) {
            resetGame(true)
        }
    }

    // Game Loop
    LaunchedEffect(ballLaunched, lives, hasWon, isGameOver, areaSize) {
        if (areaSize == IntSize.Zero || !ballLaunched || isGameOver || hasWon) return@LaunchedEffect

        var lastFrameNanos = withFrameNanos { it }

        while (isActive) {
            val frameNanos = withFrameNanos { it }
            // Cap delta time to prevent tunneling on lag spikes
            val deltaTime = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.04f)
            lastFrameNanos = frameNanos

            var nextPos = ballPosition + ballVelocity * deltaTime
            var nextVelocity = ballVelocity

            // --- Wall Collisions ---
            // Left
            if (nextPos.x - ballRadius <= 0) {
                nextPos = nextPos.copy(x = ballRadius)
                nextVelocity = nextVelocity.copy(x = abs(nextVelocity.x))
            }
            // Right
            if (nextPos.x + ballRadius >= areaSize.width) {
                nextPos = nextPos.copy(x = areaSize.width - ballRadius)
                nextVelocity = nextVelocity.copy(x = -abs(nextVelocity.x))
            }
            // Top
            if (nextPos.y - ballRadius <= 0) {
                nextPos = nextPos.copy(y = ballRadius)
                nextVelocity = nextVelocity.copy(y = abs(nextVelocity.y))
            }

            // --- Paddle Collision ---
            val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
            val paddleRect = Rect(paddleX, paddleTop, paddleX + paddleWidthPx, paddleTop + paddleHeightPx)

            if (nextVelocity.y > 0 && circleIntersectsRect(nextPos, ballRadius, paddleRect)) {
                // Calculate impact point (-1 left, 0 center, 1 right)
                val hitPoint = ((nextPos.x - paddleRect.center.x) / (paddleWidthPx / 2f)).coerceIn(-1f, 1f)

                // Add English/Spin to the ball based on where it hit
                val currentSpeed = nextVelocity.getDistance()
                // Slightly increase speed on paddle hit to keep game moving (ahora 3% en vez de 2%)
                val newSpeed = (currentSpeed * 1.03f).coerceAtMost(baseBallVelocity * 3f)

                // Deflect angle (Max 60 degrees)
                val deflectionFactor = 0.6f
                val newVx = newSpeed * hitPoint * deflectionFactor
                // Ensure Vy is large enough to move up
                val vyComponent = -kotlin.math.sqrt(max(0f, (newSpeed * newSpeed) - (newVx * newVx)))

                nextVelocity = Offset(newVx, vyComponent)
                nextPos = nextPos.copy(y = paddleRect.top - ballRadius - 1f)
            }

            // --- Brick Collisions ---
            // Simple iteration - checks all bricks. Optimization: Spatial partition if > 100 bricks
            var hitOccurred = false

            // We use an iterator to safely modify hits inside loop if needed,
            // though here we just modify state
            val bricksIterator = bricks.listIterator()

            while (bricksIterator.hasNext()) {
                val index = bricksIterator.nextIndex()
                val brick = bricksIterator.next()

                if (brick.hitsRemaining <= 0 && brick.type != BrickType.Solid) continue

                if (circleIntersectsRect(nextPos, ballRadius, brick.rect)) {
                    hitOccurred = true

                    // Determine bounce direction
                    val overlapLeft = nextPos.x - brick.rect.left
                    val overlapRight = brick.rect.right - nextPos.x
                    val overlapTop = nextPos.y - brick.rect.top
                    val overlapBottom = brick.rect.bottom - nextPos.y

                    val minOverlapX = min(abs(overlapLeft), abs(overlapRight))
                    val minOverlapY = min(abs(overlapTop), abs(overlapBottom))

                    if (minOverlapX < minOverlapY) {
                        nextVelocity = nextVelocity.copy(x = -nextVelocity.x)
                    } else {
                        nextVelocity = nextVelocity.copy(y = -nextVelocity.y)
                    }

                    // Logic based on type
                    if (brick.type != BrickType.Solid) {
                        val newHits = brick.hitsRemaining - 1
                        bricks[index] = brick.copy(hitsRemaining = newHits)

                        score += if (brick.type == BrickType.Hard) 100 else 50

                        // Acelerar un poco la bola cada vez que se rompe algo para evitar que sea monótono
                        nextVelocity = nextVelocity * 1.015f

                        // Check Win Condition
                        if (bricks.none { it.type != BrickType.Solid && it.hitsRemaining > 0 }) {
                            hasWon = true
                            ballLaunched = false
                        }
                    } else {
                        // Solid brick hit sound?
                        // Add slight shake?
                    }

                    // Only process one brick collision per frame to prevent weird sticking
                    // Or process closest. Here 'break' simplifies it.
                    break
                }
            }

            // --- Death Condition ---
            if (nextPos.y - ballRadius > areaSize.height) {
                lives -= 1
                if (lives <= 0) {
                    isGameOver = true
                }
                ballLaunched = false
                attachBallToPaddle()
            } else {
                ballPosition = nextPos
                ballVelocity = nextVelocity
            }
        }
    }

    // --- UI Structure ---
    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 1. Header & Status Bar Padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surfaceContainer)
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "PixelPlay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // 2. Stats Dashboard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GameStat("SCORE", score.toString())
                    // Level Indicator
                    Surface(
                        color = colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "LVL $level",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = colorScheme.onPrimary
                        )
                    }
                    GameStat("LIVES", lives.toString(), isLives = true)
                }
            }

            // 3. Game Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes remaining space
                    .padding(16.dp)
                    .clip(RoundedCornerShape(
                        topStart = 24.dp,
                        topEnd = 24.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    ))
                    .background(colorScheme.surfaceContainerLow)
                    .onSizeChanged { areaSize = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!ballLaunched && !isGameOver && !hasWon) {
                                        ballLaunched = true
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (areaSize != IntSize.Zero) {
                                        paddleX = (paddleX + dragAmount.x)
                                            .coerceIn(0f, areaSize.width - paddleWidthPx)

                                        if (!ballLaunched) {
                                            attachBallToPaddle()
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (!ballLaunched && !isGameOver && !hasWon) {
                                        ballLaunched = true
                                    }
                                }
                            )
                        }
                ) {
                    val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
                    val paddleRect = Rect(paddleX, paddleTop, paddleX + paddleWidthPx, paddleTop + paddleHeightPx)

                    // Draw Bricks
                    bricks.forEach { brick ->
                        if (brick.hitsRemaining > 0 || brick.type == BrickType.Solid) {
                            drawBrick(brick)
                        }
                    }

                    // Draw Paddle (Capsule shape for modern look)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(colorScheme.primary, colorScheme.primaryContainer)
                        ),
                        topLeft = paddleRect.topLeft,
                        size = paddleRect.size,
                        cornerRadius = CornerRadius(paddleRect.height / 2, paddleRect.height / 2)
                    )

                    // Paddle Glow (Subtle)
                    drawRoundRect(
                        color = colorScheme.primary.copy(alpha = 0.3f),
                        topLeft = paddleRect.topLeft.copy(y = paddleRect.top + 4),
                        size = paddleRect.size,
                        cornerRadius = CornerRadius(paddleRect.height / 2, paddleRect.height / 2)
                    )

                    // Draw Ball
                    drawCircle(
                        color = colorScheme.onSurface,
                        radius = ballRadius,
                        center = ballPosition
                    )

                    // Simple Trail/Shadow for speed indication
                    if (ballLaunched) {
                        drawCircle(
                            color = colorScheme.onSurface.copy(alpha = 0.2f),
                            radius = ballRadius * 0.8f,
                            center = ballPosition - (ballVelocity * 0.03f)
                        )
                    }
                }

                // Game Over / Win Overlays
                if (isGameOver || hasWon) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.scrim.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val title = if (hasWon) "LEVEL COMPLETE!" else "GAME OVER"
                            val subtitle = if (hasWon) "Score: $score" else "Try Again?"
                            val icon = if (hasWon) Icons.Rounded.Star else Icons.Rounded.Refresh

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                            )

                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            androidx.compose.material3.Button(
                                onClick = {
                                    if (hasWon) nextLevel() else resetGame(true)
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primaryContainer,
                                    contentColor = colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(if (hasWon) "Next Level" else "Restart Game")
                            }
                        }
                    }
                } else if (!ballLaunched) {
                    // Tap to start hint
                    Text(
                        text = "TAP TO LAUNCH",
                        modifier = Modifier.align(Alignment.Center).padding(bottom = 100.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

// --- Helper Components & Extensions ---

@Composable
private fun GameStat(label: String, value: String, isLives: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLives) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun DrawScope.drawBrick(brick: BrickState) {
    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

    // Fill
    val alpha = if (brick.hitsRemaining < brick.maxHits && brick.type == BrickType.Hard) 0.5f else 1f

    if (brick.type == BrickType.Solid) {
        drawRoundRect(
            color = brick.color,
            topLeft = brick.rect.topLeft,
            size = brick.rect.size,
            cornerRadius = cornerRadius
        )
        // Add "bolt" or structure detail for solid
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = 3.dp.toPx(),
            center = brick.rect.center
        )
    } else {
        drawRoundRect(
            color = brick.color.copy(alpha = alpha),
            topLeft = brick.rect.topLeft,
            size = brick.rect.size,
            cornerRadius = cornerRadius
        )

        // Bevel/Highlight effect for 3D feel
        drawPath(
            path = Path().apply {
                moveTo(brick.rect.left, brick.rect.bottom)
                lineTo(brick.rect.left, brick.rect.top)
                lineTo(brick.rect.right, brick.rect.top)
            },
            color = Color.White.copy(alpha = 0.2f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun circleIntersectsRect(center: Offset, radius: Float, rect: Rect): Boolean {
    val closestX = center.x.coerceIn(rect.left, rect.right)
    val closestY = center.y.coerceIn(rect.top, rect.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}