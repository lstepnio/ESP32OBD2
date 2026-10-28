#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <setjmp.h>
#include "display_settings.h"
#include "nvs.h"

int test_lock_busy, test_critical_depth;
typedef struct
{
    unsigned char bytes[16];
    size_t        length;
} blob_t;
static blob_t             flash[3], pending[3];
static int                commit_count, cut_commit, fail_commit, fail_set, set_count;
static bool               cut_after;
static jmp_buf            power_loss;
static display_settings_t before;
static int                key_index(const char *key)
{
    if (strcmp(key, "settings") == 0)
        return 0;
    if (strcmp(key, "smart0") == 0)
        return 1;
    assert(strcmp(key, "smart1") == 0);
    return 2;
}
esp_err_t nvs_open(const char *key, int mode, nvs_handle_t *handle)
{
    assert(strcmp(key, "display") == 0 && test_critical_depth == 0);
    (void)mode;
    memcpy(pending, flash, sizeof(flash));
    *handle = 1;
    return ESP_OK;
}
esp_err_t nvs_get_blob(nvs_handle_t h, const char *key, void *value, size_t *length)
{
    (void)h;
    assert(test_critical_depth == 0);
    blob_t *blob = &flash[key_index(key)];
    if (!blob->length)
        return ESP_ERR_NVS_NOT_FOUND;
    if (*length < blob->length)
        return ESP_ERR_NVS_INVALID_LENGTH;
    memcpy(value, blob->bytes, blob->length);
    *length = blob->length;
    return ESP_OK;
}
esp_err_t nvs_set_blob(nvs_handle_t h, const char *key, const void *value, size_t length)
{
    (void)h;
    assert(test_critical_depth == 0 && length <= sizeof(pending[0].bytes));
    if (++set_count == fail_set)
        return ESP_FAIL;
    blob_t *blob = &pending[key_index(key)];
    memcpy(blob->bytes, value, length);
    blob->length = length;
    return ESP_OK;
}
esp_err_t nvs_commit(nvs_handle_t handle)
{
    (void)handle;
    assert(test_critical_depth == 0);
    ++commit_count;
    /* A reader during either flash transaction never takes the writer lock. */
    test_lock_busy              = 1;
    display_settings_t observed = display_settings_snapshot();
    assert(observed.revision == before.revision && observed.smart_cycle == before.smart_cycle);
    test_lock_busy = 0;
    if (commit_count == cut_commit && !cut_after)
        longjmp(power_loss, 1);
    if (commit_count == fail_commit)
        return ESP_FAIL;
    memcpy(flash, pending, sizeof(flash));
    if (commit_count == cut_commit && cut_after)
        longjmp(power_loss, 1);
    return ESP_OK;
}
void nvs_close(nvs_handle_t handle)
{
    (void)handle;
    assert(test_critical_depth == 0);
    memcpy(pending, flash, sizeof(flash));
}
static void reboot(void)
{
    test_lock_busy = 0;
    cut_commit = fail_commit = fail_set = set_count = commit_count = 0;
    assert(display_settings_init(0, 80, 0) == ESP_OK);
    before = display_settings_snapshot();
}
static void save(bool smart)
{
    before = display_settings_snapshot();
    assert(display_settings_save_smart(1, 100, 1, 10, smart, before.revision, NULL) == ESP_OK);
}
static void seed_legacy(unsigned version, unsigned revision)
{
    memset(flash, 0, sizeof(flash));
    unsigned char legacy[] = {(unsigned char)version, 2, 70, 0, (unsigned char)revision, 0, 0, 0, 15, 0, 0, 0};
    flash[0].length        = version == 3 ? 12 : 8;
    memcpy(flash[0].bytes, legacy, flash[0].length);
    reboot();
}
int main(void)
{
    reboot();
    assert(!before.smart_cycle && before.revision == 0);
    save(true);
    reboot();
    assert(before.smart_cycle && before.revision == 1 && before.cycle_seconds == 10);
    assert(display_settings_save(3, 50, 0, 0, 1, NULL) == ESP_OK);
    reboot();
    assert(before.smart_cycle && before.cycle_seconds == 0 && before.revision == 2);
    save(false);
    reboot();
    assert(!before.smart_cycle && before.revision == 3);

    /* Cut immediately before/after each of the two commit activation points,
     * testing both preference directions with a previously committed slot. */
    for (int old_smart = 0; old_smart <= 1; ++old_smart)
        for (int point = 1; point <= 2; ++point)
            for (int after = 0; after <= 1; ++after)
            {
                memset(flash, 0, sizeof(flash));
                reboot();
                save(old_smart != 0);
                reboot();
                cut_commit = point;
                cut_after  = after != 0;
                if (setjmp(power_loss) == 0)
                {
                    (void)display_settings_save_smart(2, 60, 0, 30, !old_smart, before.revision, NULL);
                    assert(false);
                }
                reboot();
                bool activated = point == 2 && after;
                assert(before.revision == (activated ? 2U : 1U));
                assert(before.smart_cycle == (activated ? !old_smart : old_smart));
                /* A retry must preserve the active slot while staging. */
                save(!old_smart);
                reboot();
                assert(before.smart_cycle == !old_smart);
            }

    /* First opt-in has no pre-existing sidecar. A cut cannot turn it on
     * unless the matching legacy revision was activated. */
    for (int point = 1; point <= 2; ++point)
        for (int after = 0; after <= 1; ++after)
        {
            memset(flash, 0, sizeof(flash));
            reboot();
            cut_commit = point;
            cut_after  = after != 0;
            if (setjmp(power_loss) == 0)
            {
                (void)display_settings_save_smart(2, 60, 0, 30, true, 0, NULL);
                assert(false);
            }
            reboot();
            bool activated = point == 2 && after;
            assert(before.revision == (activated ? 1U : 0U));
            assert(before.smart_cycle == activated);
        }

    for (int fail = 1; fail <= 2; ++fail)
        for (int on_set = 0; on_set <= 1; ++on_set)
        {
            memset(flash, 0, sizeof(flash));
            reboot();
            save(true);
            reboot();
            if (on_set)
                fail_set = fail;
            else
                fail_commit = fail;
            display_settings_t untouched = {.revision = 123};
            assert(display_settings_save_smart(0, 50, 0, 0, false, 1, &untouched) == ESP_FAIL);
            assert(untouched.revision == 123);
            assert(display_settings_snapshot().revision == 1 && display_settings_snapshot().smart_cycle);
            reboot();
            assert(before.revision == 1 && before.smart_cycle);
        }
    assert(display_settings_save_smart(0, 50, 0, 0, false, 0, NULL) == ESP_ERR_INVALID_STATE);
    test_lock_busy = 1;
    assert(display_settings_save_smart(0, 50, 0, 0, false, 1, NULL) == ESP_ERR_TIMEOUT);
    test_lock_busy = 0;
    assert(display_settings_save(4, 50, 0, 0, 1, NULL) == ESP_ERR_INVALID_ARG);
    assert(display_settings_save(0, 50, 0, 7, 1, NULL) == ESP_ERR_INVALID_ARG);

    for (unsigned version = 1; version <= 3; ++version)
    {
        seed_legacy(version, 9);
        assert(before.revision == 9 && !before.smart_cycle);
        assert(before.cycle_seconds == (version == 3 ? 15 : 0));
        save(true);
        reboot();
        assert(before.smart_cycle && before.revision == 10);
        /* Old firmware writes only its unchanged legacy record. */
        flash[0].bytes[4] = 11;
        reboot();
        assert(!before.smart_cycle && !before.auto_orientation && before.revision == 11);
    }
    /* Every malformed sidecar field and missing/oversize blob fails closed. */
    for (int field = 0; field < 7; ++field)
    {
        memset(flash, 0, sizeof(flash));
        reboot();
        save(true);
        reboot();
        if (field == 0)
            flash[1].bytes[0] = 2;
        if (field == 1)
            flash[1].bytes[1] = 4;
        if (field == 2)
            flash[1].bytes[2] = 1;
        if (field == 3)
            flash[1].bytes[4] = 99;
        if (field == 4)
            flash[1].length = 7;
        if (field == 5)
            flash[1].length = 9;
        if (field == 6)
            flash[1].length = 0;
        reboot();
        assert(!before.smart_cycle && before.revision == 1);
    }
    for (int old_auto = 0; old_auto <= 1; ++old_auto)
        for (int point = 1; point <= 2; ++point)
            for (int after = 0; after <= 1; ++after)
            {
                memset(flash, 0, sizeof(flash));
                reboot();
                save(true);
                reboot();
                assert(display_settings_save_orientation(old_auto != 0, 1, before.revision, NULL) == ESP_OK);
                reboot();
                cut_commit = point;
                cut_after  = after != 0;
                if (setjmp(power_loss) == 0)
                {
                    (void)display_settings_save_orientation(!old_auto, 1, before.revision, NULL);
                    assert(false);
                }
                reboot();
                bool activated = point == 2 && after;
                assert(before.auto_orientation == (activated ? !old_auto : old_auto));
                assert(before.revision == (activated ? 3U : 2U));
                assert(before.smart_cycle && before.rotation == 1);
                assert(display_settings_save_orientation(!old_auto, 1, before.revision, NULL) == ESP_OK);
                reboot();
                assert(before.auto_orientation == !old_auto && before.smart_cycle);
            }
    memset(flash, 0, sizeof(flash));
    reboot();
    assert(!before.auto_orientation);
    assert(display_settings_save_orientation(true, 2, 0, NULL) == ESP_OK);
    reboot();
    assert(before.auto_orientation && before.rotation == 2);
    assert(display_settings_save_smart(2, 50, 0, 30, true, 1, NULL) == ESP_OK);
    reboot();
    assert(before.auto_orientation && before.smart_cycle && before.rotation == 2);
    assert(display_settings_save_orientation(false, 2, 2, NULL) == ESP_OK);
    reboot();
    assert(!before.auto_orientation && before.smart_cycle);
    assert(display_settings_save_orientation(true, 2, 3, NULL) == ESP_OK);
    reboot();
    assert(display_settings_save(2, 50, 0, 30, 4, NULL) == ESP_OK);
    reboot();
    assert(!before.auto_orientation && before.smart_cycle);
    /* Auto shares the same revision activation and preserves old state on failure. */
    for (int fail = 1; fail <= 2; ++fail)
    {
        fail_commit  = fail;
        commit_count = 0;
        assert(display_settings_save_orientation(true, 2, before.revision, NULL) == ESP_FAIL);
        reboot();
        assert(!before.auto_orientation && before.rotation == 2);
    }
    seed_legacy(3, 1);
    memset(flash[0].bytes + 4, 255, 4);
    reboot();
    assert(display_settings_save_smart(0, 50, 0, 0, true, UINT32_MAX, NULL) == ESP_ERR_INVALID_STATE);
    assert(test_critical_depth == 0);
    puts(
        "Smart sidecars: commit cuts, failures, nonblocking readers, legacy migration/downgrade and corruption passed");
}
