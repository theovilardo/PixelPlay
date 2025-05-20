package com.theveloper.pixelplay.ui.glancewidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class WidgetUpdateReceiver : BroadcastReceiver() {
    private val coroutineScope = MainScope()
    private val TAG = "WidgetUpdateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: Received action: ${intent.action}")
        if (intent.action == "com.example.pixelplay.ACTION_WIDGET_UPDATE_PLAYBACK_STATE") {
            coroutineScope.launch {
                try {
                    val glanceAppWidgetManager = GlanceAppWidgetManager(context)
                    val glanceIds = glanceAppWidgetManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
                    Log.d(TAG, "Found glanceIds to update: $glanceIds")
                    glanceIds.forEach { glanceId ->
                        PixelPlayGlanceWidget().update(context, glanceId)
                        Log.d(TAG, "Requested update for glanceId: $glanceId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widgets: ${e.message}", e)
                }
            }
        }
    }
}