/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.isVisible
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.provider.archive.isArchivePath

object MtFileMenuHelper {

    fun show(
        fragment: FileListFragment,
        file: FileItem
    ) {
        val context = fragment.requireContext()
        val activity = fragment.requireActivity()
        val selected = fragment.selectedFiles
        val targetFiles = if (file in selected && selected.size > 1) selected else fileItemSetOf(file)
        val isSingleFile = targetFiles.size <= 1
        val isReadOnly = file.path.fileSystem.isReadOnly
        val isDirectory = file.attributes.isDirectory
        val isArchivePath = file.path.isArchivePath

        val dialog = AppCompatDialog(context)
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_mt_file_menu, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val displayMetrics = context.resources.displayMetrics
            val targetWidth = (displayMetrics.widthPixels * 0.90).toInt().coerceAtMost(
                (420 * displayMetrics.density).toInt()
            )
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Close button
        view.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        val opposite = (activity as? FileListActivity)?.getOppositeFragment(fragment)
        val oppositePath = opposite?.currentPath

        // 1. Copy -> •
        val btnCopy = view.findViewById<LinearLayout>(R.id.btnCopy)
        val tvCopyLabel = view.findViewById<TextView>(R.id.tvCopyLabel)
        if (isArchivePath) {
            tvCopyLabel.text = "Extraer -> "
        }
        btnCopy.setOnClickListener {
            dialog.dismiss()
            if (isArchivePath) {
                if (oppositePath != null) {
                    fragment.copyFilesTo(targetFiles, oppositePath)
                } else {
                    fragment.copyFiles(targetFiles)
                }
            } else if (oppositePath != null) {
                fragment.copyFilesTo(targetFiles, oppositePath)
            } else {
                fragment.copyFiles(targetFiles)
            }
        }
        btnCopy.setOnLongClickListener {
            dialog.dismiss()
            fragment.copyFiles(targetFiles)
            true
        }

        // 2. Move -> •
        val btnMove = view.findViewById<LinearLayout>(R.id.btnMove)
        if (isReadOnly || isArchivePath) {
            btnMove.alpha = 0.4f
            btnMove.isEnabled = false
        } else {
            btnMove.setOnClickListener {
                dialog.dismiss()
                if (oppositePath != null) {
                    fragment.moveFilesTo(targetFiles, oppositePath)
                } else {
                    fragment.cutFiles(targetFiles)
                }
            }
            btnMove.setOnLongClickListener {
                dialog.dismiss()
                fragment.cutFiles(targetFiles)
                true
            }
        }

        // 3. Delete
        val btnDelete = view.findViewById<LinearLayout>(R.id.btnDelete)
        if (isReadOnly) {
            btnDelete.alpha = 0.4f
            btnDelete.isEnabled = false
        } else {
            btnDelete.setOnClickListener {
                dialog.dismiss()
                fragment.confirmDeleteFiles(targetFiles)
            }
        }

        // 4. Rename
        val btnRename = view.findViewById<LinearLayout>(R.id.btnRename)
        if (isReadOnly || !isSingleFile) {
            btnRename.alpha = 0.4f
            btnRename.isEnabled = false
        } else {
            btnRename.setOnClickListener {
                dialog.dismiss()
                fragment.showRenameFileDialog(file)
            }
        }

        // 5. Tools
        val btnTools = view.findViewById<LinearLayout>(R.id.btnTools)
        btnTools.setOnClickListener {
            dialog.dismiss()
            showToolsMenu(fragment, file, targetFiles)
        }

        // 6. Compress
        val btnCompress = view.findViewById<LinearLayout>(R.id.btnCompress)
        if (isArchivePath) {
            btnCompress.alpha = 0.4f
            btnCompress.isEnabled = false
        } else {
            btnCompress.setOnClickListener {
                dialog.dismiss()
                fragment.showCreateArchiveDialog(targetFiles)
            }
        }

        // 7. Properties
        val btnProperties = view.findViewById<LinearLayout>(R.id.btnProperties)
        if (!isSingleFile) {
            btnProperties.alpha = 0.4f
            btnProperties.isEnabled = false
        } else {
            btnProperties.setOnClickListener {
                dialog.dismiss()
                fragment.showPropertiesDialog(file)
            }
        }

        // 8. Share
        val btnShare = view.findViewById<LinearLayout>(R.id.btnShare)
        btnShare.setOnClickListener {
            dialog.dismiss()
            fragment.shareFiles(targetFiles)
        }

        // 9. Open with...
        val btnOpenWith = view.findViewById<LinearLayout>(R.id.btnOpenWith)
        if (isDirectory || !isSingleFile) {
            btnOpenWith.alpha = 0.4f
            btnOpenWith.isEnabled = false
        } else {
            btnOpenWith.setOnClickListener {
                dialog.dismiss()
                fragment.openFileWith(file)
            }
        }

        // 10. Add bookmark
        val btnAddBookmark = view.findViewById<LinearLayout>(R.id.btnAddBookmark)
        if (!isDirectory || !isSingleFile) {
            btnAddBookmark.alpha = 0.4f
            btnAddBookmark.isEnabled = false
        } else {
            btnAddBookmark.setOnClickListener {
                dialog.dismiss()
                fragment.addBookmark(file)
            }
        }

        dialog.show()
    }

