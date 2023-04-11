//
// Created by Dorck on 2023/4/10.
//
#include <jni.h>
#include <string>
#include <android/log.h>
#include "bytehook.h"
#include "str_utils.h"

#define LOG_TAG "【Thread-Hook】"
#define LOG(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define HOOKER_JNI_CLASS_NAME "com/dorck/android/thread/hook/ThreadHooker"

// Current JavaVM
static JavaVM *jvm;
static jclass jHookerClz;
static jmethodID jHookerMethodGetStack;
// Hook 存根，用于unhook
static bytehook_stub_t stub;

std::atomic<bool> alreadyHooked;

void printJavaStack() {
    JNIEnv* jniEnv;
    // JNIEnv 是绑定线程的，所以这里要重新取
    jvm->GetEnv((void**)&jniEnv, JNI_VERSION_1_6);
    jstring java_stack = static_cast<jstring>(jniEnv->CallStaticObjectMethod(jHookerClz, jHookerMethodGetStack));
    if (nullptr == java_stack) {
        return;
    }
    char* stack = jstringToChars(jniEnv, java_stack);
    LOG("Stack info from Java: %s", stack);
    free(stack);

    jniEnv->DeleteLocalRef(java_stack);
}


// Proxy of hooked method.
int pthreadCreateProxy(
        pthread_t* thread,
        const pthread_attr_t* attr,
        void* (*start_routine) (void *),
        void* arg) {
    // 执行 stack 清理
    BYTEHOOK_STACK_SCOPE();
    // Print java stack info again.
    printJavaStack();
    int result = BYTEHOOK_CALL_PREV(pthreadCreateProxy, thread, attr,
                                    reinterpret_cast<void *(*)(void *)>(start_routine),
                                    arg);
    LOG("pthreadCreateProxy finished, result: %d", result);
    LOG("Thread id: %d", thread);
    return result;
}

static bool allow_filter(const char *caller_path_name, void *arg) {
    (void)arg;

    if (NULL != strstr(caller_path_name, "libc.so")) return false;
    if (NULL != strstr(caller_path_name, "libbase.so")) return false;
    if (NULL != strstr(caller_path_name, "liblog.so")) return false;
    if (NULL != strstr(caller_path_name, "libunwindstack.so")) return false;
    if (NULL != strstr(caller_path_name, "libutils.so")) return false;
    // ......

    return true;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dorck_android_thread_hook_ThreadHooker_nativeHookThread(JNIEnv *env, jclass clazz) {
    if (alreadyHooked) {
        LOG("You have already hooked this thread.");
        return false;
    }
    LOG("Start to hook...");
    alreadyHooked = true;
    void *pthread_create_proxy = (void *) pthreadCreateProxy;
    stub = bytehook_hook_partial(allow_filter, nullptr, nullptr, "pthread_create", pthread_create_proxy, nullptr,
                          nullptr);
    return true;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dorck_android_thread_hook_ThreadHooker_nativeUnhookThread(JNIEnv *env, jclass clazz) {
    // TODO: implement unhookThread()
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOG("JNI_OnLoad");
    if (nullptr == vm) {
        return JNI_ERR;
    }
    jvm = vm;
    JNIEnv *env;
    if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    if (nullptr == env) return JNI_ERR;
    jclass jclz;
    if (nullptr == (jclz = env->FindClass(HOOKER_JNI_CLASS_NAME))) return JNI_ERR;
    jHookerClz = reinterpret_cast<jclass>(env->NewGlobalRef(jclz));
    jHookerMethodGetStack = env->GetStaticMethodID(jHookerClz, "getStack", "()Ljava/lang/String;");
    if (nullptr == jHookerMethodGetStack) return JNI_ERR;
    return JNI_VERSION_1_6;
}