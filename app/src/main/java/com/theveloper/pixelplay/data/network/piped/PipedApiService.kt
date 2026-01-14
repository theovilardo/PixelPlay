package com.theveloper.pixelplay.data.network.piped

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit interface for Piped API.
 * Piped is a privacy-friendly YouTube frontend.
 */
interface PipedApiService {

    /**
     * Search for videos on Piped.
     * @param query Search query
     * @param filter Filter type (e.g., "music", "videos", "all")
     * @return Search response containing list of matching videos
     */
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("filter") filter: String? = null
    ): PipedSearchResponse?

    /**
     * Get stream information for a video.
     * @param videoId YouTube video ID
     * @return Stream response containing audio/video stream URLs
     */
    @GET("streams/{videoId}")
    suspend fun getStreams(
        @Path("videoId") videoId: String
    ): PipedStreamResponse?

    /**
     * Get stream information using a full URL (for custom instances).
     * @param url Full URL to the stream endpoint
     * @return Stream response containing audio/video stream URLs
     */
    @GET
    suspend fun getStreamsByUrl(
        @Url url: String
    ): PipedStreamResponse?
}
