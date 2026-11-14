// Copyright (c) 2026 XtraCube
#include <unistd.h>
#include <thread>
#include <chrono>
#include <cstdio>
#include <jni.h>
#include <filesystem>
#include <logger.h>
#include <libmain.h>
#include <sba_config.h>
#include <hooking/il2cpp.h>
#include <hooking/safehook.h>
#include <hooking/allocator.h>
#include <hooking/libunity.h>
#include <dotnet.h>
#include <native_mods.h>
#include <memory_log.h>
#include <external/dobby.h>
#include <utilities/java.h>
#include <dlfcn.h>

#define TAG "SBAModLoader"

namespace fs = std::filesystem;

SBAConfig runtime_config;

static bool execute_sba_config(const SBAConfig &config)
{
    sba_print_config(config);

    fs::path gameLibsPath(config.gameLibraryDirectory);
    fs::path codeCache(config.codeCacheDirectory);

    fs::path libUnity;
    fs::path patchedLibIl2Cpp;
    {
        patchedLibIl2Cpp = codeCache / "libil2cpp.so";
        fs::path libIl2Cpp = gameLibsPath / "libil2cpp.so";
        allocate_setup_injected(libIl2Cpp.c_str(), patchedLibIl2Cpp.c_str(), 1024 * 1024);
    }
    if (config.useOriginalLibUnity)
    {
        libUnity = gameLibsPath / "libunity.so";
    }
    else
    {
        libUnity = codeCache / "libunity.so";
    }

    std::string libUnityPath = libUnity.string();
    try_hook_libunity(libUnityPath, (gameLibsPath / "libunity.so").string());

    std::string patchedPath = patchedLibIl2Cpp.string();
    libmain_set_override_il2cpp_path(patchedPath.c_str());
    libmain_set_override_unity_path(libUnityPath.c_str());

    log(LogLevel::INFO, TAG, "SBAModLoader bootstrap finished successfully.");
    return true;
}

int il2cpp_init_hook(char *domain_name)
{
    const char *d_name = domain_name ? domain_name : "Unknown Domain Name";
    log_format(LogLevel::INFO, TAG, "il2cpp_init called with domain: {}", d_name);
    il2cpp_destroy_init_hook();

    // Native mods load before the runtime initialises and before BepInEx (and so before every managed mod).
    // Unity and il2cpp are already mapped by now, which is where native mods look for them.
    if (runtime_config.initialized)
    {
        native_mods_load(runtime_config.nativeMods);
    }

    // call the original il2cpp_init function
    memlog_mark("il2cpp: before init");
    int result = il2cpp_init(domain_name);
    memlog_mark("il2cpp: init returned");
    if (runtime_config.initialized)
    {
        native_mods_il2cpp_ready();
    }

    if (runtime_config.initialized && runtime_config.fusionEnabled)
    {
        // setup environment variables
        setenv("BEPINEX_GAME_ASSEMBLY_PATH", libmain_get_override_il2cpp_path(), 1);
        // These names are baked into the bundled BepInEx build and must not change.
        setenv("FUSION_BEPINEX_PATH", runtime_config.bepInExDirectory.c_str(), 1);
        setenv("FUSION_GAME_BINARY", libmain_get_override_il2cpp_path(), 1);
        setenv("FUSION_GAME_DATA_DIR", runtime_config.unityDataDirectory.c_str(), 1);
        setenv("FUSION_APP_DATA_DIR", runtime_config.appDataDirectory.c_str(), 1);
        setenv("FUSION_UNITY_VERSION", runtime_config.unityVersion.c_str(), 1);

        const char *ssl_cert_path = "/apex/com.android.conscrypt/cacerts";
        const char *backup_cert_path = "/system/etc/security/cacerts";
        if (access(ssl_cert_path, R_OK) == 0) {
            setenv("SSL_CERT_DIR", ssl_cert_path, 1);
        } else if (access(backup_cert_path, R_OK) == 0) {
            setenv("SSL_CERT_DIR", backup_cert_path, 1);
        } else {
            log(LogLevel::WARN, TAG, "No readable SSL cert file found; HTTPS requests may fail.");
        }
        const char *ssl_path = getenv("SSL_CERT_DIR");
        const char *safe_ssl_path = ssl_path ? ssl_path : "(null)";
        log_format(LogLevel::INFO, TAG, "Using {} for SSL certificates", safe_ssl_path);

        fs::path bepInExCoreDirectory = fs::path(runtime_config.bepInExDirectory) / "core";

        DotNetConfig dotNetConfig;
        dotNetConfig.runtimeDir = runtime_config.dotnetDirectory;
        dotNetConfig.managedLibsDir = bepInExCoreDirectory.string();
        dotNetConfig.entryPointAssembly = "BepInEx.Unity.IL2CPP";
        dotNetConfig.entryPointType = "BepInEx.Unity.IL2CPP.FusionCoreEntrypoint";
        dotNetConfig.entryPointMethod = "Start";
        if (!runtime_config.logDirectory.empty()) dotNetConfig.envOverridesFile = runtime_config.logDirectory + "/dotnet_env.txt";
        // set TMPDIR for MonoMod lib drops
        setenv("TMPDIR", runtime_config.codeCacheDirectory.c_str(), 1);

        // create AuxFolderPluginList
        auto aux = runtime_config.auxiliaryPluginFolders;
        int size = static_cast<int>(aux.size());
        const char** pointerArray = new const char*[size];

        for (int i = 0; i < size; i++) {
            char* strCopy = new char[aux[i].length() + 1];
            std::copy(aux[i].begin(), aux[i].end(), strCopy);
            strCopy[aux[i].length()] = '\0';

            pointerArray[i] = strCopy;
        }

        AuxPluginFolderList list{};
        list.count = size;
        list.folders = pointerArray;

        // change working directory to sba's scoped data directory
        chdir(runtime_config.appDataDirectory.c_str());
        dotnet_execute_assembly(dotNetConfig, &list);

        // Tell the loader this launch got through BepInEx's start and kept running: it counts a launch that crashed
        // before this as a failure of the downloaded interop assemblies (see InteropDownloader).
        std::string healthyMarker = runtime_config.codeCacheDirectory + "/interop_ok";
        std::thread([healthyMarker]() {
            std::this_thread::sleep_for(std::chrono::seconds(30));
            if (FILE *f = fopen(healthyMarker.c_str(), "w")) {
                fputs("ok\n", f);
                fclose(f);
            }
        }).detach();
    }
    else if (runtime_config.initialized)
    {
        log(LogLevel::INFO, TAG, "Fusion is off or has no enabled mods: not starting the .NET runtime or BepInEx.");
        // Only Fusion hides the loading view when it is done; hide it here so it cannot stay up forever.
        setLoadingState(false);
    }
    else
    {
        log(LogLevel::WARN, TAG, "SBAConfig not initialized. Skipping modloader initialization.");
    }

    log_format(LogLevel::INFO, TAG, "il2cpp_init returned: {}", result);
    return result;
}

