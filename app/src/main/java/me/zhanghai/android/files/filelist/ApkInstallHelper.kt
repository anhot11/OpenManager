package me.zhanghai.android.files.filelist

import android.content.Context
import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.root.LibSuFileServiceLauncher
import java.io.File
import java.io.FileOutputStream

object ApkInstallHelper {

    fun isRootAvailable(): Boolean {
        return LibSuFileServiceLauncher.isSuAvailable() || (Shell.isAppGrantedRoot() == true)
    }

    suspend fun installWithRoot(
        context: Context,
        file: FileItem
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val path = file.path
        val tempApkInCache = if (!path.isLinuxPath) {
            val cacheFile = File(context.cacheDir, "temp_install_${System.currentTimeMillis()}.apk")
            try {
                path.newInputStream().use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                cacheFile
            } catch (e: Exception) {
                return@withContext false to "Error al preparar archivo APK: ${e.message}"
            }
        } else {
            null
        }

        val sourceFilePath = tempApkInCache?.absolutePath ?: path.toFile().absolutePath
        val targetTmpPath = "/data/local/tmp/om_root_install_${System.currentTimeMillis()}.apk"

        val grantPermFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "-g " else ""
        // Flags: -r (replace existing), -d (allow downgrade), -t (allow test packages), -g (grant permissions)
        val installCmd = "pm install -r -d -t $grantPermFlag\"$targetTmpPath\""

        val commands = listOf(
            "cp \"$sourceFilePath\" \"$targetTmpPath\"",
            "chmod 644 \"$targetTmpPath\"",
            installCmd,
            "STATUS=$?",
            "rm -f \"$targetTmpPath\"",
            "exit \$STATUS"
        )
        val script = commands.joinToString(" && ")

        val result = try {
            Shell.cmd(script).exec()
        } catch (e: Exception) {
            tempApkInCache?.delete()
            return@withContext false to "Error al ejecutar en shell root: ${e.message}"
        }

        tempApkInCache?.delete()

        val output = (result.out + result.err).joinToString("\n").trim()
        val isSuccess = (result.isSuccess || result.code == 0) &&
            (output.contains("Success", ignoreCase = true) || !output.contains("Failure", ignoreCase = true))

        if (isSuccess) {
            true to "Instalación completada con éxito"
        } else {
            val errorMsg = if (output.isNotEmpty()) output else "Código de error: ${result.code}"
            false to errorMsg
        }
    }
}
