/* Include once in a presentation provider with GAUGE_SYMBOL_PX defined.
 * Compile-time vector coordinates keep icons in flash at native resolution. */
static const lv_point_precise_t engine_outline[] = {
    {GAUGE_SYMBOL_PX(5), GAUGE_SYMBOL_PX(4)},   {GAUGE_SYMBOL_PX(13), GAUGE_SYMBOL_PX(4)},
    {GAUGE_SYMBOL_PX(13), GAUGE_SYMBOL_PX(1)},  {GAUGE_SYMBOL_PX(22), GAUGE_SYMBOL_PX(1)},
    {GAUGE_SYMBOL_PX(22), GAUGE_SYMBOL_PX(5)},  {GAUGE_SYMBOL_PX(29), GAUGE_SYMBOL_PX(5)},
    {GAUGE_SYMBOL_PX(29), GAUGE_SYMBOL_PX(17)}, {GAUGE_SYMBOL_PX(22), GAUGE_SYMBOL_PX(17)},
    {GAUGE_SYMBOL_PX(19), GAUGE_SYMBOL_PX(20)}, {GAUGE_SYMBOL_PX(11), GAUGE_SYMBOL_PX(20)},
    {GAUGE_SYMBOL_PX(8), GAUGE_SYMBOL_PX(16)},  {GAUGE_SYMBOL_PX(5), GAUGE_SYMBOL_PX(16)},
    {GAUGE_SYMBOL_PX(5), GAUGE_SYMBOL_PX(4)}};
static const lv_point_precise_t engine_pipe[]      = {{GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(7)},
                                                      {GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(15)}};
static const lv_point_precise_t engine_join[]      = {{GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(11)},
                                                      {GAUGE_SYMBOL_PX(5), GAUGE_SYMBOL_PX(11)}};
static const lv_point_precise_t wrench_handle[]    = {{GAUGE_SYMBOL_PX(7), GAUGE_SYMBOL_PX(18)},
                                                      {GAUGE_SYMBOL_PX(21), GAUGE_SYMBOL_PX(4)}};
static const lv_point_precise_t wrench_head[]      = {{GAUGE_SYMBOL_PX(21), GAUGE_SYMBOL_PX(4)},
                                                      {GAUGE_SYMBOL_PX(25), GAUGE_SYMBOL_PX(7)},
                                                      {GAUGE_SYMBOL_PX(21), GAUGE_SYMBOL_PX(12)},
                                                      {GAUGE_SYMBOL_PX(17), GAUGE_SYMBOL_PX(8)},
                                                      {GAUGE_SYMBOL_PX(20), GAUGE_SYMBOL_PX(3)}};
static const lv_point_precise_t warning_outline[]  = {{GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(1)},
                                                      {GAUGE_SYMBOL_PX(19), GAUGE_SYMBOL_PX(18)},
                                                      {GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(18)},
                                                      {GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(1)}};
static const lv_point_precise_t critical_outline[] = {{GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(1)},
                                                      {GAUGE_SYMBOL_PX(19), GAUGE_SYMBOL_PX(10)},
                                                      {GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(19)},
                                                      {GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(10)},
                                                      {GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(1)}};
static const lv_point_precise_t warning_stem[]     = {{GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(6)},
                                                      {GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(11)}};
static const lv_point_precise_t warning_dot[]      = {{GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(14)},
                                                      {GAUGE_SYMBOL_PX(10), GAUGE_SYMBOL_PX(16)}};
static const lv_point_precise_t arrow_up[]         = {{GAUGE_SYMBOL_PX(4), GAUGE_SYMBOL_PX(14)},
                                                      {GAUGE_SYMBOL_PX(4), GAUGE_SYMBOL_PX(1)},
                                                      {GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(5)},
                                                      {GAUGE_SYMBOL_PX(4), GAUGE_SYMBOL_PX(1)},
                                                      {GAUGE_SYMBOL_PX(7), GAUGE_SYMBOL_PX(5)}};
static const lv_point_precise_t arrow_down[]       = {{GAUGE_SYMBOL_PX(4), GAUGE_SYMBOL_PX(1)},
                                                      {GAUGE_SYMBOL_PX(4), GAUGE_SYMBOL_PX(14)},
                                                      {GAUGE_SYMBOL_PX(1), GAUGE_SYMBOL_PX(10)},
                                                      {GAUGE_SYMBOL_PX(4), GAUGE_SYMBOL_PX(14)},
                                                      {GAUGE_SYMBOL_PX(7), GAUGE_SYMBOL_PX(10)}};

#define GAUGE_SYMBOL_PATHS                                                                                             \
    {[GAUGE_SYMBOL_ENGINE_OUTLINE]   = engine_outline,                                                                 \
     [GAUGE_SYMBOL_ENGINE_PIPE]      = engine_pipe,                                                                    \
     [GAUGE_SYMBOL_ENGINE_JOIN]      = engine_join,                                                                    \
     [GAUGE_SYMBOL_WRENCH_HANDLE]    = wrench_handle,                                                                  \
     [GAUGE_SYMBOL_WRENCH_HEAD]      = wrench_head,                                                                    \
     [GAUGE_SYMBOL_WARNING_OUTLINE]  = warning_outline,                                                                \
     [GAUGE_SYMBOL_CRITICAL_OUTLINE] = critical_outline,                                                               \
     [GAUGE_SYMBOL_WARNING_STEM]     = warning_stem,                                                                   \
     [GAUGE_SYMBOL_WARNING_DOT]      = warning_dot,                                                                    \
     [GAUGE_SYMBOL_ARROW_UP]         = arrow_up,                                                                       \
     [GAUGE_SYMBOL_ARROW_DOWN]       = arrow_down}
