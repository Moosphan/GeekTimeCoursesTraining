package com.dorck.android.trace.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.dorck.android.trace.plugin.ext.IOUtil
import com.dorck.android.trace.plugin.ext.readWhiteListFromJsonFile
import com.dorck.android.trace.plugin.ext.requireTrack
import com.dorck.android.trace.plugin.visitor.TimeTraceClassVisitor
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Transform to record methods' time cost.
 * @author Dorck
 * @since 2023/04/13
 * TODO:
 * 1.支持混淆方法的反编译
 * 2.重量级耗时方法自动收集归纳
 */
class MethodTraceTransform(
    private val traceConfiguration: TraceConfigExtension,
    private val project: Project
    ) : Transform() {
    private var mWhiteList: List<String> = emptyList()
    private val mMethodCounter: AtomicInteger = AtomicInteger()

    init {
        if (!traceConfiguration.whiteListFile.isNullOrEmpty()) {
            mWhiteList = readWhiteListFromJsonFile(traceConfiguration.whiteListFile!!)
            project.logger.error("MethodTraceTransform, init, whitelist: $mWhiteList")
        }
    }

    override fun getName(): String {
        return TAG
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun isIncremental(): Boolean {
        return traceConfiguration.supportIncremental
    }

    override fun transform(transformInvocation: TransformInvocation) {
        super.transform(transformInvocation)
        log("Started transform.....")
        val trsStartTime = System.currentTimeMillis()
        val isIncremental = transformInvocation.isIncremental
        if (!isIncremental) {
            transformInvocation.outputProvider.deleteAll()
        }

//        val outputDir =  File(traceConfiguration.output, "classes/${TAG}/")
//        if (!outputDir.exists()) {
//            outputDir.mkdirs()
//        }
        transformInvocation.inputs.forEach {
            log("Transform jar input size: ${it.jarInputs.size}, dir input size: ${it.directoryInputs.size}")
            it.jarInputs.forEach { jarInput ->
                collectAndHandleJars(jarInput, transformInvocation.outputProvider, isIncremental)
            }
            it.directoryInputs.forEach { dirInput ->
                collectAndHandleDirectories(dirInput, transformInvocation.outputProvider, isIncremental)
            }
        }

        log("The transform time cost: ${System.currentTimeMillis() - trsStartTime}ms")
    }

    // 1.创建output文件夹
    // 2.处理增量更新
    // 3.根据输入文件树生成输出文件树
    // 4.更改输入侧.class字节码并保存
    // 5.讲输入侧文件树内容复制到输出侧
    private fun collectAndHandleDirectories(
        dirInput: DirectoryInput,
        outputProvider: TransformOutputProvider,
        incremental: Boolean
    ) {
        val inputDir = dirInput.file
        val outputDir = outputProvider.getContentLocation(dirInput.name, dirInput.contentTypes, dirInput.scopes, Format.DIRECTORY)
        if (incremental) {
            dirInput.changedFiles.forEach { (file, status) ->
                val changedInputFilePath = file.absolutePath
                val changedOutputFile = File(
                    changedInputFilePath.replace(inputDir.absolutePath, outputDir.absolutePath))
                if (status == Status.ADDED || status == Status.CHANGED) {
                    // Let changed files be transformed with our injected methods.
                    transformClassFile(file, changedOutputFile)
                } else if (status == Status.REMOVED) {
                    changedOutputFile.delete()
                }
            }
        } else {
            handleDirectoriesTransform(inputDir, outputDir)
        }
    }

    /**
     * 递归预创建output文件树，然后寻找.class格式文件并执行代码插桩后复制到输出文件
     */
    private fun handleDirectoriesTransform(inputDir: File, outputDir: File) {
        if (!inputDir.isDirectory) {
            return
        }
        val childrenFiles = inputDir.listFiles()
        childrenFiles?.forEach {
            if (it.isFile) {
                val realOutputFile = File(it.absolutePath.replace(inputDir.absolutePath, outputDir.absolutePath))
                if (!realOutputFile.exists()) {
                    realOutputFile.parentFile.mkdirs()
                }
                realOutputFile.createNewFile()
                transformClassFile(it, realOutputFile)
            } else {
                // 继续递归找到 class 文件
                handleDirectoriesTransform(it, outputDir)
            }
        }
    }

    /**
     * 基于ASM执行具体的代码插桩操作
     */
    private fun transformClassFile(src: File, dest: File) {
        val inputStream = FileInputStream(src)
        val outputStream = FileOutputStream(dest)
        try {
            if (src.requireTrack(mWhiteList, traceConfiguration.packageList)) {
                // 字节码插桩
                val classReader = ClassReader(inputStream)
                val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
                val classVisitor = TimeTraceClassVisitor(Opcodes.ASM9, classWriter, mMethodCounter)
                classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                // 将修改的class文件写到output文件
                outputStream.write(classWriter.toByteArray())
                inputStream.close()
                outputStream.close()
            } else {
                outputStream.write(IOUtil.toByteArray(inputStream)!!)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            inputStream.close()
            outputStream.close()
        }
    }

    private fun collectAndHandleJars(
        input: JarInput,
        outputProvider: TransformOutputProvider,
        incremental: Boolean
    ) {
        val jarInput = input.file
        val jarOutput = outputProvider.getContentLocation(input.name, input.contentTypes, input.scopes, Format.JAR)
        if (incremental) {
            if (input.status == Status.ADDED || input.status == Status.CHANGED) {
                handleJarsTransform(jarInput, jarOutput)
            } else if (input.status == Status.REMOVED) {
                outputProvider.deleteAll()
            }
        } else {
            handleJarsTransform(jarInput, jarOutput)
        }
    }

    /**
     * 1.将jarInput内容全部复制到jarOutput
     * 2.遇到class文件则需要先执行asm操作后再复制过去
     */
    private fun handleJarsTransform(jarInput: File, jarOutput: File) {
//        var jarOutputStream: JarOutputStream? = null
//        var jarFile: JarFile? = null
//        try {
//            jarFile = JarFile(jarInput)
//            jarOutputStream = JarOutputStream(FileOutputStream(jarOutput))
//            val enumeration: Enumeration<JarEntry> = jarFile.entries()
//            // 逐条遍历(如果遇到嵌套jar如何处理？)
//            while (enumeration.hasMoreElements()) {
//                val jarEntry = enumeration.nextElement()
//                val entryName = jarEntry.name
//                val entryInputStream = jarFile.getInputStream(jarEntry)
//                jarOutputStream.putNextEntry(JarEntry(jarEntry.name))
//                // 找到class文件&不处于白名单
//                if (entryName.requireTrack(mWhiteList, traceConfiguration.packageList)) {
//                    val classReader = ClassReader(entryInputStream)
//                    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
//                    val classVisitor = TimeTraceClassVisitor(Opcodes.ASM9, classWriter, mMethodCounter)
//                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
//                    // 将修改后的class写入output
//                    jarOutputStream.write(classWriter.toByteArray())
//                } else {
//                    jarOutputStream.write(IOUtil.toByteArray(entryInputStream)!!)
//                }
//                jarOutputStream.closeEntry()
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            jarOutputStream?.closeEntry()
//        } finally {
//            jarOutputStream?.closeEntry()
//        }

        JarFile(jarInput).use { srcJarFile ->
            JarOutputStream(FileOutputStream(jarOutput)).use { destJarFileOs ->
                val enumeration: Enumeration<JarEntry> = srcJarFile.entries()
                //遍历srcJar中的每一条目
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    srcJarFile.getInputStream(entry).use { entryIs ->
                        destJarFileOs.putNextEntry(JarEntry(entry.name))
                        if (entry.name.requireTrack(mWhiteList, traceConfiguration.packageList)) { //如果是class文件
                            // 通过asm修改class文件并写入output
                            val classReader = ClassReader(entryIs)
                            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
                            val classVisitor = TimeTraceClassVisitor(Opcodes.ASM9, classWriter, mMethodCounter)
                            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                            destJarFileOs.write(classWriter.toByteArray())
                        } else {
                            // 非class原样复制到destJar中
                            destJarFileOs.write(IOUtil.toByteArray(entryIs)!!)
                        }
                        destJarFileOs.closeEntry()
                    }
                }
            }
        }
    }

    private fun log(message: String) {
        project.logger.error("[$TAG] $message")
    }

    companion object {
        private const val TAG = "MethodTraceTransform"
    }
}