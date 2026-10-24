cmake_minimum_required(VERSION 3.22)
project(rise_whisper C CXX)
set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# 0.25.0: whisper/ggml are always optimized, even in the debug APK. Until 0.24.0 the debug
# build compiled them at -O0, which made captions ~15–30× slower (measured on the box).
add_compile_options(-O3 -DNDEBUG)
# arm64: dot-product + fp16 kernels (ARMv8.2-A; every Snapdragon/Exynos/Tensor since 2018).
# The app checks /proc/cpuinfo (asimddp, fphp) before loading the library.
if (ANDROID_ABI STREQUAL "arm64-v8a")
  set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()
add_subdirectory(whisper.cpp)
add_library(rise_whisper SHARED rise_whisper.cpp)
target_link_libraries(rise_whisper whisper log)
