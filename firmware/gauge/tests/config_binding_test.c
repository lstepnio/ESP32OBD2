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
    assert(argc==3);
    FILE *file=fopen(argv[1],"rb"); assert(file);
    char input[65537]; size_t length=fread(input,1,sizeof(input)-1,file); fclose(file); input[length]=0;
    cJSON *root=cJSON_Parse(input); assert(root);
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"baseRevision"),21);
    set(root); assert(validate()==ESP_OK);
    config_runtime_t runtime; config_store_record_t active; bool previous;
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK);
    assert(!runtime.legacy_auto_discovery && !strcmp(runtime.sources[0].address,"C0:00:00:00:00:01") && runtime.sources[0].address_type==1);
    assert(runtime.pids[0].responder==0x7e8);
    cJSON *engine_root=cJSON_Duplicate(root,1);
    cJSON *source=cJSON_GetArrayItem(cJSON_GetObjectItem(root,"sources"),0);
    cJSON *original_label=cJSON_Duplicate(cJSON_GetObjectItem(source,"label"),1);
    char label[34]; memset(label,'x',33); label[32]=0;
    cJSON_ReplaceItemInObject(source,"label",cJSON_CreateString(label));
    set(root); assert(validate()==ESP_OK);
    label[32]='x'; label[33]=0;
    cJSON_ReplaceItemInObject(source,"label",cJSON_CreateString(label));
    set(root); assert(validate()!=ESP_OK);
    cJSON_ReplaceItemInObject(source,"label",cJSON_CreateString(""));
    set(root); assert(validate()!=ESP_OK);
    cJSON_ReplaceItemInObject(source,"label",original_label);

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
    assert(!runtime.legacy_auto_discovery && runtime.sources[0].address[0]==0);
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"schemaVersion"),1);
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK && runtime.legacy_auto_discovery);
    /* Single experimental TCM source using the actual observed three-byte reply. */
    cJSON_SetNumberValue(cJSON_GetObjectItem(root,"schemaVersion"),2);
    cJSON_ReplaceItemInObject(source,"id",cJSON_CreateString("tcm"));
    cJSON_ReplaceItemInObject(source,"role",cJSON_CreateString("tcm"));
    cJSON_ReplaceItemInObject(source,"label",cJSON_CreateString("TCM adapter (experimental)"));
    cJSON_AddItemToObject(source,"adapter",cJSON_Parse("{\"id\":\"test-adapter\",\"address\":\"C0:00:00:00:00:01\",\"addressType\":\"random\",\"driver\":\"elm-18f0-v1\"}"));
    cJSON *definitions=cJSON_GetObjectItem(root,"definitions");
    while(cJSON_GetArraySize(definitions)>1) cJSON_DeleteItemFromArray(definitions,1);
    cJSON *definition=cJSON_GetArrayItem(definitions,0);
    cJSON_ReplaceItemInObject(definition,"sourceId",cJSON_CreateString("tcm"));
    cJSON_ReplaceItemInObject(definition,"name",cJSON_CreateString("TCM temp (experimental)"));
    cJSON_ReplaceItemInObject(definition,"unit",cJSON_CreateString("degC"));
    cJSON_ReplaceItemInObject(definition,"request",cJSON_Parse("{\"service\":\"22\",\"identifier\":\"04FE\",\"route\":\"can11_physical\",\"requestId\":\"7E1\",\"responseId\":\"7E9\"}"));
    cJSON_ReplaceItemInObject(definition,"response",cJSON_Parse("{\"prefix\":\"6204FE\",\"minPayloadBytes\":3}"));
    cJSON_ReplaceItemInObject(definition,"decoder",cJSON_Parse("{\"byteOffset\":0,\"byteLength\":1,\"endian\":\"big\",\"signed\":false,\"numerator\":1,\"denominator\":1,\"offset\":-40}"));
    cJSON_ReplaceItemInObject(definition,"range",cJSON_Parse("{\"min\":0,\"max\":180}"));
    cJSON_ReplaceItemInObject(definition,"vectors",cJSON_Parse("[{\"payloadHex\":\"555455\",\"expected\":45}]"));
    cJSON *pages=cJSON_GetObjectItem(root,"pages");
    while(cJSON_GetArraySize(pages)>1) cJSON_DeleteItemFromArray(pages,1);
    cJSON_ReplaceItemInObject(cJSON_GetArrayItem(pages,0),"pidIds",cJSON_Parse("[\"engine.rpm\"]"));
    cJSON_ReplaceItemInObject(root,"alerts",cJSON_CreateArray());
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK);
    assert(runtime.sources[0].transmission && runtime.pids[0].obd.pid==0x04fe &&
           runtime.pids[0].obd.len==3 && runtime.pids[0].obd.decoder.byte_length==1 && runtime.pids[0].responder==0x7e9);
    double temperature;
    const uint8_t observed[]={85,84,85}, placeholder[]={0,0,0};
    assert(pid_decoder_eval(&runtime.pids[0].obd.decoder,observed,sizeof(observed),&temperature) && temperature==45);
    assert(!pid_decoder_eval(&runtime.pids[0].obd.decoder,placeholder,sizeof(placeholder),&temperature));
    cJSON *request=cJSON_GetObjectItem(definition,"request");
    cJSON_ReplaceItemInObject(request,"requestId",cJSON_CreateString("7E0"));
    set(root); assert(config_runtime_validate(&partition,0,document_length,NULL)==ESP_ERR_NOT_SUPPORTED);
    cJSON_ReplaceItemInObject(request,"requestId",cJSON_CreateString("7E1"));
    cJSON_ReplaceItemInObject(request,"identifier",cJSON_CreateString("5043"));
    set(root); assert(config_runtime_validate(&partition,0,document_length,NULL)==ESP_ERR_NOT_SUPPORTED);
    cJSON_ReplaceItemInObject(request,"identifier",cJSON_CreateString("04FE"));
    /* Use the actual App asset gear definition with the production compiler. */
    file=fopen(argv[2],"rb"); assert(file);
    length=fread(input,1,sizeof(input)-1,file); fclose(file); input[length]=0;
    cJSON *asset=cJSON_Parse(input); assert(asset);
    const cJSON *gear=NULL;
    cJSON_ArrayForEach(gear,cJSON_GetObjectItem(asset,"definitions"))
        if (!strcmp(cJSON_GetObjectItem(gear,"id")->valuestring,"transmission.gear")) break;
    assert(gear);
    cJSON *gear_definition=cJSON_Duplicate(gear,1);
    cJSON_AddItemToArray(definitions,gear_definition);
    cJSON *page=cJSON_GetArrayItem(pages,0);
    cJSON_ReplaceItemInObject(page,"renderer",cJSON_CreateString("dual"));
    cJSON_ReplaceItemInObject(page,"pidIds",cJSON_Parse("[\"engine.rpm\",\"transmission.gear\"]"));
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK);
    assert(runtime.pid_count==2 && runtime.page_count==1 && runtime.pages[0].pid_count==2);
    assert(runtime.pids[1].obd.pid==0x5503 && runtime.pids[1].obd.len==1);
    const uint8_t park[]={13};
    assert(pid_decoder_eval(&runtime.pids[1].obd.decoder,park,1,&temperature) && temperature==13);
    /* Compile the bounded dual-source payload using the same production compiler. */
    cJSON *dual=cJSON_Duplicate(engine_root,1);
    cJSON *child_source=cJSON_Duplicate(source,1);
    cJSON *child_binding=cJSON_GetObjectItem(child_source,"adapter");
    cJSON_ReplaceItemInObject(child_binding,"id",cJSON_CreateString("child.radio"));
    cJSON_ReplaceItemInObject(child_binding,"address",cJSON_CreateString("C0:00:00:00:00:02"));
    cJSON_AddItemToArray(cJSON_GetObjectItem(dual,"sources"),child_source);
    cJSON *tcm_defs=cJSON_Duplicate(cJSON_GetObjectItem(root,"definitions"),1);
    cJSON_ReplaceItemInObject(cJSON_GetArrayItem(tcm_defs,0),"id",cJSON_CreateString("transmission.temperature"));
    cJSON *child_page=cJSON_Duplicate(page,1);
    cJSON_ReplaceItemInObject(child_page,"id",cJSON_CreateString("child.page"));
    cJSON_ReplaceItemInObject(child_page,"pidIds",cJSON_Parse("[\"transmission.temperature\",\"transmission.gear\"]"));
    cJSON_AddItemToArray(cJSON_GetObjectItem(dual,"pages"),child_page);
    while(cJSON_GetArraySize(tcm_defs)) cJSON_AddItemToArray(cJSON_GetObjectItem(dual,"definitions"),cJSON_DetachItemFromArray(tcm_defs,0));
    cJSON_Delete(tcm_defs);
    set(dual); assert(validate()==ESP_OK);
    assert(config_runtime_load(&runtime,&active,&previous)==ESP_OK);
    assert(runtime.source_count==2 && !runtime.sources[0].transmission && runtime.sources[1].transmission);
    assert(runtime.pids[runtime.pid_count-1].source_index==1 && runtime.pids[0].source_index==0);
    cJSON_ReplaceItemInObject(child_page,"pidIds",cJSON_Parse("[\"engine.rpm\",\"transmission.gear\"]"));
    set(dual); assert(validate()==ESP_OK);
    assert(config_runtime_validate(&partition,0,document_length,NULL)!=ESP_OK);
    cJSON_ReplaceItemInObject(child_page,"pidIds",cJSON_Parse("[\"transmission.temperature\",\"transmission.gear\"]"));
    cJSON_ReplaceItemInObject(child_binding,"address",cJSON_CreateString("C0:00:00:00:00:01"));
    set(dual); assert(validate()!=ESP_OK);
    assert(config_runtime_validate(&partition,0,document_length,NULL)!=ESP_OK);
    cJSON_ReplaceItemInObject(child_binding,"address",cJSON_CreateString("C0:00:00:00:00:02"));
    cJSON_DeleteItemFromObject(child_source,"adapter");
    set(dual); assert(config_runtime_validate(&partition,0,document_length,NULL)!=ESP_OK);
    cJSON_Delete(dual); cJSON_Delete(engine_root);
    cJSON_ReplaceItemInObject(page,"renderer",cJSON_CreateString("arc"));
    cJSON_ReplaceItemInObject(page,"pidIds",cJSON_Parse("[\"transmission.gear\"]"));
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_validate(&partition,0,document_length,NULL)==ESP_ERR_NOT_SUPPORTED);
    cJSON_ReplaceItemInObject(page,"renderer",cJSON_CreateString("numeric"));
    cJSON_ReplaceItemInObject(cJSON_GetObjectItem(gear_definition,"request"),"identifier",cJSON_CreateString("5504"));
    cJSON_ReplaceItemInObject(cJSON_GetObjectItem(gear_definition,"response"),"prefix",cJSON_CreateString("625504"));
    set(root); assert(validate()==ESP_OK);
    assert(config_runtime_validate(&partition,0,document_length,NULL)==ESP_ERR_NOT_SUPPORTED);
    cJSON_Delete(asset);
    free(document); cJSON_Delete(root);
    puts("Production configuration binding validation and runtime fixtures passed");
}
