package me.zhanghai.android.files.filelist

import java.io.File
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.file.asDocumentTreeUriOrNull
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.DocumentTree
import me.zhanghai.android.files.util.valueCompat

object PathNavigator {
    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "apk", "jar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "7z", "rar", "iso"
    )

    fun isArchiveFileName(name: String): Boolean {
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return ext in ARCHIVE_EXTENSIONS ||
            lower.endsWith(".tar.gz") ||
            lower.endsWith(".tar.bz2") ||
            lower.endsWith(".tar.xz")
    }

    fun resolve(rawInput: String, currentPath: Path?): Path {
        var input = rawInput.trim()
        if (input.isEmpty()) {
            return currentPath ?: Paths.get("/storage/emulated/0")
        }

        // Handle common shortcuts
        if (input == "~" || input.startsWith("~/")) {
            input = "/storage/emulated/0" + input.removePrefix("~")
        }
        if (input == "/sdcard" || input.startsWith("/sdcard/")) {
            input = "/storage/emulated/0" + input.removePrefix("/sdcard")
        }
        if (input == "sdcard" || input.startsWith("sdcard/")) {
            input = "/storage/emulated/0" + input.removePrefix("sdcard")
        }

        // Relative path handling
        val fullPathStr = if (input.startsWith("/")) {
            input
        } else {
            val firstSegment = input.substringBefore('/').lowercase()
            if (firstSegment in setOf(
                    "download", "downloads", "dcim", "documents", "android",
                    "movies", "music", "pictures", "alarms", "notifications",
                    "ringtones", "podcasts"
                )) {
                val normalizedSub = if (firstSegment == "downloads" || firstSegment == "download") {
                    "Download" + input.substring(firstSegment.length)
                } else if (firstSegment == "android") {
                    "Android" + input.substring(firstSegment.length)
                } else if (firstSegment == "dcim") {
                    "DCIM" + input.substring(firstSegment.length)
                } else if (firstSegment == "documents") {
                    "Documents" + input.substring(firstSegment.length)
                } else if (firstSegment == "movies") {
                    "Movies" + input.substring(firstSegment.length)
                } else if (firstSegment == "music") {
                    "Music" + input.substring(firstSegment.length)
                } else if (firstSegment == "pictures") {
                    "Pictures" + input.substring(firstSegment.length)
                } else {
                    input
                }
                "/storage/emulated/0/$normalizedSub"
            } else if (currentPath != null) {
                val currentDisplay = currentPath.toDisplayPath(trailingSlash = false)
                "$currentDisplay/$input"
            } else {
                "/storage/emulated/0/$input"
            }
        }

        // Canonicalize segments ("." and "..")
        val segments = fullPathStr.split('/').filter { it.isNotEmpty() && it != "." }
        val canonicalSegments = mutableListOf<String>()
        for (seg in segments) {
            if (seg == "..") {
                if (canonicalSegments.isNotEmpty()) {
                    canonicalSegments.removeAt(canonicalSegments.size - 1)
                }
            } else {
                canonicalSegments.add(seg)
            }
        }

        // Check if any segment along the path is an archive
        var archiveIndex = -1
        val pathBuilder = StringBuilder()
        for (i in canonicalSegments.indices) {
            pathBuilder.append("/").append(canonicalSegments[i])
            val candidatePath = pathBuilder.toString()
            val candidateFile = File(candidatePath)
            if (candidateFile.isFile && isArchiveFileName(canonicalSegments[i])) {
                archiveIndex = i
                break
            }
            if (isArchiveFileName(canonicalSegments[i]) && i < canonicalSegments.size - 1) {
                archiveIndex = i
                break
            }
        }

        if (archiveIndex != -1) {
            val archiveFilePathStr = "/" + canonicalSegments.subList(0, archiveIndex + 1).joinToString("/")
            val archiveFilePath = Paths.get(archiveFilePathStr)
            var currentArchive: Path = archiveFilePath.createArchiveRootPath()
            val remainingSegments = canonicalSegments.subList(archiveIndex + 1, canonicalSegments.size)
            for (subSeg in remainingSegments) {
                currentArchive = currentArchive.resolve(subSeg)
                if (isArchiveFileName(subSeg)) {
                    try {
                        currentArchive = currentArchive.createArchiveRootPath()
                    } catch (e: Exception) {
                        // ignore and keep currentArchive
                    }
                }
            }
            return currentArchive
        }

        // Check for Android/data or Android/obb with SAF DocumentTree
        val canonicalPathStr = "/" + canonicalSegments.joinToString("/")
        if (canonicalPathStr.startsWith("/storage/emulated/0/Android/data") ||
            canonicalPathStr.startsWith("/storage/emulated/0/Android/obb")) {
            val isObb = canonicalPathStr.startsWith("/storage/emulated/0/Android/obb")
            val basePrefix = if (isObb) "/storage/emulated/0/Android/obb" else "/storage/emulated/0/Android/data"
            val targetDocId = if (isObb) "primary:Android/obb" else "primary:Android/data"

            val storages = Settings.STORAGES.valueCompat
            val match = storages.filterIsInstance<DocumentTree>().firstOrNull { t ->
                val uriStr = t.uri.value.toString()
                val docId = t.uri.asDocumentTreeUriOrNull()?.documentId
                docId == targetDocId ||
                    (if (isObb) uriStr.contains("Android%2Fobb", true) else uriStr.contains("Android%2Fdata", true))
            }

            if (match != null) {
                val subPath = canonicalPathStr.removePrefix(basePrefix).trimStart('/')
                return if (subPath.isEmpty()) match.path else match.path.resolve(subPath)
            }
        }

        return Paths.get(canonicalPathStr)
    }
}
