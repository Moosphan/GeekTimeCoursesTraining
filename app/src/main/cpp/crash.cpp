//
// Created by Dorck on 2023/3/5.
//

#include <stdio.h>
#include <jni.h>
#include <jni.h>


/**
 * 引起 crash
 */
void Crash() {
    volatile int *a = (int *) (NULL);
    *a = 1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dorck_android_geektime_MainActivity_crash(JNIEnv* env,jobject thiz) {
    Crash();
}
