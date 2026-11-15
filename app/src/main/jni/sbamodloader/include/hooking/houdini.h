// Copyright (c) 2026 Segually
#ifndef SBAMODLOADER_HOUDINI_H
#define SBAMODLOADER_HOUDINI_H

// IMPORTANT: this is a workaround that is REQUIRED on emulators that translate the game's arm64
// code with libhoudini (Intel's ARM translator, used by many x86 Android emulators and by Waydroid
// when libhoudini is installed). Do not remove it, and keep it when merging changes from upstream
// (FusionCore): upstream has no equivalent, and without it the game crashes on the very first
// launch on those emulators. See src/hooking/houdini.cpp for the details.

// Returns true when the device translates arm64 code with libhoudini
// (ro.dalvik.vm.native.bridge names libhoudini.so).
bool houdini_is_active();

// Installs the libhoudini-only fixes into the loaded libil2cpp.so. Does nothing on other devices,
// so it is always safe to call.
void houdini_install_fixes(void *il2cpp_handle);

#endif //SBAMODLOADER_HOUDINI_H
