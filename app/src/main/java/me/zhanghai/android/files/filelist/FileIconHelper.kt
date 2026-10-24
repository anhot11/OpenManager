/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.iconRes

object FileIconHelper {
    fun getIconRes(file: FileItem): Int {
        if (file.attributes.isDirectory) {
            return R.drawable.file_directory_icon
        }
        val lowerName = file.name.lowercase()
        return when {
            lowerName.endsWith(".dex") -> R.drawable.file_dex_icon
            lowerName.endsWith(".arsc") -> R.drawable.file_arsc_icon
            lowerName == "androidmanifest.xml" || lowerName.endsWith(".xml") -> R.drawable.file_xml_mt_icon
            lowerName.endsWith(".so") -> R.drawable.file_so_icon
            lowerName.endsWith(".sh") -> R.drawable.file_sh_icon
            lowerName.endsWith(".apk") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk") -> R.drawable.file_apk_icon
            lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".bz2") ||
                lowerName.endsWith(".xz") -> R.drawable.file_archive_icon
            else -> file.mimeType.iconRes
        }
    }
}
