#include "systemmod.h"
#include <string>
#include <map>
#include <mutex>
#include <atomic>
#include <thread>
#include <android/log.h>
#include <fstream>
#include <sstream>
#include <unistd.h>
#include <cstdlib>  // 添加 atoll 的头文件

// 外部函数声明 (从 LuaEngine.cpp 引入)
extern void run_new_vm(std::string code);
extern void run_vm_from_assets(std::string fileName);
extern std::string callJavaStatic(const char* className, const char* methodName, const char* param);

// --- 全局共享数据区 ---
static std::mutex g_share_mutex;
static std::map<std::string, std::string> g_global_vars;

// --- 虚拟机管理结构 ---
struct VMInfo {
    lua_State* L;
    bool is_running;

    VMInfo() : L(nullptr), is_running(false) {}
    VMInfo(lua_State* state, bool running) : L(state), is_running(running) {}
};

// --- 虚拟机管理区 ---
static std::mutex g_vm_mutex;
static std::map<int, VMInfo> g_active_vms;  // 使用 VMInfo 结构
static std::atomic<int> g_next_vm_id(1001); // 自增 ID，使用 atomic 保证线程安全

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

// --- 调试输出 ---
int lua_debug_print(lua_State *L) {
    int n = lua_gettop(L);

    if (n >= 2) {
        const char *tag = luaL_tolstring(L, 1, NULL);
        const char *msg = luaL_tolstring(L, 2, NULL);
        __android_log_print(ANDROID_LOG_INFO, tag, "%s", msg);
        lua_pop(L, 2); // 弹出临时字符串
    } else if (n == 1) {
        const char *msg = luaL_tolstring(L, 1, NULL);
        __android_log_print(ANDROID_LOG_INFO, "LuaLog", "%s", msg);
        lua_pop(L, 1); // 弹出临时字符串
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

// 辅助函数：从 Assets 加载 Lua 文件（需要根据您的实际实现调整）
extern bool loadLuaFromAssets(lua_State* L, const char* path);

// 启动线程(Assets 文件路径)
int lua_create_vm(lua_State *L) {
    const char* path = luaL_checkstring(L, 1);
    std::string filePath(path);

    int current_id = g_next_vm_id.fetch_add(1); // 原子操作获取ID

    // 先在 Map 中登记 ID，L 暂时设为 nullptr
    {
        std::lock_guard<std::mutex> lock(g_vm_mutex);
        g_active_vms[current_id] = VMInfo(nullptr, true); // 标记为运行中
    }

    std::thread([filePath, current_id]() {
        lua_State *subL = luaL_newstate();
        luaL_openlibs(subL);
        register_system_mod(subL); // 注册包含“停止线程”的模块

        // 绑定真实的虚拟机指针
        {
            std::lock_guard<std::mutex> lock(g_vm_mutex);
            auto it = g_active_vms.find(current_id);
            if (it != g_active_vms.end() && it->second.is_running) {
                it->second.L = subL;
            } else {
                // 如果主线程在启动瞬间就叫停了
                lua_close(subL);
                return;
            }
        }

        // 内存加载 Assets
        bool loadSuccess = false;
        if (loadLuaFromAssets) {
            loadSuccess = loadLuaFromAssets(subL, filePath.c_str());
        }

        if (!loadSuccess) {
            __android_log_print(ANDROID_LOG_ERROR, "LuaVM",
                                "虚拟机 ID %d 加载文件失败: %s", current_id, filePath.c_str());
        }

        // 执行完毕后的自清理
        {
            std::lock_guard<std::mutex> lock(g_vm_mutex);
            auto it = g_active_vms.find(current_id);
            if (it != g_active_vms.end()) {
                if (it->second.L != nullptr) {
                    lua_close(it->second.L);
                }
                g_active_vms.erase(it);
            }
        }
    }).detach();

    lua_pushinteger(L, current_id);
    return 1;
}

// 停止指定的虚拟机
int lua_stop_vm(lua_State *L) {
    int target_id = (int)luaL_checkinteger(L, 1);
    bool stopped = false;

    {
        std::lock_guard<std::mutex> lock(g_vm_mutex);
        auto it = g_active_vms.find(target_id);

        if (it != g_active_vms.end()) {
            // 先标记为停止，防止新操作
            it->second.is_running = false;

            if (it->second.L != nullptr) {
                // 注意：在多线程环境下直接 lua_close 可能不安全
                // 如果 Lua 状态正在执行，这可能导致崩溃
                // 更好的做法是通过一个标志让 Lua 自己退出
                lua_close(it->second.L);
                __android_log_print(ANDROID_LOG_WARN, "LuaEngine",
                                    "虚拟机 ID %d 已被强制关闭", target_id);
            }

            g_active_vms.erase(it);
            stopped = true;
        }
    }

    lua_pushboolean(L, stopped ? 1 : 0);
    return 1;
}

// 获取所有运行中的虚拟机ID
int lua_list_vms(lua_State *L) {
    std::lock_guard<std::mutex> lock(g_vm_mutex);

    lua_newtable(L); // 创建一个新表
    int index = 1;

    for (const auto& pair : g_active_vms) {
        lua_pushinteger(L, pair.first); // 键是虚拟机ID
        lua_rawseti(L, -2, index++);    // 设置到表中
    }

    return 1; // 返回表
}

// 检查虚拟机是否在运行
int lua_is_vm_running(lua_State *L) {
    int target_id = (int)luaL_checkinteger(L, 1);
    bool is_running = false;

    {
        std::lock_guard<std::mutex> lock(g_vm_mutex);
        auto it = g_active_vms.find(target_id);
        is_running = (it != g_active_vms.end() && it->second.is_running);
    }

    lua_pushboolean(L, is_running ? 1 : 0);
    return 1;
}

// 到文本 (对应 tostring)
int lua_to_text(lua_State *L) {
    luaL_checkany(L, 1);
    luaL_tolstring(L, 1, NULL);
    return 1;
}

// 到小数 (将文本或整数转为浮点数)
int lua_to_double(lua_State *L) {
    if (lua_isnumber(L, 1)) {
        lua_pushnumber(L, lua_tonumber(L, 1));
    } else if (lua_isstring(L, 1)) {
        const char* str = lua_tostring(L, 1);
        char* end;
        double d = strtod(str, &end);
        if (end != str) {
            lua_pushnumber(L, d);
        } else {
            lua_pushnumber(L, 0.0);
        }
    } else {
        lua_pushnumber(L, 0.0);
    }
    return 1;
}

// 到整数 (将文本或小数转为整数)
int lua_to_int(lua_State *L) {
    if (lua_isnumber(L, 1)) {
        lua_pushinteger(L, (lua_Integer)lua_tonumber(L, 1));
    } else if (lua_isstring(L, 1)) {
        const char* str = lua_tostring(L, 1);
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

    std::string typeName = t;
    if (typeName == "number") typeName = "数值";
    else if (typeName == "string") typeName = "文本";
    else if (typeName == "boolean") typeName = "布尔";
    else if (typeName == "nil") typeName = "空";
    else if (typeName == "table") typeName = "表";
    else if (typeName == "function") typeName = "函数";
    else if (typeName == "thread") typeName = "线程";
    else if (typeName == "userdata") typeName = "用户数据";

    lua_pushstring(L, typeName.c_str());
    return 1;
}

// 万能连接 (可以传入任意数量的参数)
int lua_super_concat(lua_State *L) {
    int n = lua_gettop(L);
    std::string result = "";

    for (int i = 1; i <= n; i++) {
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
    lua_register(L, "停止线程", lua_stop_vm);        // 新增：停止指定虚拟机
    lua_register(L, "取线程列表", lua_list_vms);     // 新增：获取所有虚拟机ID
    lua_register(L, "线程是否运行", lua_is_vm_running); // 新增：检查虚拟机状态
    lua_register(L, "到文本", lua_to_text);
    lua_register(L, "到小数", lua_to_double);
    lua_register(L, "到整数", lua_to_int);
    lua_register(L, "取类型", lua_get_type);
    lua_register(L, "连接", lua_super_concat);

    lua_pushinteger(L, 0); lua_setglobal(L, "整数型");
    lua_pushinteger(L, 0); lua_setglobal(L, "毫秒");
    lua_pushinteger(L, 1); lua_setglobal(L, "秒");
    lua_pushinteger(L, 2); lua_setglobal(L, "分");

    // 布林類型常量註冊
    lua_pushboolean(L, 1); lua_setglobal(L, "真");
    lua_pushboolean(L, 0); lua_setglobal(L, "假");
}