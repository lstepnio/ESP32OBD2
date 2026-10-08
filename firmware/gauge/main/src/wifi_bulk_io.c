#include "wifi_bulk_io.h"
#include "obd_wait_policy.h"
#include <errno.h>
#ifdef ESP_PLATFORM
#include "lwip/sockets.h"
#else
#include <sys/socket.h>
#include <sys/time.h>
#endif

int wifi_bulk_io(int fd, uint8_t *bytes, size_t length, bool sending,
                 uint32_t started_ms, uint32_t budget_ms,
                 wifi_bulk_clock_fn clock_ms, wifi_bulk_active_fn active, void *context)
{
    size_t offset = 0;
    while (offset < length) {
        uint32_t remaining = obd_wait_remaining(started_ms, clock_ms(context), budget_ms);
        if (!remaining || !active(context)) return -1;
        uint32_t slice = remaining < 250 ? remaining : 250;
        struct timeval timeout = {.tv_sec = 0, .tv_usec = slice * 1000};
        if (setsockopt(fd, SOL_SOCKET, sending ? SO_SNDTIMEO : SO_RCVTIMEO,
                       &timeout, sizeof(timeout)) != 0) return -1;
        int count = sending ? send(fd, bytes + offset, length - offset, 0)
                            : recv(fd, bytes + offset, length - offset, 0);
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)) continue;
        if (count <= 0) return -1;
        offset += (size_t)count;
    }
    return active(context) && obd_wait_remaining(started_ms, clock_ms(context), budget_ms) ? 0 : -1;
}
