package com.dorck.android.tracker

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import com.dorck.android.tracker.os.Sysconf
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * An simple used APM tool just for getting cpu activity records.
 * @reference {@link https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r36/core/java/com/android/internal/os/ProcessCpuTracker.java}
 * @author Dorck
 * @since 2023/03/22
 */
open class SimpleProcessTracker(val processId: Int) {
    // How long a CPU jiffy is in milliseconds.
    private var mJiffyMillis: Long = 0
    private var mLoad1 = 0f
    private var mLoad5 = 0f
    private var mLoad15 = 0f

    // All times are in milliseconds. They are converted from jiffies to milliseconds
    // when extracted from the kernel.
    private var mCurrentSampleTime: Long = 0
    private var mLastSampleTime: Long = 0
    private var mCurrentSampleRealTime: Long = 0
    private var mLastSampleRealTime: Long = 0
    private var mCurrentSampleWallTime: Long = 0
    private var mLastSampleWallTime: Long = 0
    private var mBaseUserTime: Long = 0
    private var mBaseSystemTime: Long = 0
    private var mBaseIoWaitTime: Long = 0
    private var mBaseIrqTime: Long = 0
    private var mBaseSoftIrqTime: Long = 0
    private var mBaseIdleTime: Long = 0
    private var mRelUserTime = 0
    private var mRelSystemTime = 0
    private var mRelIoWaitTime = 0
    private var mRelIrqTime = 0
    private var mRelSoftIrqTime = 0
    private var mRelIdleTime = 0
    private var mRelStatsAreGood = false
    private var mBuffer = ByteArray(4096)
    private var DEBUG = true
    private var mCurrentProcStat: Stats

    private val sLoadComparator: Comparator<Stats> =
        Comparator<Stats> { sta, stb ->
            val ta: Int = sta.rel_utime + sta.rel_stime
            val tb: Int = stb.rel_utime + stb.rel_stime
            if (ta != tb) {
                if (ta > tb) -1 else 1
            } else 0
        }

    init {
        val jiffyHz = Sysconf.getScClkTck()
        mJiffyMillis = 1000/jiffyHz
        mCurrentProcStat = Stats(processId, false)
    }

    fun update() {
        Log.d(TAG, "start update process info.")
        val nowUptime = SystemClock.uptimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowWallTime = System.currentTimeMillis()
        // Note: 当前版本可能没有权限访问 /proc/stat 文件。
        // 通常，只有 root 用户或有 root 用户权限的应用程序才能够访问这一文件。
        val sysCpu = readProcFile("/proc/stat")
        // Parse multi cpu data from /proc/stat.
        Log.d(TAG, "split cpu info from origin data: $sysCpu")
        if (sysCpu != null) {
// Total user time is user + nice time.

            // Total user time is user + nice time.
            val usertime =
                (sysCpu[SYSTEM_STATS_USER_TIME].toLong() + sysCpu[SYSTEM_STATS_NICE_TIME].toLong()) * mJiffyMillis
            // Total system time is simply system time.
            val systemtime =
                sysCpu[SYSTEM_STATS_SYS_TIME].toLong() * mJiffyMillis
            // Total idle time is simply idle time.
            val idletime =
                sysCpu[SYSTEM_STATS_IDLE_TIME].toLong() * mJiffyMillis
            // Total irq time is iowait + irq + softirq time.
            val iowaittime =
                sysCpu[SYSTEM_STATS_IOWAIT_TIME].toLong() * mJiffyMillis
            val irqtime =
                sysCpu[SYSTEM_STATS_IRQ_TIME].toLong() * mJiffyMillis
            val softirqtime =
                sysCpu[SYSTEM_STATS_SOFT_IRQ_TIME].toLong() * mJiffyMillis
            // This code is trying to avoid issues with idle time going backwards,
            // but currently it gets into situations where it triggers most of the time. :(

            mRelUserTime = (usertime - mBaseUserTime).toInt()
            mRelSystemTime = (systemtime - mBaseSystemTime).toInt()
            mRelIoWaitTime = (iowaittime - mBaseIoWaitTime).toInt()
            mRelIrqTime = (irqtime - mBaseIrqTime).toInt()
            mRelSoftIrqTime = (softirqtime - mBaseSoftIrqTime).toInt()
            mRelIdleTime = (idletime - mBaseIdleTime).toInt()
            mRelStatsAreGood = true
            if (DEBUG) {
                Log.d(
                    TAG,
                    "Rel User:" + mRelUserTime + " Sys:" + mRelSystemTime
                            + " Idle:" + mRelIdleTime + " Interrupt:" + mRelIrqTime
                )
            }
            mBaseUserTime = usertime
            mBaseSystemTime = systemtime
            mBaseIoWaitTime = iowaittime
            mBaseIrqTime = irqtime
            mBaseSoftIrqTime = softirqtime
            mBaseIdleTime = idletime
        }
        mLastSampleTime = mCurrentSampleTime
        mCurrentSampleTime = nowUptime
        mLastSampleRealTime = mCurrentSampleRealTime
        mCurrentSampleRealTime = nowRealtime
        mLastSampleWallTime = mCurrentSampleWallTime
        mCurrentSampleWallTime = nowWallTime
        // 提取进程名
        getName(mCurrentProcStat, mCurrentProcStat.cmdlineFile!!)
        // 收集进程运行时信息
        collectProcsStats("/proc/self/stat", mCurrentProcStat)
        // 收集线程信息，按照运行时间排序
        if (mCurrentProcStat.workingThreads != null && !mCurrentProcStat.threadsDir.isNullOrEmpty()) {
            val threadsProcFiles = File(mCurrentProcStat.threadsDir!!).listFiles()
            for (thread in threadsProcFiles!!) {
                val threadID = thread.name.toInt()
                Log.d(TAG, "threadId:$threadID")
                var threadStat: Stats? = findThreadStat(threadID, mCurrentProcStat.workingThreads!!)
                if (threadStat == null) {
                    threadStat = Stats(threadID, true)
                    getName(threadStat, threadStat.cmdlineFile ?: "")
                    mCurrentProcStat.workingThreads!!.add(threadStat)
                }
                threadStat.statFile?.let {
                    collectProcsStats(it, threadStat)
                }
            }
            Collections.sort(
                mCurrentProcStat.workingThreads, sLoadComparator
            )
        }

        // 统计 CPU 负载信息
        val loadAverages = readProcFile("/proc/loadavg")

        if (loadAverages != null) {
            val load1 =
                loadAverages[LOAD_AVERAGE_1_MIN].toFloat()
            val load5 =
                loadAverages[LOAD_AVERAGE_5_MIN].toFloat()
            val load15 =
                loadAverages[LOAD_AVERAGE_15_MIN].toFloat()
            if (load1 != mLoad1 || load5 != mLoad5 || load15 != mLoad15) {
                mLoad1 = load1
                mLoad5 = load5
                mLoad15 = load15
            }
        }
        if (DEBUG) {
            Log.i(
                TAG, "*** TIME TO COLLECT STATS: "
                        + (SystemClock.uptimeMillis() - mCurrentSampleTime)
            )
        }
    }

