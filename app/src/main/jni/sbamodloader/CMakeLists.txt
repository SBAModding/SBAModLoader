cmake_minimum_required(VERSION 3.22.1)
project(sbamodloader)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

set(LIB_FOLDER ${CMAKE_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI})

add_library(sbamodloader SHARED
        src/sba.cpp
        src/sba_config.cpp
        src/dotnet.cpp
        src/exports.cpp
        src/logger.cpp
        src/hooking/allocator.cpp
        src/hooking/il2cpp.cpp
        src/hooking/safehook.cpp
        src/hooking/libunity.cpp
        src/utilities/asm.cpp
        src/utilities/elf.cpp
        src/utilities/library.cpp
        src/utilities/java.cpp
        src/native_mods.cpp
)

target_include_directories(sbamodloader PUBLIC include)

target_link_libraries(
        sbamodloader
        PUBLIC
        main
        android
        log
        ${LIB_FOLDER}/libdobby.so
        ${LIB_FOLDER}/libcoreclr.so
)

# The bundled BepInEx build P/Invokes this library by name ("fusion"), so the file must be libfusion.so.
set_target_properties(sbamodloader PROPERTIES OUTPUT_NAME fusion)
