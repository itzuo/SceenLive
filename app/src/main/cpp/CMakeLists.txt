
cmake_minimum_required(VERSION 3.4.1)

add_subdirectory(librtmp)

add_library(
        native-lib

        SHARED

        native-lib.cpp)


find_library(
        log-lib

        log)


target_link_libraries(
        native-lib
        rtmp
        ${log-lib})