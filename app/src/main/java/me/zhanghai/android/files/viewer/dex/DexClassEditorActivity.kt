/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.dex

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.databinding.ActivityDexClassEditorBinding
import me.zhanghai.android.files.databinding.ItemDexMemberBinding
import me.zhanghai.android.files.provider.common.newOutputStream
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.extraPath
import java.io.File
import java.io.FileInputStream

class DexClassEditorActivity : AppActivity() {

    private lateinit var binding: ActivityDexClassEditorBinding
    private lateinit var dexFile: File
    private lateinit var classDescriptor: String
    private var originalPath: Path? = null

    private val methodsList = mutableListOf<DexMethodModel>()
    private val fieldsList = mutableListOf<DexFieldModel>()

    private lateinit var methodsAdapter: MethodsAdapter
    private lateinit var fieldsAdapter: FieldsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDexClassEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dexPathStr = intent.getStringExtra(EXTRA_DEX_PATH)
        classDescriptor = intent.getStringExtra(EXTRA_CLASS_DESCRIPTOR) ?: ""
        originalPath = intent.extraPath

        if (dexPathStr == null || classDescriptor.isEmpty()) {
            finish()
            return
        }

        dexFile = File(dexPathStr)
        val className = intent.getStringExtra(EXTRA_CLASS_NAME) ?: classDescriptor
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = className
        binding.toolbar.subtitle = if (packageName.isNotEmpty()) packageName else classDescriptor

        setupTabs()
        setupRecyclerViews()
        setupButtons()
        loadClassData()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.dex_tab_smali))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.dex_tab_methods))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.dex_tab_fields))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.dex_tab_class_details))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showView(binding.layoutSmali)
                    1 -> showView(binding.rvMethods)
                    2 -> showView(binding.rvFields)
                    3 -> showView(binding.layoutClassDetails)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showView(view: View) {
        binding.layoutSmali.visibility = if (view == binding.layoutSmali) View.VISIBLE else View.GONE
        binding.rvMethods.visibility = if (view == binding.rvMethods) View.VISIBLE else View.GONE
        binding.rvFields.visibility = if (view == binding.rvFields) View.VISIBLE else View.GONE
        binding.layoutClassDetails.visibility = if (view == binding.layoutClassDetails) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerViews() {
        methodsAdapter = MethodsAdapter(methodsList)
        binding.rvMethods.layoutManager = LinearLayoutManager(this)
        binding.rvMethods.adapter = methodsAdapter

        fieldsAdapter = FieldsAdapter(fieldsList)
        binding.rvFields.layoutManager = LinearLayoutManager(this)
        binding.rvFields.adapter = fieldsAdapter
    }

    private fun setupButtons() {
        binding.btnCopySmali.setOnClickListener {
            val code = binding.etSmaliCode.text.toString()
            clipboardManager.copyText(code, this)
        }

        binding.btnSaveSmali.setOnClickListener {
            saveAndCompileSmali()
        }
    }

    private fun loadClassData() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val smaliText = DexEngine.disassembleClass(dexFile, classDescriptor)
            val (methods, fields) = DexEngine.getClassMembers(dexFile, classDescriptor)

            withContext(Dispatchers.Main) {
                binding.etSmaliCode.setText(smaliText)

                methodsList.clear()
                methodsList.addAll(methods)
                methodsAdapter.notifyDataSetChanged()

                fieldsList.clear()
                fieldsList.addAll(fields)
                fieldsAdapter.notifyDataSetChanged()

                binding.tabLayout.getTabAt(1)?.text = "${getString(R.string.dex_tab_methods)} (${methods.size})"
                binding.tabLayout.getTabAt(2)?.text = "${getString(R.string.dex_tab_fields)} (${fields.size})"

                binding.tvDetailDescriptor.text = classDescriptor
                binding.tvDetailSourceFile.text = "${binding.toolbar.title}.java"

                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveAndCompileSmali() {
        val code = binding.etSmaliCode.text.toString()
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val (success, message) = DexEngine.assembleClassSmali(
                this@DexClassEditorActivity,
                dexFile,
                code,
                classDescriptor
            )

            // If original path is different (e.g. non-linux or content), sync back
            if (success && originalPath != null && !originalPath!!.isLinuxPath) {
                try {
                    FileInputStream(dexFile).use { input ->
                        originalPath!!.newOutputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (success) {
                    val snackbar = Snackbar.make(binding.root, R.string.dex_compile_success, Snackbar.LENGTH_LONG)
                    snackbar.show()
                } else {
                    MaterialAlertDialogBuilder(this@DexClassEditorActivity)
                        .setTitle(R.string.dex_compile_failed)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dex_class_editor_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_copy -> {
                val code = binding.etSmaliCode.text.toString()
                clipboardManager.copyText(code, this)
                return true
            }
            R.id.action_save -> {
                saveAndCompileSmali()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    inner class MethodsAdapter(
        private val items: List<DexMethodModel>
    ) : RecyclerView.Adapter<MethodsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemDexMemberBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDexMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvMemberBadge.text = if (item.isConstructor) "C" else "M"
                val bg = tvMemberBadge.background as? GradientDrawable
                    ?: GradientDrawable().apply { shape = GradientDrawable.OVAL }
                bg.setColor(if (item.isConstructor) 0xFFFFA726.toInt() else 0xFF2196F3.toInt())
                tvMemberBadge.background = bg

                tvMemberName.text = item.name
                tvMemberFlags.text = item.accessFlagsString
                tvMemberSignature.text = item.signature

                root.setOnClickListener {
                    // Switch to Smali tab and focus or show method info
                    binding.tabLayout.getTabAt(0)?.select()
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class FieldsAdapter(
        private val items: List<DexFieldModel>
    ) : RecyclerView.Adapter<FieldsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemDexMemberBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDexMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvMemberBadge.text = "F"
                val bg = tvMemberBadge.background as? GradientDrawable
                    ?: GradientDrawable().apply { shape = GradientDrawable.OVAL }
                bg.setColor(0xFF4CAF50.toInt())
                tvMemberBadge.background = bg

                tvMemberName.text = item.name
                tvMemberFlags.text = item.accessFlagsString
                tvMemberSignature.text = item.displayType

                root.setOnClickListener {
                    binding.tabLayout.getTabAt(0)?.select()
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val EXTRA_DEX_PATH = "extra_dex_path"
        const val EXTRA_CLASS_DESCRIPTOR = "extra_class_descriptor"
        const val EXTRA_CLASS_NAME = "extra_class_name"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
