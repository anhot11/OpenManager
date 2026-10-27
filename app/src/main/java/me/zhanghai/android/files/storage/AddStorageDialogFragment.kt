/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.getDescriptionCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.pathCompat
import me.zhanghai.android.files.compat.uuidCompat
import me.zhanghai.android.files.databinding.DialogAddStorageBinding
import me.zhanghai.android.files.databinding.ItemAddStorageChoiceBinding
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.isMounted
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat
import java.io.File

class AddStorageDialogFragment : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddStorageBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(binding.root)
            .create()

        binding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        val items = mutableListOf<AddStorageOption>()

        // 1. Removable storages detected natively (MicroSD card, USB OTG)
        val removableVolumes = StorageVolumeListLiveData.valueCompat.filter {
            !it.isPrimaryCompat && it.isMounted
        }
        for (volume in removableVolumes) {
            val uuid = volume.uuidCompat ?: File(volume.pathCompat).name
            val name = volume.getDescriptionCompat(requireContext())
            val path = volume.pathCompat
            items.add(
                AddStorageOption(
                    id = "volume_$uuid",
                    iconRes = R.drawable.sd_card_icon_white_24dp,
                    iconTint = 0xFF4CAF50.toInt(),
                    title = name,
                    subtitle = path,
                    badge = getString(R.string.storage_add_storage_removable_badge),
                    onClick = {
                        val secondaryStorage = SecondaryStorageVolume(uuid, null, true)
                        Storages.addOrReplace(secondaryStorage)
                        Toast.makeText(requireContext(), R.string.storage_added_success, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        finish()
                    }
                )
            )
        }

        // 2. Carpeta externa (SAF)
        items.add(
            AddStorageOption(
                id = "document_tree",
                iconRes = R.drawable.directory_icon_white_24dp,
                iconTint = 0xFF2196F3.toInt(),
                title = getString(R.string.storage_add_storage_document_tree),
                subtitle = getString(R.string.storage_add_storage_document_tree_summary),
                onClick = {
                    startActivitySafe(AddDocumentTreeActivity::class.createIntent())
                    dialog.dismiss()
                    finish()
                }
            )
        )

        // 3. Servidor SMB (Windows / Samba)
        items.add(
            AddStorageOption(
                id = "smb_server",
                iconRes = R.drawable.computer_icon_white_24dp,
                iconTint = 0xFF00BCD4.toInt(),
                title = getString(R.string.storage_add_storage_smb_server),
                subtitle = getString(R.string.storage_add_storage_smb_server_summary),
                onClick = {
                    startActivitySafe(AddLanSmbServerActivity::class.createIntent())
                    dialog.dismiss()
                    finish()
                }
            )
        )

        // 4. Servidor FTP
        items.add(
            AddStorageOption(
                id = "ftp_server",
                iconRes = R.drawable.computer_icon_white_24dp,
                iconTint = 0xFFFF9800.toInt(),
                title = getString(R.string.storage_add_storage_ftp_server),
                subtitle = getString(R.string.storage_add_storage_ftp_server_summary),
                onClick = {
                    startActivitySafe(
                        EditFtpServerActivity::class.createIntent().putArgs(EditFtpServerFragment.Args())
                    )
                    dialog.dismiss()
                    finish()
                }
            )
        )

        // 5. Servidor SFTP (SSH)
        items.add(
            AddStorageOption(
                id = "sftp_server",
                iconRes = R.drawable.shield_icon_white_24dp,
                iconTint = 0xFF9C27B0.toInt(),
                title = getString(R.string.storage_add_storage_sftp_server),
                subtitle = getString(R.string.storage_add_storage_sftp_server_summary),
                onClick = {
                    startActivitySafe(
                        EditSftpServerActivity::class.createIntent().putArgs(EditSftpServerFragment.Args())
                    )
                    dialog.dismiss()
                    finish()
                }
            )
        )

        // 6. Servidor WebDAV
        items.add(
            AddStorageOption(
                id = "webdav_server",
                iconRes = R.drawable.shared_directory_icon_white_24dp,
                iconTint = 0xFF3F51B5.toInt(),
                title = getString(R.string.storage_add_storage_webdav_server),
                subtitle = getString(R.string.storage_add_storage_webdav_server_summary),
                onClick = {
                    startActivitySafe(
                        EditWebDavServerActivity::class.createIntent().putArgs(EditWebDavServerFragment.Args())
                    )
                    dialog.dismiss()
                    finish()
                }
            )
        )

        binding.recyclerViewAddStorage.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAddStorage.adapter = AddStorageAdapter(items)

        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        finish()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        finish()
    }

    data class AddStorageOption(
        val id: String,
        @DrawableRes val iconRes: Int,
        val iconTint: Int,
        val title: String,
        val subtitle: String,
        val badge: String? = null,
        val onClick: () -> Unit
    )

    private class AddStorageAdapter(
        private val items: List<AddStorageOption>
    ) : RecyclerView.Adapter<AddStorageAdapter.ViewHolder>() {

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAddStorageChoiceBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.ivItemIcon.setImageResource(item.iconRes)
            holder.binding.ivItemIcon.imageTintList = ColorStateList.valueOf(item.iconTint)
            holder.binding.tvItemTitle.text = item.title
            holder.binding.tvItemSubtitle.text = item.subtitle
            if (item.badge != null) {
                holder.binding.tvItemBadge.visibility = View.VISIBLE
                holder.binding.tvItemBadge.text = item.badge
            } else {
                holder.binding.tvItemBadge.visibility = View.GONE
            }
            holder.binding.root.setOnClickListener { item.onClick() }
        }

        class ViewHolder(val binding: ItemAddStorageChoiceBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
