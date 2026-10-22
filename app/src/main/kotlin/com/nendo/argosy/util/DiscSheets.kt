package com.nendo.argosy.util

import java.io.File

/**
 * Disc images split into a sheet an emulator opens (`.cue`, `.gdi`, `.ccd`, `.mds`, `.toc`) and
 * the track files that sheet points at.
 */
object DiscSheets {

    val LOADER_EXTENSIONS = setOf("cue", "gdi", "ccd", "mds", "toc")

    private val CUE_FILE_LINE = Regex("^\\s*FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    fun isLoader(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in LOADER_EXTENSIONS

    fun cueTrackNames(cueFile: File): List<String> =
        runCatching { cueFile.readLines() }.getOrDefault(emptyList())
            .mapNotNull { CUE_FILE_LINE.find(it)?.groupValues?.getOrNull(1) }

    /**
     * Files in [loader]'s own directory that belong to it: what a `.cue` lists, and siblings
     * sharing its stem whose extension is in [romExtensions]. Never the loader itself, and never a
     * file outside that directory.
     */
    fun companions(loader: File, romExtensions: Set<String>): List<File> {
        val dir = loader.parentFile ?: return emptyList()
        val listed = if (loader.extension.equals("cue", ignoreCase = true)) {
            cueTrackNames(loader).map { File(dir, it) }
        } else {
            emptyList()
        }
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
