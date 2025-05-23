package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

// Enhanced floating bottom navigation bar with animations
// Enhanced floating bottom navigation bar with ripple effect animations
//@Composable
//fun FloatingNavigationBar(
//    modifier: Modifier = Modifier,
//    navItems: List<BottomNavItem>,
//    currentRoute: String?,
//    onItemClick: (BottomNavItem) -> Unit,
//    isScrolling: Boolean
//) {
//    // Animation for nav bar height when scrolling
//    val barHeight by animateDpAsState(
//        targetValue = if (isScrolling) 64.dp else 90.dp,
//        animationSpec = tween(300, easing = FastOutSlowInEasing),
//        label = "barHeight"
//    )
//
//    // Animation for nav bar width when scrolling
//    val widthFraction by animateFloatAsState(
//        targetValue = if (isScrolling) 0.6f else 0.9f,
//        animationSpec = tween(300, easing = FastOutSlowInEasing),
//        label = "navBarWidth"
//    )
//
//    // Track the previously selected item to create ripple effect
//    var previousSelectedIndex by remember { mutableStateOf<Int?>(null) }
//    var currentSelectedIndex by remember { mutableStateOf<Int?>(null) }
//
//    // Find the currently selected index
//    LaunchedEffect(currentRoute) {
//        currentSelectedIndex = navItems.indexOfFirst { it.screen.route == currentRoute }
//        if (previousSelectedIndex != currentSelectedIndex && previousSelectedIndex != null) {
//            // Only trigger animation when there's a real change
//            delay(300) // Allow time for other animations
//            previousSelectedIndex = currentSelectedIndex
//        } else if (previousSelectedIndex == null) {
//            previousSelectedIndex = currentSelectedIndex
//        }
//    }
//
//    Box(
//        modifier = modifier
//            .fillMaxWidth(),
//        contentAlignment = Alignment.BottomCenter
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth(widthFraction)
//                .wrapContentHeight()
//                .shadow(
//                    elevation = 12.dp,
//                    shape = RoundedCornerShape(28.dp),
//                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
//                ),
//            shape = RoundedCornerShape(28.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.surfaceVariant
//            )
//        ) {
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .align(Alignment.CenterHorizontally)
//                    .padding(horizontal = 8.dp, vertical = 14.dp),
//                horizontalArrangement = Arrangement.SpaceEvenly,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                navItems.forEachIndexed { index, item ->
//                    val selected = currentRoute == item.screen.route
//
//                    // Ripple effect logic
//                    val isAffectedByRipple =
//                        (currentSelectedIndex != null &&
//                            previousSelectedIndex != null &&
//                            previousSelectedIndex != currentSelectedIndex &&
//                            (index == currentSelectedIndex!! - 1 || index == currentSelectedIndex!! + 1))
//
//                    // Horizontal shift animation based on ripple effect
//                    val offsetX by animateFloatAsState(
//                        targetValue = when {
//                            isAffectedByRipple && index < currentSelectedIndex!! -> -12f // Left item shifts left
//                            isAffectedByRipple && index > currentSelectedIndex!! -> 12f  // Right item shifts right
//                            else -> 0f // Selected item or unaffected items stay in place
//                        },
//                        animationSpec = spring(
//                            dampingRatio = 0.5f,
//                            stiffness = Spring.StiffnessLow
//                        ),
//                        label = "offsetX"
//                    )
//
//                    // Scale animation
//                    val scale by animateFloatAsState(
//                        targetValue = when {
//                            selected -> 1.15f
//                            isAffectedByRipple -> 0.85f
//                            else -> 1f
//                        },
//                        animationSpec = spring(
//                            dampingRatio = 0.5f,
//                            stiffness = Spring.StiffnessLow
//                        ),
//                        label = "scale"
//                    )
//
//                    // Background indicator animation
//                    val bgAlpha by animateFloatAsState(
//                        targetValue = if (selected) 0.12f else 0f,
//                        animationSpec = tween(300),
//                        label = "bgAlpha"
//                    )
//
//                    // Text visibility animation
//                    val textAlpha by animateFloatAsState(
//                        targetValue = if (isScrolling) 0f else 1f,
//                        animationSpec = tween(200),
//                        label = "textAlpha"
//                    )
//
//                    Column(
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        modifier = Modifier
//                            .weight(1f)
//                            .offset { IntOffset(offsetX.roundToInt(), 0) }
//                            //.scale(scale)
//                            .clickable(
//                                interactionSource = remember { MutableInteractionSource() },
//                                indication = ripple(bounded = false, radius = 24.dp), // Usar ripple() de material3
//                                onClick = {
//                                    previousSelectedIndex = currentSelectedIndex
//                                    onItemClick(item)
//                                }
//                            )
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .size(78.dp)
//                                //.padding(4.dp)
//                            ,
//                            contentAlignment = Alignment.Center
//                        ) {
//                            // Background indicator for selected item
//                            Box(
//                                modifier = Modifier
//                                    //.size(40.dp)
//                                    .aspectRatio(2f/1f)
//                                    .background(
//                                        MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha),
//                                        AbsoluteSmoothCornerShape(
//                                            cornerRadiusTL = 10.dp,
//                                            smoothnessAsPercentTL = 60,
//                                            cornerRadiusTR = 10.dp,
//                                            smoothnessAsPercentTR = 60,
//                                            cornerRadiusBL = 10.dp,
//                                            smoothnessAsPercentBL = 60,
//                                            cornerRadiusBR = 10.dp,
//                                            smoothnessAsPercentBR = 60
//                                        )
//                                    )
//                            )
//
//                            Icon(
//                                painter = painteritem.selectedIconResId.takeIf { selected && it != null } ?: item.iconResId,
//                                contentDescription = item.label,
//                                modifier = Modifier
//                                    .size(24.dp)
//                                    .padding(4.dp),
//                                tint = if (selected)
//                                    MaterialTheme.colorScheme.primary
//                                else
//                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                            )
//                        }
//
//                        if (textAlpha > 0f) {
//                            Text(
//                                text = item.label,
//                                style = MaterialTheme.typography.labelSmall,
//                                color = if (selected)
//                                    MaterialTheme.colorScheme.primary
//                                else
//                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
//                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
//                                maxLines = 1,
//                                modifier = Modifier.alpha(textAlpha)
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//}

