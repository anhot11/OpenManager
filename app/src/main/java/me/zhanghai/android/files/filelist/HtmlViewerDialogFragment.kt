package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.newInputStream
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.viewer.text.TextEditorActivity

class HtmlViewerDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                defaultTextEncodingName = "UTF-8"
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                800
            )
        }

        try {
            val htmlContent = args.path.newInputStream().use { stream ->
                val bytes = ByteArray(64 * 1024)
                val read = stream.read(bytes)
                if (read > 0) String(bytes, 0, read, Charsets.UTF_8) else ""
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            webView.loadData("<h3>Error al cargar: ${e.message}</h3>", "text/html", "UTF-8")
        }

        return MaterialAlertDialogBuilder(context, theme)
            .setTitle("${getString(R.string.open_as_html_title)}: ${args.path.fileName}")
            .setView(webView)
            .setPositiveButton(R.string.open_as_html_edit) { _, _ ->
                val intent = Intent(context, TextEditorActivity::class.java).apply {
                    extraPath = args.path
                    putExtra("FORCED_SYNTAX", "HTML")
                }
                context.startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun show(path: Path, fragmentManager: androidx.fragment.app.FragmentManager) {
            HtmlViewerDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
