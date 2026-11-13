#include <native_mods.h>
#include <dlfcn.h>
#include <jni.h>
#include <logger.h>
#include <utilities/java.h>

#define TAG "SBA.NativeMods"

using JNI_OnLoad_t = jint (*)(JavaVM *vm, void *reserved);

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

        auto onLoad = reinterpret_cast<JNI_OnLoad_t>(dlsym(handle, "JNI_OnLoad"));
        if (onLoad && vm)
        {
            jint version = onLoad(vm, nullptr);
            log_format(LogLevel::INFO, TAG, "JNI_OnLoad of {} returned {}", path, version);
        }
    }
}
