package com.dorck.android.trace.plugin.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.atomic.AtomicInteger

/**
 * The ClassVisitor decorator to track method time cost.
 * @author Dorck
 * @since 2023/04/13
 */
class TimeTraceClassVisitor(
    private val api: Int,
    private val visitor: ClassVisitor,
    private val counter: AtomicInteger,
    ) : ClassVisitor(api, visitor) {

    private var isAbsClz = false
    private var className = ""

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        className = name ?: ""
        if ((access and Opcodes.ACC_ABSTRACT) > 0 || (access and Opcodes.ACC_INTERFACE) > 0) {
            isAbsClz = true
        }
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val curMethodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (!isAbsClz) {
            val curMethodCount = counter.incrementAndGet()
            //println("You have tracked method [$className$name], total methods: [$curMethodCount]")
            return TimeTraceMethodVisitor(api, curMethodVisitor, access, name ?: "", descriptor ?: "", className, name ?: "")
        }
        return curMethodVisitor
    }
}