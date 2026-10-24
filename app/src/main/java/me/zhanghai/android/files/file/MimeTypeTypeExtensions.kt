/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

val MimeType.isApk: Boolean
    get() = this == MimeType.APK

val MimeType.isSupportedArchive: Boolean
    get() = this in supportedArchiveMimeTypes

private val supportedArchiveMimeTypes = mutableListOf(
    "application/gzip",
    "application/java-archive",
    "application/rar",
    "application/zip",
    "application/zstd",
    "application/vnd.android.package-archive",
    "application/vnd.debian.binary-package",
    "application/vnd.ms-cab-compressed",
    "application/vnd.rar",
    "application/x-7z-compressed",
    "application/x-bzip2",
    "application/x-cab",
    "application/x-compress",
    "application/x-cpio",
    "application/x-deb",
    "application/x-debian-package",
    "application/x-gtar",
    "application/x-gtar-compressed",
    "application/x-iso9660-image",
    "application/x-java-archive",
    "application/x-lha",
    "application/x-lzma",
    "application/x-redhat-package-manager",
    "application/x-tar",
    "application/x-ustar",
    "application/x-xz"
).map { it.asMimeType() }.toSet()

val MimeType.isImage: Boolean
    get() = icon == MimeTypeIcon.IMAGE

val MimeType.isAudio: Boolean
    get() = icon == MimeTypeIcon.AUDIO || value.startsWith("audio/")

val MimeType.isVideo: Boolean
    get() = icon == MimeTypeIcon.VIDEO || value.startsWith("video/")

val MimeType.isMedia: Boolean
    get() = isAudio || isVideo

val MimeType.isPdf: Boolean
    get() = this == MimeType.PDF

val MimeType.isText: Boolean
    get() = icon == MimeTypeIcon.DOCUMENT
        || value.startsWith("text/")
        || this in textMimeTypes

val MimeType.isSqlite: Boolean
    get() = this in sqliteMimeTypes

val MimeType.isDex: Boolean
    get() = value == "application/vnd.android.dex"

val MimeType.isArsc: Boolean
    get() = value == "application/vnd.android.arsc"

val MimeType.isSharedLibrary: Boolean
    get() = value == "application/x-sharedlib" || value == "application/x-elf"

private val textMimeTypes = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/typescript",
    "application/x-sh",
    "application/x-shellscript",
    "application/yaml",
    "application/x-yaml",
    "application/x-toml",
    "application/x-smali",
    "application/x-bat",
    "application/x-cmd",
    "application/sql",
    "application/x-sql"
).map { it.asMimeType() }.toSet()

private val sqliteMimeTypes = setOf(
    "application/x-sqlite3",
    "application/vnd.sqlite3",
    "application/sqlite3"
).map { it.asMimeType() }.toSet()

