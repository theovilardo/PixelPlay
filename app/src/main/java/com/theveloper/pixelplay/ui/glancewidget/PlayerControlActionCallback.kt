package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.theveloper.pixelplay.data.service.MusicService

class PlayerControlActionCallback : ActionCallback {
    private val TAG = "PlayerControlCallback"
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[PlayerActions.key]
        Log.d(TAG, "onAction received: $action for glanceId: $glanceId")

        if (action == null) {
            Log.w(TAG, "Action key not found in parameters.")
            return
        }

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action
            // Opcional: Podrías pasar el glanceId al servicio si necesitas actualizar
            // solo este widget, pero MusicService actualizará todos por ahora.
            // putExtra("glanceId_to_update", glanceId.toString())
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Service intent sent for action: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service for action $action: ${e.message}", e)
        }
        // La actualización del widget la manejará el MusicService después de procesar la acción.
    }
}

object PlayerActions {
    val key = ActionParameters.Key<String>("playerActionKey_v1") // Usar una key única
    const val PLAY_PAUSE = "com.example.pixelplay.ACTION_WIDGET_PLAY_PAUSE"
    const val NEXT = "com.example.pixelplay.ACTION_WIDGET_NEXT"
    const val PREVIOUS = "com.example.pixelplay.ACTION_WIDGET_PREVIOUS"
}