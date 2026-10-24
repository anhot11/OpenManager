/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.webkit.MimeTypeMap
import me.zhanghai.android.files.compat.getMimeTypeFromExtensionCompat
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asPathName

fun MimeType.Companion.guessFromPath(path: String): MimeType {
    val fileName = path.asPathName().fileName ?: return DIRECTORY
    return guessFromExtension(fileName.asFileName().singleExtension)
}

fun MimeType.Companion.guessFromExtension(extension: String): MimeType {
    val extension = extension.lowercase()
    return extensionToMimeTypeOverrideMap[extension]
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtensionCompat(extension)?.asMimeTypeOrNull()
        ?: GENERIC
}

// @see https://android.googlesource.com/platform/external/mime-support/+/master/mime.types
// @see https://android.googlesource.com/platform/frameworks/base/+/master/mime/java-res/android.mime.types
// @see http://www.iana.org/assignments/media-types/media-types.xhtml
// @see https://salsa.debian.org/debian/media-types/-/blob/master/mime.types
// @see /usr/share/mime/packages/freedesktop.org.xml
private val extensionToMimeTypeOverrideMap = mapOf(
    // Archives & Packages
    "7z" to "application/x-7z-compressed",
    "rar" to "application/vnd.rar",
    "tar" to "application/x-tar",
    "tgz" to "application/gzip",
    "tbz" to "application/x-bzip2",
    "tbz2" to "application/x-bzip2",
    "txz" to "application/x-xz",
    "zst" to "application/zstd",
    "iso" to "application/x-iso9660-image",
    "cab" to "application/vnd.ms-cab-compressed",
    "deb" to "application/vnd.debian.binary-package",
    "rpm" to "application/x-redhat-package-manager",
    "cpio" to "application/x-cpio",
    "ar" to "application/x-archive",
    "jar" to "application/java-archive",
    "zip" to "application/zip",
    "gz" to "application/gzip",
    "xz" to "application/x-xz",
    // Databases
    "db" to "application/x-sqlite3",
    "sqlite" to "application/x-sqlite3",
    "sqlite3" to "application/x-sqlite3",
    // Audio
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "flac" to "audio/flac",
    "m4a" to "audio/mp4",
    "aac" to "audio/aac",
    "opus" to "audio/opus",
    "mid" to "audio/midi",
    "midi" to "audio/midi",
    "amr" to "audio/amr",
    "wma" to "audio/x-ms-wma",
    // Video
    "mp4" to "video/mp4",
    "mkv" to "video/x-matroska",
    "webm" to "video/webm",
    "avi" to "video/avi",
    "mov" to "video/quicktime",
    "3gp" to "video/3gpp",
    "flv" to "video/x-flv",
    "ts" to "video/mp2ts",
    "m4v" to "video/x-m4v",
    "wmv" to "video/x-ms-wmv",
    // Code & Text
    "smali" to "text/x-smali",
    "json" to "application/json",
    "yaml" to "application/yaml",
    "yml" to "application/yaml",
    "toml" to "text/x-toml",
    "xml" to "application/xml",
    "html" to "text/html",
    "htm" to "text/html",
    "css" to "text/css",
    "js" to "application/javascript",
    "jsx" to "text/jsx",
    "tsx" to "text/tsx",
    "py" to "text/x-python",
    "c" to "text/x-c",
    "cpp" to "text/x-c++",
    "cc" to "text/x-c++",
    "cxx" to "text/x-c++",
    "h" to "text/x-c-header",
    "hpp" to "text/x-c++-header",
    "java" to "text/x-java",
    "kt" to "text/x-kotlin",
    "kts" to "text/x-kotlin",
    "rs" to "text/x-rust",
    "go" to "text/x-go",
    "php" to "text/x-php",
    "sql" to "text/x-sql",
    "gradle" to "text/x-gradle",
    "svg" to "image/svg+xml",
    "vue" to "text/x-vue",
    "dart" to "text/x-dart",
    "lua" to "text/x-lua",
    "rb" to "text/x-ruby",
    "swift" to "text/x-swift",
    "cmake" to "text/x-cmake",
    "proto" to "text/x-protobuf",
    "diff" to "text/x-diff",
    "patch" to "text/x-diff",
    // Fixes
    "csv" to "text/csv", // Was "text/comma-separated-values"
    "sh" to "application/x-sh", // Was "text/x-sh"
    "dex" to "application/vnd.android.dex",
    "arsc" to "application/vnd.android.arsc",
    "so" to "application/x-sharedlib",
    "apks" to "application/vnd.android.package-archive",
    "xapk" to "application/vnd.android.package-archive",
    // Addition
    "bz" to "application/x-bzip",
    "bz2" to "application/x-bzip2",
    "z" to "application/x-compress",
    "lzma" to "application/x-lzma",
    "p7b" to "application/x-pkcs7-certificates",
    "spc" to "application/x-pkcs7-certificates", // Clashes with "chemical/x-galactic-spc"
    "py3" to "text/x-python",
    "py3x" to "text/x-python",
    "pyx" to "text/x-python",
    "wsgi" to "text/x-python",
    "asm" to "text/x-asm",
    "s" to "text/x-asm",
    "cs" to "text/x-csharp",
    "azw" to "application/vnd.amazon.ebook",
    "ibooks" to "application/x-ibooks+zip",
    "msg" to "application/vnd.ms-outlook",
    "mkd" to "text/markdown",
    "conf" to "text/plain",
    "ini" to "text/plain",
    "list" to "text/plain",
    "log" to "text/plain",
    "prop" to "text/plain",
    "properties" to "text/plain",
    "rc" to "text/plain"
).mapValues { it.value.asMimeType() }


