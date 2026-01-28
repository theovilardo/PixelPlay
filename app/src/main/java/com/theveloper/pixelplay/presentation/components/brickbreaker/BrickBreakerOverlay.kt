package com.theveloper.pixelplay.presentation.components.brickbreaker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
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
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
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

private data class Particle(
    val position: Offset,
    val velocity: Offset,
    val color: Color,
    val radius: Float,
    val alpha: Float = 1f,
    val life: Float = 1.0f // 1.0 to 0.0
)

// --- Composable ---

@Composable
fun BrickBreakerOverlay(
    modifier: Modifier = Modifier,
    isMiniPlayerVisible: Boolean = false,
    onPlayRandom: () -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current

    // Game Physics Constants
    val baseBallVelocity = 800f

    // Game State
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var paddleX by remember { mutableFloatStateOf(0f) }

    // Dynamic Difficulty Props
    var level by remember { mutableIntStateOf(1) }
    var currentSpeedMult by remember { mutableFloatStateOf(1f) }
    var paddleWidthPx by remember { mutableFloatStateOf(0f) }

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
    val particles = remember { mutableStateListOf<Particle>() }

    // Initialization of dimensions
    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero && paddleWidthPx == 0f) {
            paddleWidthPx = areaSize.width * 0.25f
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
        val speed = baseBallVelocity * currentSpeedMult
        val randomX = Random.nextDouble(-0.5, 0.5).toFloat() * speed
        ballVelocity = Offset(randomX, -speed)
    }

    fun spawnParticles(rect: Rect, color: Color, count: Int = 8) {
        val centerX = rect.center.x
        val centerY = rect.center.y
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 300f + 100f
            val rad = Math.toRadians(angle.toDouble())
            val vx = (Math.cos(rad) * speed).toFloat()
            val vy = (Math.sin(rad) * speed).toFloat()
            
            particles.add(
                Particle(
                    position = Offset(
                        centerX + Random.nextFloat() * rect.width * 0.5f - rect.width * 0.25f,
                        centerY + Random.nextFloat() * rect.height * 0.5f - rect.height * 0.25f
                    ),
                    velocity = Offset(vx, vy),
                    color = color,
                    radius = Random.nextFloat() * 8f + 4f
                )
            )
        }
    }

    fun generateLevel(lvl: Int) {
        if (areaSize == IntSize.Zero) return
        bricks.clear()
        particles.clear()

        // Difficulty scaling
        currentSpeedMult = 1f + (lvl - 1) * 0.1f
        val widthFactor = (0.25f - (lvl * 0.02f)).coerceAtLeast(0.10f)
        paddleWidthPx = areaSize.width * widthFactor

        val padding = 8.dp.value * density.density
        val topOffset = 20.dp.value * density.density
        val rows = min(5 + lvl, 10)
        val cols = 8
        val brickHeight = 24.dp.value * density.density
        val totalPaddingX = padding * (cols + 1)
        val brickWidth = max((areaSize.width - totalPaddingX) / cols, 10f)

        // Generate grid
        for (row in 0 until rows) {
            for (col in 0 until cols / 2) {
                val shouldSkip = Random.nextFloat() < (0.1f * (lvl * 0.5f)).coerceAtMost(0.3f)
                val isHard = Random.nextFloat() < (0.1f * lvl).coerceAtMost(0.4f)
                val isSolid = lvl > 2 && Random.nextFloat() < 0.05f && row > 1

                if (!shouldSkip) {
                    val type = when {
                        isSolid -> BrickType.Solid
                        isHard -> BrickType.Hard
                        else -> BrickType.Normal
                    }

                    val hits = if (type == BrickType.Hard) 2 else 1

                    // Distinct Material 3 Colors
                    val baseColor = when(type) {
                        BrickType.Solid -> colorScheme.outlineVariant
                        BrickType.Hard -> colorScheme.secondary
                        else -> colorScheme.primaryContainer
                    }

                    // Left Side Brick
                    val leftX = padding + col * (brickWidth + padding)
                    val topY = topOffset + row * (brickHeight + padding)

                    bricks.add(BrickState(
                        rect = Rect(leftX, topY, leftX + brickWidth, topY + brickHeight),
                        hitsRemaining = hits,
                        maxHits = hits,
                        type = type,
                        color = baseColor
                    ))

                    // Right Side Mirror Brick
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
        particles.clear()

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

    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero) {
            if (!isInitialized) {
                resetGame(true)
                isInitialized = true
            } else {
                // Resize logic: update paddle constraints without resetting game progress
                val widthFactor = (0.25f - (level * 0.02f)).coerceAtLeast(0.10f)
                paddleWidthPx = areaSize.width * widthFactor
                paddleX = paddleX.coerceIn(0f, areaSize.width - paddleWidthPx)
                
                if (!ballLaunched && !isGameOver && !hasWon) {
                    attachBallToPaddle()
                }
            }
        }
    }

    // Game Loop
    LaunchedEffect(ballLaunched, lives, hasWon, isGameOver, areaSize) {
        if (areaSize == IntSize.Zero || isGameOver || hasWon) return@LaunchedEffect
        
        // Loop runs even if ball not launched to update particles
        var lastFrameNanos = withFrameNanos { it }

        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val deltaTime = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.04f)
            lastFrameNanos = frameNanos

            // --- Update Particles ---
            val particleIterator = particles.listIterator()
            while (particleIterator.hasNext()) {
                val p = particleIterator.next()
                if (p.life <= 0) {
                    particleIterator.remove()
                } else {
                    val newLife = p.life - deltaTime * 1.5f // Fade out speed
                    val grav = 800f // Gravity
                    val newVy = p.velocity.y + grav * deltaTime
                    val newPos = p.position + Offset(p.velocity.x * deltaTime, newVy * deltaTime)
                    particleIterator.set(p.copy(position = newPos, velocity = Offset(p.velocity.x, newVy), life = newLife))
                }
            }

            if (!ballLaunched) continue

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
                val hitPoint = ((nextPos.x - paddleRect.center.x) / (paddleWidthPx / 2f)).coerceIn(-1f, 1f)
                val currentSpeed = nextVelocity.getDistance()
                val newSpeed = (currentSpeed * 1.03f).coerceAtMost(baseBallVelocity * 3f)
                val deflectionFactor = 0.6f
                val newVx = newSpeed * hitPoint * deflectionFactor
                val vyComponent = -kotlin.math.sqrt(max(0f, (newSpeed * newSpeed) - (newVx * newVx)))

                nextVelocity = Offset(newVx, vyComponent)
                nextPos = nextPos.copy(y = paddleRect.top - ballRadius - 1f)
                
                // Haptic on paddle hit
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }

            // --- Brick Collisions ---
            val bricksIterator = bricks.listIterator()

            while (bricksIterator.hasNext()) {
                val index = bricksIterator.nextIndex()
                val brick = bricksIterator.next()

                if (brick.hitsRemaining <= 0 && brick.type != BrickType.Solid) continue

                if (circleIntersectsRect(nextPos, ballRadius, brick.rect)) {
                    // Collision Logic
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

                    // Haptic on impact
                   view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)

                    if (brick.type != BrickType.Solid) {
                        val newHits = brick.hitsRemaining - 1
                        
                        // Spawn particles
                        spawnParticles(brick.rect, brick.color)

                        if (newHits > 0) {
                            // Update brick color for visual damage (e.g., secondary -> tertiary)
                             val newColor = if (newHits == 1) colorScheme.tertiary else brick.color
                            bricks[index] = brick.copy(hitsRemaining = newHits, color = newColor)
                        } else {
                            bricks[index] = brick.copy(hitsRemaining = newHits) // Will be skipped next frame
                            score += if (brick.type == BrickType.Hard) 100 else 50
                            
                             // Stronger haptic on break
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                        
                        nextVelocity = nextVelocity * 1.015f

                        if (bricks.none { it.type != BrickType.Solid && it.hitsRemaining > 0 }) {
                            hasWon = true
                            ballLaunched = false
                        }
                    } else {
                        // Solid hit particles (grey)
                        spawnParticles(brick.rect, Color.Gray, 3)
                    }
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
            modifier = Modifier.fillMaxSize()
        ) {
            // ... (Header and Stats logic remains similar) ...
            // 1. Header
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
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp)) // Uniform radius
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
                    
                    // Draw Particles
                    particles.forEach { p ->
                        drawCircle(
                            color = p.color.copy(alpha = p.alpha * p.life),
                            radius = p.radius * p.life,
                            center = p.position
                        )
                    }

                    // Draw Paddle (Flat, Pill Shape)
                    drawRoundRect(
                        color = colorScheme.primary,
                        topLeft = paddleRect.topLeft,
                        size = paddleRect.size,
                        cornerRadius = CornerRadius(paddleRect.height / 2, paddleRect.height / 2)
                    )

                    // Draw Ball
                    drawCircle(
                        color = colorScheme.onSurface,
                        radius = ballRadius,
                        center = ballPosition
                    )
                }

                // ... (Overlays remain same) ...
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
            
            // Dynamic Bottom Section
             if (isMiniPlayerVisible) {
                Spacer(Modifier.height(MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .height(MiniPlayerHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.primaryContainer)
                        .clickable { onPlayRandom() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play Random Music",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
    val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

    // Solid Flat Color
    drawRoundRect(
        color = brick.color,
        topLeft = brick.rect.topLeft,
        size = brick.rect.size,
        cornerRadius = cornerRadius
    )

    if (brick.type == BrickType.Solid) {
        // Simple subtle logic for solid bricks to distinguish them
         drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = 4.dp.toPx(),
            center = brick.rect.center
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