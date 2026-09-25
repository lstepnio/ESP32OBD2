#include <inttypes.h>
#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"
#include "esp_log.h"

#include "display/lv_display.h"
#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "driver/i2c_types.h"
#include "driver/spi_common.h"
#include "esp_lcd_gc9a01.h"
#include "esp_lcd_io_i2c.h"
#include "esp_lcd_io_spi.h"
#include "esp_lcd_panel_dev.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_touch.h"
#include "esp_lcd_touch_cst816s.h"
#include "esp_lcd_types.h"
#include "esp_log_color.h"
#include "esp_lvgl_port.h"
#include "esp_lvgl_port_disp.h"
#include "esp_lvgl_port_touch.h"
#include "hal/gpio_types.h"
#include "hal/i2c_types.h"
#include "hal/spi_types.h"
#include "lv_api_map_v8.h"
#include "misc/lv_area.h"
#include "misc/lv_color.h"
#include "misc/lv_types.h"
#include "soc/clk_tree_defs.h"
#include "soc/gpio_num.h"

#include "bsp_err_check.h"
#include "bsp_init.h"
#include "bsp_lcd.h"

// ------------------------------------------------------------------------------------------------------------------ //
// IO Configuration
// ------------------------------------------------------------------------------------------------------------------ //

#define BSP_LCD_GPIO_DC GPIO_NUM_8
#define BSP_LCD_GPIO_CS GPIO_NUM_9
#define BSP_LCD_GPIO_CLK GPIO_NUM_10
#define BSP_LCD_GPIO_MOSI GPIO_NUM_11
#define BSP_LCD_GPIO_MISO GPIO_NUM_12
#define BSP_LCD_GPIO_RST GPIO_NUM_14
#define BSP_LCD_GPIO_BL GPIO_NUM_2

#define BSP_TOUCH_I2C_SDA GPIO_NUM_6
#define BSP_TOUCH_I2C_SCL GPIO_NUM_7
#define BSP_TOUCH_RST GPIO_NUM_13
#define BSP_TOUCH_INT GPIO_NUM_5
#define BSP_TOUCH_I2C_NUM I2C_NUM_1
#define BSP_TOUCH_I2C_CLK_HZ (400000)

// ------------------------------------------------------------------------------------------------------------------ //
// LCD Configuration
// ------------------------------------------------------------------------------------------------------------------ //

#define BSP_LCD_SPI_HOST SPI2_HOST
#define BSP_LCD_SPI_DMA_CH SPI_DMA_CH_AUTO
#define BSP_LCD_BIT_PER_PIXEL 16
#define BSP_LCD_H_RES 240
#define BSP_LCD_V_RES 240
#define BSP_LCD_BUFFER_SIZE (BSP_LCD_H_RES * BSP_LCD_V_RES * (BSP_LCD_BIT_PER_PIXEL / 8))
#define BSP_LCD_BUFFER_HEIGHT 10

// ------------------------------------------------------------------------------------------------------------------ //
// Global Variables
// ------------------------------------------------------------------------------------------------------------------ //

static const char               *TAG                 = "bsp";
static esp_lcd_panel_io_handle_t lcd_panel_io_handle = NULL;
static esp_lcd_panel_handle_t    lcd_panel_handle    = NULL;
static lv_display_t             *disp_handle         = NULL;

// ------------------------------------------------------------------------------------------------------------------ //
// Function Definitions
// ------------------------------------------------------------------------------------------------------------------ //

