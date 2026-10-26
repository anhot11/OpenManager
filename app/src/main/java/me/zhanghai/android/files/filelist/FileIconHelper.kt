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
        if (file.isParentDirectoryItem) {
            return R.drawable.file_parent_directory_icon
        }
        if (file.attributes.isDirectory) {
            return R.drawable.file_directory_icon
        }
        val lowerName = file.name.lowercase()
        return when {
            lowerName.endsWith(".dex") -> R.drawable.file_dex_icon
            lowerName.endsWith(".arsc") -> R.drawable.file_arsc_icon
            lowerName == "androidmanifest.xml" || lowerName.endsWith(".xml") -> R.drawable.file_xml_mt_icon
            lowerName.endsWith(".so") -> R.drawable.file_so_icon
            lowerName.endsWith(".sh") || lowerName.endsWith(".bash") || lowerName.endsWith(".zsh") -> R.drawable.file_sh_icon
            lowerName.endsWith(".apk") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk") || lowerName.endsWith(".apkm") -> R.drawable.file_apk_icon
            lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".bz2") ||
                lowerName.endsWith(".xz") || lowerName.endsWith(".tgz") || lowerName.endsWith(".zst") -> R.drawable.file_archive_icon
            lowerName.endsWith(".ttf") || lowerName.endsWith(".otf") || lowerName.endsWith(".woff") || lowerName.endsWith(".woff2") -> R.drawable.file_font_icon
            lowerName.endsWith(".mp3") || lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") ||
                lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
                lowerName.endsWith(".opus") || lowerName.endsWith(".wma") -> R.drawable.file_audio_icon
            lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".flv") || lowerName.endsWith(".webm") ||
                lowerName.endsWith(".3gp") || lowerName.endsWith(".wmv") -> R.drawable.file_video_icon
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".svg") || lowerName.endsWith(".ico") -> R.drawable.file_image_icon
            lowerName.endsWith(".txt") || lowerName.endsWith(".log") || lowerName.endsWith(".ini") ||
                lowerName.endsWith(".conf") || lowerName.endsWith(".properties") -> R.drawable.file_text_icon
            lowerName.endsWith(".java") || lowerName.endsWith(".kt") || lowerName.endsWith(".c") ||
                lowerName.endsWith(".cpp") || lowerName.endsWith(".h") || lowerName.endsWith(".hpp") ||
                lowerName.endsWith(".py") || lowerName.endsWith(".js") || lowerName.endsWith(".ts") ||
                lowerName.endsWith(".html") || lowerName.endsWith(".css") || lowerName.endsWith(".json") ||
                lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.endsWith(".md") ||
                lowerName.endsWith(".smali") -> R.drawable.file_code_icon
            lowerName.endsWith(".pdf") -> R.drawable.file_pdf_icon
            lowerName.endsWith(".cer") || lowerName.endsWith(".crt") || lowerName.endsWith(".pem") ||
                lowerName.endsWith(".der") || lowerName.endsWith(".p12") || lowerName.endsWith(".bks") ||
                lowerName.endsWith(".jks") -> R.drawable.file_certificate_icon
            else -> file.mimeType.iconRes
        }
    }
}
