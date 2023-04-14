package com.dorck.android.trace.plugin

import com.dorck.android.trace.plugin.ext.android
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import java.io.File

/**
 * A plugin for tracking the time spent on method calls based on transformers.
 * @author Dorck
 * @since 2023/04/12
 */
class MethodTracePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.log(LogLevel.ERROR, "[MethodTracePlugin] => apply.")
        val extension = project.extensions.create("trace", TraceConfigExtension::class.java)
        if (!project.plugins.hasPlugin("com.android.application")) {
            // Only works on app modules.
            throw GradleException("You need to apply this plugin in app module.")
        }
        project.afterEvaluate {

        }
        if (!extension.traceEnable) {
            return
        }
        if (extension.output.isEmpty()) {
            extension.output = project.buildDir.absolutePath + File.separator + "trace_result"
        }
        project.logger.error("extension: $extension")
        project.logger.log(LogLevel.ERROR, "The trace result will be saved at: ${extension.output}")
        val methodTraceTransform = MethodTraceTransform(extension, project)/*.also {
                it.setOutputDirectory(project.objects.directoryProperty().apply {
                    set(File(extension.output))
                })
            }*/
        project.android().registerTransform(methodTraceTransform)
    }
}