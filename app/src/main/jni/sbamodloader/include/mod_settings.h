#ifndef SBAMODLOADER_MOD_SETTINGS_H
#define SBAMODLOADER_MOD_SETTINGS_H

// Settings that players change in the loader's Mods screen, read by native mods through the sba_get_setting_* functions
// (native API version 2). The loader writes <data>/ModSettings/<mod id>.settings, one `key=value` per line, before each
// launch; a value never changes while the game runs.

// Tells the loader where the files are (the folder above the BepInEx folder, plus /ModSettings). Called once at start-up.
void mod_settings_init(const char *bepinex_directory);

#endif //SBAMODLOADER_MOD_SETTINGS_H
