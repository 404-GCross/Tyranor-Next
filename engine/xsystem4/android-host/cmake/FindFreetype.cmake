# FindFreetype.cmake —— TyranorNext 自研，供 xsystem4 Android 构建（不拷贝任何第三方 find 模块）。
#
# 只在 CMAKE_FIND_ROOT_PATH（依赖 staging 前缀）内查找静态 libfreetype.a，
# 提供 Freetype::Freetype（IMPORTED STATIC）。依赖裁剪（FT_DISABLE_*）由构建脚本负责。

find_path(FREETYPE_INCLUDE_DIR_ft2build NAMES ft2build.h
  PATH_SUFFIXES freetype2)
find_path(FREETYPE_INCLUDE_DIR_freetype2 NAMES freetype/config/ftheader.h
  PATH_SUFFIXES freetype2)
find_library(FREETYPE_LIBRARY NAMES freetype)

if(FREETYPE_INCLUDE_DIR_ft2build AND FREETYPE_INCLUDE_DIR_freetype2 AND FREETYPE_LIBRARY)
  set(FREETYPE_INCLUDE_DIRS "${FREETYPE_INCLUDE_DIR_ft2build};${FREETYPE_INCLUDE_DIR_freetype2}")
  set(FREETYPE_LIBRARIES "${FREETYPE_LIBRARY}")
endif()

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(Freetype
  REQUIRED_VARS FREETYPE_LIBRARY FREETYPE_INCLUDE_DIR_ft2build FREETYPE_INCLUDE_DIR_freetype2)

if(Freetype_FOUND AND NOT TARGET Freetype::Freetype)
  add_library(Freetype::Freetype STATIC IMPORTED)
  set_target_properties(Freetype::Freetype PROPERTIES
    IMPORTED_LOCATION "${FREETYPE_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${FREETYPE_INCLUDE_DIRS}")
endif()
