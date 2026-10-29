# Private integration builds may supply one adapter without forking the runtime.
set(EGAUGE_BOARD_ADAPTER_SOURCE "${CMAKE_CURRENT_LIST_DIR}/src/gauge_board_reference.c"
    CACHE FILEPATH "Board/display implementation for this firmware target")
if(NOT IS_ABSOLUTE "${EGAUGE_BOARD_ADAPTER_SOURCE}" OR NOT EXISTS "${EGAUGE_BOARD_ADAPTER_SOURCE}")
    message(FATAL_ERROR "EGAUGE_BOARD_ADAPTER_SOURCE must name an existing absolute source path")
endif()

include("${CMAKE_CURRENT_LIST_DIR}/../cmake/presentation.cmake")

idf_component_register(
    SRCS
        "main.c"
        "src/ble_init.c"
        "src/ble_companion.c"
        "src/ble_mgr.c"
        "src/adapter_registry.c"
        "src/adapter_status.c"
        "src/obd_adapter_profile.c"
        "src/ble_obd.c"
        "src/obd_trace.c"
        "src/obd_trace_format.c"
        "src/elm_response.c"
        "src/ble_util.c"
        "src/config.c"
        "src/display_settings.c"
        "src/display_idle_policy.c"
        "src/orientation_policy.c"
        "src/orientation_runtime.c"
        "src/orientation_qmi8658.c"
        "src/display_units.c"
        "src/config_store.c"
        "src/config_transfer.c"
        "src/config_runtime.c"
        "src/config_trial.c"
        "src/config_trial_policy.c"
        "src/alert_engine.c"
        "src/alert_events.c"
        "src/alert_runtime.c"
        "src/clear_policy.c"
        "src/diagnostic_clear.c"
        "src/ota_transfer.c"
        "src/wifi_bulk.c"
        "src/wifi_bulk_policy.c"
        "src/wifi_bulk_io.c"
        "src/transfer_gate.c"
        "src/diagnostics_state.c"
        "src/diagnostic_alerts.c"
        "src/hardware_probe.c"
        "src/worker_health.c"
        "src/memory_health.c"
        "src/config_document.c"
        "src/json_guard.c"
        "src/pid_decoder.c"
        "src/poll_scheduler.c"
        "src/reading_policy.c"
        "src/page_save_policy.c"
        "src/smart_page_policy.c"
        "src/smart_metric_catalog.c"
        "src/page_action.c"
        "src/temporary_idle.c"
        "src/jeep_idle_driver.c"
        "src/jeep_abs_tcs_driver.c"
        "src/vehicle_commands.c"
        "src/vehicle_idle_coolant.c"
        "src/jeep_idle_parked_test.c"
        "src/vehicle_commands_runtime.c"
        "src/gauge_layout.c"
        "src/gauge_surface.c"
        "src/gauge_presentation.c"
        "${EGAUGE_UI_PROFILE_SOURCE}"
        "${EGAUGE_BOARD_ADAPTER_SOURCE}"
        "src/diagnostic_filter.c"
        "src/display_showcase.c"
        "src/ui.c"
    "src/trend_curve.c"
        ${FONT_SOURCES}
    INCLUDE_DIRS
        "."
        "inc"
    EMBED_TXTFILES "certs/dev-update-public.pem"
    REQUIRES lvgl esp_lvgl_port bt
    PRIV_REQUIRES nvs_flash spi_flash mbedtls json app_update esp_wifi esp_netif esp_event lwip esp_psram esp_timer
)

target_compile_options(${COMPONENT_TARGET} PRIVATE -Wall -Wextra -Wshadow -Werror)
target_compile_definitions(${COMPONENT_TARGET} PRIVATE LV_LVGL_H_INCLUDE_SIMPLE=1)
