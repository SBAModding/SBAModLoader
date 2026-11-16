# Compatibility

Which loader versions were tried on which Android versions and kinds of device, and how it went.
This page is generated from [`compatibility/results.json`](compatibility/results.json); to add or correct a result, change that file (see [compatibility/README.md](compatibility/README.md)).

## Close to stock Android

Stock or close to AOSP: no manufacturer skin.

| Android | Device | Loader | Game | Result | Fusion | Notes |
|---|---|---|---|---|---|---|
| 17 | Close to AOSP | 0.0.4.1 | 13.0.8 | Works | Verified |  |
| 16 | Close to AOSP | 0.0.4.1 | 13.0.8 | Works | Verified |  |

## Manufacturer skins

A phone maker's version of Android, such as One UI, MIUI or HyperOS.

| Android | Device | Loader | Game | Result | Fusion | Notes |
|---|---|---|---|---|---|---|
| 14 | itel | 0.0.4.1 | 13.0.8 | Works | Not verified yet |  |
| 13 | Redmi | 0.0.4.1 | 13.0.8 | Works | Not verified yet |  |

## Emulators

Emulators that run the arm64 game through a translator (the native bridge).

> **Emulators that use libhoudini need loader 0.0.3 or newer.** That version added the detection of libhoudini and the guard against its first-launch crash; older versions crash on the first launch. Emulators that use libndk_translation are not affected.

| Android | Device | Native bridge | Loader | Game | Result | Fusion | Notes |
|---|---|---|---|---|---|---|---|
| 13 |  | libhoudini | 0.0.4.1 | 13.0.8 | Works | Verified |  |
| 13 |  | libndk_translation | 0.0.4.1 | 13.0.8 | Works | Verified |  |
| 11 |  | libhoudini | 0.0.4.1 | 13.0.8 | Works | Verified | Emulators of this kind, such as LDPlayer. |

## BlueStacks

Listed on its own: it runs the game through libhoudini like other emulators, but its anti-detection checks make it behave differently.

> **BlueStacks needs loader 0.0.4.2a or newer.** Older versions do not work on it: BlueStacks loads libhoudini without declaring it, so versions before 0.0.4.2a did not detect it and crashed on the first launch.

| Android | Device | Native bridge | Loader | Game | Result | Fusion | Notes |
|---|---|---|---|---|---|---|---|
| 13 | BlueStacks | libhoudini | 0.0.4.2a | 13.0.8 | Works | Not verified yet | The guard is installed on BlueStacks; the crash is gone. |
| 13 | BlueStacks | libhoudini | 0.0.4.1 | 13.0.8 | Crashes | Not verified yet | The first launch crashed in SystemNative_ReadDirR. The libhoudini guard was not installed, because the loader did not recognise BlueStacks as a libhoudini device. |
