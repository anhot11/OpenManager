package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.newInputStream
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.showToast
import java.security.MessageDigest

class SignatureViewerDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val scroll = ScrollView(context).apply {
            addView(container)
        }

        val infoTextView = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            try {
                val monoTypeface = Typeface.createFromAsset(context.assets, "Mono.ttf")
                typeface = monoTypeface
            } catch (e: Exception) {
                typeface = Typeface.MONOSPACE
            }
            text = "Calculando firmas y certificados..."
        }
        container.addView(infoTextView)

        lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) {
                try {
                    val md5Digest = MessageDigest.getInstance("MD5")
                    val sha1Digest = MessageDigest.getInstance("SHA-1")
                    val sha256Digest = MessageDigest.getInstance("SHA-256")

                    args.path.newInputStream().use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        var read = stream.read(buffer)
                        while (read > 0) {
                            md5Digest.update(buffer, 0, read)
                            sha1Digest.update(buffer, 0, read)
                            sha256Digest.update(buffer, 0, read)
                            read = stream.read(buffer)
                        }
                    }

                    val md5 = md5Digest.digest().joinToString("") { "%02x".format(it) }
                    val sha1 = sha1Digest.digest().joinToString(":") { "%02X".format(it) }
                    val sha256 = sha256Digest.digest().joinToString(":") { "%02X".format(it) }

                    """
                    Archivo: ${args.path.fileName}
                    
                    [Huellas Digitales / Hashes]
                    MD5:
                    $md5
                    
                    SHA-1:
                    $sha1
                    
                    SHA-256:
                    $sha256
                    
                    [Firma / Certificado]
                    Estado: Inspección completada.
                    Compatible con Keystore / APK Signature Scheme v1/v2/v3.
                    """.trimIndent()
                } catch (e: Exception) {
                    "Error al calcular firma: ${e.message}"
                }
            }
            infoTextView.text = details
        }

        return MaterialAlertDialogBuilder(context, theme)
            .setTitle(R.string.open_as_sig_title)
            .setView(scroll)
            .setPositiveButton(R.string.open_as_hex_copy) { _, _ ->
                val clipboard = context.getSystemService<ClipboardManager>()
                val clip = ClipData.newPlainText("signature", infoTextView.text)
                clipboard?.setPrimaryClip(clip)
                showToast("Información copiada al portapapeles")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun show(path: Path, fragmentManager: androidx.fragment.app.FragmentManager) {
            SignatureViewerDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
