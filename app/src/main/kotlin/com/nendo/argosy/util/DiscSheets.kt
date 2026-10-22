package com.nendo.argosy.util

import java.io.File

/**
 * Disc images split into a sheet an emulator opens (`.cue`, `.gdi`, `.ccd`, `.mds`, `.toc`) and
 * the track files that sheet points at.
 */
object DiscSheets {

    val LOADER_EXTENSIONS = setOf("cue", "gdi", "ccd", "mds", "toc")

    private val CUE_FILE_LINE = Regex("^\\s*FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val GDI_TRACK_LINE = Regex("^\\s*\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+(\"([^\"]+)\"|(\\S+))")

    fun isLoader(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in LOADER_EXTENSIONS

    /**
     * The track file names a `.cue` or `.gdi` lists, in sheet order. Empty for any other sheet or
     * an unreadable file.
     */
    fun trackNames(sheet: File): List<String> {
        val lines = runCatching { sheet.readLines() }.getOrDefault(emptyList())
        return when (sheet.extension.lowercase()) {
            "cue" -> lines.mapNotNull { CUE_FILE_LINE.find(it)?.groupValues?.getOrNull(1) }
            "gdi" -> lines.drop(1).mapNotNull { line ->
                GDI_TRACK_LINE.find(line)?.groupValues?.let { it[2].ifEmpty { it[3] } }
            }
            else -> emptyList()
        }
    }

    /**
     * Files in [loader]'s own directory that belong to it: the tracks a `.cue` or `.gdi` lists,
     * and siblings sharing its stem whose extension is in [romExtensions]. Never the loader
     * itself, and never a file outside that directory.
     */
    fun companions(loader: File, romExtensions: Set<String>): List<File> {
        val dir = loader.parentFile ?: return emptyList()
        val listed = trackNames(loader).map { File(dir, it) }
        val sameStem = dir.listFiles().orEmpty().filter {
            it.isFile && it.nameWithoutExtension.equals(loader.nameWithoutExtension, ignoreCase = true) &&
                it.extension.lowercase() in romExtensions
        }
        return (listed + sameStem)
            .filter { it.isFile && it.parentFile?.canonicalPath == dir.canonicalPath }
            .filterNot { it.canonicalPath == loader.canonicalPath }
            .distinctBy { it.canonicalPath }
    }
}
