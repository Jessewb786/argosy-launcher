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
        private const val FULL_ID_HEX_LENGTH = 16
        private const val USER_DIR = "user"
        private const val COMMON_DIR = "common"
        private val PERSISTENT_ID = Regex("8[0-9a-fA-F]{7}")
        private val CATEGORY_DIR = Regex("0005[0-9a-fA-F]{4}")
        private val FULL_ID = Regex("[0-9a-fA-F]{$FULL_ID_HEX_LENGTH}")

        fun lowId(saveId: String): String =
            saveId.trimEnd('/').substringAfterLast('/').takeLast(ID_HEX_LENGTH).lowercase()

        fun categoryOf(saveId: String): String? =
            saveId.replace("/", "").takeIf { FULL_ID.matches(it) }?.take(ID_HEX_LENGTH)?.lowercase()

        fun categoryBase(basePath: String, saveId: String): String {
            val base = basePath.trimEnd('/')
            val category = categoryOf(saveId) ?: return base
            val lastSegment = base.substringAfterLast('/')
            if (!CATEGORY_DIR.matches(lastSegment) || lastSegment.equals(category, ignoreCase = true)) return base
            return "${base.substringBeforeLast('/')}/$category"
        }

        fun isUserFolderArchive(roots: Set<String>): Boolean =
            roots.isNotEmpty() && roots.all { it == COMMON_DIR || PERSISTENT_ID.matches(it) }
    }

    override fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.equals(lowId(saveId), ignoreCase = true)

    override fun constructSavePath(baseDir: String, saveId: String): String? =
        "${categoryBase(baseDir, saveId)}/${lowId(saveId)}"

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? =
        super.findSaveFolderBySaveId(categoryBase(basePath, saveId), saveId)

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> =
        super.findAllSaveFoldersBySaveId(categoryBase(basePath, saveId), saveId)

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
