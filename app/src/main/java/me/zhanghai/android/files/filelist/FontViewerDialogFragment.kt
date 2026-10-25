package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import java.io.File

class FontViewerDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val customTypeface = try {
            Typeface.createFromFile(File(args.path.toString()))
        } catch (e: Exception) {
            Typeface.DEFAULT
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val scroll = ScrollView(context).apply {
            addView(container)
        }

        fun addSample(label: String, sample: String, sizeSp: Float) {
            val labelView = TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(0xFF90A4AE.toInt())
                setPadding(0, 16, 0, 4)
            }
            val sampleView = TextView(context).apply {
                text = sample
                textSize = sizeSp
                typeface = customTypeface
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, 12)
            }
            container.addView(labelView)
            container.addView(sampleView)
        }

        addSample("Párrafo de muestra (16sp):", getString(R.string.open_as_font_sample_es), 16f)
        addSample("Alfabeto Mayúsculas (20sp):", "A B C D E F G H I J K L M N Ñ O P Q R S T U V W X Y Z", 20f)
        addSample("Alfabeto Minúsculas (20sp):", "a b c d e f g h i j k l m n ñ o p q r s t u v w x y z", 20f)
        addSample("Números y Símbolos (22sp):", "0 1 2 3 4 5 6 7 8 9 ! @ # $ % & * ( ) _ +", 22f)
        addSample("Tamaño Titular (28sp):", "OpenManager Font Viewer", 28f)

        return MaterialAlertDialogBuilder(context, theme)
            .setTitle("${getString(R.string.open_as_font_title)}: ${args.path.fileName}")
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    companion object {
        fun show(path: Path, fragmentManager: androidx.fragment.app.FragmentManager) {
            FontViewerDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
