package com.theveloper.pixelplay.data.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.theveloper.pixelplay.R

@UnstableApi
class MusicNotificationProvider(
    private val context: Context,
    private val musicService: MusicService
) : DefaultMediaNotificationProvider(context) {

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        // Get the standard buttons (Prev, Play/Pause, Next) from the base implementation
        val standardButtons = super.getMediaButtons(session, playerCommands, mediaButtonPreferences, showPauseButton)
        val finalButtons = mutableListOf<CommandButton>()

        // 1. Create custom "Like" button
        val isFavorite = musicService.isSongFavorite(session.player.currentMediaItem?.mediaId)
        val likeIcon = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24
        android.util.Log.d("MusicNotificationProvider", "Creating like button. isFavorite: $isFavorite")
        val likeButton = CommandButton.Builder()
            .setDisplayName("Like")
            .setIconResId(likeIcon)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
            .build()

        // 2. Create custom "Shuffle" button
        val shuffleOn = session.player.shuffleModeEnabled
        val shuffleCommandAction = if (shuffleOn) CUSTOM_COMMAND_SHUFFLE_OFF else CUSTOM_COMMAND_SHUFFLE_ON
        android.util.Log.d("MusicNotificationProvider", "Creating shuffle button. shuffleOn: $shuffleOn, command: $shuffleCommandAction")
        val shuffleIcon = if (shuffleOn) R.drawable.rounded_shuffle_on_24 else R.drawable.rounded_shuffle_24
        val shuffleButton = CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setIconResId(shuffleIcon)
            .setSessionCommand(SessionCommand(shuffleCommandAction, Bundle.EMPTY))
            .build()

        // 3. Create custom "Repeat" button
        val repeatIcon = when (session.player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_on_24
            Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_on_24
            else -> R.drawable.rounded_repeat_24
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName("Repeat")
            .setIconResId(repeatIcon)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_CYCLE_REPEAT_MODE, Bundle.EMPTY))
            .build()

        // 4. Assemble the final list of buttons
        // Order: Shuffle, Previous, Play/Pause, Next, Repeat
        finalButtons.add(shuffleButton)
        finalButtons.addAll(standardButtons)
        finalButtons.add(repeatButton)

        // Let's add the like button at the beginning of the list, before shuffle
        finalButtons.add(0, likeButton)

        return ImmutableList.copyOf(finalButtons)
    }

    companion object {
        const val CUSTOM_COMMAND_SHUFFLE_ON = "com.theveloper.pixelplay.SHUFFLE_ON"
        const val CUSTOM_COMMAND_SHUFFLE_OFF = "com.theveloper.pixelplay.SHUFFLE_OFF"
        const val CUSTOM_COMMAND_CYCLE_REPEAT_MODE = "com.theveloper.pixelplay.CYCLE_REPEAT"
        const val CUSTOM_COMMAND_LIKE = "com.theveloper.pixelplay.LIKE"
    }
}
