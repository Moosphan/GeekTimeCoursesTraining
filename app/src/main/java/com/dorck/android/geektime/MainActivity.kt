package com.dorck.android.geektime

import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.widget.Button
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }

    private fun initView() {
        val bitmap1 = BitmapFactory.decodeResource(resources, R.drawable.bird)
        val bitmap2 = BitmapFactory.decodeResource(resources, R.drawable.bird)
        Log.d(TAG, "bitmap1===>$bitmap1")
        Log.d(TAG, "bitmap2===>$bitmap2")
        findViewById<Button>(R.id.hprof).setOnClickListener {
            val dumpFile = File(externalCacheDir, "bitmap.hprof")
            Log.d(TAG, "dump hprof file path: ${dumpFile.absolutePath}")
            Debug.dumpHprofData(dumpFile.absolutePath)
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}