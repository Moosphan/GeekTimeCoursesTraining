//
// Created by Dorck on 2023/4/10.
//

#ifndef GEEKTIMETRAINING_STR_UTILS_H
#define GEEKTIMETRAINING_STR_UTILS_H
#include <jni.h>

char *jstringToChars(JNIEnv *env, jstring jstr) {
    if (jstr == nullptr) {
        return nullptr;
    }

    jboolean isCopy = JNI_FALSE;
    const char *str = env->GetStringUTFChars(jstr, &isCopy);
    char *ret = strdup(str);
    env->ReleaseStringUTFChars(jstr, str);
    return ret;
}
#endif //GEEKTIMETRAINING_STR_UTILS_H
