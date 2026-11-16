# Reporting a result

Add an object to `results` in [`results.json`](results.json) and open a pull request. Change only that file:
[`COMPATIBILITY.md`](../COMPATIBILITY.md) is generated from it, and a workflow rebuilds it after the merge.

```json
{
  "id": "android-14-itel",
  "category": "manufacturer-skin",
  "android": "14",
  "device": "itel",
  "loader": "0.0.4.1",
  "game": "13.0.8",
  "fusion": "not-verified",
  "result": "works",
  "notes": "Optional. What is worth knowing, in a sentence or two."
}
```

| Field | Meaning |
|---|---|
| `id` | A unique name for the entry: lowercase letters, digits, `.` and `-`. |
| `category` | `aosp` (stock or close to AOSP), `manufacturer-skin` (One UI, MIUI, HyperOS and so on), `emulator`, or `bluestacks` (listed on its own because its anti-detection checks make it behave differently). |
| `android` | The Android version, such as `13`. |
| `device` | Optional. The phone brand, or the emulator's name. |
| `bridge` | Emulators only: `libhoudini` or `libndk_translation`. The `getprop ro.dalvik.vm.native.bridge` value is not always reliable (BlueStacks does not set it), so check the logcat for `libnb` lines or `houdini`. |
| `loader` | The loader version you tried, as shown in the app, such as `0.0.4.2a`. |
| `game` | The game version, such as `13.0.8`. |
| `fusion` | `verified` if plugin (Fusion) mods were tried and worked, otherwise `not-verified`. Fusion needs a lot of memory and is reported apart from the basic result. |
| `result` | `works`, `partial` or `crashes`. |
| `notes` | Optional, up to 300 characters. |

One entry per combination: a different loader version, Android version or bridge is a new entry. Keep old
entries: a crash that a later version fixed is useful to see.

Check your change before the pull request:

```
python3 compatibility/build.py --check
```
