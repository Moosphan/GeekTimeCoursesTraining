package com.dorck.android.trace.plugin.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter

/**
 * A method visitor to modify bytecodes.
 * TODO: 相比于onMethodEnter和onMethodExit中处理大量trace逻辑，不如委托给自己的抽象方法，业务层具体实现
 * @author Dorck
 * @since 2023/04/14
 */
class TimeTraceMethodVisitor(
    api: Int,
    methodVisitor: MethodVisitor,
    access: Int,
    name: String,
    descriptor: String,
    val className: String,
    val methodName: String
) : AdviceAdapter(api, methodVisitor, access, name, descriptor) {

    private var startTimeIndex: Int = 0
    private var endTimeIndex: Int = 0
    private var timeCostIndex: Int = 0

    override fun onMethodEnter() {
        // long startTime = System.currentTimeMillis();
        mv?.visitMethodInsn(INVOKESTATIC,
            "java/lang/System", "currentTimeMillis", "()J", false)
        startTimeIndex = newLocal(Type.LONG_TYPE)
        mv?.visitVarInsn(LSTORE, startTimeIndex)
    }

    override fun onMethodExit(opcode: Int) {
        val methodSignature = "$className#$methodName"
        // long endTime = System.currentTimeMillis();
        mv?.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/System",
            "currentTimeMillis",
            "()J",
            false
        )
        endTimeIndex = newLocal(Type.LONG_TYPE)
        mv?.visitVarInsn(LSTORE, endTimeIndex)
        // 先将endTime压栈，再存入startTime
        mv?.visitVarInsn(LLOAD, endTimeIndex)
        mv?.visitVarInsn(LLOAD, startTimeIndex)
        // long timeCost = endTime - startTime;
        mv?.visitInsn(LSUB)
        timeCostIndex = newLocal(Type.LONG_TYPE)
        mv?.visitVarInsn(LSTORE, timeCostIndex)

        ////////////////////////////////////////////////////////////////////////////
        // Log.d("MethodTracer", String.format("Time of method [%s] calling: %d ms", method, timeCost));

        ////////////////////////////////////////////////////////////////////////////

        // System.out.println("Time of method calling: " + timeCost);
        mv?.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
        mv?.visitTypeInsn(NEW, "java/lang/StringBuilder")
        mv?.visitInsn(DUP)
        mv?.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
        mv?.visitLdcInsn("Time of method calling: ")
        mv?.visitMethodInsn(INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        )
        mv?.visitVarInsn(LLOAD, timeCostIndex)
        mv?.visitMethodInsn(INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(J)Ljava/lang/StringBuilder;",
            false
        )
        // 拼接class+method
        mv?.visitLdcInsn("ms, name[$methodSignature]");
        mv?.visitMethodInsn(INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false
        );
        mv?.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "toString",
            "()Ljava/lang/String;",
            false
        )
        mv?.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/io/PrintStream",
            "println",
            "(Ljava/lang/String;)V",
            false
        )
    }

}