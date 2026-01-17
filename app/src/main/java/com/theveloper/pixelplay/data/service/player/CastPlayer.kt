package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class CastPlayer(private val castSession: CastSession) {

    private val remoteMediaClient: RemoteMediaClient? = castSession.remoteMediaClient

    fun loadQueue(
        songs: List<Song>,
        startIndex: Int,
        startPosition: Long,
        repeatMode: Int,
        serverAddress: String,
        autoPlay: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        Timber.tag(CAST_TAG).i("loadQueue called: songs=%d startIndex=%d startPosition=%d serverAddress=%s", 
            songs.size, startIndex, startPosition, serverAddress)
        
        val client = remoteMediaClient
        if (client == null) {
            Timber.tag(CAST_TAG).e("loadQueue failed: remoteMediaClient is null")
            onComplete(false)
            return
        }

        if (songs.isEmpty()) {
            Timber.tag(CAST_TAG).e("loadQueue failed: songs list is empty")
            onComplete(false)
            return
        }

        // Track if callback was already invoked to prevent double invocation
        val callbackInvoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val safeOnComplete: (Boolean) -> Unit = { success ->
            if (callbackInvoked.compareAndSet(false, true)) {
                Timber.tag(CAST_TAG).i("loadQueue completing with success=%s", success)
                onComplete(success)
            }
        }

        // Safety timeout - if callback never fires within 20s, fail gracefully
        CoroutineScope(Dispatchers.Main).launch {
            delay(20_000L)
            if (callbackInvoked.compareAndSet(false, true)) {
                Timber.tag(CAST_TAG).w("loadQueue callback timeout after 20s - forcing failure")
                onComplete(false)
            }
        }

        try {
            // Build MediaQueueItems for all songs
            val mediaItems = songs.mapIndexed { index, song ->
                song.toMediaQueueItem(serverAddress, index)
            }
            
            Timber.tag(CAST_TAG).i("Created %d media items", mediaItems.size)
            
            val currentSong = songs.getOrNull(startIndex)
            if (currentSong != null) {
                Timber.tag(CAST_TAG).d("Starting song: %s (mimeType=%s, url=%s/song/%s)", 
                    currentSong.title, currentSong.mimeType, serverAddress, currentSong.id)
            }

        // Legacy Single Item Load (v2 API)
        // AirReceiver seems to dislike MediaLoadRequestData (v3).
        // Now that Server supports HEAD/GET / correctly, we retry this legacy method.
        val currentItem = mediaItems.getOrNull(startIndex)
        if (currentItem == null) {
             Timber.tag(CAST_TAG).e("loadQueue failed: Invalid startIndex")
             safeOnComplete(false)
             return
        }

        Timber.tag(CAST_TAG).i("Calling client.load(MediaInfo) - Legacy Single Item Mode...")
        
        @Suppress("DEPRECATION")
        ((currentItem.media)?.let {
            client.load(it, autoPlay, 0L).setResultCallback { result ->
                val statusCode = result.status.statusCode
                val statusMessage = result.status.statusMessage
                Timber.tag(CAST_TAG).i("legacyLoad result: isSuccess=%s statusCode=%d statusMessage=%s",
                    result.status.isSuccess, statusCode, statusMessage)

                if (result.status.isSuccess) {
                    safeOnComplete(true)
                    client.requestStatus()
                } else {
                    Timber.tag(CAST_TAG).e("legacyLoad FAILED: code=%d message=%s", statusCode, statusMessage)
                    safeOnComplete(false)
                }
            }
        })
        } catch (e: Exception) {
            Timber.tag(CAST_TAG).e(e, "Error loading queue to cast device")
            safeOnComplete(false)
        }
    }

    companion object {
        private const val CAST_TAG = "PixelPlayCastDebug"
    }

    private fun Song.toMediaQueueItem(serverAddress: String, itemId: Int): MediaQueueItem {
        // Use MEDIA_TYPE_GENERIC for maximum compatibility (some receivers reject MUSIC_TRACK)
        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, this.title)
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, this.artist) // Map Artist to Subtitle
        // mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE, this.album) // Skip Album for GENERIC
        val artUrl = "$serverAddress/art/${this.id}"
        mediaMetadata.addImage(WebImage(Uri.parse(artUrl)))

        // Determine extension for URL (helps some receivers like AirReceiver/DLNA)
        val extension = when {
            this.mimeType.equals("audio/flac", ignoreCase = true) -> "flac"
            this.mimeType.equals("audio/mp4", ignoreCase = true) -> "m4a"
            this.mimeType.equals("audio/x-m4a", ignoreCase = true) -> "m4a"
            this.mimeType.equals("audio/ogg", ignoreCase = true) -> "ogg"
            else -> "mp3"
        }
        val mediaUrl = "$serverAddress/song/${this.id}.$extension"
        
        // Use actual MIME type from the song, fallback to audio/mpeg if unknown
        val contentType = this.mimeType?.takeIf { it.isNotBlank() && it != "-" } ?: "audio/mpeg"
        
        Timber.tag(CAST_TAG).d("Generating MediaQueueItem: $mediaUrl | Mime: $contentType | Art: $artUrl")
        
    val mediaInfo = MediaInfo.Builder(mediaUrl)
        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
        .setContentType(contentType)
        .setStreamDuration(this.duration) // Restoring duration for valid seek
        .setMetadata(mediaMetadata) // Restoring basic metadata - Receiver might require Title
        .build()

        return MediaQueueItem.Builder(mediaInfo)
            .setItemId(itemId)
            .build()
    }

    fun seek(position: Long) {
        val client = remoteMediaClient ?: return
        try {
            Timber.d("Seeking to position: $position ms")
            val seekOptions = MediaSeekOptions.Builder()
                .setPosition(position)
                .build()

            client.seek(seekOptions)
            // Force status update to prevent UI bouncing
            client.requestStatus()
        } catch (e: Exception) {
            Timber.e(e, "Error seeking cast device")
        }
    }

    fun play() {
        remoteMediaClient?.play()
    }

    fun pause() {
        remoteMediaClient?.pause()
    }

    fun next() {
        remoteMediaClient?.queueNext(null)?.setResultCallback {
            if (!it.status.isSuccess) {
                Timber.w("Remote failed to advance to next item: ${it.status.statusMessage}")
            }
            remoteMediaClient?.requestStatus()
        }
    }

    fun previous() {
        remoteMediaClient?.queuePrev(null)?.setResultCallback {
            if (!it.status.isSuccess) {
                Timber.w("Remote failed to go to previous item: ${it.status.statusMessage}")
            }
            remoteMediaClient?.requestStatus()
        }
    }

    fun jumpToItem(itemId: Int, position: Long) {
        remoteMediaClient?.queueJumpToItem(itemId, position, null)?.setResultCallback { result ->
            if (!result.status.isSuccess) {
                Timber.w("Remote failed to jump to item %d: %s", itemId, result.status.statusMessage)
            }
            remoteMediaClient?.requestStatus()
        }
    }

    fun setRepeatMode(repeatMode: Int) {
        remoteMediaClient?.queueSetRepeatMode(repeatMode, null)
    }
}
