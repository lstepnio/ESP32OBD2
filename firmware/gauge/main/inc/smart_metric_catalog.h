#pragma once
#include "smart_page_policy.h"
#include "pid_decoder.h"
/* Definition match prevents renamed/custom units or decoder edits receiving a
 * guessed thermal heuristic. Unknown definitions still get alert priority. */
smart_metric_rule_t smart_metric_rule(const char                  *id,
                                      uint8_t                      service,
                                      uint16_t                     pid,
                                      uint32_t                     responder,
                                      const char                  *unit,
                                      const pid_numeric_decoder_t *decoder);
