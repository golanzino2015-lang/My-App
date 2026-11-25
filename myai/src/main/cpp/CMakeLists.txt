cmake_minimum_required(VERSION 3.22.1)
project(tinyai C)

add_library(tinyai SHARED tinyai.c tinyai_jni.c)
# Speed matters more than strict float rules here: let the compiler vectorize (NEON on phones).
target_compile_options(tinyai PRIVATE -O3 -ffast-math -funroll-loops)
target_link_libraries(tinyai m)
