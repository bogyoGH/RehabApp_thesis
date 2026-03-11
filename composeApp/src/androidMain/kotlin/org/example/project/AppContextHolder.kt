package org.example.project

import android.annotation.SuppressLint
import android.content.Context
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("StaticFieldLeak")
object AppContextHolder {
    private val contextRef = AtomicReference<Context?>(null)

    var context: Context?
        get() = contextRef.get()
        set(value) {
            contextRef.set(value)
        }

    fun isInitialized(): Boolean = contextRef.get() != null
}
