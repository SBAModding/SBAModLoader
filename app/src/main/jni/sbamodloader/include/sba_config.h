// Copyright (c) 2026 XtraCube
#ifndef SBAMODLOADER_SBA_CONFIG_H
#define SBAMODLOADER_SBA_CONFIG_H
#include <unordered_map>
#include <vector>
#include <string>
#include <jni.h>

struct SBAConfig {
    bool initialized;
    bool useOriginalLibUnity;
    bool isIl2Cpp2Mono;
    std::string gameLibraryDirectory;
    std::string appLibraryDirectory;
    std::string appDataDirectory;
    std::string codeCacheDirectory;
    std::string bepInExDirectory;
    std::string dotnetDirectory;
    std::string unityDataDirectory;
    std::string unityVersion;
    std::vector<std::string> sbaVariables;
    std::vector<std::string> auxiliaryPluginFolders;
    std::unordered_map<std::string, std::string> il2cppApiMap;
};

SBAConfig sba_parse_config(JNIEnv *env, jobject jSBAConfig);

void sba_print_config(const SBAConfig &config);

extern SBAConfig runtime_config;

#endif //SBAMODLOADER_SBA_CONFIG_H
