#ifndef SYSTEMMOD_H
#define SYSTEMMOD_H

#include <unistd.h>      // 延时
#include <stdlib.h>      // system
#include <android/log.h> // 日志
#include <android/asset_manager.h> // 新增：资源管理头文件
#include "lua.hpp"

// --- 全局变量声明 ---
// 声明在 LuaEngine.cpp 中定义的全局资源管理器
extern AAssetManager* global_assets;

// --- 模块注册函数 ---
// 模块注册函数：让总调度调用
void register_system_mod(lua_State *L);

// --- 具体的 Lua 注册函数声明 ---

// 打印 日志 调试输出
int lua_debug_print(lua_State *L);

// 延时命令
int lua_msleep_ext(lua_State *L);

// 共享变量读写
int lua_set_shared_var(lua_State *L);
int lua_get_shared_var(lua_State *L);

// 启动线程（多线程内存加载）
int lua_create_vm(lua_State *L);

// 类型转换与连接
int lua_to_text(lua_State *L);
int lua_to_int(lua_State *L);
int lua_to_double(lua_State *L);
int lua_get_type(lua_State *L);
int lua_super_concat(lua_State *L);

// 调用 Java 静态方法
int lua_call_java(lua_State *L);

// --- 内部辅助函数声明 ---
// 从 Assets 内存加载 Lua 脚本的工具函数
bool loadLuaFromAssets(lua_State *L, const char* fileName);

#endif