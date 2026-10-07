#define _POSIX_C_SOURCE 200809L
#include "wifi_bulk_io.h"
#include "obd_wait_policy.h"
#include <assert.h>
#include <pthread.h>
#include <signal.h>
#include <stdatomic.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

static uint32_t now_ms(void *context)
{
    (void)context;
    struct timespec now;
    assert(clock_gettime(CLOCK_MONOTONIC, &now) == 0);
    return (uint32_t)((uint64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000);
}
static bool live(void *context) { (void)context; return true; }
static bool paused(void *context) { return now_ms(NULL) - *(uint32_t *)context < 40; }
static atomic_bool stop_writer;
static void *trickle(void *context)
{
    int fd = *(int *)context;
    uint8_t byte = 1;
    struct timespec pause = {.tv_nsec = 20000000};
    while (!atomic_load(&stop_writer)) {
        if (send(fd, &byte, 1, 0) != 1) break;
        nanosleep(&pause, NULL);
    }
    return NULL;
}
int main(void)
{
    signal(SIGPIPE, SIG_IGN);
    assert(obd_wait_remaining(UINT32_MAX - 4, 5, 20) == 10);
    assert(obd_wait_remaining(100, 150, 50) == 0);
    int pair[2]; uint8_t bytes[100] = {0};
    assert(socketpair(AF_UNIX, SOCK_STREAM, 0, pair) == 0);
    uint32_t start = now_ms(NULL);
    assert(send(pair[1], "abcd", 4, 0) == 4);
    assert(wifi_bulk_io(pair[0], bytes, 2, false, start, 1000, now_ms, live, NULL) == 0);
    assert(wifi_bulk_io(pair[0], bytes + 2, 2, false, start, 1000, now_ms, live, NULL) == 0);
    assert(memcmp(bytes, "abcd", 4) == 0);
    start = now_ms(NULL);
    assert(wifi_bulk_io(pair[0], bytes, 1, false, start, 90, now_ms, live, NULL) == -1);
    assert(now_ms(NULL) - start < 600);
    start = now_ms(NULL);
    assert(wifi_bulk_io(pair[0], bytes, 1, false, start, 2000, now_ms, paused, &start) == -1);
    assert(now_ms(NULL) - start < 600);
    pthread_t writer;
    atomic_store(&stop_writer, false);
    assert(pthread_create(&writer, NULL, trickle, &pair[1]) == 0);
    start = now_ms(NULL);
    assert(wifi_bulk_io(pair[0], bytes, sizeof(bytes), false, start, 110, now_ms, live, NULL) == -1);
    assert(now_ms(NULL) - start < 600);
    atomic_store(&stop_writer, true); assert(pthread_join(writer, NULL) == 0);
    close(pair[0]); close(pair[1]);
    // Backpressure on writes shares the total budget too.
    assert(socketpair(AF_UNIX, SOCK_STREAM, 0, pair) == 0);
    int buffer = 1024;
    assert(setsockopt(pair[0], SOL_SOCKET, SO_SNDBUF, &buffer, sizeof(buffer)) == 0);
    static uint8_t large[1024 * 1024];
    start = now_ms(NULL);
    assert(wifi_bulk_io(pair[0], large, sizeof(large), true, start, 90, now_ms, live, NULL) == -1);
    assert(now_ms(NULL) - start < 600);
    close(pair[1]);
    start = now_ms(NULL);
    assert(wifi_bulk_io(pair[0], bytes, 1, false, start, 1000, now_ms, live, NULL) == -1);
    close(pair[0]);
    return 0;
}
