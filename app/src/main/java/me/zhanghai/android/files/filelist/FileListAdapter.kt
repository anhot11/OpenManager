/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import java8.nio.file.Path
import me.zhanghai.android.fastscroll.PopupTextProvider
import me.zhanghai.android.files.R
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.isSingleLineCompat
import me.zhanghai.android.files.databinding.FileItemGridBinding
import me.zhanghai.android.files.databinding.FileItemListBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatMtDate
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AnimatedListAdapter
import me.zhanghai.android.files.ui.CheckableForegroundLinearLayout
import me.zhanghai.android.files.ui.CheckableItemBackground
import me.zhanghai.android.files.util.isMaterial3Theme
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.valueCompat
import java.util.Locale

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(CALLBACK), PopupTextProvider {
    private var isSearching = false

    private lateinit var _viewType: FileViewType
    var viewType: FileViewType
        get() = _viewType
        set(value) {
            _viewType = value
            if (!isSearching) {
                super.replace(list, true)
            }
        }

    private lateinit var _sortOptions: FileSortOptions
    var sortOptions: FileSortOptions
        get() = _sortOptions
        set(value) {
            _sortOptions = value
            if (!isSearching) {
                val parentItem = list.firstOrNull { it.isParentDirectoryItem }
                val otherFiles = if (parentItem != null) list.filterNot { it.isParentDirectoryItem } else list
                val sortedOthers = otherFiles.sortedWith(value.createComparator())
                val sortedList = if (parentItem != null) {
                    listOf(parentItem) + sortedOthers
                } else {
                    sortedOthers
                }
                super.replace(sortedList, true)
                rebuildFilePositionMap()
            }
        }

    var pickOptions: PickOptions? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    private val selectedFiles = fileItemSetOf()

    private val filePositionMap = mutableMapOf<Path, Int>()

    private lateinit var _nameEllipsize: TextUtils.TruncateAt
    var nameEllipsize: TextUtils.TruncateAt
        get() = _nameEllipsize
        set(value) {
            _nameEllipsize = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    var maxLines: Int = 2
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
            }
        }

    var fileListSize: Int = 0
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
            }
        }

    fun replaceSelectedFiles(files: FileItemSet) {
        val changedFiles = fileItemSetOf()
        val iterator = selectedFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file !in files) {
                iterator.remove()
                changedFiles.add(file)
            }
        }
        for (file in files) {
            if (file !in selectedFiles) {
                selectedFiles.add(file)
                changedFiles.add(file)
            }
        }
        for (file in changedFiles) {
            val position = filePositionMap[file.path]
            position?.let { notifyItemChanged(it, PAYLOAD_STATE_CHANGED) }
        }
    }

    private fun selectFile(file: FileItem) {
        if (file.isParentDirectoryItem || !isFileSelectable(file)) {
            return
        }
        val selected = file in selectedFiles
        val pickOptions = pickOptions
        if (!selected && pickOptions != null && !pickOptions.allowMultiple) {
            listener.clearSelectedFiles()
        }
        listener.selectFile(file, !selected)
    }

    fun selectAllFiles() {
        val files = fileItemSetOf()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            if (!file.isParentDirectoryItem && isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    private fun isFileSelectable(file: FileItem): Boolean {
        if (file.isParentDirectoryItem) {
            return false
        }
        val pickOptions = pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                !file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    override fun clear() {
        super.clear()

        rebuildFilePositionMap()
    }

    @Deprecated("", ReplaceWith("replaceListAndSearching(list, searching)"))
    override fun replace(list: List<FileItem>, clear: Boolean) {
        throw UnsupportedOperationException()
    }

    fun replaceListAndIsSearching(list: List<FileItem>, isSearching: Boolean) {
        val clear = this.isSearching != isSearching
        this.isSearching = isSearching
        val sortedList = if (!isSearching) {
            val parentItem = list.firstOrNull { it.isParentDirectoryItem }
            val otherFiles = if (parentItem != null) list.filterNot { it.isParentDirectoryItem } else list
            val sortedOthers = otherFiles.sortedWith(sortOptions.createComparator())
            if (parentItem != null) {
                listOf(parentItem) + sortedOthers
            } else {
                sortedOthers
            }
        } else {
            list
        }
        super.replace(sortedList, clear)
        rebuildFilePositionMap()
    }

    private fun rebuildFilePositionMap() {
        filePositionMap.clear()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            filePositionMap[file.path] = index
        }
    }

    override fun getItemViewType(position: Int): Int = viewType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewType = FileViewType.entries[viewType]
        val inflater = parent.context.layoutInflater
        val holder = when (viewType) {
            FileViewType.LIST -> ViewHolder(FileItemListBinding.inflate(inflater, parent, false))
            FileViewType.GRID -> ViewHolder(FileItemGridBinding.inflate(inflater, parent, false))
        }
        return holder.apply {
            itemLayout.apply {
                val context = context
                val isMaterial3Theme = context.isMaterial3Theme
                if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    foregroundCompat =
                        context.getDrawableCompat(R.drawable.file_item_grid_foreground_material3)
                }
                background = if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    CheckableItemBackground.create(4f, 12f, context)
                } else {
                    CheckableItemBackground.create(0f, 0f, context)
                }
            }
            thumbnailOutlineView?.apply {
                val context = context
                if (context.isMaterial3Theme) {
                    background = context.getDrawableCompat(
                        R.drawable.file_item_grid_thumbnail_outline_material3
                    )
                }
            }
            popupMenu = PopupMenu(itemLayout.context, itemLayout)
                .apply { inflate(R.menu.file_item) }
            menuButton.setOnClickListener { popupMenu.show() }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val file = getItem(position)
        val isDirectory = file.attributes.isDirectory
        val isEnabled = isFileSelectable(file) || isDirectory
        holder.itemLayout.isEnabled = isEnabled
        holder.menuButton.isVisible = false
        val menu = holder.popupMenu.menu
        val path = file.path
        val hasPickOptions = pickOptions != null
        val isReadOnly = path.fileSystem.isReadOnly
        menu.findItem(R.id.action_cut).isVisible = !hasPickOptions && !isReadOnly
        menu.findItem(R.id.action_copy).isVisible = !hasPickOptions
        val checked = file in selectedFiles
        holder.itemLayout.isChecked = checked
        holder.nameText.apply {
            maxLines = this@FileListAdapter.maxLines
            if (this@FileListAdapter.maxLines == 1) {
                val nameEllipsize = nameEllipsize
                ellipsize = nameEllipsize
                isSelected = nameEllipsize == TextUtils.TruncateAt.MARQUEE
            } else {
                ellipsize = TextUtils.TruncateAt.END
                isSelected = false
            }
        }
        val density = holder.itemView.resources.displayMetrics.density
        val targetHeightPx = when (fileListSize) {
            1 -> (52 * density).toInt()
            2 -> (62 * density).toInt()
            else -> (44 * density).toInt()
        }
        val lp = holder.itemLayout.layoutParams
        if (lp != null && lp.height != targetHeightPx) {
            lp.height = targetHeightPx
            holder.itemLayout.layoutParams = lp
        }
        if (file.isParentDirectoryItem) {
            holder.itemLayout.isEnabled = true
            holder.itemLayout.isChecked = false
            holder.menuButton.isVisible = false
            holder.nameText.text = ".."
            holder.descriptionText?.isVisible = false
            holder.itemLayout.setOnClickListener { listener.navigateUp() }
            holder.iconLayout.setOnClickListener { listener.navigateUp() }
            holder.itemLayout.setOnLongClickListener { false }
            holder.iconImage.apply {
                isVisible = true
                setImageResource(R.drawable.directory_icon_white_24dp)
            }
            holder.directoryThumbnailImage?.isVisible = false
            holder.thumbnailOutlineView?.isVisible = false
            holder.thumbnailIconImage?.isVisible = false
            holder.thumbnailImage.isVisible = false
            holder.appIconBadgeImage.isVisible = false
            holder.badgeImage.isVisible = false
            return
        }
        if (payloads.isNotEmpty()) {
            return
        }
        bindViewHolderAnimation(holder)
        holder.itemLayout.apply {
            setOnClickListener {
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            setOnLongClickListener {
                if (selectedFiles.isEmpty()) {
                    holder.popupMenu.show()
                } else {
                    selectFile(file)
                }
                true
            }
        }
        holder.iconLayout.setOnClickListener { selectFile(file) }
        val attributes = file.attributes
        val isSymlink = file.attributesNoFollowLinks.isSymbolicLink
        if (isSymlink) {
            holder.nameText.text = "${file.name} ->"
        } else {
            holder.nameText.text = file.name
        }

        val appDirectoryPackageName = file.appDirectoryPackageName
        val appLabel = if (isDirectory && appDirectoryPackageName != null) {
            AppMetadataCache.getAppLabel(holder.itemView.context, appDirectoryPackageName)
        } else {
            null
        }

        holder.descriptionText?.isVisible = true
        val lastModificationTime = attributes.lastModifiedTime().toInstant().formatMtDate()
        holder.descriptionText?.text = if (isDirectory) {
            if (appLabel != null) "$lastModificationTime  $appLabel" else lastModificationTime
        } else {
            val size = attributes.fileSize.formatHumanReadable(holder.itemView.context)
            "$lastModificationTime  $size"
        }

        val iconRes = FileIconHelper.getIconRes(file)
        holder.iconImage.apply {
            isVisible = true
            setImageResource(iconRes)
        }
        holder.directoryThumbnailImage?.isVisible = isDirectory
        holder.thumbnailOutlineView?.isVisible = !isDirectory
        val supportsThumbnail = file.supportsThumbnail
        val shouldLoadThumbnailIcon = supportsThumbnail && holder.thumbnailIconImage != null &&
            file.mimeType.isApk
        holder.thumbnailIconImage?.apply {
            dispose()
            isVisible = !isDirectory
            setImageResource(iconRes)
            if (shouldLoadThumbnailIcon) {
                load(path to attributes)
            }
        }
        holder.thumbnailImage.apply {
            dispose()
            setImageDrawable(null)
            if (isDirectory && appLabel != null && appDirectoryPackageName != null) {
                isVisible = true
                load(AppIconPackageName(appDirectoryPackageName)) {
                    listener(
                        onSuccess = { _, _ -> holder.iconImage.isVisible = false },
                        onError = { _, _ ->
                            holder.iconImage.isVisible = true
                            holder.iconImage.setImageResource(R.drawable.file_directory_icon)
                        }
                    )
                }
            } else {
                val shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
                isVisible = shouldLoadThumbnail
                if (shouldLoadThumbnail) {
                    load(path to attributes) {
                        listener { _, _ ->
                            val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                            iconImage.isVisible = false
                        }
                    }
                }
            }
        }
        holder.appIconBadgeImage.apply {
            dispose()
            setImageDrawable(null)
            val hasAppIconBadge = !isDirectory && appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        holder.badgeImage.apply {
            val badgeIconRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
                if (file.isSymbolicLinkBroken) {
                    R.drawable.error_badge_icon_18dp
                } else {
                    R.drawable.symbolic_link_badge_icon_18dp
                }
            } else if (file.attributesNoFollowLinks.isEncrypted()) {
                R.drawable.encrypted_badge_icon_18dp
            } else {
                null
            }
            val hasBadge = badgeIconRes != null
            isVisible = hasBadge
            if (hasBadge) {
                setImageResource(badgeIconRes!!)
            } else {
                setImageDrawable(null)
            }
        }
        val isArchivePath = path.isArchivePath
        menu.findItem(R.id.action_copy)
            .setTitle(if (isArchivePath) R.string.file_item_action_extract else R.string.copy)
        menu.findItem(R.id.action_delete).isVisible = !isReadOnly
        menu.findItem(R.id.action_rename).isVisible = !isReadOnly
        menu.findItem(R.id.action_extract).isVisible = file.isArchiveFile
        menu.findItem(R.id.action_archive).isVisible = !isArchivePath
        menu.findItem(R.id.action_add_bookmark).isVisible = isDirectory
        menu.findItem(R.id.action_copy_opposite)?.isVisible = !hasPickOptions
        menu.findItem(R.id.action_move_opposite)?.isVisible = !hasPickOptions && !isReadOnly
        holder.popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_open_with -> {
                    listener.openFileWith(file)
                    true
                }
                R.id.action_copy_opposite -> {
                    listener.copyFileToOppositePanel(file)
                    true
                }
                R.id.action_move_opposite -> {
                    listener.moveFileToOppositePanel(file)
                    true
                }
                R.id.action_cut -> {
                    listener.cutFile(file)
                    true
                }
                R.id.action_copy -> {
                    listener.copyFile(file)
                    true
                }
                R.id.action_delete -> {
                    listener.confirmDeleteFile(file)
                    true
                }
                R.id.action_rename -> {
                    listener.showRenameFileDialog(file)
                    true
                }
                R.id.action_extract -> {
                    listener.extractFile(file)
                    true
                }
                R.id.action_archive -> {
                    listener.showCreateArchiveDialog(file)
                    true
                }
                R.id.action_share -> {
                    listener.shareFile(file)
                    true
                }
                R.id.action_copy_path -> {
                    listener.copyPath(file)
                    true
                }
                R.id.action_add_bookmark -> {
                    listener.addBookmark(file)
                    true
                }
                R.id.action_create_shortcut -> {
                    listener.createShortcut(file)
                    true
                }
                R.id.action_permissions -> {
                    listener.showPermissionsDialog(file)
                    true
                }
                R.id.action_properties -> {
                    listener.showPropertiesDialog(file)
                    true
                }
                else -> false
            }
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val file = getItem(position)
        if (file.isParentDirectoryItem) {
            return ".."
        }
        return when (sortOptions.by) {
            FileSortOptions.By.NAME -> file.name.take(1).uppercase(Locale.getDefault())
            FileSortOptions.By.TYPE -> file.extension.uppercase(Locale.getDefault())
            FileSortOptions.By.SIZE -> file.attributes.fileSize.formatHumanReadable(view.context)
            FileSortOptions.By.LAST_MODIFIED ->
                file.attributes.lastModifiedTime().toInstant().formatMtDate()
        }
    }

    override val isAnimationEnabled: Boolean
        get() = Settings.FILE_LIST_ANIMATION.valueCompat

    companion object {
        private val PAYLOAD_STATE_CHANGED = Any()

        private val CALLBACK = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem == newItem
        }
    }

    class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        val iconLayout: View,
        val iconImage: ImageView,
        val directoryThumbnailImage: ImageView?,
        val thumbnailOutlineView: View?,
        val thumbnailIconImage: ImageView?,
        val thumbnailImage: ImageView,
        val appIconBadgeImage: ImageView,
        val badgeImage: ImageView,
        val nameText: TextView,
        val descriptionText: TextView?,
        val menuButton: ImageButton
    ) : RecyclerView.ViewHolder(root) {
        constructor(binding: FileItemListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.menuButton
        )

        constructor(binding: FileItemGridBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            binding.directoryThumbnailImage,
            binding.thumbnailOutlineView,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            null,
            binding.menuButton
        )

        lateinit var popupMenu: PopupMenu
    }

    interface Listener {
        fun navigateUp()
        fun clearSelectedFiles()
        fun selectFile(file: FileItem, selected: Boolean)
        fun selectFiles(files: FileItemSet, selected: Boolean)
        fun openFile(file: FileItem)
        fun openFileWith(file: FileItem)
        fun cutFile(file: FileItem)
        fun copyFile(file: FileItem)
        fun copyFileToOppositePanel(file: FileItem)
        fun moveFileToOppositePanel(file: FileItem)
        fun confirmDeleteFile(file: FileItem)
        fun showRenameFileDialog(file: FileItem)
        fun extractFile(file: FileItem)
        fun showCreateArchiveDialog(file: FileItem)
        fun shareFile(file: FileItem)
        fun copyPath(file: FileItem)
        fun addBookmark(file: FileItem)
        fun createShortcut(file: FileItem)
        fun showPermissionsDialog(file: FileItem)
        fun showPropertiesDialog(file: FileItem)
    }
}
