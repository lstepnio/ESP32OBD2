cmake_minimum_required(VERSION 3.16)
set(IDF_COMPONENT_MANAGER=1)

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32-idf-project)

idf_build_set_property(COMPILE_OPTIONS "-Wno-shadow;-Wno-error" APPEND)