    private fun showToolsMenu(
        fragment: FileListFragment,
        file: FileItem,
        targetFiles: FileItemSet
    ) {
        val context = fragment.requireContext()
        val activity = fragment.requireActivity()
        val isSingleFile = targetFiles.size <= 1
        val isReadOnly = file.path.fileSystem.isReadOnly
        val isDirectory = file.attributes.isDirectory
        val isArchivePath = file.path.isArchivePath

        val dialog = AppCompatDialog(context)
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_mt_tools_menu, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val displayMetrics = context.resources.displayMetrics
            val targetWidth = (displayMetrics.widthPixels * 0.90).toInt().coerceAtMost(
                (420 * displayMetrics.density).toInt()
            )
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        view.findViewById<ImageView>(R.id.btnCloseTools).setOnClickListener {
            dialog.dismiss()
        }

        // Dividir archivo
        val toolSplitFile = view.findViewById<LinearLayout>(R.id.toolSplitFile)
        if (!isDirectory && !isArchivePath && isSingleFile) {
            toolSplitFile.setOnClickListener {
                dialog.dismiss()
                fragment.splitFile(file)
            }
        } else {
            toolSplitFile.alpha = 0.4f
            toolSplitFile.isEnabled = false
        }

        // Unir archivos
        val toolMergeFiles = view.findViewById<LinearLayout>(R.id.toolMergeFiles)
        if (FileSplitMergeHelper.isSplitPart(file) && isSingleFile) {
            toolMergeFiles.setOnClickListener {
                dialog.dismiss()
                fragment.mergeFiles(file)
            }
        } else {
            toolMergeFiles.alpha = 0.4f
            toolMergeFiles.isEnabled = false
        }

        // Intercambiar nombres de archivos
        val toolSwapNames = view.findViewById<LinearLayout>(R.id.toolSwapNames)
        if (!isReadOnly) {
            toolSwapNames.setOnClickListener {
                dialog.dismiss()
                val list = targetFiles.toList()
                if (list.size == 2) {
                    FileSwapNamesHelper.swap(context, list[0].path, list[1].path) {
                        fragment.viewModel.clearSelectedFiles()
                        fragment.viewModel.reload()
                    }
                } else {
                    fragment.swapFileNames(file)
                }
            }
        } else {
            toolSwapNames.alpha = 0.4f
            toolSwapNames.isEnabled = false
        }

        // Crear enlace simbólico
        val toolCreateSymlink = view.findViewById<LinearLayout>(R.id.toolCreateSymlink)
        if (!isArchivePath && isSingleFile) {
            toolCreateSymlink.setOnClickListener {
                dialog.dismiss()
                fragment.createSymlink(file)
            }
        } else {
            toolCreateSymlink.alpha = 0.4f
            toolCreateSymlink.isEnabled = false
        }

        // Crear acceso directo
        val toolCreateShortcut = view.findViewById<LinearLayout>(R.id.toolCreateShortcut)
        if (isSingleFile) {
            toolCreateShortcut.setOnClickListener {
                dialog.dismiss()
                fragment.createShortcut(file)
            }
        } else {
            toolCreateShortcut.alpha = 0.4f
            toolCreateShortcut.isEnabled = false
        }

        // Permisos
        val toolPermissions = view.findViewById<LinearLayout>(R.id.toolPermissions)
        if (!isReadOnly && !isArchivePath && isSingleFile) {
            toolPermissions.setOnClickListener {
                dialog.dismiss()
                fragment.showPermissionsDialog(file)
            }
        } else {
            toolPermissions.alpha = 0.4f
            toolPermissions.isEnabled = false
        }

        // Exportar lista de archivos
        val toolExportList = view.findViewById<LinearLayout>(R.id.toolExportList)
        toolExportList.setOnClickListener {
            dialog.dismiss()
            val allFiles = fragment.viewModel.fileListStateful.value ?: emptyList()
            ExportFileListHelper.showDialog(activity, fragment.currentPath, allFiles, targetFiles.toList()) {
                fragment.viewModel.clearSelectedFiles()
            }
        }

        // Extraer archivo
        val toolExtract = view.findViewById<LinearLayout>(R.id.toolExtract)
        if (file.isArchiveFile && isSingleFile) {
            toolExtract.isVisible = true
            toolExtract.setOnClickListener {
                dialog.dismiss()
                fragment.extractFile(file)
            }
        } else {
            toolExtract.isVisible = false
        }

        // Copiar ruta
        val toolCopyPath = view.findViewById<LinearLayout>(R.id.toolCopyPath)
        toolCopyPath.setOnClickListener {
            dialog.dismiss()
            fragment.copyPath(file)
        }

        dialog.show()
    }
}
