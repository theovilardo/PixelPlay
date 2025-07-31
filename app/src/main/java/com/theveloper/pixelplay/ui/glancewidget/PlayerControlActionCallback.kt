package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.service.MusicService
import timber.log.Timber

class PlayerControlActionCallback : ActionCallback {
    private val TAG = "PlayerControlCallback"

    @OptIn(UnstableApi::class)
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[PlayerActions.key]
        Timber.tag(TAG).d("onAction received: $action for glanceId: $glanceId")

        if (action == null) {
            Timber.tag(TAG).w("Action key not found in parameters.")
            return
        }

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action
            if (action == PlayerActions.PLAY_FROM_QUEUE) {
                val songId = parameters[PlayerActions.songIdKey]
                if (songId != null) {
                    putExtra("song_id", songId)
                } else {
                    Timber.tag(TAG).w("PLAY_FROM_QUEUE action received but no songId found.")
                    return // No hacer nada si no hay ID de canción
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Timber.tag(TAG).d("Service intent sent for action: $action")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error starting service for action $action: ${e.message}")
        }
    }
}

object PlayerActions {
    val key = ActionParameters.Key<String>("playerActionKey_v1")
    val songIdKey = ActionParameters.Key<Long>("songIdKey_v1")
    const val PLAY_PAUSE = "com.example.pixelplay.ACTION_WIDGET_PLAY_PAUSE"
    const val NEXT = "com.example.pixelplay.ACTION_WIDGET_NEXT"
    const val PREVIOUS = "com.example.pixelplay.ACTION_WIDGET_PREVIOUS"
    const val FAVORITE = "com.example.pixelplay.ACTION_WIDGET_FAVORITE"
    const val PLAY_FROM_QUEUE = "com.example.pixelplay.ACTION_WIDGET_PLAY_FROM_QUEUE"
}