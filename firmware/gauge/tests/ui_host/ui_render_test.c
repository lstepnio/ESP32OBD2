/* Runs the production UI and fonts against real LVGL software rendering.
 * Only RTOS mailboxes/time and hardware owners are replaced. Not a hardware test. */
#include <assert.h>
#include <stdio.h>
#include <sys/stat.h>
#include "lvgl.h"
#include "../../main/src/ui.c"

uint32_t host_ms;
static bool vehicle,maintenance;
static bool source_ready[2];
static uint32_t source_generation[2] = {7, 11};
static unsigned acknowledgments;
void worker_health_progress(worker_id_t id,uint32_t now) { (void)id;(void)now; }
bool adapter_status_ready_for(unsigned source) { return source < 2 && (vehicle || source_ready[source]); }
uint32_t adapter_status_generation(unsigned source) { return source < 2 ? source_generation[source] : 0; }
uint8_t transfer_gate_current(void) { return maintenance?2:0; }
bool alert_runtime_command(const uint8_t *data,size_t length) { (void)data;(void)length;acknowledgments++;return true; }
static uint32_t pixels[240*240],draw_buffer[240*240];
static void flush(lv_display_t *display,const lv_area_t *area,uint8_t *data) {
    unsigned width=area->x2-area->x1+1;
    for(int y=area->y1;y<=area->y2;y++)memcpy(pixels+y*240+area->x1,data+(y-area->y1)*width*4,width*4);
    lv_display_flush_ready(display);
}
static void tick(unsigned ms) { host_ms+=ms;lv_tick_inc(ms);lv_timer_handler();lv_refr_now(NULL); }
static void check_labels(lv_obj_t *root) {
    if(lv_obj_has_flag(root,LV_OBJ_FLAG_HIDDEN))return;
    if(lv_obj_check_type(root,&lv_label_class)) {
        const char *text=lv_label_get_text(root);const lv_font_t *font=lv_obj_get_style_text_font(root,0);ink_t b;
        assert(ink(text,font,&b));lv_area_t a;lv_obj_get_coords(root,&a);
        int x=a.x1+lv_obj_get_style_pad_left(root,0),y=a.y1+lv_obj_get_style_pad_top(root,0);
        if(*text&&!gauge_layout_fits(x+b.left,y+b.top,x+b.right,y+b.bottom)) {
            fprintf(stderr,"Outside circle: %s (%d,%d,%d,%d)\n",text,x+b.left,y+b.top,x+b.right,y+b.bottom);assert(false);
        }
        assert(strcmp(text,"Value too wide")&&strcmp(text,"Unit?"));
    }
    for(unsigned i=0;i<lv_obj_get_child_count(root);i++)check_labels(lv_obj_get_child(root,i));
}
static void capture(const char *directory,const char *name) {
    check_labels(lv_screen_active());char path[512];snprintf(path,sizeof(path),"%s/%s.rgb",directory,name);
    FILE *out=fopen(path,"wb");assert(out);
    for(unsigned i=0;i<240*240;i++) {unsigned char rgb[]={pixels[i]>>16,pixels[i]>>8,pixels[i]};assert(fwrite(rgb,1,3,out)==3);}
    fclose(out);
}
static unsigned clicks, holds, jumps;
static ui_page_t jump_page;
static void touch(ui_t *ui,lv_event_code_t event) {
    if (event == LV_EVENT_CLICKED) { clicks++; ui_set_page(ui, &ui->display.page); }
    if (event == LV_EVENT_LONG_PRESSED) holds++;
    if (event == UI_EVENT_PAGE_ACTION) { jumps++; ui_set_page(ui, &jump_page); ui_action_applied(ui); }
}
static lv_point_t finger;
static bool finger_down;
static void read_finger(lv_indev_t *input, lv_indev_data_t *data) {
    (void)input; data->point = finger;
    data->state = finger_down ? LV_INDEV_STATE_PRESSED : LV_INDEV_STATE_RELEASED;
}
static void pointer(lv_indev_t *input, int x, int y, bool down, unsigned ms) {
    host_ms += ms; lv_tick_inc(ms);
    finger = (lv_point_t){x,y}; finger_down = down;
    lv_indev_read(input);
    tick(0);
}
static void stroke(lv_indev_t *input, int dx, unsigned duration) {
    pointer(input,120,165,true,40);
    pointer(input,120+dx,120,true,duration);
    pointer(input,120+dx,100,false,0);
}
static void test_page_cache(ui_t *ui, ui_page_t rpm) {
    ui_set_units(ui,false);
    source_ready[0] = source_ready[1] = true;
    ui_page_t temp = {.name="TCM", .renderer=UI_RENDERER_ARC, .metric_count=1,
        .metrics={{.source=1,.pid=0x04fe,.name="TCM temp",.unit="°C",.maximum=150,.stale_after_ms=2000}}};
    double engine=675, transmission=45, got=0;
    ui_set_value(ui,0,7,12,&engine);
    ui_set_value(ui,1,11,0x04fe,&transmission); // hidden page receives data
    ui_set_page(ui,&temp); // no timer or new poll before first frame
    assert(metric_value(ui,0,&got) && got==45 && ui->display.rendered_available[0]);
    assert(!strcmp(lv_label_get_text(ui->widgets.value_lbl),"45"));
    ui_set_page(ui,&rpm);
    assert(metric_value(ui,0,&got) && got==675 && ui->display.rendered_available[0]);
    ui_page_t dual=rpm; dual.renderer=UI_RENDERER_DUAL; dual.metric_count=2; dual.metrics[1]=temp.metrics[0];
    ui_set_page(ui,&dual);
    assert(ui->display.rendered_available[0] && ui->display.rendered_available[1]);
    tick(1500); ui_set_page(ui,&temp); tick(501);
    assert(!metric_value(ui,0,&got)); // navigation does not renew age
    ui_set_value(ui,1,11,0x04fe,&transmission);
    ui_set_page(ui,&rpm); ui_set_value(ui,1,11,0x04fe,NULL); ui_set_page(ui,&temp);
    assert(!metric_value(ui,0,&got)); // invalid hidden reading stays unavailable
    ui_set_value(ui,1,11,0x04fe,&transmission);
    source_ready[1]=false; ui_set_page(ui,&temp); assert(!metric_value(ui,0,&got));
    ui_set_value(ui,0,7,12,&engine); ui_set_page(ui,&rpm); assert(metric_value(ui,0,&got));
    source_generation[1]=12; source_ready[1]=true; ui_set_page(ui,&temp);
    assert(!metric_value(ui,0,&got)); // reconnect never revives old-session data
    ui_set_value(ui,1,12,0x04fe,&transmission);
    ui_set_value(ui,1,11,0x04fe,NULL); ui_invalidate_source(ui,1,11);
    assert(metric_value(ui,0,&got)); // late failure cannot erase new session
    ui_invalidate_source(ui,1,12); assert(!metric_value(ui,0,&got));
    ui_set_value(ui,1,12,0x04fe,&transmission);
    ui_set_page(ui,&dual); assert(metric_value(ui,0,&got) && metric_value(ui,1,&got));
    ui_invalidate_source(ui,0,7);
    assert(!metric_value(ui,0,&got) && metric_value(ui,1,&got));
    ui_set_value(ui,0,7,12,&engine);
    // Identical numeric PIDs on different transports do not alias.
    ui_page_t alias=temp; alias.metrics[0].pid=12;
    ui_set_value(ui,1,12,12,&transmission);ui_set_page(ui,&alias);
    assert(metric_value(ui,0,&got) && got==45);
    ui_set_page(ui,&rpm);assert(metric_value(ui,0,&got) && got==675);
    double invalid=NAN; ui_set_value(ui,0,7,12,&invalid);assert(!metric_value(ui,0,&got));
    /* Fill the entire runtime catalog and repeatedly update it without FIFO loss. */
    for (unsigned i=0;i<29;i++) ui_set_value(ui,0,7,0x100+i,&engine);
    for (unsigned i=0;i<100;i++) { engine=i; ui_set_value(ui,0,7,12,&engine); }
    ui_set_page(ui,&rpm); assert(metric_value(ui,0,&got) && got==99);
    ui_set_value(ui,0,7,0x200,&engine); // overflow cannot evict an existing reading
    assert(metric_value(ui,0,&got) && got==99);
    source_ready[0]=source_ready[1]=false;
    ui_set_page(ui,&rpm);
}
static void test_gestures(ui_t *ui, lv_display_t *display, ui_page_t rpm, const char *directory) {
    lv_indev_t *input=lv_indev_create();lv_indev_set_type(input,LV_INDEV_TYPE_POINTER);
    lv_indev_set_display(input,display);lv_indev_set_read_cb(input,read_finger);
    // Input is driven explicitly so each assertion covers actual LVGL event order.
    lv_indev_set_mode(input,LV_INDEV_MODE_EVENT);
    jump_page=rpm;
    source_ready[0]=true;
    double engine=675;ui_set_value(ui,0,7,12,&engine);
    ui_configure_page_action(ui,(page_action_config_t){true,1,3,5000});
    ui_set_action_context(ui,true);tick(60);
    pointer(input,120,160,true,60);assert(ui_action_pending(ui));
    pointer(input,120,160,false,100);
    assert(clicks==1 && holds==0 && jumps==0); // normal tap retained
    stroke(input,0,60); assert(ui->action.progress==1 && ui_action_pending(ui));
    // Phone reconnects publish HIDDEN repeatedly; they must not erase a sequence.
    ui_show_pairing_code(ui,UI_PAIRING_HIDDEN);tick(60);assert(ui->action.progress==1);
    diagnostics_snapshot_t cel={.valid=true,.mil_known=true,.mil_on=true,.count_known=true,.distinct_count=4};
    ui_set_vehicle_diagnostics(ui,&cel);tick(60);
    assert(!lv_obj_has_flag(ui->action_label,LV_OBJ_FLAG_HIDDEN));
    assert(!strcmp(lv_label_get_text(ui->action_label),"1/3"));
    assert(!lv_obj_has_flag(ui->widgets.diagnostics_icon,LV_OBJ_FLAG_HIDDEN));
    capture(directory,"gesture-cel-progress");
    ui_set_alert_event(ui,1,false,"Coolant",false,1,1,1,112,110,"degC");tick(60);
    capture(directory,"gesture-cel-warning-progress");
    assert(!lv_obj_has_flag(ui->widgets.alert_lbl,LV_OBJ_FLAG_HIDDEN));
    assert(!lv_obj_has_flag(ui->action_label,LV_OBJ_FLAG_HIDDEN));
    lv_area_t condition_bounds,progress_bounds;
    lv_obj_get_coords(ui->widgets.alert_lbl,&condition_bounds);lv_obj_get_coords(ui->action_label,&progress_bounds);
    assert(condition_bounds.y2 < progress_bounds.y1); // distinct lanes, no overlap
    ui_set_alert(ui,0,false,"");tick(60);
    stroke(input,0,100);stroke(input,0,100);
    assert(jumps==1 && clicks==1 && holds==0);
    tick(60);assert(!strcmp(lv_label_get_text(ui->action_label),"Done"));
    capture(directory,"gesture-complete");
    stroke(input,0,100);tick(60);
    assert(jumps==1 && !strcmp(lv_label_get_text(ui->action_label),"Wait"));
    tick(5100);
    stroke(input,35,100); // rejected diagonal cannot turn into a page tap
    assert(!ui->action.progress && clicks==1 && holds==0);
    pointer(input,120,165,true,60);pointer(input,120,145,true,250);
    pointer(input,120,120,true,250);pointer(input,120,100,false,100);
    assert(ui->action.progress==1 && holds==0); // slow swipe doesn't open pairing
    pointer(input,120,165,true,60);pointer(input,120,145,true,80);
    lv_obj_send_event(lv_screen_active(),LV_EVENT_PRESS_LOST,NULL);
    pointer(input,120,100,false,60);
    assert(!ui->action.progress && clicks==1 && jumps==1);
    pointer(input,75,120,true,60);pointer(input,130,120,true,80);
    lv_obj_send_event(lv_screen_active(),LV_EVENT_PRESS_LOST,NULL);
    pointer(input,160,120,false,60);assert(clicks==1); // lost horizontal press cannot browse
    // Required context loss cancels input, and expiry releases cycling.
    stroke(input,0,100);assert(ui->action.progress==1);
    ui_set_alert_event(ui,2,false,"Coolant",true,1,1,1,118,110,"degC");tick(60);
    assert(!ui->action.progress); ui_set_alert(ui,0,false,"");tick(60);
    stroke(input,0,100);assert(ui->action.progress==1);tick(5001);assert(!ui_action_pending(ui));
    stroke(input,0,100);assert(ui->action.progress==1);
    ui_set_action_context(ui,false);tick(60);assert(!ui_action_pending(ui));
    ui_set_action_context(ui,true);tick(60);
    ui_show_pairing_code(ui,UI_PAIRING_READY);tick(60);stroke(input,0,100);
    assert(jumps==1 && !ui->action.progress);
    ui_show_pairing_code(ui,UI_PAIRING_HIDDEN);tick(60);
    pointer(input,120,160,true,60);pointer(input,120,160,true,500);
    pointer(input,120,160,false,60);assert(holds==1 && clicks==1); // stationary pairing hold retained
    pointer(input,75,120,true,60);pointer(input,140,120,true,100);pointer(input,160,120,false,60);
    assert(clicks==2 && jumps==1 && holds==1); // horizontal browsing retained
    ui_configure_page_action(ui,(page_action_config_t){0});
    stroke(input,0,100);assert(clicks==3); // no binding: ordinary swipe browsing retained
    ui_set_action_context(ui,false);tick(60);lv_indev_delete(input);
    puts("PASS: production LVGL taps, fast/slow swipes, CEL feedback, reconnect, lost press, attention, cooldown and hold arbitration");
}
int main(int argc,char **argv) {
    assert(argc==2);mkdir(argv[1],0700);lv_init();lv_display_t *display=lv_display_create(240,240);
    lv_display_set_color_format(display,LV_COLOR_FORMAT_XRGB8888);lv_display_set_flush_cb(display,flush);
    lv_display_set_buffers(display,draw_buffer,NULL,sizeof(draw_buffer),LV_DISPLAY_RENDER_MODE_FULL);
    ui_page_t saved={.name="RPM",.renderer=UI_RENDERER_NUMERIC,.metric_count=1,.metrics={{.pid=12,.name="Engine RPM",.unit="rpm",.maximum=8000,.stale_after_ms=2000}}};
    ui_t *ui=ui_init(&saved,50,touch,false,false);assert(ui);ui_set_page_position(ui,1,3);
    assert(ui_request_showcase(ui,true,8,7,false,false));tick(60);
    assert(lv_obj_has_flag(ui->widgets.demo_lbl,LV_OBJ_FLAG_HIDDEN));
    for(unsigned i=0;i<display_showcase_count();i++) {
        assert(lv_obj_has_flag(ui->widgets.demo_lbl,LV_OBJ_FLAG_HIDDEN));
        uint8_t status[20];assert(ui_showcase_status(ui,host_ms,status)==20);assert(status[1]==1&&status[3]==i);
        if(display_showcase_frame(i)->renderer==UI_RENDERER_TREND)for(unsigned j=0;j<20;j++)tick(100);
        char name[64];snprintf(name,sizeof(name),"demo-%02u",i+1);capture(argv[1],name);
        if(display_showcase_frame(i)->attention)
            lv_obj_send_event(ui->widgets.attention_button,LV_EVENT_CLICKED,NULL);
        else ui_showcase_next(ui);
        tick(60);
    }
    assert(acknowledgments==0);
    vehicle=true;tick(60);assert(!ui_showcase_active(ui));assert(ui->display.page.metrics[0].pid==12);
    vehicle=false;assert(ui_request_showcase(ui,true,8,8,false,false));tick(60);assert(ui_showcase_active(ui));
    maintenance=true;tick(60);assert(!ui_showcase_active(ui));maintenance=false;
    ui_set_alert_event(ui,2,false,"Coolant",true,1,1,1,118,110,"degC");tick(60);capture(argv[1],"critical-live");
    assert(ui_request_showcase(ui,true,8,9,false,false));tick(60);assert(!ui_showcase_active(ui));
    ui_set_alert(ui,0,false,"");tick(60);
    ui_show_pairing_code(ui,UI_PAIRING_READY);tick(60);capture(argv[1],"pair-ready");
    ui_show_pairing_code(ui,123456);tick(60);capture(argv[1],"pair-code");
    ui_show_pairing_code(ui,UI_PAIRING_OWNER_SAVED);tick(60);capture(argv[1],"pair-success");tick(2600);
    assert(!ui->pairing_visible);assert(!ui_showcase_active(ui));
    assert(ui->display.page.metrics[0].pid==12);assert(acknowledgments==0);
    /* Font can step down for a longer value, then stays stable until page/unit change. */
    double wide=16383,shorter=9;source_ready[0]=true;ui_set_value(ui,0,7,12,&wide);tick(60);
    const lv_font_t *chosen=lv_obj_get_style_text_font(ui->widgets.value_lbl,0);
    ui_set_value(ui,0,7,12,&shorter);tick(60);
    assert(lv_obj_get_style_text_font(ui->widgets.value_lbl,0)==chosen);
    ui_set_units(ui,true);tick(60);
    source_ready[0]=false;
    assert(ui_request_showcase(ui,true,8,10,false,false));tick(60);
    for(unsigned i=0;i<display_showcase_count();i++){check_labels(lv_screen_active());ui_showcase_next(ui);tick(60);}
    ui_showcase_stop(ui);tick(60);assert(!ui_showcase_active(ui)&&ui->display.page.metrics[0].pid==12);
    diagnostics_snapshot_t filtered={.valid=true,.mil_known=true,.count_known=true};
    ui_set_vehicle_diagnostics(ui,&filtered);tick(60);
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon,LV_OBJ_FLAG_HIDDEN));
    filtered.mil_known=false;ui_set_vehicle_diagnostics(ui,&filtered);tick(60);
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon,LV_OBJ_FLAG_HIDDEN));
    assert(lv_obj_has_flag(ui->widgets.diagnostics_lbl,LV_OBJ_FLAG_HIDDEN));
    test_page_cache(ui,saved);
    test_gestures(ui,display,saved,argv[1]);
    puts("PASS: page cache freshness/session isolation and production LVGL showcase frames, circular glyphs, vehicle/maintenance/critical preemption and pairing restore");
}