    private fun findThreadStat(id: Int, stats: ArrayList<Stats>): Stats? {
        for (stat in stats) {
            if (stat.pid == id) {
                return stat
            }
        }
        return null
    }

    // 这里只需要获取第一行总cpu数据即可。
    private fun readProcFile(file: String): Array<String>? {
        var procFile: RandomAccessFile? = null
        var procFileContents: String
        return try {
            procFile = RandomAccessFile(file, "r")
            procFileContents = procFile.readLine()
            Log.d(TAG, "readProcFile: readline: $procFileContents")
            val rightIndex = procFileContents.indexOf(")")
            Log.d(TAG, "readProcFile: rightIndex => $rightIndex")
            if (rightIndex > 0) {
                procFileContents = procFileContents.substring(rightIndex + 2)
            }

            procFileContents.split(" ").toTypedArray()
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            // FIXME The `/proc/stat` access denied since Android8.0(API 26).
            Log.d(TAG, "exception: ${ioe.toString()}")
            return null
        } finally {
            procFile?.close()
        }
    }

    fun getName(st: Stats, cmdlineFile: String) {
        var newName = st.name
        if (st.name == null || st.name == "app_process" || st.name == "<pre-initialized>") {
            val cmdName = readFile(cmdlineFile, '\u0000')
            if (!cmdName.isNullOrEmpty() && cmdName.length > 1) {
                newName = cmdName
                val i = newName.lastIndexOf("/")
                if (i > 0 && i < newName.length - 1) {
                    newName = newName.substring(i + 1)
                }
            }
            if (newName == null) {
                newName = st.baseName
            }
        }
        if (st.name == null || newName != st.name) {
            st.name = newName
        }
    }

