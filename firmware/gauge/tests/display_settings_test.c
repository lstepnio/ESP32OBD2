#include <assert.h>
#include <stdio.h>
#include "display_settings.h"
#include "nvs.h"
int test_lock_busy, test_critical_depth;
static int commit_fails, commits;
esp_err_t nvs_open(const char *key, int mode, nvs_handle_t *handle) {
 (void)key; assert(test_critical_depth == 0); *handle = 1;
 return mode == NVS_READONLY ? ESP_ERR_NVS_NOT_FOUND : ESP_OK;
}
esp_err_t nvs_get_blob(nvs_handle_t h, const char *k, void *v, size_t *l) {
 (void)h; (void)k; (void)v; (void)l; return ESP_ERR_NVS_NOT_FOUND;
}
esp_err_t nvs_set_blob(nvs_handle_t h, const char *k, const void *v, size_t l) {
 (void)h; (void)k; (void)v; (void)l; assert(test_critical_depth == 0); return ESP_OK;
}
esp_err_t nvs_commit(nvs_handle_t handle) {
 (void)handle; assert(test_critical_depth == 0); commits++;
 /* Emulate a reader during flash commit: it gets the last committed revision. */
 test_lock_busy = 1;
 display_settings_t snapshot = display_settings_snapshot();
 assert(snapshot.revision == (unsigned)(commits == 1 ? 0 : 1));
 test_lock_busy = 0;
 return commit_fails ? ESP_FAIL : ESP_OK;
}
void nvs_close(nvs_handle_t handle) { (void)handle; }
int main(void) {
 assert(display_settings_init(0, 80, 0) == ESP_OK);
 display_settings_t saved;
 assert(display_settings_save(1, 100, 1, 10, 0, &saved) == ESP_OK);
 assert(saved.revision == 1 && saved.brightness == 100 && saved.units == 1);
 assert(display_settings_save(0, 50, 0, 0, 0, &saved) == ESP_ERR_INVALID_STATE);
 commit_fails = 1;
 assert(display_settings_save(0, 50, 0, 0, 1, &saved) == ESP_FAIL);
 assert(display_settings_snapshot().revision == 1);
 assert(display_settings_snapshot().rotation == 1);
 test_lock_busy = 1;
 assert(display_settings_save(0, 50, 0, 0, 1, &saved) == ESP_ERR_TIMEOUT);
 test_lock_busy = 0;
 assert(display_settings_save(4, 50, 0, 0, 1, &saved) == ESP_ERR_INVALID_ARG);
 assert(commits == 2 && test_critical_depth == 0);
 puts("Display read during commit, failed commit, stale revision and bounded writer contention passed");
}
