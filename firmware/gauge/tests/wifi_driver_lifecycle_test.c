#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>

typedef int esp_err_t;
typedef struct { int unused; } esp_netif_t;
typedef struct { int unused; } esp_netif_config_t;
typedef struct { int unused; } wifi_init_config_t;
#define ESP_OK 0
#define ESP_ERR_INVALID_STATE 1
#define WIFI_EVENT 0
#define ESP_EVENT_ANY_ID 0
#define ESP_NETIF_DEFAULT_WIFI_AP() ((esp_netif_config_t){0})
#define WIFI_INIT_CONFIG_DEFAULT() ((wifi_init_config_t){0})
static esp_netif_t netif;
static esp_netif_t *ap_netif;
static bool wifi_initialized, netif_live, driver_live, default_handlers, custom_handler;
static int failure, stops, deinits, destroys;
enum { NETIF_INIT=1, EVENT_LOOP, NETIF_NEW, ATTACH, DEFAULT_HANDLERS, WIFI_INIT, CUSTOM_HANDLER };
static void wifi_event(void *arg) { (void)arg; }
static int result(int stage) { return failure==stage ? -1 : ESP_OK; }
static esp_err_t esp_netif_init(void) { return result(NETIF_INIT); }
static esp_err_t esp_event_loop_create_default(void) { return failure==EVENT_LOOP ? -1 : ESP_ERR_INVALID_STATE; }
static esp_netif_t *esp_netif_new(const esp_netif_config_t *c) { (void)c; if(failure==NETIF_NEW)return NULL; netif_live=true; return &netif; }
static esp_err_t esp_netif_attach_wifi_ap(esp_netif_t *n) { assert(n==&netif); return result(ATTACH); }
static esp_err_t esp_wifi_set_default_wifi_ap_handlers(void) { if(failure==DEFAULT_HANDLERS)return -1;default_handlers=true;return ESP_OK; }
static esp_err_t esp_wifi_init(const wifi_init_config_t *c) { (void)c; if(failure==WIFI_INIT)return -1;driver_live=true;return ESP_OK; }
static esp_err_t esp_event_handler_register(int event,int id,void (*handler)(void*),void *arg) { (void)event;(void)id;(void)handler;(void)arg;if(failure==CUSTOM_HANDLER)return -1;custom_handler=true;return ESP_OK; }
static esp_err_t esp_event_handler_unregister(int event,int id,void (*handler)(void*)) { (void)event;(void)id;(void)handler;custom_handler=false;return ESP_OK; }
static esp_err_t esp_wifi_stop(void) { assert(driver_live);++stops;return ESP_OK; }
static esp_err_t esp_wifi_deinit(void) { assert(driver_live);driver_live=false;++deinits;return ESP_OK; }
static void esp_netif_destroy_default_wifi(esp_netif_t *n) { assert(n==&netif && netif_live);netif_live=false;default_handlers=false;++destroys; }

/* Generated from the actual production lifecycle functions by the host runner. */
#include "wifi_driver_lifecycle.inc"

static void clean(void) {
    assert(!ap_netif && !wifi_initialized && !netif_live && !driver_live);
    assert(!default_handlers && !custom_handler);
}
int main(void) {
    for(int stage=NETIF_INIT;stage<=CUSTOM_HANDLER;++stage) {
        failure=stage;
        assert(!initialize_wifi());
        release_wifi_driver();
        clean();
        failure=0;
        assert(initialize_wifi());
        assert(wifi_initialized && netif_live && driver_live && custom_handler);
        release_wifi_driver();
        clean();
    }
    int old_stops=stops,old_deinits=deinits,old_destroys=destroys;
    release_wifi_driver();
    clean();
    assert(stops==old_stops && deinits==old_deinits && destroys==old_destroys);
    assert(initialize_wifi());
    assert(initialize_wifi());
    release_wifi_driver();
    clean();
    puts("Wi-Fi lifecycle failures, retries and idempotent close passed");
}
