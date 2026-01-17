package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
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
                Timber.tag(CAST_TAG).d(
                    "Starting song: %s (mimeType=%s, url=%s/song/%s)",
                    currentSong.title,
                    currentSong.mimeType,
                    serverAddress,
                    currentSong.id
                )
            }

            val currentItem = mediaItems.getOrNull(startIndex)
            if (currentItem == null) {
                Timber.tag(CAST_TAG).e("loadQueue failed: Invalid startIndex")
                safeOnComplete(false)
                return
            }
            val currentSongForLoad = songs[startIndex]
            val minimalMediaInfo = currentSongForLoad.toMediaInfo(
                serverAddress = serverAddress,
                includeMetadata = false,
                includeDuration = true
            )

            val castDeviceName = castSession.castDevice?.friendlyName?.lowercase()
            val castDeviceModel = castSession.castDevice?.modelName?.lowercase()
            val castDeviceVersion = castSession.castDevice?.deviceVersion?.lowercase()
            val isAirReceiver = listOfNotNull(castDeviceName, castDeviceModel, castDeviceVersion)
                .any { it.contains("airreceiver") }
            val skipQueueLoad = isAirReceiver

            val safeStartPosition = startPosition.coerceAtLeast(0L)

            fun attemptQueueLoad(onResult: (Boolean, Int, String?) -> Unit) {
                Timber.tag(CAST_TAG).i("Attempting queueLoad for %d items", mediaItems.size)
                client.queueLoad(
                    mediaItems.toTypedArray(),
                    startIndex,
                    repeatMode,
                    null
                ).setResultCallback { result ->
                    onResult(
                        result.status.isSuccess,
                        result.status.statusCode,
                        result.status.statusMessage
                    )
                }
            }

            fun attemptLoadRequest(onResult: (Boolean, Int, String?) -> Unit) {
                Timber.tag(CAST_TAG).i("Attempting load(MediaLoadRequestData) for current item")
                val requestData = MediaLoadRequestData.Builder()
                    .setMediaInfo(minimalMediaInfo)
                    .setAutoplay(autoPlay)
                    .build()

                client.load(requestData).setResultCallback { result ->
                    onResult(
                        result.status.isSuccess,
                        result.status.statusCode,
                        result.status.statusMessage
                    )
                }
            }

            fun attemptLegacyLoad(onResult: (Boolean, Int, String?) -> Unit) {
                Timber.tag(CAST_TAG).i("Attempting legacy client.load(MediaInfo)")
                @Suppress("DEPRECATION")
                client.load(minimalMediaInfo, autoPlay, 0L)
                    .setResultCallback { result ->
                        onResult(
                            result.status.isSuccess,
                            result.status.statusCode,
                            result.status.statusMessage
                        )
                    }
            }

            val attempts = if (skipQueueLoad) {
                Timber.tag(CAST_TAG).w(
                    "Skipping queueLoad for receiver=%s; using direct load fallbacks",
                    castDeviceName ?: castDeviceModel ?: castDeviceVersion
                )
                listOf(::attemptLoadRequest, ::attemptLegacyLoad)
            } else {
                listOf(::attemptQueueLoad, ::attemptLoadRequest, ::attemptLegacyLoad)
            }

            val attemptDelayMs = if (skipQueueLoad) 1200L else 600L
            val initialDelayMs = if (skipQueueLoad) 1500L else 400L
            val readyTimeoutMs = if (skipQueueLoad) 8000L else 5000L
            val readyPollMs = 250L

            lateinit var runAttempt: (Int) -> Unit

            fun scheduleAttempt(index: Int) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(attemptDelayMs)
                    runAttempt(index)
                }
            }

            runAttempt = { index ->
                if (index >= attempts.size) {
                    Timber.tag(CAST_TAG).e("All cast load attempts failed")
                    safeOnComplete(false)
                } else {
                    try {
                        attempts[index].invoke { success, statusCode, statusMessage ->
                            Timber.tag(CAST_TAG).i(
                                "Cast load attempt %d result: success=%s statusCode=%d statusMessage=%s",
                                index + 1,
                                success,
                                statusCode,
                                statusMessage
                            )
                            if (success) {
                                if (safeStartPosition > 0L) {
                                    client.seek(
                                        MediaSeekOptions.Builder()
                                            .setPosition(safeStartPosition)
                                            .build()
                                    )
                                }
                                safeOnComplete(true)
                                client.requestStatus()
                            } else {
                                scheduleAttempt(index + 1)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag(CAST_TAG).w(e, "Cast load attempt %d threw exception", index + 1)
                        scheduleAttempt(index + 1)
                    }
                }
            }

            client.requestStatus()
            CoroutineScope(Dispatchers.Main).launch {
                var elapsedMs = 0L
                while (!castSession.isConnected && elapsedMs < readyTimeoutMs) {
                    delay(readyPollMs)
                    elapsedMs += readyPollMs
                }
                if (!castSession.isConnected) {
                    Timber.tag(CAST_TAG).w(
                        "Cast session not connected after %dms; attempting load anyway",
                        readyTimeoutMs
                    )
                }
                delay(initialDelayMs)
                runAttempt(0)
            }
        } catch (e: Exception) {
            Timber.tag(CAST_TAG).e(e, "Error loading queue to cast device")
            safeOnComplete(false)
        }
    }

    companion object {
        private const val CAST_TAG = "PixelPlayCastDebug"
    }

    private fun Song.toMediaQueueItem(serverAddress: String, itemId: Int): MediaQueueItem {
        val mediaInfo = toMediaInfo(serverAddress, includeMetadata = true, includeDuration = true)
        return MediaQueueItem.Builder(mediaInfo)
            .setItemId(itemId)
            .build()
    }

    private fun Song.toMediaInfo(
        serverAddress: String,
        includeMetadata: Boolean,
        includeDuration: Boolean
    ): MediaInfo {
        val mediaMetadata = if (includeMetadata) {
            MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, this@toMediaInfo.title)
                putString(MediaMetadata.KEY_SUBTITLE, this@toMediaInfo.artist)
                putString(MediaMetadata.KEY_ALBUM_TITLE, this@toMediaInfo.album)
                val artUrl = "$serverAddress/art/${this@toMediaInfo.id}.jpg"
                addImage(WebImage(Uri.parse(artUrl)))
            }
        } else {
            null
        }

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

        Timber.tag(CAST_TAG).d(
            "Generating MediaInfo: $mediaUrl | Mime: $contentType | Metadata=%s",
            includeMetadata
        )

        val builder = MediaInfo.Builder(mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)

        if (includeDuration && this.duration > 0) {
            builder.setStreamDuration(this.duration)
        }
        if (mediaMetadata != null) {
            builder.setMetadata(mediaMetadata)
        }

        return builder.build()
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
