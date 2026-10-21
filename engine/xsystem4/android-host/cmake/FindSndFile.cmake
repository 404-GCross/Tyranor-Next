# FindSndFile.cmake —— TyranorNext 自研，供 xsystem4 Android 构建（不拷贝任何第三方 find 模块）。
#
# 提供 SndFile::sndfile（IMPORTED STATIC）。libsndfile 为静态链入，其 Ogg/Vorbis 依赖
# 通过 INTERFACE_LINK_LIBRARIES 按链接顺序（vorbisenc → vorbis → ogg）一并带上。
# libsndfile 本体与 ogg/vorbis 均由构建脚本装入同一 staging 前缀。

find_path(SndFile_INCLUDE_DIR NAMES sndfile.h)
find_library(SndFile_LIBRARY NAMES sndfile)
find_library(SndFile_VORBISENC_LIBRARY NAMES vorbisenc)
find_library(SndFile_VORBIS_LIBRARY NAMES vorbis)
find_library(SndFile_OGG_LIBRARY NAMES ogg)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(SndFile
  REQUIRED_VARS SndFile_INCLUDE_DIR SndFile_LIBRARY
    SndFile_VORBISENC_LIBRARY SndFile_VORBIS_LIBRARY SndFile_OGG_LIBRARY)

if(SndFile_FOUND AND NOT TARGET SndFile::sndfile)
  add_library(SndFile::sndfile STATIC IMPORTED)
  set_target_properties(SndFile::sndfile PROPERTIES
    IMPORTED_LOCATION "${SndFile_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${SndFile_INCLUDE_DIR}"
    INTERFACE_LINK_LIBRARIES "${SndFile_VORBISENC_LIBRARY};${SndFile_VORBIS_LIBRARY};${SndFile_OGG_LIBRARY}")
endif()
