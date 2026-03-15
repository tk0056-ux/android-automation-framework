cmake_minimum_required(VERSION 3.10.2)

# 1. 声明项目名和支持语言 (C 和 C++)
project("native-lua" C CXX)

# 2. 语言标准配置
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 11)

# 3. 核心宏定义：开启 Linux 兼容模式和 64 位支持
add_definitions(-DLUA_USE_LINUX -D_LUA_64BITS)

# --- 4. 自动化搜集 EPL 目录下的所有源码和头文件 ---
# 使用 GLOB_RECURSE 会自动进入子目录查找（如 EPL/lua1, EPL/text 等）
file(GLOB_RECURSE EPL_SOURCES
        "EPL/*.cpp"
        "EPL/*.c"
        "EPL/*.h"
)

# --- 5. 获取 Lua 核心源码 ---
file(GLOB LUA_CORE_SRC "lua/*.c")
# 排除 Lua 官方的命令行入口文件（必须排除，否则 main 函数冲突）
list(REMOVE_ITEM LUA_CORE_SRC
        "${CMAKE_CURRENT_SOURCE_DIR}/lua/lua.c"
        "${CMAKE_CURRENT_SOURCE_DIR}/lua/luac.c"
)

# --- 6. 定义核心总调度文件 ---
# 这些是你的 JNI 入口和模块管理文件，通常放在 cpp 根目录
# --- 6. 定义核心总调度文件 ---
# 使用 ${CMAKE_CURRENT_SOURCE_DIR} 强制指定当前目录，防止 CMake 迷路
set(CORE_SOURCES
        "${CMAKE_CURRENT_SOURCE_DIR}/EPL/LuaEngine.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/EPL/systemmod.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/EPL/text_mod.cpp"
)

# --- 7. 配置头文件搜索路径 (关键) ---
# 包含根目录、EPL 根目录、Lua 目录
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/lua
        ${CMAKE_CURRENT_SOURCE_DIR}/EPL
)

# 【高级自动化】自动将 EPL 下的所有子目录添加到搜索路径
# 这样你在 EPL/lua1/test.h 里的文件，直接 #include "test.h" 就能找到
file(GLOB_RECURSE ALL_EPL_DIRS LIST_DIRECTORIES true "${CMAKE_CURRENT_SOURCE_DIR}/EPL/*")
foreach(dir ${ALL_EPL_DIRS})
    if(IS_DIRECTORY ${dir})
        include_directories(${dir})
    endif()
endforeach()





# --- 8. 生成动态库 .so ---
add_library(native-lua SHARED
        ${CORE_SOURCES}    # 总调度
        ${EPL_SOURCES}     # 自动化搜集的 EPL 模块
        ${LUA_CORE_SRC}    # Lua 解释器内核
)

# --- 9. 链接系统库 ---
# 查找 Android 的日志库
find_library(log-lib log)

find_library( # 找到安卓系统资源库
        android-lib
        android )
# 链接
target_link_libraries(native-lua
        ${log-lib}
        ${android-lib}
)

# --- 10. 强制链接器使用 CXX ---
# 因为我们混合了 C (Lua) 和 C++ (EPL/Engine)，必须用 CXX 链接确保 STL 正常工作
set_target_properties(native-lua PROPERTIES LINKER_LANGUAGE CXX)


# ... (保留你原来的 1-10 项内容不变) ...

# --- 11. 集成 CESERVER 自动化模块 ---

# 设置 ce 源码目录路径
set(CE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ce")

# 自动搜集 ce 目录下的所有 C 文件
file(GLOB CE_SOURCES
        "${CE_DIR}/*.c"
)

# 排除测试用的文件，防止 main 函数重复
list(REMOVE_ITEM CE_SOURCES "${CE_DIR}/ceservertest.c")

# 添加头文件搜索路径
include_directories(${CE_DIR})
include_directories(${CE_DIR}/extension)

# 生成可执行文件 (注意：这里用 add_executable 而不是 add_library)
# 我们给它起个名字叫 "my_ceserver_svc"
add_executable(my_ceserver_svc ${CE_SOURCES})

# 链接必要库：ceserver 需要 dl (动态链接) 和 log (日志)
target_link_libraries(my_ceserver_svc
        ${log-lib}
        ${android-lib}
        dl
)

# 强制使用 C 链接，因为 ceserver 纯 C 编写 (可选)
set_target_properties(my_ceserver_svc PROPERTIES LINKER_LANGUAGE C)