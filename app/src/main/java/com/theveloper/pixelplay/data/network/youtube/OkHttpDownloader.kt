package com.theveloper.pixelplay.data.network.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor(
    private val client: OkHttpClient
) : Downloader() {

    companion object {
        @Volatile
        private var instance: OkHttpDownloader? = null

        fun getInstance(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpDownloader {
            return instance ?: synchronized(this) {
                instance ?: OkHttpDownloader(
                    builder
                        .readTimeout(30, TimeUnit.SECONDS)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                ).also { instance = it }
            }
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        var requestBody: RequestBody? = null
        if (dataToSend != null) {
            requestBody = RequestBody.create(null, dataToSend)
        }

        val requestBuilder = Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)

        headers.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        val response: Response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val body: ResponseBody? = response.body
        var responseBodyToReturn: String? = null

        if (body != null) {
            responseBodyToReturn = body.string()
        }

        val latestUrl = response.request.url.toString()
        return ExtractorResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            latestUrl
        )
    }
}
