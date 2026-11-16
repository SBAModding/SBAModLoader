# SBAModLoader

A mod loader for **Super Bear Adventure** on Android. It runs the game from inside the loader app, so mods can
change it without modifying the installed game.

- **Fusion mods**: BepInEx plugins (.NET 10) that hook into the game's IL2CPP code.
- **Native mods**: `.so` libraries loaded into the game before its runtime starts.
- **SBA packs** (`.sbapack`): texture packs, asset replacements, native code and Java code in one file.
- **Mod browser**: every listed mod is audited by the project and pinned by hash, so a file that changes after the
  audit is refused. The list lives in the [mod repository](https://github.com/SBAModding/ModRepository).
- **Mandatory patches**: the game's protection needs a patch, which the loader downloads and applies by itself
  ([patches repository](https://github.com/SBAModding/patches)).
- **In-game menu** (press back): freeze the game, a floating logcat view, and exit to the dashboard.

## Requirements

- Android 7.0 or newer on an arm64 device (or an emulator that translates arm64). What has been tried, on which
  Android versions, devices and emulators, is in [COMPATIBILITY.md](COMPATIBILITY.md).
- Super Bear Adventure (`com.Earthkwak.Platformer`) installed from your own copy. The loader does not include the
  game. Version 13.0.8 has a patch today.
- An internet connection the first time, for the patch and for mods.

## Using it

1. Install the loader and the game, then open **Dashboard**. It checks the game, the loader version and the patch,
   and downloads the patch if one exists for your build of the game.
2. Find mods in **Browse** and install them. Installed mods appear in **Mods**, where you can switch them off,
   remove them, and change the load order of native mods and packs.
3. Press **Launch**.

Adding files by hand in **Mods** is a developer feature: turn on *Developer features* in **Settings**. That also
unlocks loading a local patch and using a custom patch source.

## Making mods

| You want to ship | Make |
|---|---|
| A BepInEx plugin | a `.dll` (or a `.zip` of one) |
| Native code | a `.so`, loaded before the game's runtime and before BepInEx |
| Replaced or extra assets, texture packs, Java code, or a mix | an `.sbapack` |

A mod can declare **settings** (switches, sliders, choices, text) in its `sbapack.json`; the Mods screen builds a
settings page from them and native mods read the values with `sba_get_setting_*` (native API version 2). The format is
described in the [mod maker skill](https://github.com/SBAModding/skill).

To get a mod into the browser, follow the
[mod repository's contributing guide](https://github.com/SBAModding/ModRepository/blob/main/CONTRIBUTING.md).

## Building

You need JDK 17 and an Android SDK with platform 37, build tools, CMake 3.22.1 and an NDK (28 works).

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Release builds read `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` from the environment.

## Credits and license

SBAModLoader is a fork of [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) by All Of Us Mods and its
contributors, whose work (the Android hosting layer, the native bootstrap and the IL2CPP hooking) this project is
built on; their commits are preserved in this repository's history. The BepInEx build bundled in the app is that
project's BepInEx fork.

It also uses [BepInEx](https://github.com/BepInEx/BepInEx), [Il2CppInterop](https://github.com/BepInEx/Il2CppInterop),
the [.NET runtime](https://github.com/dotnet/runtime), [Dobby](https://github.com/jmpews/Dobby) and Material
Components for Android. The app's font is [Roboto Flex](https://github.com/googlefonts/roboto-flex) (SIL Open Font
License 1.1, see `third_party/roboto-flex/OFL.txt`).

Licensed under the GNU General Public License v3.0; see [LICENSE](LICENSE).

SBAModLoader is not affiliated with Earthkwak Games. Super Bear Adventure and its assets belong to their owners.
