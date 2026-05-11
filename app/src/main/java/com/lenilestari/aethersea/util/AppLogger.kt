package com.lenilestari.aethersea.util

import android.util.Log
import com.lenilestari.aethersea.BuildConfig

object AppLogger {
    private const val MAX_TAG_LEN = 23

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag.take(MAX_TAG_LEN), msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag.take(MAX_TAG_LEN), msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        // Error selalu di-log agar mudah debug — tidak ada PII di message
        if (throwable != null) Log.e(tag.take(MAX_TAG_LEN), msg, throwable)
        else Log.e(tag.take(MAX_TAG_LEN), msg)
    }

    fun coroutineCancel(tag: String, context: String) {
        if (BuildConfig.DEBUG) Log.w(tag.take(MAX_TAG_LEN), "[$context] coroutine cancelled")
    }

    fun timeout(tag: String, context: String, limitMs: Long) {
        Log.w(tag.take(MAX_TAG_LEN), "[$context] TIMEOUT after ${limitMs}ms")
    }

    fun lifecycle(tag: String, event: String) {
        if (BuildConfig.DEBUG) Log.d(tag.take(MAX_TAG_LEN), "[lifecycle] $event")
    }
}
