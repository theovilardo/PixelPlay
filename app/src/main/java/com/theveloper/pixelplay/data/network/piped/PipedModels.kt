package com.theveloper.pixelplay.data.network.piped

import com.google.gson.annotations.SerializedName

/**
 * Response from Piped search API.
 */
data class PipedSearchResponse(
    @SerializedName("items") val items: List<PipedVideo> = emptyList()
)

/**
 * Video data from Piped API.
 */
data class PipedVideo(
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String? = null,
    @SerializedName("videoId") val videoId: String,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("uploaderName") val uploaderName: String? = null,
    @SerializedName("uploaderUrl") val uploaderUrl: String? = null,
    @SerializedName("uploadedDate") val uploadedDate: String? = null,
    @SerializedName("shortDescription") val shortDescription: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("views") val views: Long = 0
)

/**
 * Response from Piped stream API.
 */
data class PipedStreamResponse(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("uploadDate") val uploadDate: String? = null,
    @SerializedName("uploader") val uploader: String? = null,
    @SerializedName("uploaderUrl") val uploaderUrl: String? = null,
    @SerializedName("uploaderAvatar") val uploaderAvatar: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("hls") val hls: String? = null,
    @SerializedName("dash") val dash: String? = null,
    @SerializedName("lbryId") val lbryId: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("visibility") val visibility: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("metaInfo") val metaInfo: PipedMetaInfo? = null,
    @SerializedName("relatedStreams") val relatedStreams: List<PipedVideo> = emptyList(),
    @SerializedName("audioStreams") val audioStreams: List<PipedAudioStream> = emptyList(),
    @SerializedName("videoStreams") val videoStreams: List<PipedVideoStream> = emptyList()
)

/**
 * Audio stream information.
 */
data class PipedAudioStream(
    @SerializedName("url") val url: String,
    @SerializedName("format") val format: String? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("audioTrackId") val audioTrackId: String? = null,
    @SerializedName("audioTrackName") val audioTrackName: String? = null,
    @SerializedName("audioTrackType") val audioTrackType: String? = null,
    @SerializedName("audioTrackLocale") val audioTrackLocale: String? = null,
    @SerializedName("videoOnly") val videoOnly: Boolean = false,
    @SerializedName("itag") val itag: Int? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("initStart") val initStart: Int? = null,
    @SerializedName("initEnd") val initEnd: Int? = null,
    @SerializedName("indexStart") val indexStart: Int? = null,
    @SerializedName("indexEnd") val indexEnd: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("fps") val fps: Int? = null
)

/**
 * Video stream information.
 */
data class PipedVideoStream(
    @SerializedName("url") val url: String,
    @SerializedName("format") val format: String? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("videoOnly") val videoOnly: Boolean = false,
    @SerializedName("itag") val itag: Int? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("initStart") val initStart: Int? = null,
    @SerializedName("initEnd") val initEnd: Int? = null,
    @SerializedName("indexStart") val indexStart: Int? = null,
    @SerializedName("indexEnd") val indexEnd: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("fps") val fps: Int? = null
)

/**
 * Meta information about the video.
 */
data class PipedMetaInfo(
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("song") val song: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("copyright") val copyright: String? = null
)
