cmake_minimum_required(VERSION 3.10.2)
project("native-lua" C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 11)
add_definitions(-DLUA_USE_LINUX -D_LUA_64BITS)

# 1. 搜集 Lua 内核
file(GLOB LUA_CORE_SRC "lua/*.c")
list(REMOVE_ITEM LUA_CORE_SRC "${CMAKE_CURRENT_SOURCE_DIR}/lua/lua.c" "${CMAKE_CURRENT_SOURCE_DIR}/lua/luac.c")

# 2. 搜集你的业务代码 (EPL 目录)
file(GLOB_RECURSE EPL_SOURCES "EPL/*.cpp" "EPL/*.c")

# 3. 设置头文件路径
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/lua ${CMAKE_CURRENT_SOURCE_DIR}/EPL)

# 4. 生成动态库 (这是你 Java 层要用的 JNI 库)
add_library(native-lua SHARED ${EPL_SOURCES} ${LUA_CORE_SRC})

# 5. 链接系统库
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(native-lua ${log-lib} ${android-lib})

# ---------------------------------------------------------
# 注意：这里已经没有 ce_test 的编译逻辑了！
# 因为 ce_test 我们直接用现成的二进制文件放进 assets。
# ---------------------------------------------------------