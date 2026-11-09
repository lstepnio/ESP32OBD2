#include "platform_features.h"

#include <stdbool.h>
#include <string.h>

#include "esp_log.h"
#include "esp_mac.h"
#include "esp_partition.h"
#include "mbedtls/pk.h"
#include "mbedtls/sha256.h"
#include "nvs.h"
#include "nvs_flash.h"

#define LICENSE_PARTITION "eg_entitle"
#define LICENSE_PARTITION_OFFSET 0x660000U
#define LICENSE_PARTITION_SIZE 0x3000U
#define FACTORY_MAC_SIZE 6U
#define UNIT_ID_SIZE 32U
#define SIGNATURE_MIN_SIZE 8U
#define SIGNATURE_MAX_SIZE 72U

extern const uint8_t license_key_start[] asm("_binary_oled_license_public_pem_start");
extern const uint8_t license_key_end[] asm("_binary_oled_license_public_pem_end");

static const uint8_t license_magic[] = "EGOLIC1";
static uint8_t       effective_features;

#define LICENSE_PAYLOAD_SIZE (sizeof(license_magic) + UNIT_ID_SIZE + 1U)
#define LICENSE_MAX_SIZE (LICENSE_PAYLOAD_SIZE + SIGNATURE_MAX_SIZE)

static bool read_license(uint8_t bytes[LICENSE_MAX_SIZE], size_t *length)
{
    const esp_partition_t *partition =
        esp_partition_find_first(ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_DATA_NVS, LICENSE_PARTITION);
    if (!partition || partition->address != LICENSE_PARTITION_OFFSET || partition->size != LICENSE_PARTITION_SIZE ||
        !partition->readonly || partition->encrypted || nvs_flash_init_partition(LICENSE_PARTITION) != ESP_OK)
        return false;

    nvs_handle_t handle;
    esp_err_t    err = nvs_open_from_partition(LICENSE_PARTITION, "license", NVS_READONLY, &handle);
    if (err == ESP_OK)
    {
        err = nvs_get_blob(handle, "entitlement", bytes, length);
        nvs_close(handle);
    }
    (void)nvs_flash_deinit_partition(LICENSE_PARTITION);
    return err == ESP_OK;
}

static bool matches_factory_mac(const uint8_t unit_id[UNIT_ID_SIZE])
{
    static const uint8_t domain[] = "egauge/esp32s3/factory-mac/v1";
    uint8_t              input[sizeof(domain) + FACTORY_MAC_SIZE];
    uint8_t              digest[UNIT_ID_SIZE];
    uint8_t             *mac = input + sizeof(domain);
    static const uint8_t empty_mac[FACTORY_MAC_SIZE];
    if (esp_efuse_mac_get_default(mac) != ESP_OK || (mac[0] & 3U) || memcmp(mac, empty_mac, FACTORY_MAC_SIZE) == 0)
        return false;
    memcpy(input, domain, sizeof(domain));
    return mbedtls_sha256(input, sizeof(input), digest, 0) == 0 && memcmp(digest, unit_id, sizeof(digest)) == 0;
}

static bool verifies(const uint8_t *bytes, size_t length)
{
    if (length < LICENSE_PAYLOAD_SIZE + SIGNATURE_MIN_SIZE || length > LICENSE_MAX_SIZE ||
        memcmp(bytes, license_magic, sizeof(license_magic)) != 0 ||
        (bytes[LICENSE_PAYLOAD_SIZE - 1] & ~PLATFORM_FEATURE_ALL) != 0 ||
        !matches_factory_mac(bytes + sizeof(license_magic)))
        return false;

    uint8_t digest[32];
    if (mbedtls_sha256(bytes, LICENSE_PAYLOAD_SIZE, digest, 0) != 0)
        return false;
    mbedtls_pk_context key;
    mbedtls_pk_init(&key);
    int rc = mbedtls_pk_parse_public_key(&key, license_key_start, license_key_end - license_key_start);
    const mbedtls_ecp_keypair *ec_key = rc == 0 ? mbedtls_pk_ec(key) : NULL;
    bool valid = ec_key && mbedtls_ecp_keypair_get_group_id(ec_key) == MBEDTLS_ECP_DP_SECP256R1 &&
                 mbedtls_pk_verify(&key, MBEDTLS_MD_SHA256, digest, sizeof(digest), bytes + LICENSE_PAYLOAD_SIZE,
                                   length - LICENSE_PAYLOAD_SIZE) == 0;
    mbedtls_pk_free(&key);
    return valid;
}

void platform_features_init(void)
{
    uint8_t bytes[LICENSE_MAX_SIZE];
    size_t  length     = sizeof(bytes);
    bool    valid      = read_license(bytes, &length) && verifies(bytes, length);
    effective_features = valid ? bytes[LICENSE_PAYLOAD_SIZE - 1] & EGAUGE_COMPILED_FEATURES : 0;
    ESP_LOGI("entitlement", "OLED entitlement %s", valid ? "verified" : "unavailable or invalid");
}

uint8_t platform_feature_mask(void)
{
    return effective_features;
}
