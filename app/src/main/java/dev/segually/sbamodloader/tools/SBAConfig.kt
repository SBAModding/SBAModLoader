package dev.segually.sbamodloader.tools

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// todo: convert to a standard lib class because passing a
//  parcelable across classloader namespaces is a bad idea
@Parcelize
class SBAConfig(
    /** The package ID of the game. */
    @JvmField var gamePackageId: String,
    /** The ComponentName of the target activity. */
    @JvmField var gameLauncherName: String,
    /** The directory where the game's native libraries are located. */
    @JvmField var gameLibraryDirectory: String,
    /** The directory where SBA's native libraries are located. */
    @JvmField var appLibraryDirectory: String,
    /** The directory where SBA's data files are located. */
    @JvmField var appDataDirectory: String,
    /** The directory where SBA's code cache is located. */
    @JvmField var codeCacheDirectory: String,
    /** The directory where BepInEx should be installed. */
    @JvmField var bepInExDirectory: String,
    /** The directory where the .NET runtime should be installed. */
    @JvmField var dotnetDirectory: String,
    /** The directory where the game's Unity data files are located. */
    @JvmField var unityDataDirectory: String,
    /** The Unity version of the game. */
    @JvmField var unityVersion: String,
    /** Whether to use the original libunity.so from the game or the one provided by SBA. */
    @JvmField var useOriginalLibUnity: Boolean,
    /** Variables to encode in the SBA_VARIABLES environment variable. */
    @JvmField var sbaVariables: Array<String>,
    /** Auxiliary folders to load BepInEx plugins from. */
    @JvmField var auxiliaryPluginFolders: Array<String>,
    /** Absolute paths of the native mods (.so files) to load, in load order. */
    @JvmField var nativeMods: Array<String>,
    /** Mapped IL2CPP api calls. Format: original_name:mapped_name */
    @JvmField var il2cppApiMap: Map<String, String>,
    /** Whether to start the .NET runtime and BepInEx (Fusion). Off when no Fusion mod is enabled. */
    @JvmField var fusionEnabled: Boolean,
    /** The folder the memory log is written to. */
    @JvmField var logDirectory: String
) : Parcelable