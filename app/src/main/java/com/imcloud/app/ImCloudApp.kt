package com.imcloud.app

import android.app.Application
import android.util.Log
import com.imcloud.app.data.remote.ApiClient

class ImCloudApp : Application() {
    companion object {
        const val BASE_URL = "http://49.232.224.90:8080/"
        private const val TAG = "ImCloudApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Global crash handler to catch ANY uncaught exception
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on ${thread.name}: ${throwable.message}", throwable)
            throwable.printStackTrace()
            // Call default handler (shows crash dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            ApiClient.init(this)
            Log.d(TAG, "ApiClient initialized OK")
        } catch (e: Exception) {
            Log.e(TAG, "ApiClient init failed", e)
        }
    }
}
