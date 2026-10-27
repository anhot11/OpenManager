/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import me.zhanghai.android.files.R

class CreateChoiceBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var pathDisplay: String = ""
    var onFolderSelected: (() -> Unit)? = null
    var onFileSelected: (() -> Unit)? = null
    var onZipSelected: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathDisplay = arguments?.getString(ARG_PATH) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_create_item_choice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvPath = view.findViewById<TextView>(R.id.tvCreatePath)
        if (pathDisplay.isNotEmpty()) {
            tvPath.text = pathDisplay
            tvPath.visibility = View.VISIBLE
        } else {
            tvPath.visibility = View.GONE
        }

        view.findViewById<LinearLayout>(R.id.btnOptionFolder).setOnClickListener {
            dismiss()
            onFolderSelected?.invoke()
        }

        view.findViewById<LinearLayout>(R.id.btnOptionFile).setOnClickListener {
            dismiss()
            onFileSelected?.invoke()
        }

        view.findViewById<LinearLayout>(R.id.btnOptionZip).setOnClickListener {
            dismiss()
            onZipSelected?.invoke()
        }
    }

    companion object {
        private const val TAG = "CreateChoiceBottomSheetDialogFragment"
        private const val ARG_PATH = "arg_path"

        fun show(
            fragmentManager: FragmentManager,
            currentPath: String,
            onFolder: () -> Unit,
            onFile: () -> Unit,
            onZip: () -> Unit
        ): CreateChoiceBottomSheetDialogFragment {
            val fragment = CreateChoiceBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, currentPath)
                }
                this.onFolderSelected = onFolder
                this.onFileSelected = onFile
                this.onZipSelected = onZip
            }
            fragment.show(fragmentManager, TAG)
            return fragment
        }
    }
}