esp_err_t bsp_touch_init(void)
{
    ESP_LOGI(TAG, "Initializing touch panel ...");

    /* Initialize I2C */

    const i2c_master_bus_config_t i2c_cfg        = {.i2c_port          = BSP_TOUCH_I2C_NUM,
                                                    .scl_io_num        = BSP_TOUCH_I2C_SCL,
                                                    .sda_io_num        = BSP_TOUCH_I2C_SDA,
                                                    .clk_source        = I2C_CLK_SRC_DEFAULT,
                                                    .glitch_ignore_cnt = 7,
                                                    .intr_priority     = 0,
                                                    .trans_queue_depth = 0,
                                                    .flags             = {.enable_internal_pullup = true, .allow_pd = false}};
    i2c_master_bus_handle_t       i2c_bus_handle = NULL;
    ESP_LOGI(TAG, "Creating I2C bus: SCL=GPIO_%d, SDA=GPIO_%d", BSP_TOUCH_I2C_SCL, BSP_TOUCH_I2C_SDA);
    ESP_ERROR_CHECK(i2c_new_master_bus(&i2c_cfg, &i2c_bus_handle));
    BSP_NULL_CHECK(i2c_bus_handle, ESP_ERR_NO_MEM);

    /* Initialize Touch HW */

    const esp_lcd_panel_io_i2c_config_t tp_io_config = ESP_LCD_TOUCH_IO_I2C_CST816S_CONFIG();
    esp_lcd_panel_io_handle_t           tp_io_handle = NULL;
    ESP_LOGI(TAG, "Creating touch panel IO: I2C_ADDR=0x%02" PRIx32, tp_io_config.dev_addr);
    ESP_ERROR_CHECK(esp_lcd_new_panel_io_i2c(i2c_bus_handle, &tp_io_config, &tp_io_handle));
    BSP_NULL_CHECK(tp_io_handle, ESP_ERR_NO_MEM);

    const esp_lcd_touch_config_t tp_lcd_cfg = {
        .x_max        = BSP_LCD_H_RES,
        .y_max        = BSP_LCD_V_RES,
        .rst_gpio_num = BSP_TOUCH_RST,
        .int_gpio_num = BSP_TOUCH_INT,
        .levels =
            {
                .reset     = 0,
                .interrupt = 0,
            },
        .flags =
            {
                .swap_xy  = false,
                .mirror_x = false,
                .mirror_y = false,
            },
        .interrupt_callback = NULL,
    };
    ESP_LOGI(TAG, "Creating touch panel driver");
    esp_lcd_touch_handle_t touch_handle = NULL;
    ESP_ERROR_CHECK(esp_lcd_touch_new_i2c_cst816s(tp_io_handle, &tp_lcd_cfg, &touch_handle));
    BSP_NULL_CHECK(touch_handle, ESP_ERR_NO_MEM);

    ESP_LOGI(TAG, "Creating LVGL touch panel");
    BSP_NULL_CHECK(disp_handle, ESP_ERR_INVALID_ARG);
    const lvgl_port_touch_cfg_t lvgl_touch_config = {
        .disp   = disp_handle,
        .handle = touch_handle,
    };
    const lv_indev_t *lvgl_pt = lvgl_port_add_touch(&lvgl_touch_config);
    BSP_NULL_CHECK(lvgl_pt, ESP_ERR_NO_MEM);

    ESP_LOGI(TAG, "Touch panel initialized successfully");
    return ESP_OK;
}

esp_err_t bsp_lvgl_init(void)
{
    const lvgl_port_cfg_t lvgl_cfg = ESP_LVGL_PORT_INIT_CONFIG();
    ESP_LOGI(TAG, "Initializing LVGL port");
    ESP_ERROR_CHECK(lvgl_port_init(&lvgl_cfg));

    // *** LVGL display configuration ***
    const lvgl_port_display_cfg_t disp_cfg = {
        .io_handle    = lcd_panel_io_handle,
        .panel_handle = lcd_panel_handle,
        .buffer_size  = BSP_LCD_H_RES * BSP_LCD_BUFFER_HEIGHT,
        .hres         = BSP_LCD_H_RES,
        .vres         = BSP_LCD_V_RES,
        .monochrome   = false,
        .color_format = LV_COLOR_FORMAT_RGB565,
        .rotation =
            {
                .swap_xy  = false,
                .mirror_x = true,
                .mirror_y = false,
            },
    };
    ESP_LOGI(TAG, "Creating LVGL display (HRES=%d, VRES=%d)", BSP_LCD_H_RES, BSP_LCD_V_RES);
    disp_handle = lvgl_port_add_disp(&disp_cfg);
    BSP_NULL_CHECK(disp_handle, ESP_ERR_NO_MEM);
    lv_disp_set_rotation(disp_handle, LV_DIR_NONE);
    lv_disp_set_default(disp_handle);

    return ESP_OK;
}

