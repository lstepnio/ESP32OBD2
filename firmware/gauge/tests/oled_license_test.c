#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "esp_partition.h"
#include "nvs.h"
#include "platform_features.h"

static esp_partition_t partition = {0x660000, 0x3000, true, false};
static uint8_t         data[256];
static size_t          data_len;
static bool            wrong_board, unavailable, no_key;

const esp_partition_t *esp_partition_find_first(int type, int subtype, const char *name)
{
    assert(type == ESP_PARTITION_TYPE_DATA && subtype == ESP_PARTITION_SUBTYPE_DATA_NVS);
    assert(strcmp(name, "eg_entitle") == 0);
    return unavailable ? NULL : &partition;
}
int nvs_flash_init_partition(const char *name)
{
    assert(strcmp(name, "eg_entitle") == 0);
    return ESP_OK;
}
int nvs_flash_deinit_partition(const char *name)
{
    assert(strcmp(name, "eg_entitle") == 0);
    return ESP_OK;
}
esp_err_t nvs_open_from_partition(const char *part, const char *namespace, int mode, nvs_handle_t *handle)
{
    assert(strcmp(part, "eg_entitle") == 0 && strcmp(namespace, "license") == 0 && mode == NVS_READONLY);
    *handle = 1;
    return ESP_OK;
}
esp_err_t nvs_get_blob(nvs_handle_t handle, const char *key, void *out, size_t *length)
{
    assert(handle == 1 && strcmp(key, "entitlement") == 0);
    if (no_key || *length < data_len)
        return 1;
    memcpy(out, data, data_len);
    *length = data_len;
    return ESP_OK;
}
void nvs_close(nvs_handle_t handle)
{
    assert(handle == 1);
}
int esp_efuse_mac_get_default(uint8_t *out)
{
    const uint8_t mac[] = {0x00, 0x11, 0x22, 0x33, 0x44, wrong_board ? 0x56 : 0x55};
    memcpy(out, mac, sizeof(mac));
    return ESP_OK;
}
int main(int argc, char **argv)
{
    assert(argc == 3);
    FILE *license = fopen(argv[1], "rb");
    assert(license);
    data_len = fread(data, 1, sizeof(data), license);
    assert(data_len > 0 && feof(license) && fclose(license) == 0);
    assert(platform_feature_mask() == 0);
    platform_features_init();
    assert(platform_feature_mask() == atoi(argv[2]));
    wrong_board = true;
    platform_features_init();
    assert(platform_feature_mask() == 0);
    wrong_board = false;
    unavailable = true;
    platform_features_init();
    assert(platform_feature_mask() == 0);
    unavailable = false;
    no_key      = true;
    platform_features_init();
    assert(platform_feature_mask() == 0);
    no_key             = false;
    partition.readonly = false;
    platform_features_init();
    assert(platform_feature_mask() == 0);
    partition.readonly = true;
    data[data_len - 1] ^= 1;
    platform_features_init();
    assert(platform_feature_mask() == 0);
    data_len = 12;
    platform_features_init();
    assert(platform_feature_mask() == 0);
    puts("License boot policy checks passed");
}
