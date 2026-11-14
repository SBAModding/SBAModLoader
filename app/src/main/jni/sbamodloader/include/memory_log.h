#ifndef SBAMODLOADER_MEMORY_LOG_H
#define SBAMODLOADER_MEMORY_LOG_H

#include <string>

/**
 * Writes a memory timeline of the game process to {@code <logDirectory>/memory.log}: a header, a line for every
 * labelled mark, and a sample every half second for the first two minutes (then every few seconds for a while).
 * Every line is flushed at once, so the file survives a crash and shows the last readings before it.
 * The previous launch's file is kept as memory_prev.log. Does nothing when the directory is empty.
 */
void memlog_start(const std::string &logDirectory, const std::string &header);

/** Records the current readings under {@code label}. Safe to call from any thread; does nothing before start. */
void memlog_mark(const char *label);

/** Writes a plain text line (no readings) to the log; does nothing before start. */
void memlog_note(const char *text);

#endif //SBAMODLOADER_MEMORY_LOG_H