esp_err_t bsp_display_start(void)
{

    const spi_bus_config_t buscfg =
        GC9A01_PANEL_BUS_SPI_CONFIG(BSP_LCD_GPIO_CLK, BSP_LCD_GPIO_MOSI, BSP_LCD_BUFFER_SIZE);
    ESP_LOGI(TAG, "Initializing SPI bus: CLK=GPIO_%d, MOSI=GPIO_%d", BSP_LCD_GPIO_CLK, BSP_LCD_GPIO_MOSI);
    ESP_ERROR_CHECK(spi_bus_initialize(BSP_LCD_SPI_HOST, &buscfg, BSP_LCD_SPI_DMA_CH));

    const esp_lcd_panel_io_spi_config_t io_config =
        GC9A01_PANEL_IO_SPI_CONFIG(BSP_LCD_GPIO_CS, BSP_LCD_GPIO_DC, NULL, NULL);
    ESP_LOGI(TAG, "Creating panel IO: CS=GPIO_%d, DC=GPIO_%d", BSP_LCD_GPIO_CS, BSP_LCD_GPIO_DC);
    ESP_ERROR_CHECK(
        esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)BSP_LCD_SPI_HOST, &io_config, &lcd_panel_io_handle));

    ESP_LOGI(TAG, "Install gc9a01 panel driver: RST=GPIO_%d", BSP_LCD_GPIO_RST);
    const esp_lcd_panel_dev_config_t panel_config = {
        .reset_gpio_num = BSP_LCD_GPIO_RST,
        .rgb_ele_order  = LCD_RGB_ELEMENT_ORDER_RGB,
        .bits_per_pixel = BSP_LCD_BIT_PER_PIXEL,
    };
    ESP_ERROR_CHECK(esp_lcd_new_panel_gc9a01(lcd_panel_io_handle, &panel_config, &lcd_panel_handle));
    BSP_NULL_CHECK(lcd_panel_handle, ESP_ERR_NO_MEM);
    ESP_ERROR_CHECK(esp_lcd_panel_reset(lcd_panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_init(lcd_panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_invert_color(lcd_panel_handle, true));
    ESP_ERROR_CHECK(esp_lcd_panel_swap_xy(lcd_panel_handle, false));

    ESP_LOGI(TAG, "Display initialized successfully");

    return ESP_OK;
}

esp_err_t bsp_init(void)
{
    // Initialize GPIOs
    gpio_config_t bl_gpio_config = {
        .pin_bit_mask = 1ULL << BSP_LCD_GPIO_BL,
        .mode         = GPIO_MODE_OUTPUT,
    };
    ESP_LOGI(TAG, "Configuring backlight GPIO_%d", BSP_LCD_GPIO_BL);
    ESP_ERROR_CHECK(gpio_config(&bl_gpio_config));
    ESP_ERROR_CHECK(bsp_display_backlight_set(false));

    ESP_LOGI(TAG, "BSP initialized successfully");

    return ESP_OK;
}

esp_err_t bsp_display_on_off(bool on)
{
    if (on)
    {
        ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(lcd_panel_handle, true));
        ESP_ERROR_CHECK(bsp_display_backlight_set(true));
    }
    else
    {
        ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(lcd_panel_handle, false));
        ESP_ERROR_CHECK(bsp_display_backlight_set(false));
    }
    return ESP_OK;
}

esp_err_t bsp_display_backlight_set(bool on)
{
    return gpio_set_level(BSP_LCD_GPIO_BL, on ? 1 : 0);
}

esp_err_t bsp_lv_disp_set_rotation(lv_display_rotation_t rotation)
{
    lv_display_set_rotation(disp_handle, rotation);
    return ESP_OK;
}
