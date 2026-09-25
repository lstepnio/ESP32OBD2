file(GLOB FONT_SOURCES "fonts/*.c")

idf_component_register(
    SRCS
        "main.c"
        "src/ble_init.c"
        "src/ble_companion.c"
        "src/ble_mgr.c"
        "src/ble_obd.c"
        "src/elm_response.c"
        "src/ble_util.c"
        "src/config.c"
        "src/config_store.c"
        "src/config_document.c"
        "src/ui.c"
        ${FONT_SOURCES}
    INCLUDE_DIRS
        "."
        "inc"
    REQUIRES lvgl esp_lvgl_port bt
    PRIV_REQUIRES nvs_flash spi_flash mbedtls json
)

target_compile_options(${COMPONENT_TARGET} PRIVATE -Wall -Wextra -Wshadow -Werror)
target_compile_definitions(${COMPONENT_TARGET} PRIVATE LV_LVGL_H_INCLUDE_SIMPLE=1)
