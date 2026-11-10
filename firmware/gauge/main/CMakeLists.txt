# Private integration builds may supply one adapter without forking the runtime.
set(EGAUGE_BOARD_ADAPTER_SOURCE "${CMAKE_CURRENT_LIST_DIR}/src/gauge_board_reference.c"
    CACHE FILEPATH "Board/display implementation for this firmware target")
if(NOT IS_ABSOLUTE "${EGAUGE_BOARD_ADAPTER_SOURCE}" OR NOT EXISTS "${EGAUGE_BOARD_ADAPTER_SOURCE}")
    message(FATAL_ERROR "EGAUGE_BOARD_ADAPTER_SOURCE must name an existing absolute source path")
endif()

set(EGAUGE_DEV_UPDATE_PUBLIC_KEY "${CMAKE_CURRENT_LIST_DIR}/certs/dev-update-public.pem"
    CACHE FILEPATH "P-256 public verification key for development firmware updates")
if(NOT IS_ABSOLUTE "${EGAUGE_DEV_UPDATE_PUBLIC_KEY}" OR NOT EXISTS "${EGAUGE_DEV_UPDATE_PUBLIC_KEY}"
    OR IS_DIRECTORY "${EGAUGE_DEV_UPDATE_PUBLIC_KEY}")
    message(FATAL_ERROR "EGAUGE_DEV_UPDATE_PUBLIC_KEY must name an existing absolute PEM path")
endif()
file(READ "${EGAUGE_DEV_UPDATE_PUBLIC_KEY}" dev_update_public_pem)
if(NOT dev_update_public_pem MATCHES "^-----BEGIN PUBLIC KEY-----[\r\n]+[A-Za-z0-9+/=\r\n]+-----END PUBLIC KEY-----[\r\n]*$")
    message(FATAL_ERROR "EGAUGE_DEV_UPDATE_PUBLIC_KEY must contain a single PUBLIC KEY PEM")
endif()
set(dev_update_public_embed "${CMAKE_CURRENT_BINARY_DIR}/dev-update-public.pem")
configure_file("${EGAUGE_DEV_UPDATE_PUBLIC_KEY}" "${dev_update_public_embed}" COPYONLY)

set(EGAUGE_PROTOCOL_BOARD_NAME "ESP32-S3-Touch-LCD-1.28" CACHE STRING "Board label in BLE discovery")
string(LENGTH "${EGAUGE_PROTOCOL_BOARD_NAME}" protocol_board_name_length)
if(protocol_board_name_length GREATER 29 OR NOT EGAUGE_PROTOCOL_BOARD_NAME MATCHES "^[A-Za-z0-9][A-Za-z0-9._-]*$")
    message(FATAL_ERROR "EGAUGE_PROTOCOL_BOARD_NAME must be a 1-29 byte ASCII board label")
endif()

# Public firmware defaults. Private product projects opt in to each feature.
option(EGAUGE_FEATURE_DUAL_ADAPTER "Enable a separate child OBD2 adapter" OFF)
option(EGAUGE_FEATURE_SMART_CYCLE "Enable reading-driven automatic page cycling" OFF)
option(EGAUGE_FEATURE_VEHICLE_ACTIONS "Enable vehicle action pages and Start" OFF)
option(EGAUGE_FEATURE_CONNECTION_FOOTER "Enable subtle display link status" OFF)
if((CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST OR CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST)
    AND NOT EGAUGE_FEATURE_VEHICLE_ACTIONS)
    message(FATAL_ERROR "Owner action candidates require EGAUGE_FEATURE_VEHICLE_ACTIONS")
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
        "src/adapter_role_policy.c"
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
        "src/sunset_policy.c"
        "src/sunset_runtime.c"
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
        "src/reading_presentation.c"
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
    EMBED_TXTFILES "${dev_update_public_embed}"
    REQUIRES lvgl esp_lvgl_port bt
    PRIV_REQUIRES nvs_flash spi_flash mbedtls json app_update esp_wifi esp_netif esp_event lwip esp_psram esp_timer
)

target_compile_options(${COMPONENT_TARGET} PRIVATE -Wall -Wextra -Wshadow -Werror)
target_compile_definitions(${COMPONENT_TARGET} PRIVATE
    LV_LVGL_H_INCLUDE_SIMPLE=1
    EGAUGE_PROTOCOL_BOARD_NAME="${EGAUGE_PROTOCOL_BOARD_NAME}"
    EGAUGE_FEATURE_DUAL_ADAPTER=$<BOOL:${EGAUGE_FEATURE_DUAL_ADAPTER}>
    EGAUGE_FEATURE_SMART_CYCLE=$<BOOL:${EGAUGE_FEATURE_SMART_CYCLE}>
    EGAUGE_FEATURE_VEHICLE_ACTIONS=$<BOOL:${EGAUGE_FEATURE_VEHICLE_ACTIONS}>
    EGAUGE_FEATURE_CONNECTION_FOOTER=$<BOOL:${EGAUGE_FEATURE_CONNECTION_FOOTER}>)
