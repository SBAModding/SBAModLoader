// Copyright (c) 2026 XtraCube. All rights reserved.

#ifndef SBAMODLOADER_ELF_H
#define SBAMODLOADER_ELF_H

#include <unistd.h>

uintptr_t get_rva_from_sym_file(const char* filepath, const char* target_symbol);

#endif //SBAMODLOADER_ELF_H
