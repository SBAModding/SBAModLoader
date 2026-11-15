// Copyright (c) 2026 Segually
#include <hooking/houdini.h>
#include <external/dobby.h>
#include <logger.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstring>
#include <sys/system_properties.h>

#define TAG "SBAHoudini"

// ---------------------------------------------------------------------------------------------
// REQUIRED FOR LIBHOUDINI - DO NOT REMOVE (and keep it when merging upstream FusionCore changes).
//
// What goes wrong: on the first launch (no save.sba yet) the game enumerates a directory through
// Unity's own SystemNative_ReadDirR, which lives in libil2cpp.so. Under libhoudini that call crashes
// the whole process with SIGSEGV / SI_KERNEL inside libhoudini.so, at the first readdir() of the
// function. The DIR* it passes to readdir() is garbage (for example 0x002c002b0000004e, which is
// not even a canonical user-space address). Real arm64 devices and libndk_translation do not hit it,
// so the crash is specific to libhoudini. It was reproduced on Waydroid and reported by a user on
// another x86 emulator, with identical registers and the same two libhoudini frames.
//
// The workaround: wrap SystemNative_ReadDirR and, before the original runs, check that the handle's
// DIR* looks like a real pointer. If it does not, report an empty directory (end of stream) instead
// of letting the translator fault. The wrapper is installed only when libhoudini is the native
// bridge, so every other device keeps running the unmodified function.
// ---------------------------------------------------------------------------------------------

// Layout of Unity's directory handle, as built by SystemNative_OpenDir in libil2cpp.so
// (malloc(0x28): the DIR*, then the lazily built entry array, the read index, the entry count and
// a strdup of the path). Only the fields needed for the check are named here.
struct UnityDirHandle
{
    void *dir;
    void *entries;
};

using read_dir_r_t = int32_t (*)(void *handle, uint8_t *buffer, int32_t buffer_size, void *output_entry);

static read_dir_r_t original_read_dir_r = nullptr;
static bool reported_bad_handle = false;

// A pointer returned by opendir() is a heap address: never null and never outside the 47-bit
// user-space range. Anything else cannot be dereferenced, whatever the bridge thinks.
static bool looks_like_user_pointer(const void *pointer)
{
    const auto value = reinterpret_cast<uintptr_t>(pointer);
    return value >= 0x10000 && (value >> 47) == 0;
}

static int32_t read_dir_r_hook(void *handle, uint8_t *buffer, int32_t buffer_size, void *output_entry)
{
    const auto *dir_handle = static_cast<const UnityDirHandle *>(handle);

    // entries == nullptr means the first call, the one that goes on to call readdir() on the DIR*.
    // Once the entries are cached the DIR* is not touched again, so only this case is checked.
    if (dir_handle && !dir_handle->entries && !looks_like_user_pointer(dir_handle->dir))
    {
        if (!reported_bad_handle)
        {
            reported_bad_handle = true;
            log_format(LogLevel::WARN, TAG,
                       "SystemNative_ReadDirR got an invalid DIR* (0x{:X}); reporting an empty directory "
                       "instead of crashing libhoudini",
                       reinterpret_cast<uintptr_t>(dir_handle->dir));
        }

        // The function's own end-of-stream result: a zeroed entry (16 bytes) and -1.
        if (output_entry) memset(output_entry, 0, 16);
        return -1;
    }

    return original_read_dir_r(handle, buffer, buffer_size, output_entry);
}

bool houdini_is_active()
{
    char bridge[PROP_VALUE_MAX] = {};
    if (__system_property_get("ro.dalvik.vm.native.bridge", bridge) <= 0) return false;
    return strstr(bridge, "houdini") != nullptr;
}

void houdini_install_fixes(void *il2cpp_handle)
{
    if (!houdini_is_active()) return;

    // REQUIRED FOR LIBHOUDINI: see the explanation at the top of this file.
    auto *target = il2cpp_handle ? dlsym(il2cpp_handle, "SystemNative_ReadDirR") : nullptr;
    if (!target)
    {
        log(LogLevel::WARN, TAG, "libhoudini is active but SystemNative_ReadDirR was not found; "
                                 "the first launch may crash");
        return;
    }

    dobby_disable_near_branch_trampoline();
    const int result = DobbyHook(target,
                                 reinterpret_cast<dobby_dummy_func_t>(read_dir_r_hook),
                                 reinterpret_cast<dobby_dummy_func_t *>(&original_read_dir_r));
    if (result != 0)
    {
        log_format(LogLevel::ERROR, TAG, "Failed to hook SystemNative_ReadDirR: {:d}", result);
        return;
    }

    log(LogLevel::INFO, TAG, "libhoudini detected: SystemNative_ReadDirR is guarded against invalid directory handles");
}
