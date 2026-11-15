#include <mod_settings.h>

#include <logger.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <string>

#define TAG "SBA.ModSettings"

namespace {
std::mutex lock;
std::string directory;
// mod id -> key -> value; a mod's file is read the first time it is asked about.
std::map<std::string, std::map<std::string, std::string>> loaded;

bool safe_id(const char *id) {
    if (!id || !*id || strlen(id) > 64) return false;
    for (const char *p = id; *p; p++) {
        char c = *p;
        bool ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
        if (!ok) return false;
    }
    return *id != '.';
}

std::string unescape(const std::string &s) {
    std::string out;
    for (size_t i = 0; i < s.size(); i++) {
        if (s[i] == '\\' && i + 1 < s.size()) {
            char n = s[++i];
            out += n == 'n' ? '\n' : n;
        } else {
            out += s[i];
        }
    }
    return out;
}

const std::map<std::string, std::string> &values_of(const std::string &mod) {
    auto it = loaded.find(mod);
    if (it != loaded.end()) return it->second;
    std::map<std::string, std::string> values;
    std::string path = directory + "/" + mod + ".settings";
    if (FILE *f = fopen(path.c_str(), "r")) {
        char line[1024];
        while (fgets(line, sizeof line, f)) {
            std::string l(line);
            while (!l.empty() && (l.back() == '\n' || l.back() == '\r')) l.pop_back();
            size_t eq = l.find('=');
            if (eq == std::string::npos || eq == 0) continue;
            values[l.substr(0, eq)] = unescape(l.substr(eq + 1));
        }
        fclose(f);
    }
    return loaded.emplace(mod, std::move(values)).first->second;
}

// The value as text, or nullptr when the mod has no such setting.
const std::string *find(const char *mod, const char *key) {
    if (directory.empty() || !safe_id(mod) || !key || !*key) return nullptr;
    const auto &values = values_of(mod);
    auto it = values.find(key);
    return it == values.end() ? nullptr : &it->second;
}
}  // namespace

void mod_settings_init(const char *bepinex_directory) {
    std::lock_guard<std::mutex> guard(lock);
    std::string bepinex = bepinex_directory ? bepinex_directory : "";
    while (bepinex.size() > 1 && bepinex.back() == '/') bepinex.pop_back();
    size_t slash = bepinex.find_last_of('/');
    directory = slash == std::string::npos ? "" : bepinex.substr(0, slash) + "/ModSettings";
    log_format(LogLevel::INFO, TAG, "Mod settings are read from {}", directory);
}

// The functions below are the loader's side of native API version 2. A mod finds them with
// dlsym(RTLD_DEFAULT, "sba_get_setting_int") and so on, after checking sba_native_api_version() >= 2.
extern "C" {

__attribute__((visibility("default"))) int sba_get_setting_bool(const char *mod, const char *key, int fallback) {
    std::lock_guard<std::mutex> guard(lock);
    const std::string *v = find(mod, key);
    if (!v) return fallback;
    return *v == "true" ? 1 : (*v == "false" ? 0 : fallback);
}

__attribute__((visibility("default"))) int sba_get_setting_int(const char *mod, const char *key, int fallback) {
    std::lock_guard<std::mutex> guard(lock);
    const std::string *v = find(mod, key);
    if (!v) return fallback;
    char *end = nullptr;
    errno = 0;
    long n = strtol(v->c_str(), &end, 10);
    return (end == v->c_str() || errno != 0) ? fallback : (int) n;
}

__attribute__((visibility("default"))) double sba_get_setting_float(const char *mod, const char *key, double fallback) {
    std::lock_guard<std::mutex> guard(lock);
    const std::string *v = find(mod, key);
    if (!v) return fallback;
    char *end = nullptr;
    errno = 0;
    double d = strtod(v->c_str(), &end);
    return (end == v->c_str() || errno != 0) ? fallback : d;
}

// Copies the value (a choice's value, or text) into out as a C string and returns its length; -1 when the mod has no such
// setting (out is then left alone). The copy is cut to fit, and the length returned is that of the full value.
__attribute__((visibility("default"))) int sba_get_setting_string(const char *mod, const char *key, char *out, int capacity) {
    std::lock_guard<std::mutex> guard(lock);
    const std::string *v = find(mod, key);
    if (!v) return -1;
    if (out && capacity > 0) {
        size_t n = v->size() < (size_t) capacity - 1 ? v->size() : (size_t) capacity - 1;
        memcpy(out, v->data(), n);
        out[n] = '\0';
    }
    return (int) v->size();
}

}  // extern "C"
