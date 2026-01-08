package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max

@Composable
fun ImageCropView(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    pan: Offset = Offset.Zero, // Normalized (relative to Viewport Width/Height)
    enabled: Boolean = true,
    onCrop: (Float, Offset) -> Unit // scale, normalizedPan
) {
    val density = LocalDensity.current
    
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f) // Enforce square container
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        
        // Image Dimensions
        val bitmapW = imageBitmap.width.toFloat()
        val bitmapH = imageBitmap.height.toFloat()
        val bitmapRatio = if(bitmapH > 0) bitmapW / bitmapH else 1f
        val viewportRatio = if (viewportHeight > 0) viewportWidth / viewportHeight else 1f
        
        // Calculate Base Dimensions (Zoom = 1.0f)
        // The image should COVER the viewport completely minimalistically.
        val (baseW, baseH) = if (bitmapRatio > viewportRatio) {
            // Image is wider than viewport: Height constrains
            val h = viewportHeight
            val w = h * bitmapRatio
            w to h
        } else {
            // Image is taller than viewport: Width constrains
            val w = viewportWidth
            val h = w / bitmapRatio
            w to h
        }
        
        // Current Dimensions
        val currentW = baseW * scale
        val currentH = baseH * scale
        
        // Calculate Pan Limits (Center is 0,0 relative to Viewport center)
        // If image is larger than viewport, we can pan.
        // Limit is: (ContentSize - ViewportSize) / 2
        val maxPanX = max(0f, (currentW - viewportWidth) / 2f)
        val maxPanY = max(0f, (currentH - viewportHeight) / 2f)
        
        // Current Pan in Pixels
        // pan input is normalized: panX * viewportWidth
        val currentPanX = pan.x * viewportWidth
        val currentPanY = pan.y * viewportHeight
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(baseW, baseH, viewportWidth, viewportHeight) {
                            detectTransformGestures { _, panDelta, zoom, _ ->
                                // 1. Update Scale
                                val newScale = (scale * zoom).coerceAtLeast(1f).coerceAtMost(5f)
                                
                                // 2. Update Pan
                                // We need to re-calculate limits based on new scale
                                val newW = baseW * newScale
                                val newH = baseH * newScale
                                val newMaxPanX = max(0f, (newW - viewportWidth) / 2f)
                                val newMaxPanY = max(0f, (newH - viewportHeight) / 2f)
                                
                                // Apply delta to current pixels
                                // Note: Pan delta implies translation.
                                val newPanPxX = (currentPanX + panDelta.x).coerceIn(-newMaxPanX, newMaxPanX)
                                val newPanPxY = (currentPanY + panDelta.y).coerceIn(-newMaxPanY, newMaxPanY)
                                
                                // Normalize
                                val normX = if(viewportWidth > 0) newPanPxX / viewportWidth else 0f
                                val normY = if(viewportHeight > 0) newPanPxY / viewportHeight else 0f
                                
                                onCrop(newScale, Offset(normX, normY))
                            }
                        }
                    } else Modifier
                )
        ) {
            // Render Image at Base Size, centered, then transform.
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .requiredSize(
                        width = with(density) { baseW.toDp() },
                        height = with(density) { baseH.toDp() }
                    )
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = currentPanX
                        translationY = currentPanY
                    }
            )
        }
    }
}
