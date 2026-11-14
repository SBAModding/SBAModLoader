#include <memory_log.h>

#include <sys/system_properties.h>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <mutex>
#include <thread>

namespace fs = std::filesystem;

namespace
{
    std::mutex g_mutex;
    FILE *g_file = nullptr;
    std::chrono::steady_clock::time_point g_start;
    std::atomic<bool> g_running{false};

    struct Readings
    {
        long rssKb = -1, anonKb = -1, fileKb = -1, shmemKb = -1, swapKb = -1, hwmKb = -1, threads = -1;
        long sysAvailKb = -1, sysSwapFreeKb = -1, sysTotalKb = -1;
    };

    /** Reads "Key:   1234 kB" style lines; returns true and the number when {@code line} starts with {@code key}. */
    bool field(const char *line, const char *key, long &out)
    {
        size_t n = strlen(key);
        if (strncmp(line, key, n) != 0 || line[n] != ':') return false;
        out = strtol(line + n + 1, nullptr, 10);
        return true;
    }

    Readings read_all()
    {
        Readings r;
        char line[256];
        if (FILE *f = fopen("/proc/self/status", "r"))
        {
            while (fgets(line, sizeof line, f))
            {
                field(line, "VmRSS", r.rssKb);
                field(line, "RssAnon", r.anonKb);
                field(line, "RssFile", r.fileKb);
                field(line, "RssShmem", r.shmemKb);
                field(line, "VmSwap", r.swapKb);
                field(line, "VmHWM", r.hwmKb);
                field(line, "Threads", r.threads);
            }
            fclose(f);
        }
        if (FILE *f = fopen("/proc/meminfo", "r"))
        {
            while (fgets(line, sizeof line, f))
            {
                field(line, "MemAvailable", r.sysAvailKb);
                field(line, "SwapFree", r.sysSwapFreeKb);
                field(line, "MemTotal", r.sysTotalKb);
            }
            fclose(f);
        }
        return r;
    }

    long mb(long kb) { return kb < 0 ? -1 : kb / 1024; }

    void write_sample(const char *label)
    {
        Readings r = read_all();
        double t = std::chrono::duration<double>(std::chrono::steady_clock::now() - g_start).count();
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_file) return;
        fprintf(g_file,
                "[%7.1fs] %-30s rss=%5ld anon=%5ld file=%5ld shmem=%4ld swap=%5ld hwm=%5ld MB | threads=%3ld | "
                "system available=%5ld MB, swap free=%5ld MB\n",
                t, label, mb(r.rssKb), mb(r.anonKb), mb(r.fileKb), mb(r.shmemKb), mb(r.swapKb), mb(r.hwmKb),
                r.threads, mb(r.sysAvailKb), mb(r.sysSwapFreeKb));
        fflush(g_file);
    }

    std::string prop(const char *name)
    {
        char value[PROP_VALUE_MAX] = {0};
        __system_property_get(name, value);
        return value;
    }

    void sampler()
    {
        // Every half second for two minutes (where the crashes happen), then every five seconds for ten minutes.
        for (int i = 0; i < 240 && g_running; i++)
        {
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            write_sample("");
        }
        for (int i = 0; i < 120 && g_running; i++)
        {
            std::this_thread::sleep_for(std::chrono::seconds(5));
            write_sample("");
        }
    }
}

void memlog_start(const std::string &logDirectory, const std::string &header)
{
    if (logDirectory.empty() || g_running.exchange(true)) return;

    std::error_code ec;
    fs::create_directories(logDirectory, ec);
    fs::path current = fs::path(logDirectory) / "memory.log";
    fs::path previous = fs::path(logDirectory) / "memory_prev.log";
    fs::rename(current, previous, ec); // the crashed launch's file survives the relaunch

    FILE *f = fopen(current.c_str(), "w");
    if (!f)
    {
        g_running = false;
        return;
    }
    setvbuf(f, nullptr, _IOLBF, 0);

    Readings r = read_all();
    g_start = std::chrono::steady_clock::now();
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_file = f;
        fprintf(g_file, "SBAModLoader memory log\n");
        fprintf(g_file, "device: %s %s, Android %s, %s\n", prop("ro.product.manufacturer").c_str(),
                prop("ro.product.model").c_str(), prop("ro.build.version.release").c_str(),
                prop("ro.product.cpu.abi").c_str());
        fprintf(g_file, "system memory: %ld MB total\n", mb(r.sysTotalKb));
        fprintf(g_file, "%s\n", header.c_str());
        fprintf(g_file, "columns: rss = resident, anon = heap and stacks, file = mapped files (can be dropped by the "
                        "system), hwm = highest rss so far\n\n");
        fflush(g_file);
    }
    write_sample("start");
    std::thread(sampler).detach();
}

void memlog_mark(const char *label)
{
    if (g_running) write_sample(label);
}

void memlog_note(const char *text)
{
    if (!g_running) return;
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_file)
    {
        fprintf(g_file, "%s\n", text);
        fflush(g_file);
    }
}
