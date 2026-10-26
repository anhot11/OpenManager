package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.OpenFileAsDialogBinding
import me.zhanghai.android.files.databinding.OpenFileAsItemBinding
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.audio.AudioPlayerDialogFragment
import me.zhanghai.android.files.viewer.binary.ArscInfoDialogFragment
import me.zhanghai.android.files.viewer.binary.DexInfoDialogFragment
import me.zhanghai.android.files.viewer.dex.DexActionDialogFragment
import me.zhanghai.android.files.viewer.hex.HexEditorActivity
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import me.zhanghai.android.files.viewer.video.VideoPlayerActivity

class OpenFileAsDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private lateinit var binding: OpenFileAsDialogBinding
    private lateinit var adapter: ToolAdapter
    private var allTools: List<ToolItem> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = OpenFileAsDialogBinding.inflate(layoutInflater)

        initTools()

        adapter = ToolAdapter(allTools) { tool ->
            tool.action()
        }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        setupHeader()
        setupBottomBar()

        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    private fun setupHeader() {
        binding.searchBtn.setOnClickListener {
            if (binding.searchContainer.visibility == View.VISIBLE) {
                binding.searchContainer.visibility = View.GONE
                binding.searchEdit.text.clear()
            } else {
                binding.searchContainer.visibility = View.VISIBLE
                binding.searchEdit.requestFocus()
            }
        }

        binding.searchCloseBtn.setOnClickListener {
            binding.searchContainer.visibility = View.GONE
            binding.searchEdit.text.clear()
        }

        binding.searchEdit.doAfterTextChanged { text ->
            val query = text?.toString()?.trim()?.lowercase() ?: ""
            if (query.isEmpty()) {
                adapter.setItems(allTools)
            } else {
                val filtered = allTools.filter {
                    getString(it.titleRes).lowercase().contains(query)
                }
                adapter.setItems(filtered)
            }
        }

        binding.helpBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.open_as_help_title)
                .setMessage(R.string.open_as_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun setupBottomBar() {
        binding.closeBtn.setOnClickListener {
            dismissOrFinishActivity()
        }

        binding.typeBtn.setOnClickListener { v ->
            val popup = PopupMenu(requireContext(), v)
            val builtinItem = popup.menu.add(0, 0, 0, getString(R.string.open_as_type_builtin))
            builtinItem.isCheckable = true
            builtinItem.isChecked = true
            popup.menu.add(0, 1, 1, getString(R.string.open_as_type_text))
            popup.menu.add(0, 2, 2, getString(R.string.open_as_type_image))
            popup.menu.add(0, 3, 3, getString(R.string.open_as_type_video))
            popup.menu.add(0, 4, 4, getString(R.string.open_as_type_audio))
            popup.menu.add(0, 5, 5, getString(R.string.open_as_type_all))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        // Built-in
                        adapter.setItems(allTools)
                        binding.searchContainer.visibility = View.GONE
                    }
                    1 -> openWithMimeType("text/*")
                    2 -> openWithMimeType("image/*")
                    3 -> openWithMimeType("video/*")
                    4 -> openWithMimeType("audio/*")
                    5 -> openWithMimeType("*/*")
                }
                true
            }
            popup.show()
        }
    }

    private fun openWithMimeType(mime: String) {
        val intent = args.path.fileProviderUri.createViewIntent(mime.asMimeType())
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { extraPath = args.path }
            .withChooser()
        startActivitySafe(intent)
        dismissOrFinishActivity()
    }

    private fun initTools() {
        val path = args.path
        val fileName = path.fileName.toString()

        allTools = listOf(
            // 1. Text Editor
            ToolItem("text_editor", R.string.open_as_text_editor, R.drawable.ic_open_as_text) {
                val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                    extraPath = path
                }
                requireContext().startActivity(intent)
                dismissOrFinishActivity()
            },

            // 2. Archive Viewer
            ToolItem("archive_viewer", R.string.open_as_archive_viewer, R.drawable.ic_open_as_archive) {
                val isZip = try {
                    path.newInputStream().use { it.read() == 0x50 && it.read() == 0x4B }
                } catch (e: Exception) { false }
                if (isZip || path.isArchivePath || fileName.endsWith(".zip", true) || fileName.endsWith(".7z", true) || fileName.endsWith(".tar", true) || fileName.endsWith(".apk", true) || fileName.endsWith(".jar", true)) {
                    FileJobService.open(path, "application/zip".asMimeType(), false, requireContext())
                } else {
                    val intent = path.fileProviderUri.createViewIntent("application/zip".asMimeType())
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        .apply { extraPath = path }
                    startActivitySafe(intent)
                }
                dismissOrFinishActivity()
            },

            // 3. Image Viewer
            ToolItem("image_viewer", R.string.open_as_image_viewer, R.drawable.ic_open_as_image) {
                val intent = path.fileProviderUri.createViewIntent("image/*".asMimeType())
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    .apply { extraPath = path }
                startActivitySafe(intent)
                dismissOrFinishActivity()
            },

            // 4. Hex Editor
            ToolItem("hex_editor", R.string.open_as_hex_editor, R.drawable.ic_open_as_hex) {
                HexEditorActivity.start(requireContext(), path)
                dismissOrFinishActivity()
            },

            // 5. Music Player
            ToolItem("music_player", R.string.open_as_music_player, R.drawable.ic_open_as_music) {
                AudioPlayerDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 6. Video Player
            ToolItem("video_player", R.string.open_as_video_player, R.drawable.ic_open_as_video) {
                VideoPlayerActivity.start(requireContext(), path)
                dismissOrFinishActivity()
            },

            // 7. XML Functions
            ToolItem("xml_functions", R.string.open_as_xml_functions, R.drawable.ic_open_as_xml) {
                val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                    extraPath = path
                    putExtra("FORCED_SYNTAX", "XML")
                }
                requireContext().startActivity(intent)
                dismissOrFinishActivity()
            },

            // 8. Dex Functions
            ToolItem("dex_functions", R.string.open_as_dex_functions, R.drawable.ic_open_as_dex) {
                DexActionDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 9. ARSC Functions
            ToolItem("arsc_functions", R.string.open_as_arsc_functions, R.drawable.ic_open_as_arsc) {
                ArscInfoDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 10. APK Viewer
            ToolItem("apk_viewer", R.string.open_as_apk_viewer, R.drawable.ic_open_as_apk) {
                val intent = path.fileProviderUri.createViewIntent("application/vnd.android.package-archive".asMimeType())
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    .apply { extraPath = path }
                startActivitySafe(intent)
                dismissOrFinishActivity()
            },

            // 11. APKS Functions
            ToolItem("apks_functions", R.string.open_as_apks_functions, R.drawable.ic_open_as_apks) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.open_as_apks_functions)
                    .setMessage("Paquete o conjunto APKS: ${path.fileName}\nOpciones: Convertir a APK estándar o instalar.")
                    .setPositiveButton("Convertir APKS") { _, _ -> }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },

            // 12. Script Executor
            ToolItem("script_executor", R.string.open_as_script_executor, R.drawable.ic_open_as_script) {
                ScriptRunnerHelper.showScriptActionDialog(requireActivity(), path)
                dismissOrFinishActivity()
            },

            // 13. Font Viewer
            ToolItem("font_viewer", R.string.open_as_font_viewer, R.drawable.ic_open_as_font) {
                FontViewerDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 14. Signature Importer
            ToolItem("signature_importer", R.string.open_as_signature_importer, R.drawable.ic_open_as_signature) {
                SignatureViewerDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 15. MT-Plugin Viewer
            ToolItem("plugin_viewer", R.string.open_as_plugin_viewer, R.drawable.ic_open_as_plugin) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.open_as_plugin_viewer)
                    .setMessage("Plugin OpenManager / MT-Plugin: ${path.fileName}\nIntegración de extensiones y herramientas del sistema.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            },

            // 16. HTML Functions
            ToolItem("html_functions", R.string.open_as_html_functions, R.drawable.ic_open_as_html) {
                HtmlViewerDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 17. SVG Functions
            ToolItem("svg_functions", R.string.open_as_svg_functions, R.drawable.ic_open_as_svg) {
                HtmlViewerDialogFragment.show(path, parentFragmentManager)
                dismissOrFinishActivity()
            },

            // 18. 9-Patch Editor
            ToolItem("nine_patch_editor", R.string.open_as_nine_patch_editor, R.drawable.ic_open_as_nine_patch) {
                val intent = path.fileProviderUri.createViewIntent("image/*".asMimeType())
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    .apply { extraPath = path }
                startActivitySafe(intent)
                dismissOrFinishActivity()
            }
        )
    }

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

    data class ToolItem(
        val id: String,
        val titleRes: Int,
        val iconRes: Int,
        val action: () -> Unit
    )

    private class ToolAdapter(
        private var items: List<ToolItem>,
        private val onItemClick: (ToolItem) -> Unit
    ) : RecyclerView.Adapter<ToolViewHolder>() {

        fun setItems(newItems: List<ToolItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
            val binding = OpenFileAsItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ToolViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
            val item = items[position]
            holder.binding.itemIcon.setImageResource(item.iconRes)
            holder.binding.itemTitle.setText(item.titleRes)
            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class ToolViewHolder(val binding: OpenFileAsItemBinding) :
        RecyclerView.ViewHolder(binding.root)
}
