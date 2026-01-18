package com.theveloper.pixelplay

import android.util.Log
import timber.log.Timber

/**
 * A release-optimized Timber Tree that:
 * - Only logs WARN, ERROR, and WTF (suppresses VERBOSE, DEBUG, and INFO)
 * - Strips method/line information for performance
 * - Could be extended to report errors to crash analytics (e.g., Firebase Crashlytics)
 */
class ReleaseTree : Timber.Tree() {
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Only log WARN and above in release builds
        return priority >= Log.WARN
    }
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Skip if not loggable (redundant but explicit)
        if (priority < Log.WARN) return
        
        // Use Android's Log directly (stripped in release by R8 if configured)
        when (priority) {
            Log.WARN -> Log.w(tag, message, t)
            Log.ERROR -> Log.e(tag, message, t)
            Log.ASSERT -> Log.wtf(tag, message, t)
        }
        
        // TODO: Optionally report errors to crash analytics
        // if (priority >= Log.ERROR && t != null) {
        //     FirebaseCrashlytics.getInstance().recordException(t)
        // }
    }
}
