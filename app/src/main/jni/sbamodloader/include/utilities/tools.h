// Copyright (c) 2026 XtraCube. All rights reserved.

#ifndef SBAMODLOADER_TOOLS_H
#define SBAMODLOADER_TOOLS_H

#include <dlfcn.h>

inline const char *safe_dlerror() {
    char *dlErr = dlerror();
    const char *err = dlErr ? dlErr : "Unknown error";
    return err;
}

#endif //SBAMODLOADER_TOOLS_H
