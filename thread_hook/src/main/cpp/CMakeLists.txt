cmake_minimum_required(VERSION 3.18.1)
project(thread-hook)
find_package(bytehook REQUIRED CONFIG)

add_library(
        threadhook
        SHARED
        thread_hook.cpp
)

target_link_libraries(
        threadhook bytehook::bytehook
        log)