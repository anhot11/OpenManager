/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asDocumentTreeUriOrNull
import me.zhanghai.android.files.file.isSupportedArchive
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.document.resolver.DocumentResolver
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.DocumentTree
import me.zhanghai.android.files.util.valueCompat

val Path.name: String
    get() = fileName?.toString() ?: if (isArchivePath) archiveFile.fileName.toString() else "/"

fun Path.toDisplayPath(trailingSlash: Boolean = true): String {
    val raw = when {
        isArchivePath -> {
            val base = archiveFile.toDisplayPath(trailingSlash = false)
            val internal = toString().trimStart('/')
            if (internal.isEmpty()) {
                base
            } else {
                "$base/$internal"
            }
        }
        isDocumentPath -> {
            val docPath = this as? DocumentResolver.Path
            val treeUri = docPath?.treeUri
            if (treeUri != null && treeUri.authority == DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY) {
                val docTreeUri = treeUri.asDocumentTreeUriOrNull()
                val docId = docTreeUri?.documentId
                val base = when {
                    docId != null && docId.startsWith("primary:") -> {
                        val sub = docId.removePrefix("primary:").trimStart('/')
                        if (sub.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$sub"
                    }
                    docId != null && docId.contains(":") -> {
                        val parts = docId.split(":", limit = 2)
                        "/storage/${parts[0]}/${parts[1].trimStart('/')}"
                    }
                    else -> "/storage/emulated/0"
                }
                val internal = toString().trimStart('/')
                if (internal.isEmpty()) base else "$base/$internal"
            } else {
                val internal = toString().trimStart('/')
                val storage = Settings.STORAGES.valueCompat.filterIsInstance<DocumentTree>().firstOrNull {
                    it.uri.value == treeUri
                }
                val base = storage?.customName ?: "SAF"
                if (internal.isEmpty()) base else "$base/$internal"
            }
        }
        isLinuxPath -> {
            toFile().path
        }
        else -> {
            toString()
        }
    }
    return if (trailingSlash) {
        if (!raw.endsWith("/")) "$raw/" else raw
    } else {
        if (raw.length > 1 && raw.endsWith("/")) raw.trimEnd('/') else raw
    }
}

fun Path.toUserFriendlyString(): String = toDisplayPath(trailingSlash = false)

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath

val Path.parentOrArchiveParent: Path?
    get() {
        val directParent = parent
        if (directParent != null) return directParent
        if (isArchivePath) {
            return archiveFile.parent
        }
        if (isDocumentPath) {
            val docPath = this as? DocumentResolver.Path ?: return null
            val treeUri = docPath.treeUri
            if (treeUri.authority == DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY) {
                val docTreeUri = treeUri.asDocumentTreeUriOrNull()
                val docId = docTreeUri?.documentId ?: return null
                if (docId.startsWith("primary:Android/data") || docId.startsWith("primary:Android/obb")) {
                    return Paths.get("/storage/emulated/0/Android")
                }
                if (docId.startsWith("primary:") && docId != "primary:") {
                    val sub = docId.removePrefix("primary:").trim('/')
                    val parentSub = sub.substringBeforeLast('/', "")
                    return if (parentSub.isEmpty()) Paths.get("/storage/emulated/0") else Paths.get("/storage/emulated/0/$parentSub")
                }
            }
        }
        return null
    }
