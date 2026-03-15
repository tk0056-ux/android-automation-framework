#ifndef LUAENGINE_H
#define LUAENGINE_H

#include <jni.h>

extern "C" {
// 声明你在 .cpp 里实现的那个 JNI 函数
// 确保这里的包名 Java_com_dandantang_autoai_MainActivity 与你 Java 里的 native 方法一致
JNIEXPORT jstring JNICALL
Java_com_dandantang_autoai_MainActivity_runLuaTest(JNIEnv *env, jobject thiz, jstring j_code);
}

#endif