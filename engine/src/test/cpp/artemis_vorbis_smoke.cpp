// Device-side integration check, deliberately independent of the engine Activity.
// Build with the NDK arm64 clang++, run as the debuggable app UID:
//   artemis_vorbis_smoke /absolute/libartemis.so sample.ogg [expect-open-failure]
// Caller supplies legally available game/test audio; no samples are redistributed.
#include <dlfcn.h>
#include <cstdint>
#include <cstdio>

int main(int argc, char** argv) {
    if (argc < 3) return 2;
    void* library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) { std::fprintf(stderr, "%s\n", dlerror()); return 2; }
    auto open = reinterpret_cast<int (*)(const char*, void*)>(dlsym(library, "ov_fopen"));
    auto read = reinterpret_cast<long (*)(void*, char*, int, int, int, int, int*)>(dlsym(library, "ov_read"));
    auto seek = reinterpret_cast<int (*)(void*, int64_t)>(dlsym(library, "ov_pcm_seek"));
    auto total = reinterpret_cast<int64_t (*)(void*, int)>(dlsym(library, "ov_pcm_total"));
    auto clear = reinterpret_cast<int (*)(void*)>(dlsym(library, "ov_clear"));
    if (!open || !read || !seek || !total || !clear) return 2;
    // OggVorbis_File is opaque here: overallocate aligned storage, no guessed field ABI.
    alignas(16) unsigned char state[65536] = {};
    int result = open(argv[2], state);
    std::printf("open=%d ", result);
    if (argc > 3) { std::printf("expected-failure\n"); return result < 0 ? 0 : 1; }
    if (result != 0) return 1;
    const int64_t frames = total(state, -1);
    int64_t bytes = 0, nonzero = 0;
    char buffer[4096];
    int section = 0;
    // Full decode, two rewinds, and seek near EOF exercise serial lookup on read/seek.
    for (int pass = 0; pass < 3; ++pass) {
        if (pass && seek(state, 0) != 0) return 1;
        int64_t passBytes = 0;
        for (;;) {
            long n = read(state, buffer, sizeof(buffer), 0, 2, 1, &section);
            if (n < 0) { std::printf("read=%ld\n", n); return 1; }
            if (!n) break;
            passBytes += n;
            for (long i = 0; i < n; ++i) nonzero += buffer[i] != 0;
        }
        if (pass && passBytes != bytes) return 1;
        bytes = passBytes;
    }
    if (frames < 2 || seek(state, frames - 1) != 0 ||
            read(state, buffer, sizeof(buffer), 0, 2, 1, &section) <= 0) return 1;
    clear(state);
    std::printf("frames=%lld bytes=%lld nonzero=%lld rewinds=2 seek=OK\n",
        static_cast<long long>(frames), static_cast<long long>(bytes), static_cast<long long>(nonzero));
    return bytes > 0 && nonzero > 0 ? 0 : 1;
}
