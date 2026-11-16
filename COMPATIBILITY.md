# Compatibility

Which loader versions were tried on which Android versions and kinds of device, and how it went.
This page is generated from [`compatibility/results.json`](compatibility/results.json); to add or correct a result, change that file (see [compatibility/README.md](compatibility/README.md)).

## Close to stock Android

Stock or close to AOSP: no manufacturer skin.

| Android | Device | Loader | Game | Result | Notes |
|---|---|---|---|---|---|
| 17 | Close to AOSP | 0.0.4.1 | 13.0.8 | Works |  |
| 16 | Close to AOSP | 0.0.4.1 | 13.0.8 | Works |  |

## Manufacturer skins

A phone maker's version of Android, such as One UI, MIUI or HyperOS.

| Android | Device | Loader | Game | Result | Notes |
|---|---|---|---|---|---|
| 14 | itel | 0.0.4.1 | 13.0.8 | Works |  |
| 13 | Redmi | 0.0.4.1 | 13.0.8 | Works |  |

## Emulators

Emulators that run the arm64 game through a translator (the native bridge).

| Android | Device | Native bridge | Loader | Game | Result | Notes |
|---|---|---|---|---|---|---|
| 13 |  | libhoudini | 0.0.4.1 | 13.0.8 | Works |  |
| 13 |  | libndk_translation | 0.0.4.1 | 13.0.8 | Works |  |
| 11 |  | libhoudini | 0.0.4.1 | 13.0.8 | Works | Emulators of this kind, such as LDPlayer. |

## BlueStacks

Listed on its own: it runs the game through libhoudini like other emulators, but its anti-detection checks make it behave differently.

| Android | Device | Native bridge | Loader | Game | Result | Notes |
|---|---|---|---|---|---|---|
| 13 | BlueStacks | libhoudini | 0.0.4.2a | 13.0.8 | Works | The guard is installed on BlueStacks; the crash is gone. |
| 13 | BlueStacks | libhoudini | 0.0.4.1 | 13.0.8 | Crashes | The first launch crashed in SystemNative_ReadDirR. The libhoudini guard was not installed, because the loader did not recognise BlueStacks as a libhoudini device. |