    private fun readFile(file: String, endChar: Char): String? {
        val mBuffer = ByteArray(4096)
        var fs: FileInputStream? = null
        try {
            fs = FileInputStream(file)
            val len = fs.read(mBuffer)
            fs.close()
            if (len > 0) {
                var i = 0
                while (i < len) {
                    if (mBuffer[i].toInt().toChar() == endChar || mBuffer[i] == 10.toByte()) {
                        break
                    }
                    i++
                }
                return String(mBuffer, 0, i)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            fs?.close()
        }
        return null
    }

    private fun collectProcsStats(procFile: String, st: Stats) {
        val procStats = readProcFile(procFile) ?: return
        //for (int i = 0; i < procStats.length; i++) {
        //    android.util.Log.e(TAG,"i:" + i + ", sys:" + procStats[i]);
        //}
        val status = procStats[PROCESS_STATS_STATUS]
        val minfaults =
            procStats[PROCESS_STATS_MINOR_FAULTS].toLong()
        val majfaults =
            procStats[PROCESS_STATS_MAJOR_FAULTS].toLong()
        val utime =
            procStats[PROCESS_STATS_UTIME].toLong() * mJiffyMillis
        val stime =
            procStats[PROCESS_STATS_STIME].toLong() * mJiffyMillis
        if (DEBUG) {
            Log.v(
                TAG,
                "Stats changed " + st.name + " status:" + status + " pid=" + st.pid
                        + " utime=" + utime + "-" + st.base_utime
                        + " stime=" + stime + "-" + st.base_stime
                        + " minfaults=" + minfaults + "-" + st.base_minfaults
                        + " majfaults=" + majfaults + "-" + st.base_majfaults
            )
        }
        val uptime = SystemClock.uptimeMillis()
        st.rel_uptime = uptime - st.base_uptime
        st.base_uptime = uptime
        st.rel_utime = (utime - st.base_utime).toInt()
        st.rel_stime = (stime - st.base_stime).toInt()
        st.base_utime = utime
        st.base_stime = stime
        st.rel_minfaults = (minfaults - st.base_minfaults).toInt()
        st.rel_majfaults = (majfaults - st.base_majfaults).toInt()
        st.base_minfaults = minfaults
        st.base_majfaults = majfaults
        st.status = status
    }

    private fun printCurrentLoad(): String? {
        val sw = StringWriter()
        val pw = PrintWriter(sw, false)
        pw.print("Load: ")
        pw.print(mLoad1)
        pw.print(" / ")
        pw.print(mLoad5)
        pw.print(" / ")
        pw.println(mLoad15)
        pw.flush()
        return sw.toString()
    }

    @SuppressLint("SimpleDateFormat")
    fun printCurrentState(now: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        val sw = StringWriter()
        val pw = PrintWriter(sw, false)
        pw.println("")
        pw.print("CPU usage from ")
        if (now > mLastSampleTime) {
            pw.print(now - mLastSampleTime)
            pw.print("ms to ")
            pw.print(now - mCurrentSampleTime)
            pw.print("ms ago")
        } else {
            pw.print(mLastSampleTime - now)
            pw.print("ms to ")
            pw.print(mCurrentSampleTime - now)
            pw.print("ms later")
        }
        pw.print(" (")
        pw.print(sdf.format(Date(mLastSampleWallTime)))
        pw.print(" to ")
        pw.print(sdf.format(Date(mCurrentSampleWallTime)))
        pw.print(")")
        val sampleTime = mCurrentSampleTime - mLastSampleTime
        val sampleRealTime = mCurrentSampleRealTime - mLastSampleRealTime
        val percAwake = if (sampleRealTime > 0) sampleTime * 100 / sampleRealTime else 0
        if (percAwake != 100L) {
            pw.print(" with ")
            pw.print(percAwake)
            pw.print("% awake")
        }
        pw.println(":")
        val totalTime = (mRelUserTime + mRelSystemTime + mRelIoWaitTime
                + mRelIrqTime + mRelSoftIrqTime + mRelIdleTime)
        val st: Stats = mCurrentProcStat
        printProcessCPU(
            pw,
            st.pid, st.name ?: "", st.status ?: "", st.rel_uptime.toInt(),
            st.rel_utime, st.rel_stime, 0, 0, 0, 0, st.rel_minfaults, st.rel_majfaults
        )
        if (st.workingThreads != null) {
            pw.println("thread stats:")
            val M: Int = st.workingThreads!!.size
            for (j in 0 until M) {
                val tst: Stats = st.workingThreads!!.get(j)
                printProcessCPU(
                    pw,
                    tst.pid, tst.name ?: "", tst.status ?: "", st.rel_uptime.toInt(),
                    tst.rel_utime, tst.rel_stime, 0, 0, 0, 0, tst.rel_minfaults, tst.rel_majfaults
                )
            }
        }
        printProcessCPU(
            pw, -1, "TOTAL", "", totalTime, mRelUserTime, mRelSystemTime,
            mRelIoWaitTime, mRelIrqTime, mRelSoftIrqTime, mRelIdleTime, 0, 0
        )
        pw.println(printCurrentLoad())
        if (DEBUG) {
            Log.i(
                TAG,
                "totalTime " + totalTime + " over sample time "
                        + (mCurrentSampleTime - mLastSampleTime) + ", real uptime:" + st.rel_uptime
            )
        }
        pw.flush()
        return sw.toString()
    }

    private fun printRatio(pw: PrintWriter, numerator: Long, denominator: Long) {
        val thousands = numerator * 1000 / denominator
        val hundreds = thousands / 10
        pw.print(hundreds)
        if (hundreds < 10) {
            val remainder = thousands - hundreds * 10
            if (remainder != 0L) {
                pw.print('.')
                pw.print(remainder)
            }
        }
    }

    private fun printProcessCPU(
        pw: PrintWriter, pid: Int, label: String, status: String,
        totalTime: Int, user: Int, system: Int, iowait: Int, irq: Int, softIrq: Int, idle: Int,
        minFaults: Int, majFaults: Int
    ) {
        var totalTime = totalTime
        if (totalTime == 0) {
            totalTime = 1
        }
        printRatio(pw, (user + system + iowait + irq + softIrq + idle).toLong(), totalTime.toLong())
        pw.print("% ")
        if (pid >= 0) {
            pw.print(pid)
            pw.print("/")
        }
        pw.print("$label($status)")
        pw.print(": ")
        printRatio(pw, user.toLong(), totalTime.toLong())
        pw.print("% user + ")
        printRatio(pw, system.toLong(), totalTime.toLong())
        pw.print("% kernel")
        if (iowait > 0) {
            pw.print(" + ")
            printRatio(pw, iowait.toLong(), totalTime.toLong())
            pw.print("% iowait")
        }
        if (irq > 0) {
            pw.print(" + ")
            printRatio(pw, irq.toLong(), totalTime.toLong())
            pw.print("% irq")
        }
        if (softIrq > 0) {
            pw.print(" + ")
            printRatio(pw, softIrq.toLong(), totalTime.toLong())
            pw.print("% softirq")
        }
        if (idle > 0) {
            pw.print(" + ")
            printRatio(pw, idle.toLong(), totalTime.toLong())
            pw.print("% idle")
        }
        if (minFaults > 0 || majFaults > 0) {
            pw.print(", faults:")
            if (minFaults > 0) {
                pw.print(" ")
                pw.print(minFaults)
                pw.print(" =>minor")
            }
            if (majFaults > 0) {
                pw.print(" ")
                pw.print(majFaults)
                pw.print(" =>major")
            }
        }
        pw.println()
    }



    companion object {
        private const val TAG = "SimpleProcessTracker"

        // proc/stat
        private const val SYSTEM_STATS_USER_TIME = 2
        private const val SYSTEM_STATS_NICE_TIME = 3
        private const val SYSTEM_STATS_SYS_TIME = 4
        private const val SYSTEM_STATS_IDLE_TIME = 5
        private const val SYSTEM_STATS_IOWAIT_TIME = 6
        private const val SYSTEM_STATS_IRQ_TIME = 7
        private const val SYSTEM_STATS_SOFT_IRQ_TIME = 8
        // proc/self/stat
        private const val PROCESS_STATS_STATUS = 0
        private const val PROCESS_STATS_MINOR_FAULTS = 7
        private const val PROCESS_STATS_MAJOR_FAULTS = 9
        private const val PROCESS_STATS_UTIME = 11
        private const val PROCESS_STATS_STIME = 13

        // proc/loadavg
        private const val LOAD_AVERAGE_1_MIN = 0
        private const val LOAD_AVERAGE_5_MIN = 1
        private const val LOAD_AVERAGE_15_MIN = 2
    }

    class Stats internal constructor(val pid: Int, isThread: Boolean) {
        var statFile: String? = null
        var cmdlineFile: String? = null
        var threadsDir: String? = null
        var workingThreads: ArrayList<Stats>? = null
        var baseName: String? = null
        var name: String? = null

        /**
         * Time in milliseconds.
         */
        var base_uptime: Long = 0

        /**
         * Time in milliseconds.
         */
        var rel_uptime: Long = 0

        /**
         * Time in milliseconds.
         */
        var base_utime: Long = 0

        /**
         * Time in milliseconds.
         */
        var base_stime: Long = 0

        /**
         * Time in milliseconds.
         */
        var rel_utime = 0

        /**
         * Time in milliseconds.
         */
        var rel_stime = 0
        var base_minfaults: Long = 0
        var base_majfaults: Long = 0
        var rel_minfaults = 0
        var rel_majfaults = 0
        var status: String? = null

        init {
            if (isThread) {
                val procDir = File("/proc/self/task", pid.toString())
                workingThreads = null
                statFile = "$procDir/stat"
                cmdlineFile = File(procDir, "comm").toString()
                threadsDir = null
            } else {
                val procDir = File("/proc", pid.toString())
                statFile = File(procDir, "stat").toString()
                cmdlineFile = File(procDir, "cmdline").toString()
                threadsDir = File(procDir, "task").toString()
                workingThreads = ArrayList()
            }
        }
    }
}