cmake_minimum_required(VERSION 3.16)
project(gauge_ui_host C CXX)
option(EGAUGE_PARKED_IDLE_TEST "Render the supervised parked idle candidate" OFF)
set(LV_CONF_PATH "${CMAKE_CURRENT_SOURCE_DIR}/lv_conf.h" CACHE STRING "" FORCE)
set(LV_CONF_BUILD_DISABLE_EXAMPLES ON CACHE BOOL "" FORCE)
set(LV_CONF_BUILD_DISABLE_DEMOS ON CACHE BOOL "" FORCE)
set(LV_CONF_BUILD_DISABLE_THORVG_INTERNAL ON CACHE BOOL "" FORCE)
set(EGAUGE_LVGL_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../managed_components/lvgl__lvgl"
    CACHE PATH "Pinned LVGL source for offline host rendering")
add_subdirectory("${EGAUGE_LVGL_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/lvgl")
include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/presentation.cmake")
add_executable(gauge_ui_render ui_render_test.c
    ../../main/src/gauge_presentation.c ${EGAUGE_UI_PROFILE_SOURCE}
    ../../main/src/gauge_layout.c ../../main/src/gauge_surface.c ../../main/src/display_showcase.c
    ../../main/src/display_units.c ../../main/src/page_action.c ${FONT_SOURCES})
target_include_directories(gauge_ui_render BEFORE PRIVATE stubs ../../main/inc "${EGAUGE_LVGL_SOURCE_DIR}/src")
target_link_libraries(gauge_ui_render PRIVATE lvgl m)
target_compile_options(gauge_ui_render PRIVATE -Wall -Wextra -Werror -Wno-unused-parameter)
target_compile_definitions(gauge_ui_render PRIVATE
    CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST=$<BOOL:${EGAUGE_PARKED_IDLE_TEST}>)
