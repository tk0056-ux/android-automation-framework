#ifndef TIME_MOD_H
#define TIME_MOD_H

#include <string>
#include "lua.hpp" // 确保能引用到 lua 相关的头文件

// 声明 C++ 逻辑函数
std::string get_current_timestamp(bool isMilliseconds = false);

// 声明暴露给 Lua 的包装函数
int lua_get_timestamp(lua_State* L);

#endif