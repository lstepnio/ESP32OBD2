idf_component_register(
    SRCS "src/waveshare_lcd_128_round_touch.c"
    INCLUDE_DIRS "include" "priv_include"
)

target_compile_options(${COMPONENT_TARGET} PRIVATE -Wall -Wextra -Wshadow)