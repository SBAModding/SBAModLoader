#include <native_mods.h>
#include <dlfcn.h>
#include <jni.h>
#include <logger.h>
#include <utilities/java.h>

#define TAG "SBA.NativeMods"

using JNI_OnLoad_t = jint (*)(JavaVM *vm, void *reserved);
using IL2CPP_Ready_t = void (*)();

static std::vector<std::pair<std::string, void *>> loaded_mods;

// Mods look this up in libfusion.so to learn what the loader offers. Version 1: the sba_il2cpp_ready callback.
// Version 2: the sba_get_setting_bool/int/float/string functions (see mod_settings.cpp).
extern "C" __attribute__((visibility("default"))) int sba_native_api_version()
{
    return 2;
}

void native_mods_load(const std::vector<std::string> &paths)
{
    JavaVM *vm = get_java_vm();
    for (const std::string &path : paths)
    {
        void *handle = dlopen(path.c_str(), RTLD_NOW);
        if (!handle)
        {
            log_format(LogLevel::ERROR, TAG, "Failed to load {}: {}", path, dlerror());
            continue;
        }
        log_format(LogLevel::INFO, TAG, "Loaded {}", path);
        loaded_mods.emplace_back(path, handle);

        auto onLoad = reinterpret_cast<JNI_OnLoad_t>(dlsym(handle, "JNI_OnLoad"));
        if (onLoad && vm)
        {
            jint version = onLoad(vm, nullptr);
            log_format(LogLevel::INFO, TAG, "JNI_OnLoad of {} returned {}", path, version);
        }
    }
}

void native_mods_il2cpp_ready()
{
    for (const auto &mod : loaded_mods)
    {
        auto ready = reinterpret_cast<IL2CPP_Ready_t>(dlsym(mod.second, "sba_il2cpp_ready"));
        if (!ready) continue;
        log_format(LogLevel::INFO, TAG, "sba_il2cpp_ready of {}", mod.first);
        ready();
    }
}
