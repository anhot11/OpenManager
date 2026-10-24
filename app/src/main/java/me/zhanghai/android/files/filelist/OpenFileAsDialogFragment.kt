/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.audio.AudioPlayerActivity
import me.zhanghai.android.files.viewer.sqlite.SqliteViewerActivity
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import me.zhanghai.android.files.viewer.video.VideoPlayerActivity

class OpenFileAsDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(getString(R.string.file_open_as_title_format, args.path.name))
            .apply {
                val items = arrayOf(
                    "Texto (Editor de texto)",
                    "Audio (Reproductor de audio)",
                    "Video (Reproductor de video)",
                    "Imagen (Visor de imágenes)",
                    "Base de datos (Visor SQLite)",
                    "Otra aplicación (Elegir aplicación...)"
                )
                setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                                extraPath = args.path
                            }
                            requireContext().startActivity(intent)
                            dismissOrFinishActivity()
                        }
                        1 -> {
                            AudioPlayerActivity.start(requireContext(), args.path)
                            dismissOrFinishActivity()
                        }
                        2 -> {
                            VideoPlayerActivity.start(requireContext(), args.path)
                            dismissOrFinishActivity()
                        }
                        3 -> {
                            val intent = args.path.fileProviderUri.createViewIntent("image/*".asMimeType())
                                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                .apply { extraPath = args.path }
                            startActivitySafe(intent)
                            dismissOrFinishActivity()
                        }
                        4 -> {
                            SqliteViewerActivity.start(requireContext(), args.path)
                            dismissOrFinishActivity()
                        }
                        5 -> {
                            val intent = args.path.fileProviderUri.createViewIntent("*/*".asMimeType())
                                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                .apply { extraPath = args.path }
                                .withChooser()
                            startActivitySafe(intent)
                            dismissOrFinishActivity()
                        }
                    }
                }
            }
            .create()

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        dismissOrFinishActivity()
    }

    private fun dismissOrFinishActivity() {
        dismiss()
        if (activity is OpenFileAsDialogActivity) {
            activity?.finish()
        }
    }

    companion object {
        fun show(path: Path, fragmentManager: androidx.fragment.app.FragmentManager) {
            OpenFileAsDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
