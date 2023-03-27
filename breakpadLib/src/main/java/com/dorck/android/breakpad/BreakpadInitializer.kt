package com.dorck.android.breakpad

class BreakpadInitializer {
    fun initializeBreakpad(path: String) {
        initBreakpadNative(path)
    }
    /**
     * A native method that is implemented by the 'breakpad' native library,
     * which is packaged with this application.
     */
    external fun initBreakpadNative(path: String)

    companion object {
        // Used to load the 'breakpad' library on application startup.
        init {
            System.loadLibrary("breakpad-core")
        }
    }
}