extern "C" [[maybe_unused]] bool sba_bootstrap_from_libmain(JNIEnv *env)
{
    (void) env;

    log(LogLevel::INFO, TAG, "SBAModLoader bootstrap starting...");
    jobject javaConfig = get_sba_config(env);
    SBAConfig config = sba_parse_config(env, javaConfig);
    env->DeleteLocalRef(javaConfig);

    log(LogLevel::INFO, TAG, "Executing SBA bootstrap from libmain namespace...");
    if (!execute_sba_config(config)) {
        log(LogLevel::ERROR, TAG, "Failed to execute SBA bootstrap.");
        return false;
    }

    runtime_config = config;
    memlog_start(config.logDirectory, std::string("fusion=") + (config.fusionEnabled ? "on" : "off")
                                       + " unity=" + config.unityVersion
                                       + " native mods=" + std::to_string(config.nativeMods.size()));
    memlog_mark("loader: bootstrap");
    auto il2cpp_path = libmain_get_override_il2cpp_path();
    if (!il2cpp_initialize(il2cpp_path))
    {
        log_format(LogLevel::ERROR, TAG, "Failed to initialize il2cpp with path: {}", il2cpp_path);
        return false;
    }
    // Pool placement and library bounds must be trustworthy before any hooking.
    const uintptr_t pool_base = get_injected_pool_base();
    const uintptr_t il2cpp_base = il2cpp_get_library_base();
    if (pool_base == 0 || il2cpp_base == 0 || pool_base <= il2cpp_base)
    {
        log_format(LogLevel::ERROR, TAG,
                   "Invalid pool/library base relationship! pool=0x{:X}, il2cpp=0x{:X}",
                   pool_base, il2cpp_base);
        return false;
    }

    // plain integer subtraction (pool sits directly above the library image)
    const size_t library_size = pool_base - il2cpp_base;
    constexpr size_t kMaxPlausibleLibrarySize = 512ull * 1024 * 1024;
    if (library_size > kMaxPlausibleLibrarySize)
    {
        log_format(LogLevel::ERROR, TAG,
                   "Implausible library size 0x{:X}; refusing to initialize SafeHook",
                   library_size);
        return false;
    }

    if (!safehook_initialize(il2cpp_get_handle(), il2cpp_base, library_size, allocate_injected))
    {
        log(LogLevel::ERROR, TAG, "Failed to initialize SafeHook");
        return false;
    }

    log(LogLevel::INFO, TAG, "Installing il2cpp hooks...");
    il2cpp_install_init_hook(il2cpp_init_hook);
    log(LogLevel::INFO, TAG, "il2cpp hooks installed successfully!");
    log(LogLevel::INFO, TAG, "SBAModLoader bootstrap finished successfully.");
    return true;
}