fun MimeType.Companion.forSpecialPosixFileType(type: PosixFileType): MimeType? =
    specialPosixFileTypeToMimeTypeMap[type]

// See also https://developer.gnome.org/shared-mime-info-spec/
/** @see specialPosixFileTypeToNameResMap */
private val specialPosixFileTypeToMimeTypeMap = mapOf(
    PosixFileType.CHARACTER_DEVICE to "inode/chardevice",
    PosixFileType.BLOCK_DEVICE to "inode/blockdevice",
    PosixFileType.FIFO to "inode/fifo",
    PosixFileType.SYMBOLIC_LINK to "inode/symlink",
    PosixFileType.SOCKET to "inode/socket"
).mapValues { it.value.asMimeType() }

val MimeType.extension: String?
    // TODO: Add compat implementation as well.
    get() = MimeTypeMap.getSingleton().getExtensionFromMimeType(value)

val MimeType.intentType: String
    get() = intentMimeType.value

private val MimeType.intentMimeType: MimeType
    get() = mimeTypeToIntentMimeTypeMap[this] ?: this

private val mimeTypeToIntentMimeTypeMap = listOf(
    // Allows matching "text/*"
    "application/ecmascript" to "text/ecmascript",
    "application/javascript" to "text/javascript",
    "application/json" to "text/json",
    "application/typescript" to "text/typescript",
    "application/yaml" to "text/x-yaml",
    "application/x-sh" to "text/x-shellscript",
    "application/x-shellscript" to "text/x-shellscript",
    // Allows matching generic
    MimeType.GENERIC.value to MimeType.ANY.value
).associate { it.first.asMimeType() to it.second.asMimeType() }

val Collection<MimeType>.intentType: String
    get() {
        if (isEmpty()) {
            return MimeType.ANY.value
        }
        val intentMimeTypes = map { it.intentMimeType }
        val firstIntentMimeType = intentMimeTypes.first()
        if (intentMimeTypes.all { firstIntentMimeType.match(it) }) {
            return firstIntentMimeType.value
        }
        val wildcardIntentMimeType = MimeType.of(firstIntentMimeType.type, "*", null)
        if (intentMimeTypes.all { wildcardIntentMimeType.match(it) }) {
            return wildcardIntentMimeType.value
        }
        return MimeType.ANY.value
    }
