package me.yunfeng.eventsdk

import android.os.Bundle
import android.util.Log

object EventEmitter {
    private const val TAG = "EventEmitter"
    fun logEvent(name: String, params: Bundle) {
        Log.d(TAG, "Logging event name: $name; params: $params")
    }
}