#include "systemmod.h"
#include <string>
#include <map>
#include <mutex>
#include <thread>  // 补上这个，解决 std::thread 报错
#include <android/log.h> // 确保日志宏可用
#include <fstream>
#include <sstream>
#include <unistd.h> // 补上这个，解决 usleep 报错

// 外部函数声明 (从 LuaEngine.cpp 引入)
extern void run_new_vm(std::string code);
extern void run_vm_from_assets(std::string fileName); // 新增：从 Assets 启动线程的函数
extern std::string callJavaStatic(const char* className, const char* methodName, const char* param);

// --- 全局共享数据区 ---
static std::mutex g_share_mutex;
static std::map<std::string, std::string> g_global_vars;

// 设置共享变量
int lua_set_shared_var(lua_State *L) {
    const char* key = luaL_checkstring(L, 1);
    const char* value = luaL_checkstring(L, 2);

    std::lock_guard<std::mutex> lock(g_share_mutex);
    g_global_vars[key] = value;
    return 0;
}

// 获取共享变量
int lua_get_shared_var(lua_State *L) {
    const char* key = luaL_checkstring(L, 1);

    std::lock_guard<std::mutex> lock(g_share_mutex);
    auto it = g_global_vars.find(key);
    if (it != g_global_vars.end()) {
        lua_pushstring(L, it->second.c_str());
    } else {
        lua_pushnil(L);
    }
    return 1;
}

// --- 延时命令 ---
int lua_msleep_ext(lua_State *L) {
    long value = luaL_checkinteger(L, 1);
    int type = (lua_gettop(L) >= 2) ? (int)luaL_checkinteger(L, 2) : 0;

    long microseconds = 0;
    switch (type) {
        case 1: microseconds = value * 1000000; break; // 秒
        case 2: microseconds = value * 60000000; break; // 分
        default: microseconds = value * 1000; break; // 毫秒
    }
    if (microseconds > 0) usleep(microseconds);
    return 0;
}

// --- 调试输出 (修复了栈平衡问题) ---
int lua_debug_print(lua_State *L) {

    int n = lua_gettop(L);

// 建议使用 luaL_tolstring，这样即使 Lua 传的是数字也能打印，不会报错

    if (n >= 2) {

        const char *tag = luaL_tolstring(L, 1, NULL);

        const char *msg = luaL_tolstring(L, 2, NULL);

        __android_log_print(ANDROID_LOG_INFO, tag, "%s", msg);

    } else if (n == 1) {

        const char *msg = luaL_tolstring(L, 1, NULL);

        __android_log_print(ANDROID_LOG_INFO, "LuaLog", "%s", msg);

    }

    return 0;

}

// --- 调用 Java ---
int lua_call_java(lua_State *L) {
    const char* cls = luaL_checkstring(L, 1);
    const char* mth = luaL_checkstring(L, 2);
    const char* prm = luaL_optstring(L, 3, "");
    std::string res = callJavaStatic(cls, mth, prm);
    lua_pushstring(L, res.c_str());
    return 1;
}

// 启动线程(Assets 文件路径)
int lua_create_vm(lua_State *L) {
    // 获取 Lua 传进来的路径，例如 "scripts/monitor.lua"
    const char* path = luaL_checkstring(L, 1);
    std::string filePath(path);

    // 开启新线程
    // 修改为调用 run_vm_from_assets，实现内存加载，防止被破解
    std::thread([filePath]() {
        run_vm_from_assets(filePath);
    }).detach(); // 分离线程，让它独立运行

    return 0;
}

// 到文本 (对应 tostring)
int lua_to_text(lua_State *L) {
    // 检查是否有任何参数输入
    luaL_checkany(L, 1);
    // luaL_tolstring 会处理数字、布尔、甚至 table 并将其转为字符串压入栈
    luaL_tolstring(L, 1, NULL);
    return 1;
}
// 到小数 (将文本或整数转为浮点数)
int lua_to_double(lua_State *L) {
    if (lua_isnumber(L, 1)) {
        // 如果已经是数字，直接推回其浮点形式
        lua_pushnumber(L, lua_tonumber(L, 1));
    } else if (lua_isstring(L, 1)) {
        // 如果是文本，尝试解析
        const char* str = lua_tostring(L, 1);
        char* end;
        double d = strtod(str, &end);
        if (end != str) {
            lua_pushnumber(L, d);
        } else {
            lua_pushnumber(L, 0.0); // 转换失败默认返回 0.0
        }
    } else {
        lua_pushnumber(L, 0.0);
    }
    return 1;
}

// 到整数 (将文本或小数转为整数，去掉小数点后的部分)
int lua_to_int(lua_State *L) {
    if (lua_isnumber(L, 1)) {
        // 强制转换为长整数类型
        lua_pushinteger(L, (lua_Integer)lua_tonumber(L, 1));
    } else if (lua_isstring(L, 1)) {
        const char* str = lua_tostring(L, 1);
        // 使用 atoll 转为长整数
        lua_pushinteger(L, atoll(str));
    } else {
        lua_pushinteger(L, 0);
    }
    return 1;
}

// 取类型 (对应 type)
int lua_get_type(lua_State *L) {
    luaL_checkany(L, 1);
    const char* t = luaL_typename(L, 1);
    // 这里我们顺便翻译一下类型名
    std::string typeName = t;
    if (typeName == "number") typeName = "数值";
    else if (typeName == "string") typeName = "文本";
    else if (typeName == "boolean") typeName = "布尔";
    else if (typeName == "nil") typeName = "空";

    lua_pushstring(L, typeName.c_str());
    return 1;
}

// 万能连接 (可以传入任意数量的参数)
int lua_super_concat(lua_State *L) {
    int n = lua_gettop(L); // 获取参数个数
    std::string result = "";

    for (int i = 1; i <= n; i++) {
        // luaL_tolstring 会自动处理“真”、“假”、数字、文本等
        // 如果原本就是文本，它不会改动；如果是其他类型，它会调用对应的转换
        const char* s = luaL_tolstring(L, i, NULL);
        if (s) {
            result += s;
        }
        lua_pop(L, 1); // 弹出 tolstring 压入的临时字符串，保持栈平衡
    }

    lua_pushstring(L, result.c_str());
    return 1;
}

// --- 注册模块 ---
void register_system_mod(lua_State *L) {
    lua_register(L, "调用Java", lua_call_java);
    lua_register(L, "延时", lua_msleep_ext);
    lua_register(L, "调试输出", lua_debug_print);
    lua_register(L, "设置共享变量", lua_set_shared_var);
    lua_register(L, "读取共享变量", lua_get_shared_var);
    lua_register(L, "启动线程", lua_create_vm);
    lua_register(L, "到文本", lua_to_text);  // 对应 tostring
    lua_register(L, "到小数", lua_to_double);
    lua_register(L, "到整数", lua_to_int);
    lua_register(L, "取类型", lua_get_type);
    lua_register(L, "连接", lua_super_concat);

    lua_pushinteger(L, 0); lua_setglobal(L, "整数型");
    lua_pushinteger(L, 0); lua_setglobal(L, "毫秒");
    lua_pushinteger(L, 1); lua_setglobal(L, "秒");
    lua_pushinteger(L, 2); lua_setglobal(L, "分");

    // --- 新增：布林類型常量註冊 ---
    lua_pushboolean(L, 1);
    lua_setglobal(L, "真"); // 确保文件是 UTF-8 编码

    lua_pushboolean(L, 0);
    lua_setglobal(L, "假");
}