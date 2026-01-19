package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
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
            val currentSongForLoad = songs[startIndex]
            val castDeviceName = castSession.castDevice?.friendlyName?.lowercase()
            val castDeviceModel = castSession.castDevice?.modelName?.lowercase()
            val castDeviceVersion = castSession.castDevice?.deviceVersion?.lowercase()
            val applicationId = castSession.applicationMetadata?.applicationId
            val applicationName = castSession.applicationMetadata?.name
            Timber.tag(CAST_TAG).i(
                "Cast device: name=%s model=%s version=%s connected=%s appId=%s appName=%s",
                castDeviceName,
                castDeviceModel,
                castDeviceVersion,
                castSession.isConnected,
                applicationId,
                applicationName
            )

            // Ultra Minimal Load Strategy
            // We strip EVERYTHING that is not strictly necessary to identify why 'Invalid Request' occurs.

            val minimalMediaInfo = currentSongForLoad.toMediaInfo(
                serverAddress = serverAddress,
                includeMetadata = false, // DISABLED metadata
                includeImages = false,   // DISABLED images
                includeDuration = false, // DISABLED duration (let receiver figure it out)
                includeCustomData = false, // DISABLED custom data
                streamType = MediaInfo.STREAM_TYPE_BUFFERED
            )
            Timber.tag(CAST_TAG).i(
                "Minimal MediaInfo: url=%s mime=%s streamType=%d",
                minimalMediaInfo.contentId,
                minimalMediaInfo.contentType,
                minimalMediaInfo.streamType
            )

            // Force start position to 0 to avoid seeking errors on load
            val safeStartPosition = 0L

            fun attemptLoadRequest(onResult: (Boolean, Int, String?) -> Unit) {
                Timber.tag(CAST_TAG).i(
                    "Attempting load(MediaLoadRequestData) with MINIMAL payload"
                )
                val requestData = MediaLoadRequestData.Builder()
                    .setMediaInfo(minimalMediaInfo)
                    .setAutoplay(autoPlay)
                    .setCurrentTime(safeStartPosition)
                    .build()

                client.load(requestData).setResultCallback { result ->
                    Timber.tag(CAST_TAG).i(
                        "load(MediaLoadRequestData) status: isSuccess=%s statusCode=%d statusMessage=%s",
                        result.status.isSuccess,
                        result.status.statusCode,
                        result.status.statusMessage
                    )
                    onResult(
                        result.status.isSuccess,
                        result.status.statusCode,
                        result.status.statusMessage
                    )
                }
            }

            // ONLY try loadRequest. Queue load is more complex and failing.
            val attempts = listOf(::attemptLoadRequest)

            val attemptDelayMs = 1000L
            val initialDelayMs = 500L
            val readyTimeoutMs = 5000L
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
                            logMediaStatus("After load attempt ${index + 1}", client.mediaStatus)
                            if (success) {
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
            Timber.tag(CAST_TAG).d("Requested initial status before load attempts")
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

    private fun logMediaStatus(prefix: String, status: MediaStatus?) {
        if (status == null) {
            Timber.tag(CAST_TAG).d("%s: mediaStatus=null", prefix)
            return
        }
        Timber.tag(CAST_TAG).d(
            "%s: state=%d idleReason=%d position=%d duration=%d repeat=%d queueCount=%d currentItemId=%d contentId=%s",
            prefix,
            status.playerState,
            status.idleReason,
            status.streamPosition,
            status.mediaInfo?.streamDuration ?: -1,
            status.queueRepeatMode,
            status.queueItemCount,
            status.currentItemId,
            status.mediaInfo?.contentId
        )
    }

    companion object {
        private const val CAST_TAG = "PixelPlayCastDebug"
    }

    private fun Song.toMediaQueueItem(serverAddress: String, itemId: Int): MediaQueueItem {
        val mediaInfo = toMediaInfo(
            serverAddress = serverAddress,
            includeMetadata = true,
            includeImages = true,
            includeDuration = true,
            includeCustomData = true
        )
        return MediaQueueItem.Builder(mediaInfo)
            .setItemId(itemId)
            .setCustomData(buildCastCustomData())
            .build()
    }

    private fun Song.toMediaInfo(
        serverAddress: String,
        includeMetadata: Boolean,
        includeImages: Boolean,
        includeDuration: Boolean,
        includeCustomData: Boolean,
        streamType: Int = MediaInfo.STREAM_TYPE_BUFFERED
    ): MediaInfo {
        val mediaMetadata = if (includeMetadata) {
            MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, this@toMediaInfo.title)
                putString(MediaMetadata.KEY_SUBTITLE, this@toMediaInfo.artist)
                putString(MediaMetadata.KEY_ALBUM_TITLE, this@toMediaInfo.album)
                if (includeImages) {
                    val artUrl = "$serverAddress/art/${this@toMediaInfo.id}.jpg"
                    addImage(WebImage(Uri.parse(artUrl)))
                }
            }
        } else {
            null
        }

        val extension = resolveCastExtension()
        val mediaUrl = "$serverAddress/song/${this.id}.$extension"

        val contentType = resolveCastMimeType()

        Timber.tag(CAST_TAG).d(
            "Generating MediaInfo: $mediaUrl | Mime: $contentType | Metadata=%s",
            includeMetadata
        )

        val builder = MediaInfo.Builder(mediaUrl)
            .setStreamType(streamType)
            .setContentType(contentType)
        if (includeCustomData) {
            builder.setCustomData(buildCastCustomData())
        }

        if (includeDuration && this.duration > 0) {
            builder.setStreamDuration(this.duration)
        }
        if (mediaMetadata != null) {
            builder.setMetadata(mediaMetadata)
        }

        return builder.build()
    }

    private fun Song.resolveCastExtension(): String {
        val mime = mimeType?.takeIf { it.isNotBlank() && it != "-" }
        if (mime != null) {
            return when {
                mime.equals("audio/flac", ignoreCase = true) -> "flac"
                mime.equals("audio/mp4", ignoreCase = true) -> "m4a"
                mime.equals("audio/x-m4a", ignoreCase = true) -> "m4a"
                mime.equals("audio/ogg", ignoreCase = true) -> "ogg"
                mime.equals("audio/wav", ignoreCase = true) -> "wav"
                mime.equals("audio/aac", ignoreCase = true) -> "aac"
                else -> "mp3"
            }
        }
        val pathExtension = path.substringAfterLast('.', "").lowercase()
        return when (pathExtension) {
            "flac", "m4a", "mp3", "ogg", "wav", "aac" -> pathExtension
            "mp4" -> "m4a"
            else -> "mp3"
        }
    }

    private fun Song.resolveCastMimeType(): String {
        val mime = mimeType?.takeIf { it.isNotBlank() && it != "-" }
        if (mime != null) {
             if (mime.equals("audio/mp3", ignoreCase = true)) {
                 return "audio/mpeg"
             }
            return mime
        }
        return when (val pathExtension = path.substringAfterLast('.', "").lowercase()) {
            "flac" -> "audio/flac"
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "mp3" -> "audio/mpeg"
            else -> "audio/mpeg"
        }
    }

    private fun Song.buildCastCustomData(): JSONObject {
        return JSONObject().apply {
            put("songId", id)
            put("title", title)
            put("artist", artist)
            put("album", album)
        }
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
