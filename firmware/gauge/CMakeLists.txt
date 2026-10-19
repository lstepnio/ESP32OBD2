cmake_minimum_required(VERSION 3.16)
set(IDF_COMPONENT_MANAGER=1)

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32-idf-project)

# Keep the fixed 64 KiB LVGL arena out of scarce internal radio/DMA RAM.
# The linked-image check also runs in signed release builds, not only quality CI.
idf_component_get_property(gauge_lvgl_lib lvgl__lvgl COMPONENT_LIB)
target_compile_options(${gauge_lvgl_lib} PRIVATE
    "-include${CMAKE_CURRENT_SOURCE_DIR}/lvgl_memory.h")
idf_build_get_property(gauge_python PYTHON)
add_custom_command(TARGET ${CMAKE_PROJECT_NAME}.elf POST_BUILD
    COMMAND ${gauge_python} ${CMAKE_CURRENT_SOURCE_DIR}/../../tools/check_firmware_memory.py
        $<TARGET_FILE:${CMAKE_PROJECT_NAME}.elf>
    VERBATIM)
set_property(TARGET ${CMAKE_PROJECT_NAME}.elf APPEND PROPERTY LINK_DEPENDS
    ${CMAKE_CURRENT_SOURCE_DIR}/../../tools/check_firmware_memory.py)
