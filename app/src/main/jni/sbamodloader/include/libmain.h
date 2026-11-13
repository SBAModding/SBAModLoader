// Copyright (c) 2026 XtraCube
#ifndef SBA_LIBMAIN_H
#define SBA_LIBMAIN_H

#ifdef __cplusplus
extern "C" {
#endif

void libmain_set_override_unity_path(const char *path);

void libmain_set_override_il2cpp_path(const char *path);

const char *libmain_get_override_unity_path();

const char *libmain_get_override_il2cpp_path();

void libmain_set_log_path(const char *path);

#ifdef __cplusplus
}
#endif

#endif //SBA_LIBMAIN_H
