package com.theveloper.pixelplay.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest

// Data class to hold information about each person in the acknowledgements section
data class Contributor(
    val name: String,
    val role: String? = null,
    val avatarUrl: String? = null,          // optional remote/avatar image
    @DrawableRes val iconRes: Int? = null,  // optional local icon fallback
    val githubUrl: String? = null,
    val telegramUrl: String? = null
)

@Composable
private fun AboutTopBar(
    collapseFraction: Float,
    headerHeight: Dp,
    onBackPressed: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val titleScale = lerp(1.2f, 0.8f, collapseFraction)
    val titlePaddingStart = lerp(32.dp, 58.dp, collapseFraction)
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(88.dp, 56.dp, collapseFraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(surfaceColor.copy(alpha = collapseFraction))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Back")
            }

            Box(
                modifier = Modifier
                    .align(animatedTitleAlignment)
                    .height(titleContainerHeight)
                    .fillMaxWidth()
                    .padding(start = titlePaddingStart, end = 24.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                        }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    onNavigationIconClick: () -> Unit
) {
    val context = LocalContext.current
    val versionName = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: Exception) {
        "N/A"
    }

    val authors = listOf(
        Contributor(name = "Theo Vilardo", githubUrl = "https://github.com/theovilardo", telegramUrl = "https://t.me/thevelopersupport", avatarUrl = "https://avatars.githubusercontent.com/u/26845343?v=4"),
    )

    val contributors = listOf(
        Contributor(name = "Colby Cabrera", githubUrl = "https://github.com/ColbyCabrera", avatarUrl = "https://avatars.githubusercontent.com/u/77089439?v=4")
    )

    // Correctly initialize MutableTransitionState
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) {
        transitionState.targetState = true // Set targetState, not value
    }
    val transition = rememberTransition(transitionState, label = "AboutAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = currentTopBarHeightDp, bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App info section
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pixelplay_base_monochrome),
                        contentDescription = "App Icon",
                        colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .size(108.dp)
                            .padding(bottom = 16.dp)
                    )
                    Column(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Pixel Play",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                )
                        ) {
                            Text(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                                text = "Version $versionName",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Greeting section
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    shape = AbsoluteSmoothCornerShape(20.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = "Thanks for using Pixel Play!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "This app was built with passion and a love for music. We hope you enjoy the experience.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Author section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Author",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            item{
                ContributorCard(authors[0])
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Contributors section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Contributors",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            items(contributors) { contributor ->
                ContributorCard(contributor)
            }
        }
        AboutTopBar(
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackPressed = onNavigationIconClick
        )
    }
}

// Composable for displaying a single contributor
@Composable
fun ContributorCard(
    contributor: Contributor,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, // optional: make the whole card tappable if you want
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 22.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 22.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 22.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 22.dp, smoothnessAsPercentBR = 60
    )

    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    val clickableModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
    } else {
        Modifier.clip(shape)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(clickableModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer, // M3 “expressive” container
        tonalElevation = 4.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(3.dp, outline)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContributorAvatar(
                name = contributor.name,
                avatarUrl = contributor.avatarUrl,
                iconRes = contributor.iconRes ?: R.drawable.rounded_person_24, // keep a default drawable
                modifier = Modifier
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = contributor.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!contributor.role.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = contributor.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Social icons only if links exist
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialIconButton(
                    painterRes = R.drawable.github,
                    contentDescription = "Abrir perfil de GitHub",
                    url = contributor.githubUrl
                )
                SocialIconButton(
                    painterRes = R.drawable.telegram,
                    contentDescription = "Abrir Telegram",
                    url = contributor.telegramUrl
                )
            }
        }
    }
}

@Composable
private fun ContributorAvatar(
    name: String,
    avatarUrl: String?,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.secondaryContainer
    val fg = MaterialTheme.colorScheme.onSecondaryContainer
    val initial = name.firstOrNull()?.uppercase() ?: "?"

    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = bg,
        tonalElevation = 3.dp,
    ) {
        when {
            !avatarUrl.isNullOrBlank() -> {
                // Coil AsyncImage; if you don't use Coil, swap this for your own image loader
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Avatar de $name",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(id = iconRes ?: 0).takeIf { iconRes != null },
                    placeholder = painterResource(id = iconRes ?: 0).takeIf { iconRes != null }
                )
            }
            iconRes != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = "Icono de $name",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            else -> {
                // Letter tile fallback
                Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = fg
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialIconButton(
    painterRes: Int,
    contentDescription: String,
    url: String?,
    modifier: Modifier = Modifier
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    IconButton(
        onClick = { openUrl(context, url) },
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try { url.toUri() } catch (_: Throwable) { url.toUri() }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // As a last resort, do nothing; you could show a toast/snackbar from the caller if needed.
    }
}