//@Composable
//fun FloatingNavigationBar(
//    navItems: List<BottomNavItem>,
//    currentRoute: String?,
//    onItemClick: (BottomNavItem) -> Unit,
//    isScrolling: Boolean
//) {
//    val translationAnim by animateFloatAsState(
//        targetValue = if (isScrolling) 16f else 0f,
//        label = "navBarTranslation"
//    )
//
//    val widthFraction by animateFloatAsState(
//        targetValue = if (isScrolling) 0.6f else 0.9f,
//        label = "navBarWidth"
//    )
//
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(bottom = 16.dp),
//        contentAlignment = Alignment.BottomCenter
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth(widthFraction)
//                .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp)),
//            shape = RoundedCornerShape(28.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.surfaceVariant
//            )
//        ) {
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 8.dp, vertical = 12.dp),
//                horizontalArrangement = Arrangement.SpaceEvenly,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                navItems.forEach { item ->
//                    val selected = currentRoute == item.screen.route
//                    val scale by animateFloatAsState(
//                        targetValue = if (selected) 1.2f else 1f,
//                        label = "iconScale"
//                    )
//
//                    Column(
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        modifier = Modifier
//                            .weight(1f)
//                            .clickable { onItemClick(item) }
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .size(48.dp)
//                                .padding(4.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            // Background indicator for selected item
//                            if (selected) {
//                                Box(
//                                    modifier = Modifier
//                                        .size(40.dp)
//                                        .background(
//                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
//                                            CircleShape
//                                        )
//                                )
//                            }
//
//                            Icon(
//                                painter = item.selectedIcon.takeIf { selected && it != null } ?: item.icon,
//                                contentDescription = item.label,
//                                modifier = Modifier.scale(scale),
//                                tint = if (selected)
//                                    MaterialTheme.colorScheme.primary
//                                else
//                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                            )
//                        }
//
//                        AnimatedVisibility(
//                            visible = !isScrolling,
//                            enter = fadeIn() + slideInVertically(),
//                            exit = fadeOut() + slideOutVertically()
//                        ) {
//                            Text(
//                                text = item.label,
//                                style = MaterialTheme.typography.labelSmall,
//                                color = if (selected)
//                                    MaterialTheme.colorScheme.primary
//                                else
//                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
//                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
//                                maxLines = 1
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//}