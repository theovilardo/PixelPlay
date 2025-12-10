package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.theveloper.pixelplay.data.model.Song
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
        val client = remoteMediaClient
        if (client == null) {
            onComplete(false)
            return
        }

        try {
            val mediaItems = songs.map { song ->
                song.toMediaQueueItem(serverAddress)
            }.toTypedArray()

            client.queueLoad(
                mediaItems,
                startIndex,
                repeatMode,
                startPosition,
                null
            ).setResultCallback { result ->
                if (result.status.isSuccess) {
                    if (autoPlay) {
                        client.play()
                    }

                    // Wait for remote to be ready before reporting success
                    waitForRemoteReady {
                        onComplete(true)
                    }
                } else {
                    Timber.e("Remote media client failed to load queue: ${result.status.statusMessage}")
                    onComplete(false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading queue to cast device")
            onComplete(false)
        }
    }

    private fun Song.toMediaQueueItem(serverAddress: String): MediaQueueItem {
        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, this.title)
        mediaMetadata.putString(MediaMetadata.KEY_ARTIST, this.artist)
        val artUrl = "$serverAddress/art/${this.id}"
        mediaMetadata.addImage(WebImage(Uri.parse(artUrl)))

        val mediaUrl = "$serverAddress/song/${this.id}"
        val mediaInfo = MediaInfo.Builder(mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mpeg")
            .setStreamDuration(this.duration)
            .setMetadata(mediaMetadata)
            .build()

        return MediaQueueItem.Builder(mediaInfo)
            .setCustomData(JSONObject().put("songId", this.id))
            .build()
    }

    private fun waitForRemoteReady(onReady: () -> Unit) {
        val client = remoteMediaClient ?: return

        val callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val state = client.playerState
                val pos = client.approximateStreamPosition

                // Logic from reference project to avoid race conditions
                if (state == MediaStatus.PLAYER_STATE_PLAYING ||
                    state == MediaStatus.PLAYER_STATE_PAUSED ||
                    pos > 0
                ) {
                    try {
                        client.unregisterCallback(this)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to unregister temporary callback")
                    }
                    onReady()
                }
            }
        }

        try {
            client.registerCallback(callback)
            client.requestStatus()
        } catch (e: Exception) {
            Timber.e(e, "Error waiting for remote ready")
            onReady() // Fallback to immediate complete if registration fails
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
        remoteMediaClient?.queueNext(null)
    }

    fun previous() {
        remoteMediaClient?.queuePrev(null)
    }

    fun jumpToItem(itemId: Int, position: Long) {
        remoteMediaClient?.queueJumpToItem(itemId, position, null)
    }

    fun setRepeatMode(repeatMode: Int) {
        remoteMediaClient?.queueSetRepeatMode(repeatMode, null)
    }
}
