//
// Created by Administrator on 2026/3/11.
//

#ifndef JAVA_ENGINE_H
#define JAVA_ENGINE_H

#include <jni.h>
#include <string>

class JavaEngine {
public:
    // 初始化，传入 JNI 环境
    static void init(JNIEnv* env, jobject context);

    // 核心验证方法：调用 Java 层的截图
    // 返回值：布尔类型 (对应 Lua 的 真/假)
    static bool callJavaScreenshot(const std::string& savePath);

private:
    static JNIEnv* m_env;
    static jobject m_javaObject; // 指向 Java 层的具体实现类
};

#endif //JAVA_ENGINE_H