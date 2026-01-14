package com.theveloper.pixelplay.data.network.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractorService @Inject constructor() {

    private val youtubeService = ServiceList.YouTube

    /**
     * Search for songs on YouTube
     * @param query Search query
     * @return List of video items matching search
     */
    suspend fun searchSongs(query: String): Result<List<StreamInfoItem>> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = youtubeService.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val items = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { item ->
                    // Filter for music content - videos or audio streams
                    item.streamType == StreamType.VIDEO_STREAM || 
                    item.streamType == StreamType.AUDIO_STREAM
                }
            
            Timber.d("Found ${items.size} songs for query: $query")
            Result.success(items)
        } catch (e: Exception) {
            Timber.e(e, "Error searching songs: $query")
            Result.failure(e)
        }
    }

    /**
     * Get next page of search results
     * @param page Page object from previous search
     * @return List of video items from next page
     */
    suspend fun getNextPage(searchExtractor: SearchExtractor, page: Page): Result<List<StreamInfoItem>> = 
        withContext(Dispatchers.IO) {
            try {
                val nextPage = searchExtractor.getPage(page)
                val items = nextPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter { item ->
                        item.streamType == StreamType.VIDEO_STREAM || 
                        item.streamType == StreamType.AUDIO_STREAM
                    }
                
                Result.success(items)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching next page")
                Result.failure(e)
            }
        }

    /**
 * Get stream information for a YouTube video with fallback
 */
suspend fun getStreamInfo(videoUrl: String): Result<StreamExtractor> = withContext(Dispatchers.IO) {
    try {
        // Ensure URL is in correct format
        val fullUrl = if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            "https://www.youtube.com/watch?v=$videoUrl"
        }
        
        Timber.d("YouTubeExtractor: Fetching stream info for: $fullUrl")
        
        val streamExtractor = youtubeService.getStreamExtractor(fullUrl)
        
        try {
            streamExtractor.fetchPage()
            Timber.d("YouTubeExtractor: Got stream info for: ${streamExtractor.name}, Audio streams: ${streamExtractor.audioStreams.size}")
            Result.success(streamExtractor)
        } catch (e: Exception) {
            Timber.w("YouTubeExtractor: Primary extraction failed: ${e.message}")
            
            // Try alternative approach: use different streaming data extraction
            try {
                // Force re-extraction with different method
                val altExtractor = youtubeService.getStreamExtractor(fullUrl)
                altExtractor.fetchPage()
                
                val audioCount = altExtractor.audioStreams.size
                Timber.d("YouTubeExtractor: Alternative extraction found $audioCount audio streams")
                
                if (audioCount > 0) {
                    Result.success(altExtractor)
                } else {
                    throw Exception("No audio streams found with alternative method")
                }
            } catch (altException: Exception) {
                Timber.e("YouTubeExtractor: Alternative extraction also failed")
                throw e // Throw original exception
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "YouTubeExtractor: Error getting stream info: $videoUrl")
        Result.failure(e)
    }
}

    /**
     * Get direct audio stream URL for a YouTube video
     * @param videoUrl YouTube video URL or ID
     * @return Direct audio stream URL
     */
    suspend fun getStreamUrl(videoUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("YouTubeExtractor: Getting stream URL for: $videoUrl")
            
            val streamInfoResult = getStreamInfo(videoUrl)
            
            if (streamInfoResult.isFailure) {
                val error = streamInfoResult.exceptionOrNull()
                Timber.e("YouTubeExtractor: Failed to get stream info", error)
                return@withContext Result.failure(
                    streamInfoResult.exceptionOrNull() ?: Exception("Failed to get stream info")
                )
            }
            
            val streamExtractor = streamInfoResult.getOrThrow()
            
            // Get the best audio stream (highest bitrate)
            val audioStreams = streamExtractor.audioStreams
            Timber.d("YouTubeExtractor: Found ${audioStreams.size} audio streams")
            
            // Try to get the best audio stream
            val audioStream = audioStreams
                .filter { it.content != null && it.content.isNotEmpty() }
                .maxByOrNull { it.averageBitrate ?: 0 }
            
            if (audioStream != null && audioStream.content.isNotEmpty()) {
                val streamUrl = audioStream.content
                Timber.d("YouTubeExtractor: Got audio stream URL: ${streamUrl.take(100)}..., bitrate: ${audioStream.averageBitrate}")
                Result.success(streamUrl)
            } else {
                // Log details of all streams for debugging
                Timber.e("YouTubeExtractor: No valid audio stream available. Total streams: ${audioStreams.size}")
                audioStreams.forEachIndexed { index, stream ->
                    Timber.d("YouTubeExtractor: Stream $index - content: ${stream.content?.take(50)}, bitrate: ${stream.averageBitrate}, format: ${stream.format}")
                }
                
                // Try fallback: use any stream with content, even if bitrate is null
                val fallbackStream = audioStreams.find { it.content != null && it.content.isNotEmpty() }
                if (fallbackStream != null) {
                    Timber.w("YouTubeExtractor: Using fallback audio stream")
                    Result.success(fallbackStream.content)
                } else {
                    Result.failure(Exception("No audio stream available"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "YouTubeExtractor: Error getting stream URL: $videoUrl")
            Result.failure(e)
        }
    }

    /**
     * Get related videos for a given video
     * @param videoUrl YouTube video URL or ID
     * @return List of related video items
     */
    suspend fun getRelatedVideos(videoUrl: String): Result<List<StreamInfoItem>> = withContext(Dispatchers.IO) {
        try {
            val streamInfoResult = getStreamInfo(videoUrl)
            
            if (streamInfoResult.isFailure) {
                return@withContext Result.failure(
                    streamInfoResult.exceptionOrNull() ?: Exception("Failed to get stream info")
                )
            }
            
            val streamExtractor = streamInfoResult.getOrThrow()
            val relatedItems = streamExtractor.relatedItems?.items
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter { item ->
                    item.streamType == StreamType.VIDEO_STREAM || 
                    item.streamType == StreamType.AUDIO_STREAM
                } ?: emptyList()
            
            Timber.d("Found ${relatedItems.size} related videos")
            Result.success(relatedItems)
        } catch (e: Exception) {
            Timber.e(e, "Error getting related videos: $videoUrl")
            Result.failure(e)
        }
    }
}
