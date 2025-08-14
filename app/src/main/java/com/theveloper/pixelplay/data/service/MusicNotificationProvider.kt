package com.theveloper.pixelplay.data.service

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.DefaultMediaNotificationProvider
import com.theveloper.pixelplay.R

@UnstableApi
class MusicNotificationProvider(
    private val context: Context,
    private val musicService: MusicService
) : DefaultMediaNotificationProvider(context) {

    override fun getActions(
        mediaSession: MediaSession,
        actionFactory: DefaultMediaNotificationProvider.ActionFactory,
        onActionClicked: (String) -> PendingIntent
    ): List<NotificationCompat.Action> {
        val player = mediaSession.player

        // Standard actions from the default provider
        val standardActions = super.getActions(mediaSession, actionFactory, onActionClicked)

        // Custom actions
        val shuffleAction = createShuffleAction(player, onActionClicked)
        val repeatAction = createRepeatAction(player, onActionClicked)
        val likeAction = createLikeAction(mediaSession, onActionClicked)

        // Build the final list of actions
        val finalActions = mutableListOf<NotificationCompat.Action>()
        finalActions.add(shuffleAction)
        finalActions.addAll(standardActions) // Adds Previous, Play/Pause, Next
        finalActions.add(repeatAction)
        finalActions.add(likeAction)

        return finalActions
    }

    private fun createShuffleAction(
        player: Player,
        onActionClicked: (String) -> PendingIntent
    ): NotificationCompat.Action {
        val shuffleOn = player.shuffleModeEnabled
        val command = if (shuffleOn) CUSTOM_COMMAND_SHUFFLE_OFF else CUSTOM_COMMAND_SHUFFLE_ON
        val icon = if (shuffleOn) R.drawable.rounded_shuffle_on_24 else R.drawable.rounded_shuffle_24
        val title = if (shuffleOn) "Shuffle Off" else "Shuffle On"
        return NotificationCompat.Action(icon, title, onActionClicked(command))
    }

    private fun createRepeatAction(
        player: Player,
        onActionClicked: (String) -> PendingIntent
    ): NotificationCompat.Action {
        val command = CUSTOM_COMMAND_CYCLE_REPEAT_MODE
        val title = "Repeat"
        val icon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_on_24
            Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_on_24
            else -> R.drawable.rounded_repeat_24
        }
        return NotificationCompat.Action(icon, title, onActionClicked(command))
    }

    private fun createLikeAction(
        mediaSession: MediaSession,
        onActionClicked: (String) -> PendingIntent
    ): NotificationCompat.Action {
        val isFavorite = musicService.isSongFavorite(mediaSession.player.currentMediaItem?.mediaId)
        val command = CUSTOM_COMMAND_LIKE
        val icon = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24
        val title = if (isFavorite) "Unlike" else "Like"
        return NotificationCompat.Action(icon, title, onActionClicked(command))
    }

    companion object {
        const val CUSTOM_COMMAND_SHUFFLE_ON = "com.theveloper.pixelplay.SHUFFLE_ON"
        const val CUSTOM_COMMAND_SHUFFLE_OFF = "com.theveloper.pixelplay.SHUFFLE_OFF"
        const val CUSTOM_COMMAND_CYCLE_REPEAT_MODE = "com.theveloper.pixelplay.CYCLE_REPEAT"
        const val CUSTOM_COMMAND_LIKE = "com.theveloper.pixelplay.LIKE"
    }
}
