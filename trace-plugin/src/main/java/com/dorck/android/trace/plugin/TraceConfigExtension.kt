package com.dorck.android.trace.plugin

/**
 * Extension for method tracing config.
 * @author Dorck
 * @since 2023/04/12
 */
open class TraceConfigExtension {
    var traceEnable: Boolean = true
    var output: String = ""
    var whiteListFile: String? = ""
    var supportIncremental: Boolean = false
    var packageList: List<String> = emptyList()

    override fun toString(): String {
        return """
            {
                traceEnable: $traceEnable,
                output: $output,
                whiteList: $whiteListFile,
                supportIncremental: $supportIncremental,
                packageList: $packageList
            }
        """.trimIndent()
    }
}