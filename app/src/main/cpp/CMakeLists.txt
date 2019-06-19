cmake_minimum_required(VERSION 3.4.1)

# Enable C++11
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED TRUE)

# Set target name, change with your favorite name
set(target native-lib)
project(${target} CXX)

set(ANDROID_OPENCV_COMPONENTS "opencv_java" CACHE STRING "")
message(STATUS "ANDROID_ABI=${ANDROID_ABI}")
find_package(OpenCV REQUIRED COMPONENTS ${ANDROID_OPENCV_COMPONENTS})

file(GLOB srcs *.cpp *.c)
file(GLOB hdrs *.hpp *.h)

include_directories("${CMAKE_CURRENT_LIST_DIR}")

add_library(
        ${target}
        SHARED
        ${srcs}
        ${hdrs})
find_library(
        log-lib
        log)
target_link_libraries(
        ${target}
        ${ANDROID_OPENCV_COMPONENTS}
        ${log-lib})