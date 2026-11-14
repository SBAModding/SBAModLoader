// Copyright (c) 2026 XtraCube. All rights reserved.
#include <dotnet.h>
#include <hooking/il2cpp.h>
#include <external/coreclrhost.h>
#include <filesystem>
#include <logger.h>
#include <memory_log.h>
#include <fstream>
#include <thread>
#include <dlfcn.h>

#define TAG "SBA.NET"

namespace fs = std::filesystem;


void build_tpa(const char *directory, std::string &tpaList);

/**
 * Environment for the .NET runtime, applied before it starts. ReadyToRun is off as it has always been in this
 * code. Environment values for DOTNET_* are hexadecimal.
 */
static void apply_runtime_environment(const DotNetConfig& config)
{
    setenv("DOTNET_ReadyToRun", "0", 1);

    // For developers: DOTNET_NAME=value lines in a text file replace the defaults above, so a setting can be tried on
    // a device without building. Only DOTNET_ variables are accepted.
    if (!config.envOverridesFile.empty())
    {
        std::ifstream in(config.envOverridesFile);
        std::string line;
        while (std::getline(in, line))
        {
            while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
            size_t eq = line.find('=');
            if (line.empty() || line[0] == '#' || eq == std::string::npos || eq == 0) continue;
            std::string name = line.substr(0, eq);
            if (name.rfind("DOTNET_", 0) != 0) continue;
            setenv(name.c_str(), line.substr(eq + 1).c_str(), 1);
            log_format(LogLevel::INFO, TAG, ".NET override from file: {}", line);
            memlog_note(("runtime override: " + line).c_str());
        }
    }
}

int dotnet_execute_assembly(const DotNetConfig& config, AuxPluginFolderList *auxFolders)
{
    entrypoint_fn managedDelegate = nullptr;
    {
        log(LogLevel::INFO, TAG, "Preparing CLR properties");

        #define NUM_KEYS 3

        const char *propertyKeys[NUM_KEYS] = {
                "TRUSTED_PLATFORM_ASSEMBLIES",
                "APP_PATHS",
                "APP_CONTEXT_BASE_DIRECTORY"
        };

        std::string appPaths = (config.runtimeDir + ":" + config.managedLibsDir);

        std::string tpaPath;
        build_tpa(config.managedLibsDir.c_str(), tpaPath);
        build_tpa(config.runtimeDir.c_str(), tpaPath);

        log_format(LogLevel::DEBUG, TAG, "TPA Path: {}", tpaPath);

        const char *propertyValues[NUM_KEYS] = {
                tpaPath.c_str(),
                appPaths.c_str(),
                config.managedLibsDir.c_str()
        };

        apply_runtime_environment(config);

        int32_t hr = -1;
        void *hostHandle = nullptr;
        unsigned int domainId = 0;

        memlog_mark("clr: before initialize");
        for (int attempt = 1; attempt <= 2; ++attempt)
        {
            hostHandle = nullptr;
            domainId = 0;
            hr = coreclr_initialize(
                    config.runtimeDir.c_str(),            // AppDomain base path
                    "SBAHost",                      // AppDomain friendly name
                    sizeof(propertyKeys) / sizeof(char *),// Property count
                    propertyKeys,                         // Property names
                    propertyValues,                       // Property values
                    &hostHandle,                          // Host handle
                    &domainId);                           // AppDomain ID

            log_format(LogLevel::INFO, TAG,
                       "coreclr_initialize attempt {} -> hr={} ({:#010x}), hostHandle={}, domainId={}",
                       attempt,
                       hr,
                       static_cast<uint32_t>(hr),
                       hostHandle,
                       domainId);

            if (hr >= 0)
            {
                break;
            }

            if (attempt == 1 && hr == static_cast<int32_t>(HR_INTERNAL_ERROR))
            {
                // Retry with W^X enabled
                setenv("DOTNET_EnableWriteXorExecute", "1", 1);
                log(LogLevel::WARN, TAG, "Retrying CoreCLR init with W^X enabled");
                std::this_thread::sleep_for(std::chrono::milliseconds(75));
                continue;
            }

            break;
        }

        if (hr >= 0) {
            log_format(LogLevel::INFO, TAG, "CoreCLR started; AppDomain {} created", domainId);
            memlog_mark("clr: initialized");
        } else {
            log_format(LogLevel::ERROR, TAG, "coreclr_initialize failed - status: {} ({:#010x})",
                       hr, static_cast<uint32_t>(hr));
            return hr;
        }

        log_format(LogLevel::INFO, TAG, "Creating delegate for {}.{} in {}",
                   config.entryPointType, config.entryPointMethod, config.entryPointAssembly);

        hr = coreclr_create_delegate(
                hostHandle,
                domainId,
                config.entryPointAssembly.c_str(),
                config.entryPointType.c_str(),
                config.entryPointMethod.c_str(),
                (void**)&managedDelegate);

        if (hr >= 0)
        {
            log(LogLevel::INFO, TAG, "Managed delegate created");
        }
        else
        {
            log_format(LogLevel::ERROR, TAG, "coreclr_create_delegate failed - status: {}", hr);
            return hr;
        }
    }

    if (managedDelegate == nullptr)
    {
        log(LogLevel::ERROR, TAG, "Managed entry point delegate is null; BepInEx was not started.");
        return -1;
    }

    memlog_mark("bepinex: before start");
    managedDelegate(auxFolders);
    memlog_mark("bepinex: start returned");

    log(LogLevel::INFO, TAG, "Executed delegate!");
    return 0;
}

void build_tpa(const char *directory, std::string &tpaList)
{
    std::error_code ec;
    fs::path dir(directory);

    if (!fs::exists(dir, ec))
    {
        if (ec)
        {
            log_format(LogLevel::ERROR, TAG, "Error checking existence of {}: {}", directory, ec
                    .message());
        }
        else
        {
            log_format(LogLevel::WARN, TAG, "TPA directory does not exist: {}", directory);
        }
        return;
    }

    if (!fs::is_directory(dir, ec))
    {
        if (ec)
        {
            log_format(LogLevel::ERROR, TAG, "Error checking if {} is a directory: {}", directory, ec.message());
        }
        else
        {
            log_format(LogLevel::WARN, TAG, "TPA path is not a directory: {}", directory);
        }
        return;
    }

    std::vector<std::string> dllPaths;
    for (const auto& entry : fs::directory_iterator(dir, ec)) {
        if (ec)
        {
            log_format(LogLevel::ERROR, TAG, "Error iterating directory {}: {}", directory, ec.message());
            break;
        }

        if (!entry.is_regular_file())
        {
            continue;
        }

        const fs::path& p = entry.path();

        if (p.extension() == ".dll") {
            dllPaths.emplace_back(p.string());
        }
    }

    std::sort(dllPaths.begin(), dllPaths.end());

    for (const auto &dllPath : dllPaths)
    {
        if (!tpaList.empty())
        {
            tpaList.append(":");
        }
        tpaList.append(dllPath);
    }
}