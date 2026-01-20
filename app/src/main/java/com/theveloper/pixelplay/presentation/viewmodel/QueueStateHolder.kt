package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages queue shuffle state.
 * Extracted from PlayerViewModel to improve modularity.
 * 
 * This class handles the original queue order for shuffle/unshuffle operations.
 */
@Singleton
class QueueStateHolder @Inject constructor() {
    
    // Original queue order before shuffle (for restoring when unshuffling)
    private var _originalQueueOrder: List<Song> = emptyList()
    val originalQueueOrder: List<Song> get() = _originalQueueOrder
    
    // Original queue name before shuffle
    private var _originalQueueName: String = "None"
    val originalQueueName: String get() = _originalQueueName
    
    /**
     * Store the original queue state before shuffling.
     */
    fun saveOriginalQueueState(queue: List<Song>, queueName: String) {
        _originalQueueOrder = queue.toList()
        _originalQueueName = queueName
    }
    
    /**
     * Set original queue order (for updates during playback).
     */
    fun setOriginalQueueOrder(queue: List<Song>) {
        _originalQueueOrder = queue.toList()
    }
    
    /**
     * Check if original queue is empty.
     */
    fun hasOriginalQueue(): Boolean = _originalQueueOrder.isNotEmpty()
    
    /**
     * Get the original queue for restoring after unshuffle.
     */
    fun getOriginalQueueForRestore(): List<Song> = _originalQueueOrder.toList()
    
    /**
     * Clear the original queue state (e.g., when queue is cleared).
     */
    fun clearOriginalQueue() {
        _originalQueueOrder = emptyList()
        _originalQueueName = "None"
    }
    
    /**
     * Create a shuffled version of a queue, keeping the current song at the start.
     */
    fun createShuffledQueue(
        currentQueue: List<Song>,
        currentSongId: String?
    ): List<Song> {
        if (currentQueue.isEmpty()) return emptyList()
        
        val currentSong = currentQueue.find { it.id == currentSongId }
        val otherSongs = currentQueue.filter { it.id != currentSongId }.shuffled()
        
        return if (currentSong != null) {
            listOf(currentSong) + otherSongs
        } else {
            otherSongs.shuffled()
        }
    }
}
