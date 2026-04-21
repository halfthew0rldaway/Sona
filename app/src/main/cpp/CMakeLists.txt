cmake_minimum_required(VERSION 3.22.1)

project(usb-audio-poc)

add_library(
    usb-audio-poc
    SHARED
    native_bridge.cpp
    usb_audio_engine.cpp
    descriptor_parser.cpp
)

target_compile_features(usb-audio-poc PRIVATE cxx_std_20)

find_library(log-lib log)

target_link_libraries(
    usb-audio-poc
    ${log-lib}
)
