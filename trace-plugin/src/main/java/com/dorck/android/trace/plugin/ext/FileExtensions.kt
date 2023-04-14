package com.dorck.android.trace.plugin.ext

import com.google.gson.Gson
import com.google.gson.JsonArray
import org.gradle.internal.hash.Hashing
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader

/**
 * 检测class文件格式和白名单校验(e.g,过滤R.class|Manifest|BuildConfig等文件)
 * packageList不为空，只跟踪特定包名下的class
 */
fun File.requireTrack(whiteList: List<String>, packageList: List<String> = emptyList()): Boolean {
    return name.requireTrack(whiteList, packageList)
}

fun String.requireTrack(whiteList: List<String>, packageList: List<String> = emptyList()): Boolean {
    val isClazzFile = endsWith(".class")
    var isInWhiteList = false
    var isTrackPackage = false
    whiteList.forEach {
        if (contains(it)) {
            isInWhiteList = true
        }
    }
    packageList.forEach {
        if (contains(it)) {
            isTrackPackage = true
        }
    }
    return isClazzFile && !isInWhiteList && (packageList.isEmpty() || isTrackPackage)
}

/**
 * 根据路径生成一个唯一的jar文件名
 */
fun File.uniqueName(): String {
    val origJarName = name
    val hashing = Hashing.sha1().hashString(path).toString()
    val dotPos = origJarName.lastIndexOf('.')
    return if (dotPos < 0) {
        "${origJarName}_$hashing"
    } else {
        val nameWithoutDotExt = origJarName.substring(0, dotPos)
        val dotExt = origJarName.substring(dotPos)
        "${nameWithoutDotExt}_$hashing$dotExt"
    }
}

/**
 * 从配置文件中读取class白名单(名单内的class无需统计耗时)
 */
fun readWhiteListFromJsonFile(path: String): List<String> {
    val resultList = ArrayList<String>()

    try {
        val isr = InputStreamReader(FileInputStream(path))
        val gson = Gson()
        val jsonArray = gson.fromJson(isr, JsonArray::class.java)

        for (i in 0 until jsonArray.size()) {
            val jsonElement = jsonArray.get(i)
            val elementStr = gson.toJson(jsonElement)
            resultList.add(elementStr)
        }

        isr.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return resultList
}