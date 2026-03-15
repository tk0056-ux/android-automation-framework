#ifndef text_mod_h
#define text_mod_h

#include "lua.hpp"

// 文本模块的注册入口
void register_text_mod(lua_State *L);

// 这里可以放你以后要实现的函数声明
int lua_find_text(lua_State *L);

#endif