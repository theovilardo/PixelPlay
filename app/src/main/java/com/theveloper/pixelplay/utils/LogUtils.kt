package com.theveloper.pixelplay.utils

import timber.log.Timber

object LogUtils {

    private const val MAX_TAG_LENGTH = 23

    private fun getTag(instance: Any): String {
        val className = if (instance is String) instance else instance.javaClass.simpleName
        // Timber's tag length limit is 23 on older android versions
        return if (className.length > MAX_TAG_LENGTH) className.substring(0, MAX_TAG_LENGTH) else className
    }

    private fun buildLogMessage(message: String): String {
        val thread = Thread.currentThread()
        val stackTrace = thread.stackTrace
        // The element at index 4 should be the caller of the log method.
        // Index 0 is getThreadStackTrace, 1 is getStackTrace, 2 is buildLogMessage, 3 is the log function (e.g., d), 4 is the caller.
        val caller = stackTrace.getOrNull(4)

        val methodName = caller?.methodName ?: "UnknownMethod"
        val lineNumber = caller?.lineNumber ?: -1

        return "($methodName:$lineNumber) [${thread.name}] - $message"
    }

    fun d(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).d(buildLogMessage(message), *args)
    }

    fun i(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).i(buildLogMessage(message), *args)
    }

    fun w(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).w(buildLogMessage(message), *args)
    }

    fun e(tagProvider: Any, throwable: Throwable? = null, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).e(throwable, buildLogMessage(message), *args)
    }

    fun v(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).v(buildLogMessage(message), *args)
    }
}
