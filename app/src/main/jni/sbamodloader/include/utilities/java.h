// Copyright (c) 2026 XtraCube
#ifndef SBAMODLOADER_JAVA_H
#define SBAMODLOADER_JAVA_H
#include <jni.h>

#define GET_JAVA_STRING(env, javaString, assignment) \
    do { \
        const char *chars = (env)->GetStringUTFChars(javaString, nullptr); \
        assignment = std::string(chars); \
        (env)->ReleaseStringUTFChars(javaString, chars); \
    } while(0)

JNIEnv* getJNIEnv();

jclass find_class_in_app_classloader(JNIEnv *env, const char *className);

jobject get_sba_config(JNIEnv *env);

// The JavaVM the loader was initialised with, or null before init_java().
JavaVM *get_java_vm();

jint getStaticResourceId(JNIEnv* env, const char *name);

jobject getUnityActivity(JNIEnv* env);

void setLoadingState(bool enabled);

void setLoadingText(const char *text);

#endif //SBAMODLOADER_JAVA_H
