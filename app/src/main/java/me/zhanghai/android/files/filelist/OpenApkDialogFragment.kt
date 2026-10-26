/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getPackageArchiveInfoCompat
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.valueCompat

class OpenApkDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_mt_apk_info, null)

        val ivIcon = view.findViewById<ImageView>(R.id.ivApkIcon)
        val tvLabel = view.findViewById<TextView>(R.id.tvApkLabel)
        val tvVersion = view.findViewById<TextView>(R.id.tvApkVersion)
        val tvPackage = view.findViewById<TextView>(R.id.tvApkPackage)
        val tvVersionCode = view.findViewById<TextView>(R.id.tvApkVersionCode)
        val tvSize = view.findViewById<TextView>(R.id.tvApkSize)
        val tvSignature = view.findViewById<TextView>(R.id.tvApkSignature)
        val tvProtection = view.findViewById<TextView>(R.id.tvApkProtection)
        val tvTargetSdk = view.findViewById<TextView>(R.id.tvApkTargetSdk)
        val tvMinSdk = view.findViewById<TextView>(R.id.tvApkMinSdk)
        val tvInstalled = view.findViewById<TextView>(R.id.tvApkInstalled)

        val btnFunction = view.findViewById<Button>(R.id.btnApkFunction)
        val btnView = view.findViewById<Button>(R.id.btnApkView)
        val btnRootInstall = view.findViewById<Button>(R.id.btnApkRootInstall)
        val btnInstall = view.findViewById<Button>(R.id.btnApkInstall)

        val file = args.file
        val sizeStr = file.attributes.fileSize.formatHumanReadable(context)
        tvSize.text = sizeStr
        tvProtection.text = "No Detected"

        try {
            var packageInfoFlags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfoFlags = packageInfoFlags or PackageManager.GET_SIGNING_CERTIFICATES
            }
            val (packageInfo, closeable) = packageManager.getPackageArchiveInfoCompat(file.path, packageInfoFlags)
            closeable.use {
                if (packageInfo != null) {
                    val appInfo = packageInfo.applicationInfo
                    if (appInfo != null) {
                        try {
                            appInfo.sourceDir = file.path.toUserFriendlyString()
                            appInfo.publicSourceDir = file.path.toUserFriendlyString()
                        } catch (e: Exception) {
                            // Ignored
                        }
                        val label = try {
                            appInfo.loadLabel(packageManager).toString()
                        } catch (e: Exception) {
                            file.name
                        }
                        tvLabel.text = label

                        val icon = try {
                            appInfo.loadIcon(packageManager)
                        } catch (e: Exception) {
                            null
                        }
                        if (icon != null) {
                            ivIcon.setImageDrawable(icon)
                        } else {
                            ivIcon.setImageResource(R.drawable.file_apk_icon)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            tvMinSdk.text = "Android ${getSdkName(appInfo.minSdkVersion)} (API ${appInfo.minSdkVersion})"
                        } else {
                            tvMinSdk.text = "API ?"
                        }
                        tvTargetSdk.text = "Android ${getSdkName(appInfo.targetSdkVersion)} (API ${appInfo.targetSdkVersion})"
                    } else {
                        tvLabel.text = file.name
                        ivIcon.setImageResource(R.drawable.file_apk_icon)
                        tvMinSdk.text = "-"
                        tvTargetSdk.text = "-"
                    }

                    tvPackage.text = packageInfo.packageName ?: file.name
                    tvVersion.text = "v${packageInfo.versionName ?: "1.0"}"
                    tvVersionCode.text = packageInfo.longVersionCodeCompat.toString()

                    val sigCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.signingInfo?.apkContentsSigners?.size ?: 0
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.signatures?.size ?: 0
                    }
                    tvSignature.text = if (sigCount > 0) "V1 + V2 + V3 (Válida)" else "Sin firmar"

                    // Check if installed
                    val pkgName = packageInfo.packageName
                    if (pkgName != null) {
                        try {
                            val installedInfo = packageManager.getPackageInfo(pkgName, 0)
                            tvInstalled.text = "v${installedInfo.versionName} (${installedInfo.longVersionCodeCompat})"
                        } catch (e: Exception) {
                            tvInstalled.text = "No instalado"
                        }
                    } else {
                        tvInstalled.text = "No instalado"
                    }
                } else {
                    tvLabel.text = file.name
                    ivIcon.setImageResource(R.drawable.file_apk_icon)
                    tvPackage.text = file.name
                    tvVersion.text = "-"
                    tvVersionCode.text = "-"
                    tvSignature.text = "-"
                    tvMinSdk.text = "-"
                    tvTargetSdk.text = "-"
                    tvInstalled.text = "No instalado"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tvLabel.text = file.name
            ivIcon.setImageResource(R.drawable.file_apk_icon)
            tvPackage.text = file.name
            tvVersion.text = "-"
            tvVersionCode.text = "-"
            tvSignature.text = "-"
            tvMinSdk.text = "-"
            tvTargetSdk.text = "-"
            tvInstalled.text = "No instalado"
        }

        val isRootAvailable = ApkInstallHelper.isRootAvailable()
        btnRootInstall.isVisible = isRootAvailable

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()

        btnView.setOnClickListener {
            dialog.dismiss()
            listener.viewApk(file)
        }

        btnRootInstall.setOnClickListener {
            dialog.dismiss()
            listener.installApkWithRoot(file)
        }

        btnInstall.setOnClickListener {
            dialog.dismiss()
            if (isRootAvailable && Settings.USE_ROOT_INSTALL.valueCompat) {
                listener.installApkWithRoot(file)
            } else {
                listener.installApk(file)
            }
        }

        btnFunction.setOnClickListener {
            val optionsList = mutableListOf(
                getString(R.string.view)
            )
            if (isRootAvailable) {
                optionsList.add(getString(R.string.install_root))
            }
            optionsList.addAll(listOf(
                getString(R.string.apk_sign),
                getString(R.string.apk_clone),
                getString(R.string.apk_optimize),
                getString(R.string.apk_convert_apks)
            ))
            val options = optionsList.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.apk_function)
                .setItems(options) { _, which ->
                    val chosen = options[which]
                    when (chosen) {
                        getString(R.string.view) -> {
                            dialog.dismiss()
                            listener.viewApk(file)
                        }
                        getString(R.string.install_root) -> {
                            dialog.dismiss()
                            listener.installApkWithRoot(file)
                        }
                        getString(R.string.apk_sign) -> Toast.makeText(context, "Firma de APK seleccionada", Toast.LENGTH_SHORT).show()
                        getString(R.string.apk_clone) -> Toast.makeText(context, "Clonación de APK seleccionada", Toast.LENGTH_SHORT).show()
                        getString(R.string.apk_optimize) -> Toast.makeText(context, "Optimización zipalign seleccionada", Toast.LENGTH_SHORT).show()
                        getString(R.string.apk_convert_apks) -> Toast.makeText(context, "Conversión de APKS a APK seleccionada", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        return dialog
    }

    private fun getSdkName(apiLevel: Int): String = when (apiLevel) {
        21 -> "5.0"
        22 -> "5.1"
        23 -> "6.0"
        24 -> "7.0"
        25 -> "7.1"
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        else -> "$apiLevel"
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            OpenApkDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun installApk(file: FileItem)
        fun installApkWithRoot(file: FileItem)
        fun viewApk(file: FileItem)
    }
}
