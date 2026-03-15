#include "time_mod.h"
#include <chrono>

std::string get_current_timestamp(bool isMilliseconds) {
    auto now = std::chrono::system_clock::now();
    auto duration = now.time_since_epoch();
    if (isMilliseconds) {
        return std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(duration).count());
    }
    return std::to_string(std::chrono::duration_cast<std::chrono::seconds>(duration).count());
}

// 这是真正给 Lua 调用的接口
int lua_get_timestamp(lua_State* L) {
    // 默认不传参数或者传 false 时，gettop 为 0 或第一个参数为 false
    bool isMs = false;
    if (lua_gettop(L) > 0) {
        isMs = lua_toboolean(L, 1);
    }

    std::string ts = get_current_timestamp(isMs);
    lua_pushstring(L, ts.c_str());
    return 1; // 返回 1 个参数给 Lua
}