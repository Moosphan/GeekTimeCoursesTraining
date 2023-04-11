package com.dorck.android.geektime

import android.app.Application
import com.dorck.android.thread.hook.ThreadHooker

class SampleApp() : Application() {
    override fun onCreate() {
        super.onCreate()
        ThreadHooker.initialize()
    }
}