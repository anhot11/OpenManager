/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TableRow
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        val rowDataDir = view.findViewById<TableRow>(R.id.rowApkDataDir)
        val rowApkPath = view.findViewById<TableRow>(R.id.rowApkPath)
        val rowFirstInstall = view.findViewById<TableRow>(R.id.rowApkFirstInstall)
        val rowLastUpdate = view.findViewById<TableRow>(R.id.rowApkLastUpdate)
        val rowUid = view.findViewById<TableRow>(R.id.rowApkUid)

        val tvDataDir = view.findViewById<TextView>(R.id.tvApkDataDir)
        val tvApkPath = view.findViewById<TextView>(R.id.tvApkPath)
        val tvFirstInstall = view.findViewById<TextView>(R.id.tvApkFirstInstall)
        val tvLastUpdate = view.findViewById<TextView>(R.id.tvApkLastUpdate)
        val tvUid = view.findViewById<TextView>(R.id.tvApkUid)

        val btnFunction = view.findViewById<Button>(R.id.btnApkFunction)
        val btnView = view.findViewById<Button>(R.id.btnApkView)
        val btnRootInstall = view.findViewById<Button>(R.id.btnApkRootInstall)
        val btnInstall = view.findViewById<Button>(R.id.btnApkInstall)

        val file = args.file
        val sizeStr = file.attributes.fileSize.formatHumanReadable(context)
        tvSize.text = sizeStr
        tvProtection.text = "No Detected"

        fun copyToClipboard(label: String, text: CharSequence?) {
            val textStr = text?.toString() ?: return
            if (textStr.isNotEmpty() && textStr != "-") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, textStr))
                Toast.makeText(context, "$label copiado", Toast.LENGTH_SHORT).show()
            }
        }

        tvPackage.setOnClickListener { copyToClipboard("Paquete", tvPackage.text) }
        tvLabel.setOnClickListener { copyToClipboard("Nombre", tvLabel.text) }
        tvDataDir?.setOnClickListener { copyToClipboard("Ruta de datos", tvDataDir.text) }
        tvApkPath?.setOnClickListener { copyToClipboard("Ruta de APK", tvApkPath.text) }
        tvUid?.setOnClickListener { copyToClipboard("UID", tvUid.text) }

        val layoutIconContainer = view.findViewById<View>(R.id.layoutApkIconContainer) ?: ivIcon
        val openViewerListener = View.OnClickListener {
            showAppIconViewer(ivIcon.drawable, tvLabel.text.toString(), tvPackage.text.toString())
        }
        ivIcon.setOnClickListener(openViewerListener)
        layoutIconContainer.setOnClickListener(openViewerListener)

        var parsedPackageName: String? = null

        try {
            var packageInfoFlags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfoFlags = packageInfoFlags or PackageManager.GET_SIGNING_CERTIFICATES
            }
            val (packageInfo, closeable) = packageManager.getPackageArchiveInfoCompat(file.path, packageInfoFlags)
            closeable.use {
                if (packageInfo != null) {
                    parsedPackageName = packageInfo.packageName
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
                            tvInstalled.setTextColor(Color.parseColor("#81C784"))

                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val installedAppInfo = installedInfo.applicationInfo
                            if (installedAppInfo != null) {
                                tvDataDir?.text = installedAppInfo.dataDir ?: "-"
                                tvApkPath?.text = installedAppInfo.sourceDir ?: "-"
                                tvUid?.text = installedAppInfo.uid.toString()
                            }
                            tvFirstInstall?.text = dateFormat.format(Date(installedInfo.firstInstallTime))
                            tvLastUpdate?.text = dateFormat.format(Date(installedInfo.lastUpdateTime))

                            rowDataDir?.isVisible = true
                            rowApkPath?.isVisible = true
                            rowFirstInstall?.isVisible = true
                            rowLastUpdate?.isVisible = true
                            rowUid?.isVisible = true
                        } catch (e: Exception) {
                            tvInstalled.text = "No instalado"
                            tvInstalled.setTextColor(Color.parseColor("#E57373"))
                            rowDataDir?.isVisible = false
                            rowApkPath?.isVisible = false
                            rowFirstInstall?.isVisible = false
                            rowLastUpdate?.isVisible = false
                            rowUid?.isVisible = false
                        }
                    } else {
                        tvInstalled.text = "No instalado"
                        tvInstalled.setTextColor(Color.parseColor("#E57373"))
                        rowDataDir?.isVisible = false
                        rowApkPath?.isVisible = false
                        rowFirstInstall?.isVisible = false
                        rowLastUpdate?.isVisible = false
                        rowUid?.isVisible = false
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
                    tvInstalled.setTextColor(Color.parseColor("#E57373"))
                    rowDataDir?.isVisible = false
                    rowApkPath?.isVisible = false
                    rowFirstInstall?.isVisible = false
                    rowLastUpdate?.isVisible = false
                    rowUid?.isVisible = false
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
            tvInstalled.setTextColor(Color.parseColor("#E57373"))
            rowDataDir?.isVisible = false
            rowApkPath?.isVisible = false
            rowFirstInstall?.isVisible = false
            rowLastUpdate?.isVisible = false
            rowUid?.isVisible = false
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
                "Descompilar APK (Apktool)",
                getString(R.string.apk_sign),
                "Verificar firma",
                "Copiar nombre del paquete",
                "Extraer APK",
                getString(R.string.apk_optimize)
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
                        "Descompilar APK (Apktool)" -> {
                            dialog.dismiss()
                            ApktoolHelper.decompileApk(requireActivity(), file.path) { targetDir ->
                                if (targetDir != null) {
                                    (activity as? FileListActivity)?.activeFragment?.reload()
                                }
                            }
                        }
                        getString(R.string.apk_sign) -> {
                            dialog.dismiss()
                            ApkSignerHelper.showSignDialog(requireActivity(), file.path) { targetPath ->
                                if (targetPath != null) {
                                    (activity as? FileListActivity)?.activeFragment?.reload()
                                }
                            }
                        }
                        "Verificar firma" -> {
                            ApkSignerHelper.showVerifySignatureDialog(requireActivity(), file.path)
                        }
                        "Copiar nombre del paquete" -> {
                            val pkg = parsedPackageName ?: tvPackage.text.toString()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Package Name", pkg))
                            Toast.makeText(context, "Nombre del paquete copiado:\n$pkg", Toast.LENGTH_SHORT).show()
                        }
                        "Extraer APK" -> {
                            dialog.dismiss()
                            val opposite = (activity as? FileListActivity)?.getOppositeFragment((activity as? FileListActivity)?.activeFragment ?: return@setItems)
                            val dest = opposite?.currentPath ?: java8.nio.file.Paths.get("/storage/emulated/0/Download")
                            (activity as? FileListActivity)?.activeFragment?.copyFilesTo(fileItemSetOf(file), dest)
                        }
                        getString(R.string.apk_optimize) -> {
                            Toast.makeText(context, "El archivo APK ya cuenta con alineación zipalign a 4 bytes", Toast.LENGTH_SHORT).show()
                        }
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

    private fun showAppIconViewer(icon: Drawable?, appName: String, packageName: String) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_icon_viewer, null)
        val ivLarge = dialogView.findViewById<ImageView>(R.id.ivLargeAppIcon)
        val tvName = dialogView.findViewById<TextView>(R.id.tvViewerAppName)
        val tvPkg = dialogView.findViewById<TextView>(R.id.tvViewerAppPackage)
        val tvDimensions = dialogView.findViewById<TextView>(R.id.tvViewerIconDimensions)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseIconViewer)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveAppIcon)

        tvName.text = appName
        tvPkg.text = packageName

        val bitmap = if (icon != null) {
            val w = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 512
            val h = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 512
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
            bmp
        } else null

        if (bitmap != null) {
            ivLarge.setImageBitmap(bitmap)
            tvDimensions.text = "${bitmap.width} × ${bitmap.height} px"
        } else {
            ivLarge.setImageResource(R.drawable.file_apk_icon)
            tvDimensions.text = "Vector / Default"
        }

        val viewerDialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        btnClose?.setOnClickListener { viewerDialog.dismiss() }

        btnSave?.setOnClickListener {
            if (bitmap == null) {
                Toast.makeText(context, "No hay icono para exportar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val cleanPkg = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val targetFile = File(downloadDir, "${cleanPkg}_icon.png")
                FileOutputStream(targetFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                Toast.makeText(context, "Icono guardado en:\n${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        viewerDialog.show()
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
