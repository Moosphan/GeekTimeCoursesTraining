package com.dorck.android.geektime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dorck.android.breakpad.BreakpadInitializer
import com.dorck.android.tracker.SimpleProcessTracker
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private var externalReportPath: File? = null
    var handler = Handler()
    private val mProcessCpuTracker: SimpleProcessTracker by lazy {
        SimpleProcessTracker(Process.myPid())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initPermission()
        initView()
    }

    private fun initPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "initPermission: start request permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        } else {
            Log.d(TAG, "initPermission: check for dump path")
            initExternalReportPath();
        }
        // FIXME：Android13 开始强制使用文件分区，此为临时方案，获取文件系统管理权
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                //去设置页打开权限
                val intent= Intent()
                intent.action= Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
            }
        } else {
            initExternalReportPath();
        }
    }

    private fun initView() {
        // 1. 测试如何捕捉 native crash 并解析出堆栈信息
        findViewById<Button>(R.id.btnCrash).setOnClickListener {
            initBreakPad()
            crash()
        }
        val bitmap1 = BitmapFactory.decodeResource(resources, R.drawable.bird)
        val bitmap2 = BitmapFactory.decodeResource(resources, R.drawable.bird)
        Log.d(TAG, "bitmap1===>$bitmap1")
        Log.d(TAG, "bitmap2===>$bitmap2")
        // 2. 测试内存分析，检查是否存在相同的 bitmap
        findViewById<Button>(R.id.hprof).setOnClickListener {
            val dumpFile = File(externalCacheDir, "bitmap.hprof")
            Log.d(TAG, "dump hprof file path: ${dumpFile.absolutePath}")
            Debug.dumpHprofData(dumpFile.absolutePath)
        }
        // 3. 测试不同场景下的卡顿问题，并采集 CPU&进程&线程相关数据助于分析
        findViewById<Button>(R.id.monitorCpuOfIo).setOnClickListener {
            mProcessCpuTracker.update()
            testIO()
            mProcessCpuTracker.update()
            Log.d(TAG, "GC CPU changed => " + mProcessCpuTracker.printCurrentState(SystemClock.uptimeMillis()))
        }

        findViewById<Button>(R.id.monitorCpuOfGc).setOnClickListener {
            mProcessCpuTracker.update()
            testGc()
            handler.postDelayed({
                mProcessCpuTracker.update()
                Log.d(TAG, "IO CPU changed => " + mProcessCpuTracker.printCurrentState(SystemClock.uptimeMillis()))
            }, 5000)
        }
    }

    /**
     * 一般来说，crash捕获初始化都会放到Application中，这里主要是为了大家有机会可以把崩溃文件输出到sdcard中
     * 做进一步的分析
     */
    private fun initBreakPad() {
        if (externalReportPath == null) {
            externalReportPath = File(filesDir, "crashDump")
            if (!externalReportPath!!.exists()) {
                externalReportPath!!.mkdirs()
            }
        }
        BreakpadInitializer().initializeBreakpad(externalReportPath!!.absolutePath)
    }


    private fun initExternalReportPath() {
        externalReportPath = File(Environment.getExternalStorageDirectory(), "crashDump")
        if (!externalReportPath!!.exists()) {
            externalReportPath!!.mkdirs()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        initExternalReportPath()
    }

    private fun testIO() {
        val thread = Thread {
            writeSth()
            try {
                Thread.sleep(100000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        thread.name = "SingleThread"
        thread.start()
    }


    private fun testGc() {
        for (i in 0..9999) {
            val test = IntArray(100000)
            System.gc()
        }
    }


    private fun writeSth() {
        try {
            val f = File(filesDir, "aee.txt")
            if (f.exists()) {
                f.delete()
            }
            val fos = FileOutputStream(f)
            val data = ByteArray(1024 * 4 * 3000)
            for (i in 0..29) {
                Arrays.fill(data, i.toByte())
                fos.write(data)
                fos.flush()
            }
            fos.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    external fun crash()

    companion object {
        const val WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 100
        const val TAG = "MainActivity"
        init {
            System.loadLibrary("crash-lib")
        }
    }
}