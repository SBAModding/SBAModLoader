#ifndef SBAMODLOADER_NATIVE_MODS_H
#define SBAMODLOADER_NATIVE_MODS_H

#include <string>
#include <vector>

// Loads native mods (.so files) into the game's process, in the given order. A mod is just a shared
// library: its constructors run when it is loaded, and its JNI_OnLoad (if it has one) is called with the
// JavaVM. Mods find libunity.so / libil2cpp.so themselves, for example through /proc/self/maps.
void native_mods_load(const std::vector<std::string> &paths);

#endif //SBAMODLOADER_NATIVE_MODS_H
