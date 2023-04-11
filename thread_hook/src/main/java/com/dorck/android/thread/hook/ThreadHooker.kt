package com.dorck.android.thread.hook

import com.bytedance.android.bytehook.ByteHook

/**
 * A type of PLT hook tech used on Thread creation.
 * @author Dorck
 * @since 2023/04/10
 */
object ThreadHooker {
    init {
        // Load local so library
        System.loadLibrary("threadhook")
    }

    private var sHooked = false

    @JvmStatic
    fun initialize() {
        ByteHook.init(
            ByteHook.ConfigBuilder()
                .setMode(ByteHook.Mode.AUTOMATIC)
                .setDebug(BuildConfig.DEBUG)
                .build()
        )
    }

    @JvmStatic
    fun getStack(): String {
        return stackTraceToString(Throwable().stackTrace)
    }

    @JvmStatic
    fun hookThreadInfo() {
        if (sHooked) {
            return
        }
        sHooked = true
        nativeHookThread()
    }

    @JvmStatic
    private external fun nativeHookThread(): Boolean

    @JvmStatic
    private external fun nativeUnhookThread(): Boolean

    private fun stackTraceToString(arr: Array<StackTraceElement>?): String {
        if (arr == null) {
            return ""
        }
        val sb = StringBuffer()
        for (stackTraceElement in arr) {
            val className = stackTraceElement.className
            // remove unused stacks
            if (className.contains("java.lang.Thread")) {
                continue
            }
            sb.append(stackTraceElement).append('\n')
        }
        return sb.toString()
    }
}