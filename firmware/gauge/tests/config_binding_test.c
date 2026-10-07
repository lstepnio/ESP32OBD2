/* Production validators/compiler with an in-memory partition, never a mock decoder. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "cJSON.h"
#include "config_document.h"
#include "config_runtime.h"
#include "config_trial.h"

static char *document;
static size_t document_length;
static esp_partition_t partition;
esp_err_t esp_partition_read(const esp_partition_t *p, size_t offset, void *out, size_t length)
{
    (void)p;
    if (offset > document_length || length > document_length-offset) return ESP_ERR_INVALID_ARG;
    memcpy(out, document+offset, length); return ESP_OK;
}
esp_err_t config_store_read_record(const config_store_record_t *record, uint32_t offset, void *out, size_t length)
{ (void)record; return esp_partition_read(&partition, offset, out, length); }
esp_err_t config_store_active(config_store_record_t *record)
{ *record = (config_store_record_t){.revision=22, .length=document_length}; return ESP_OK; }
esp_err_t config_store_previous(config_store_record_t *record) { (void)record; return ESP_ERR_NOT_FOUND; }
esp_err_t config_store_select(const config_store_record_t *record) { (void)record; return ESP_ERR_NOT_FOUND; }
esp_err_t config_trial_decide(uint32_t rev, config_trial_decision_t *out)
{ (void)rev; *out=CONFIG_TRIAL_USE_ACTIVE; return ESP_OK; }
esp_err_t config_trial_reject(uint32_t rev) { (void)rev; return ESP_OK; }
static void set(cJSON *root)
{ free(document); document=cJSON_PrintUnformatted(root); assert(document); document_length=strlen(document); }
static esp_err_t validate(void)
{ config_document_context_t ctx={.base_revision=21,.max_adapter_links=2}; return config_document_validate(&partition,0,document_length,&ctx); }
int main(int argc, char **argv)
{
    assert(argc==2);
    FILE *file=fopen(argv[1],"rb"); assert(file);
    char input[65537]; size_t length=fread(input,1,sizeof(input)-1,file); fclose(file); input[length]=0;
    cJSON *root=cJSON_Parse(input); assert(root);
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"baseRevision"),21);
    set(root); assert(validate()==ESP_OK);
    config_runtime_t runtime; config_store_record_t active; bool previous;
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK);
    assert(!runtime.legacy_auto_discovery && !strcmp(runtime.adapter_address,"C0:00:00:00:00:01") && runtime.adapter_address_type==1);
    assert(runtime.pids[0].responder==0x7e8);
    cJSON *source=cJSON_GetArrayItem(cJSON_GetObjectItem(root,"sources"),0);
    cJSON *adapter=cJSON_GetObjectItem(source,"adapter");
    cJSON_ReplaceItemInObject(adapter,"addressType",cJSON_CreateString("guess"));
    set(root); assert(validate()==ESP_ERR_INVALID_ARG);
    cJSON_ReplaceItemInObject(adapter,"addressType",cJSON_CreateString("random"));
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"schemaVersion"),1);
    set(root); assert(validate()==ESP_ERR_INVALID_ARG);
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"schemaVersion"),2);
    cJSON_ReplaceItemInObject(adapter,"driver",cJSON_CreateString("elm-bench-v1"));
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_validate(&partition,0,document_length,NULL)==ESP_ERR_NOT_SUPPORTED);
    cJSON_DeleteItemFromObject(source,"adapter");
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK);
    assert(!runtime.legacy_auto_discovery && runtime.adapter_address[0]==0);
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"schemaVersion"),1);
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK && runtime.legacy_auto_discovery);
    free(document); cJSON_Delete(root);
    puts("Production configuration binding validation and runtime fixtures passed");
}
