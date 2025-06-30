package com.theveloper.pixelplay.presentation.components

import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import kotlinx.coroutines.Dispatchers

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int? = null, // e.g., R.drawable.rounded_imagesmode_24
    errorResId: Int? = null,       // e.g., R.drawable.rounded_broken_image_24
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeDurationMillis: Int = 300,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = true, // Default to true, set to false if CPU access to Bitmap is needed later
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    onState: ((AsyncImagePainter.State) -> Unit)? = null // Callback for image loading state
) {
    val context = LocalContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (placeholderResId != null) {
                    placeholder(placeholderResId)
                }
                if (errorResId != null) {
                    error(errorResId)
                }
                crossfade(crossfadeDurationMillis)
                diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                allowHardware(allowHardware) // Important if you need to read the bitmap on CPU later
                // Add other request options if needed, e.g., transformations
            }
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        onState = onState, // Propagate the state change
        // For simple placeholder/error without needing the full state:
        //placeholder = placeholderResId?.let { painterResource(id = it) },
        //error = errorResId?.let { painterResource(id = it) }
    )
}
//@OptIn(ExperimentalCoilApi::class)
//@Composable
//fun SmartImage(
//    // Data source: could be Uri, URL string, @DrawableRes Int, etc.
//    model: Any?,
//    contentDescription: String? = null,
//    modifier: Modifier = Modifier,
//    // Placeholder & error
//    @DrawableRes placeholder: Int = R.drawable.rounded_imagesmode_24,
//    @DrawableRes error: Int = R.drawable.rounded_broken_image_24,
//    // Shape and size
//    shape: Shape = RectangleShape,
//    contentScale: ContentScale = ContentScale.Crop,
//    // Animation & caching
//    crossfadeDuration: Int = 300,
//    useDiskCache: Boolean = true,
//    useMemoryCache: Boolean = true,
//    // Color Filter, alpha, etc.
//    colorFilter: ColorFilter? = null,
//    alpha: Float = 1f,
//    // Optional warm-up callback
//    onSuccessWarmUp: Boolean = true,
//    // Callback for color extraction
//    onColorExtracted: ((Color) -> Unit)? = null
//) {
//    val context = LocalContext.current
//    // val scope = rememberCoroutineScope() // Alternative for launching coroutine
//
//    // 1) Build and remember the ImageRequest
//    val request = remember(model, useDiskCache, useMemoryCache) { // Include cache policies in remember key
//        ImageRequest.Builder(context)
//            .data(model)
//            .placeholder(placeholder)
//            .error(error)
//            .crossfade(crossfadeDuration)
//            .dispatcher(Dispatchers.IO) // Offload loading to IO thread
//            .allowHardware(false) // Required for Palette to work correctly from Bitmap
//            .apply {
//                diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
//                memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
//            }
//            .build()
//    }
//
//    // 2) Create and remember painter
//    val painter = rememberAsyncImagePainter(
//        model = request,
//        onSuccess = { state ->
//            val drawable = state.result.drawable
//            // 3) Warm up bitmap for smoother draws
//            if (onSuccessWarmUp) {
//                (drawable as? BitmapDrawable)
//                    ?.bitmap
//                    ?.asImageBitmap()
//                    ?.prepareToDraw()
//            }
//
//            // 4) Extract color if callback is provided
//            onColorExtracted?.let { callback ->
//                // LaunchedEffect is preferred here as it ties the coroutine to the composable's lifecycle
//                // and the specific key (drawable).
//                // Using a local scope `rememberCoroutineScope` and launching from it is also an option,
//                // but LaunchedEffect is more idiomatic for this case.
//                // For this to be available here, we'd need to make SmartImage accept a CoroutineScope
//                // or use a side-effect that provides one.
//                // For simplicity and correctness within a composable that might recompose,
//                // we'll need to trigger this from a LaunchedEffect if SmartImage itself is @Composable.
//                // The current structure means this onSuccess is a lambda, not a composable context.
//                // So, we'd typically call a suspend function or pass the result up.
//                // However, the example showed LaunchedEffect(successState) { ... }
//                // This implies the onSuccess lambda itself might be able to host a LaunchedEffect
//                // if 'state' changes trigger recomposition, or painter itself recomposes.
//                // Let's assume we need to get a bitmap and then call a suspend function for extraction.
//                // The provided example `OptimizedAlbumArt` used `LaunchedEffect(successState)`
//                // This means the `onSuccess` itself is not where the LaunchedEffect is, but rather
//                // the composable observing the painter's state would use LaunchedEffect.
//
//                // Correct approach for Coil's onSuccess:
//                // The onSuccess lambda is not a Composable context.
//                // We should extract the bitmap here and then use a LaunchedEffect in the calling Composable
//                // if we need to run further suspend functions based on the bitmap.
//                // OR, if color extraction is quick enough not to block UI for too long,
//                // can do it directly on Dispatchers.Default.
//                // The prompt example's OptimizedAlbumArt shows a LaunchedEffect *outside* rememberAsyncImagePainter,
//                // observing its success state. That's a cleaner pattern.
//                // For now, let's stick to the simpler, direct (but potentially blocking if not careful) approach
//                // for Palette, ensuring it runs off the main thread.
//                // The provided example for OptimizedAlbumArt is actually better:
//                // onSuccess = { successState ->
//                //    LaunchedEffect(successState) { <--- This is not how Coil's onSuccess works.
//                //       withContext(Dispatchers.Default) { ... extract ... }
//                //    }
//                // }
//                // The LaunchedEffect should be in the Composable that *uses* rememberAsyncImagePainter.
//                // So, SmartImage can't directly use LaunchedEffect in its onSuccess.
//                // It should provide the bitmap, and the caller can do the LaunchedEffect.
//                //
//                // Simpler for now: extract color directly if callback present, but ensure it's off main.
//                // This is a compromise. A better API would be for SmartImage to have an onBitmapLoaded callback.
//
//                val bitmap = (drawable as? BitmapDrawable)?.bitmap
//                if (bitmap != null) {
//                    // This part needs to be done carefully.
//                    // If extractDominantColor is suspending or long, it should not block here.
//                    // For now, assuming extractDominantColor will be quick or internally managed.
//                    // This is a simplification based on the prompt's initial structure.
//                    // To be truly non-blocking for Palette, it needs a coroutine.
//                    // We will ensure extractDominantColor itself is efficient or uses a background dispatcher.
//                    // This will be handled when implementing extractDominantColor.
//                    // For now, conceptual call:
//                    // val color = extractDominantColor(bitmap) // This needs context or to be a suspend fun
//                    // callback(color)
//                    //
//                    // Let's refine this. The color extraction should be done by the ViewModel or a dedicated utility.
//                    // SmartImage's role is to provide the bitmap.
//                    // So, let's change onColorExtracted to onBitmapLoaded: (Bitmap) -> Unit
//                    // This makes SmartImage more reusable.
//                    //
//                    // Re-evaluating based on prompt: "extract colors as a background operation"
//                    // "onSuccess = { /* Cache or trigger color extraction here, if needed */ }"
//                    // The example `OptimizedAlbumArt` uses `LaunchedEffect(successState)` with `withContext(Dispatchers.Default)`.
//                    // This implies the `onSuccess` in `rememberAsyncImagePainter` can somehow trigger this.
//                    // This is possible if `rememberAsyncImagePainter`'s state is collected and `LaunchedEffect` observes it.
//                    //
//                    // Let's try to align with the problem's OptimisedAlbumArt example's *intent* for color extraction:
//                    // The `rememberAsyncImagePainter`'s `onSuccess` lambda itself is not a composable scope.
//                    // The color extraction should happen in a coroutine.
//                    // We can't launch a coroutine directly here without a scope.
//                    // A common pattern is to use a side-effect in the Composable that calls `rememberAsyncImagePainter`.
//                    //
//                    // Given the constraints, the most direct way to implement something *like* the request
//                    // within SmartImage is to make onColorExtracted accept the bitmap and the caller handles the coroutine.
//                    // OR, SmartImage itself could take a CoroutineScope.
//                    //
//                    // Let's try a slightly different approach based on the prompt's example for OptimizedAlbumArt:
//                    // The color extraction is tied to the image loading success.
//                    // We can simplify by saying `onColorExtracted` will be called, and it's the responsibility
//                    // of `extractDominantColor` to be efficient or use background threads.
//                    //
//                    // This is what was in the problem description's example:
//                    // onSuccess = { successState ->
//                    //    LaunchedEffect(successState) { // This is the tricky part, as onSuccess is not a Composable
//                    //       withContext(Dispatchers.Default) {
//                    //          val bitmap = (successState.result.drawable as? BitmapDrawable)?.bitmap
//                    //          val color = bitmap?.let { extractDominantColor(it) }
//                    //          color?.let { onColorExtracted(it) }
//                    //       }
//                    //    }
//                    // }
//                    //
//                    // Since `LaunchedEffect` can't be used directly in `onSuccess`,
//                    // the responsibility of running extractDominantColor off the main thread
//                    // will be inside `extractDominantColor` itself or managed by the ViewModel.
//                    // For now, SmartImage will just call it.
//
//                    // This is a placeholder for where the actual call to a color extraction method would go.
//                    // The actual extraction logic (with Palette, coroutines) will be in that method.
//                    // For now, we pass the bitmap to the callback.
//                    // The callback itself should handle the threading.
//                    // This is not ideal, but aligns with keeping SmartImage simpler.
//                    //
//                    // A better pattern: SmartImage provides the painter. The Composable using SmartImage
//                    // observes painter.state. If it's Success, it can then launch a coroutine.
//                    // Example:
//                    // val painter = rememberAsyncImagePainter(...)
//                    // Image(painter, ...)
//                    // LaunchedEffect(painter.state) {
//                    //   if (painter.state is AsyncImagePainter.State.Success) {
//                    //     val bitmap = (painter.state as AsyncImagePainter.State.Success).result.drawable as? BitmapDrawable)?.bitmap
//                    //     bitmap?.let {
//                    //       val color = withContext(Dispatchers.Default) { extractDominantColor(it) }
//                    //       onColorExtracted(color)
//                    //     }
//                    //   }
//                    // }
//                    // This is the best practice. SmartImage should not have onColorExtracted.
//                    //
//                    // Revisiting the prompt: "Defer Heavy Operations... Show a placeholder... load images/colors as a background operation."
//                    // "OptimizedAlbumArt composable uses disk and memory cache, but these are empty on cold boot."
//                    // "onSuccess = { /* Cache or trigger color extraction here, if needed */ }"
//                    //
//                    // The provided `OptimizedAlbumArt` snippet for the *solution* is:
//                    // onSuccess = { successState ->
//                    //    LaunchedEffect(successState) { // This is still the problematic line for Coil's direct onSuccess
//                    //       withContext(Dispatchers.Default) { ... color?.let { onColorExtracted(it) } ... }
//                    //    }
//                    // }
//                    //
//                    // Let's assume the spirit is: when image is loaded, trigger color extraction off-thread.
//                    // The `PlayerViewModel` already has `extractAndGenerateColorScheme` which uses coroutines.
//                    // `SmartImage` should probably not be doing this itself.
//                    //
//                    // Decision: For now, `SmartImage` will NOT have `onColorExtracted`.
//                    // The color extraction logic is already in `PlayerViewModel` and is triggered
//                    // when a song changes or an album is loaded.
//                    // The `EnhancedSongListItem` and `AlbumGridItemRedesigned` use `SmartImage` for display.
//                    // If per-item dynamic theming based on its own art is needed (beyond what PlayerViewModel centralizes),
//                    // then those items would use the painter state pattern described above.
//                    //
//                    // The main goal "Defer image loading" is met by Coil + Placeholders in SmartImage.
//                    // Color extraction is a separate concern, often tied to ViewModel logic.
//                    //
//                    // Let's re-read the "Actionable Optimizations":
//                    // "A. For the LibraryScreen (songs tab) LazyColumn"
//                    // "1. Defer Heavy Operations: Don’t load album art or extract colors during the first composition of each item."
//                    //    - Album art loading is deferred by Coil.
//                    //    - Color extraction: If it's for theming the item itself, it needs to be deferred.
//                    //      Currently, `PlayerViewModel` handles theme extraction for the *main player* based on current song.
//                    //      It also has `getAlbumColorSchemeFlow` for `AlbumGridItemRedesigned`, which is deferred.
//                    //      `EnhancedSongListItem` does not currently use item-specific extracted colors for its own theming.
//                    //
//                    // So, the existing structure with `SmartImage` (for deferred image loading) and `PlayerViewModel`
//                    // (for deferred color extraction for player/album themes) largely meets the requirements.
//                    // The `ShimmerBox` handles the "placeholder for empty states" during initial load.
//                    //
//                    // No change needed to SmartImage for color extraction callback based on this re-evaluation.
//                    // The key is that `PlayerViewModel.extractAndGenerateColorScheme` and `getAlbumColorSchemeFlow`
//                    // already use coroutines and caching.
//                }
//            }
//    )
//
//    // Display via Image
//    Image(
//        painter = painter,
//        contentDescription = contentDescription,
//        contentScale = contentScale,
//        colorFilter = colorFilter,
//        alpha = alpha,
//        modifier = modifier
//            .clip(shape)
//    )
//}
