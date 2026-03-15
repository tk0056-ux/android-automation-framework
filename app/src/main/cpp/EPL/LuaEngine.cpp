#include <jni.h>
#include <string>
#include <thread>
#include <android/log.h>
#include "lua.hpp"
#include "systemmod.h"
#include "text_mod.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <malloc.h> // 新增：用于内存分配

// --- 全局变量区 ---
// 这里去掉了 static，以便 systemmod.cpp 可以 extern 使用它
AAssetManager* global_assets = nullptr;
JavaVM* g_jvm = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_dandantang_autoai_MainActivity_nativeInit(JNIEnv *env, jobject thiz, jobject assetManager) {
    if (assetManager != nullptr) {
        // 从 Java 对象获取 C++ 指针
        global_assets = AAssetManager_fromJava(env, assetManager);
        __android_log_print(ANDROID_LOG_INFO, "LuaEngine", "✅ 成功关联 Assets 资源管理器");
    }
}

// 这是一个内部工具函数，用于从内存加载并运行 Assets 里的 Lua 脚本
// 为了让 systemmod.cpp 也能用，这里不设为 static
bool loadLuaFromAssets(lua_State *L, const char* fileName) {
    if (global_assets == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "LuaEngine", "❌ 资源管理器未初始化");
        return false;
    }

    AAsset* asset = AAssetManager_open(global_assets, fileName, AASSET_MODE_BUFFER);
    if (!asset) {
        __android_log_print(ANDROID_LOG_ERROR, "LuaEngine", "❌ 找不到文件: %s", fileName);
        return false;
    }

    size_t size = AAsset_getLength(asset);
    char* buf = (char*)malloc(size);
    if (buf) {
        AAsset_read(asset, buf, size);
        AAsset_close(asset);

        // 加载并运行内存中的代码块
        int status = luaL_loadbuffer(L, buf, size, fileName);
        if (status == LUA_OK) {
            status = lua_pcall(L, 0, LUA_MULTRET, 0);
        }

        if (status != LUA_OK) {
            const char* err = lua_tostring(L, -1);
            __android_log_print(ANDROID_LOG_ERROR, "LuaEngine", "运行失败 [%s]: %s", fileName, err);
        }

        free(buf); // 释放内存
        return status == LUA_OK;
    }

    AAsset_close(asset);
    return false;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// --- 核心：万能调用工具 (供 systemmod.cpp 调用) ---
std::string callJavaStatic(const char* className, const char* methodName, const char* param) {
    if (!g_jvm) return "Error: JVM NULL";

    JNIEnv* env;
    bool isAttached = false;
    jint res = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, NULL) != 0) {
            return "Error: Attach failed";
        }
        isAttached = true;
    }

    std::string result = "Unknown Error";
    jclass clazz = env->FindClass(className);
    if (clazz) {
        jmethodID mid = env->GetStaticMethodID(clazz, methodName, "(Ljava/lang/String;)Ljava/lang/String;");
        if (mid) {
            jstring jParam = env->NewStringUTF(param);
            jstring jResult = (jstring)env->CallStaticObjectMethod(clazz, mid, jParam);

            if (jResult) {
                const char* resultCStr = env->GetStringUTFChars(jResult, NULL);
                result = resultCStr;
                env->ReleaseStringUTFChars(jResult, resultCStr);
            } else {
                result = "Success";
            }
            env->DeleteLocalRef(jParam);
        } else {
            result = "Error: Method not found";
        }
    } else {
        result = "Error: Class not found";
    }

    if (isAttached) g_jvm->DetachCurrentThread();
    return result;
}

// --- 核心：启动子虚拟机的线程执行函数 ---
// 原逻辑保留：支持直接运行字符串代码
void run_new_vm(std::string code) {
    lua_State *L = luaL_newstate();
    luaL_openlibs(L);

    // 给子虚拟机注册所有 C++ 指令
    register_system_mod(L);
    register_text_mod(L);

    if (luaL_dostring(L, code.c_str()) != LUA_OK) {
        const char* err = lua_tostring(L, -1);
        __android_log_print(ANDROID_LOG_ERROR, "LuaEngine", "子脚本错误: %s", err);
    }

    lua_close(L);
}

// --- 新增：启动子虚拟机执行 Assets 文件的线程函数 ---
// 用于支持 启动线程("scripts/test.lua")
void run_vm_from_assets(std::string fileName) {
    lua_State *L = luaL_newstate();
    luaL_openlibs(L);

    register_system_mod(L);
    register_text_mod(L);

    // 调用内存加载函数
    loadLuaFromAssets(L, fileName.c_str());

    lua_close(L);
}

// --- JNI 主入口 (由 Java 点击运行测试调用) ---
extern "C"
JNIEXPORT jstring JNICALL
Java_com_dandantang_autoai_MainActivity_runLuaTest(JNIEnv *env, jobject thiz, jstring j_code) {
    const char *code = env->GetStringUTFChars(j_code, nullptr);

    lua_State *L = luaL_newstate();
    luaL_openlibs(L);

    register_system_mod(L);
    register_text_mod(L);

    std::string result;
    if (luaL_dostring(L, code) != LUA_OK) {
        result = "Lua 错误: ";
        result += lua_tostring(L, -1);
    } else {
        result = "主脚本执行成功";
    }

    lua_close(L);
    env->ReleaseStringUTFChars(j_code, code);
    return env->NewStringUTF(result.c_str());
}