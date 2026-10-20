package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import java.io.File

internal class WiiUFolderHandler(
    context: Context,
    fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "wiiu") {

    companion object {
        private const val ID_HEX_LENGTH = 8
        private const val USER_DIR = "user"
        private const val COMMON_DIR = "common"
        private val PERSISTENT_ID = Regex("8[0-9a-fA-F]{7}")

        fun lowId(saveId: String): String =
            saveId.trimEnd('/').substringAfterLast('/').takeLast(ID_HEX_LENGTH).lowercase()

        fun isUserFolderArchive(roots: Set<String>): Boolean =
            roots.isNotEmpty() && roots.all { it == COMMON_DIR || PERSISTENT_ID.matches(it) }
    }

    override val unidentifiedArchiveRoots: Set<String> = setOf(COMMON_DIR)

    override fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.equals(lowId(saveId), ignoreCase = true)

    override fun constructSavePath(baseDir: String, saveId: String): String? =
        "${baseDir.trimEnd('/')}/${lowId(saveId)}"

    override fun matchArchive(tempFile: File, saveId: String): ArchiveRootMatch? {
        val roots = saveArchiver.peekRootEntryNames(tempFile)
        return when {
            roots.any { it.equals(lowId(saveId), ignoreCase = true) } -> ArchiveRootMatch.EXACT
            isUserFolderArchive(roots) -> ArchiveRootMatch.UNIDENTIFIED
            else -> super.matchArchive(tempFile, saveId)
        }
    }

    override fun unpackArchive(tempFile: File, targetFolder: File, saveId: String?): Boolean {
        val roots = saveArchiver.peekRootEntryNames(tempFile)
        if (!isUserFolderArchive(roots)) return super.unpackArchive(tempFile, targetFolder, saveId)
        val userFolder = File(targetFolder, USER_DIR)
        return saveArchiver.unzipRootsTo(tempFile, roots.associateWith { File(userFolder, it) })
    }
}
