# FindSDL2.cmake —— TyranorNext 自研，供 xsystem4 Android 构建（不拷贝任何第三方 find 模块）。
#
# 提供 SDL2::SDL2（IMPORTED SHARED）：
#   头文件  XSYS4_SDL2_INCLUDE_DIR：SDL2-2.32.10 源码包的 include/（仅取头文件，由构建脚本下发）
#   运行库  XSYS4_SDL2_LIBRARY    ：缺省为 engine/src/main/jniLibs/arm64-v8a/libSDL2x.so
#
# 该 .so 即 System35 已入库的运行库（SONAME libSDL2x.so，配套 org.libsdl.app32），
# 本模块只引用、不构建、不改动它；SDL 版本由 SDL_version.h 解析并与 2.32.10 比对。

set(_xs4_sdl2_default_lib "${CMAKE_CURRENT_LIST_DIR}/../../../src/main/jniLibs/arm64-v8a/libSDL2x.so")

if(NOT XSYS4_SDL2_LIBRARY)
  set(XSYS4_SDL2_LIBRARY "${_xs4_sdl2_default_lib}")
endif()

# 头文件目录位于依赖缓存而非 CMAKE_FIND_ROOT_PATH 下，故直接校验而不走 find_path（工具链限定了 root 模式）。
set(SDL2_INCLUDE_DIR "")
if(XSYS4_SDL2_INCLUDE_DIR AND EXISTS "${XSYS4_SDL2_INCLUDE_DIR}/SDL.h")
  set(SDL2_INCLUDE_DIR "${XSYS4_SDL2_INCLUDE_DIR}")
endif()

set(SDL2_LIBRARY "")
if(EXISTS "${XSYS4_SDL2_LIBRARY}")
  set(SDL2_LIBRARY "${XSYS4_SDL2_LIBRARY}")
endif()

if(SDL2_INCLUDE_DIR AND EXISTS "${SDL2_INCLUDE_DIR}/SDL_version.h")
  file(STRINGS "${SDL2_INCLUDE_DIR}/SDL_version.h" _xs4_sdl2_ver_lines
    REGEX "^#define[ \t]+SDL_(MAJOR_VERSION|MINOR_VERSION|PATCHLEVEL)[ \t]+[0-9]+")
  foreach(_line IN LISTS _xs4_sdl2_ver_lines)
    if(_line MATCHES "SDL_MAJOR_VERSION[ \t]+([0-9]+)")
      set(_xs4_sdl2_major "${CMAKE_MATCH_1}")
    elseif(_line MATCHES "SDL_MINOR_VERSION[ \t]+([0-9]+)")
      set(_xs4_sdl2_minor "${CMAKE_MATCH_1}")
    elseif(_line MATCHES "SDL_PATCHLEVEL[ \t]+([0-9]+)")
      set(_xs4_sdl2_patch "${CMAKE_MATCH_1}")
    endif()
  endforeach()
  set(SDL2_VERSION "${_xs4_sdl2_major}.${_xs4_sdl2_minor}.${_xs4_sdl2_patch}")
endif()

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(SDL2
  REQUIRED_VARS SDL2_INCLUDE_DIR SDL2_LIBRARY
  VERSION_VAR SDL2_VERSION)

if(SDL2_FOUND)
  set(SDL2_INCLUDE_DIRS "${SDL2_INCLUDE_DIR}")
  set(SDL2_LIBRARIES "${SDL2_LIBRARY}")
  if(NOT TARGET SDL2::SDL2)
    add_library(SDL2::SDL2 SHARED IMPORTED)
    set_target_properties(SDL2::SDL2 PROPERTIES
      IMPORTED_LOCATION "${SDL2_LIBRARY}"
      IMPORTED_SONAME "libSDL2x.so"
      INTERFACE_INCLUDE_DIRECTORIES "${SDL2_INCLUDE_DIR}")
  endif()
endif()
