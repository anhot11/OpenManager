/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.arsc

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
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
import me.zhanghai.android.files.databinding.ActivityArscEditorBinding
import me.zhanghai.android.files.databinding.ItemArscResourceBinding
import me.zhanghai.android.files.databinding.ItemArscStringBinding
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.extraPath
import java.io.ByteArrayOutputStream

class ArscEditorActivity : AppActivity() {

    private lateinit var binding: ActivityArscEditorBinding
    private lateinit var path: Path
    private var originalBytes: ByteArray? = null

    private val allStrings = mutableListOf<ArscStringItem>()
    private val displayedStrings = mutableListOf<ArscStringItem>()
    private lateinit var stringsAdapter: StringsAdapter

    private val allResources = mutableListOf<ArscResourceItem>()
    private val displayedResources = mutableListOf<ArscResourceItem>()
    private lateinit var resourcesAdapter: ResourcesAdapter

    private val allPackages = mutableListOf<ArscPackageItem>()
    private val displayedPackages = mutableListOf<ArscPackageItem>()
    private lateinit var packagesAdapter: PackagesAdapter

    private var activeTab = 0 // 0: Strings, 1: Resources, 2: Packages
    private var searchQuery = ""

    val isModified: Boolean
        get() = allStrings.any { it.isModified }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArscEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        path = intent.extraPath ?: run {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { handleBack() }
        binding.toolbar.title = path.fileName.toString()

        setupTabs()
        setupRecyclerViews()
        setupBackHandler()
        loadArsc()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.arsc_tab_strings))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.arsc_tab_resources))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.arsc_tab_packages))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                activeTab = tab?.position ?: 0
                updateTabVisibility()
                filterContent()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTabVisibility() {
        binding.rvStrings.visibility = if (activeTab == 0) View.VISIBLE else View.GONE
        binding.rvResources.visibility = if (activeTab == 1) View.VISIBLE else View.GONE
        binding.rvPackages.visibility = if (activeTab == 2) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerViews() {
        stringsAdapter = StringsAdapter(displayedStrings) { item ->
            showEditStringDialog(item)
        }
        binding.rvStrings.layoutManager = LinearLayoutManager(this)
        binding.rvStrings.adapter = stringsAdapter

        resourcesAdapter = ResourcesAdapter(displayedResources)
        binding.rvResources.layoutManager = LinearLayoutManager(this)
        binding.rvResources.adapter = resourcesAdapter

        packagesAdapter = PackagesAdapter(displayedPackages)
        binding.rvPackages.layoutManager = LinearLayoutManager(this)
        binding.rvPackages.adapter = packagesAdapter
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
    }

    private fun handleBack() {
        if (isModified) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.arsc_editor_title)
                .setMessage(R.string.arsc_unsaved_changes)
                .setPositiveButton(R.string.save) { _, _ ->
                    saveArsc { success ->
                        if (success) finish()
                    }
                }
                .setNegativeButton("Descartar") { _, _ ->
                    finish()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        } else {
            finish()
        }
    }

    private fun loadArsc() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = path.newInputStream().use { input ->
                    val bos = ByteArrayOutputStream()
                    input.copyTo(bos)
                    bos.toByteArray()
                }
                originalBytes = bytes

                val model = ArscEngine.parse(bytes)

                withContext(Dispatchers.Main) {
                    allStrings.clear()
                    allStrings.addAll(model.strings)

                    allResources.clear()
                    allResources.addAll(model.resources)

                    allPackages.clear()
                    allPackages.addAll(model.packages)

                    binding.tabLayout.getTabAt(0)?.text = "${getString(R.string.arsc_tab_strings)} (${model.strings.size})"
                    binding.tabLayout.getTabAt(1)?.text = "${getString(R.string.arsc_tab_resources)} (${model.resources.size})"
                    binding.tabLayout.getTabAt(2)?.text = "${getString(R.string.arsc_tab_packages)} (${model.packages.size})"

                    val encodingStr = if (model.isUtf8) "UTF-8" else "UTF-16"
                    binding.toolbar.subtitle = "${model.strings.size} cadenas  •  $encodingStr  •  ${model.packages.size} paquetes"

                    binding.progressBar.visibility = View.GONE
                    filterContent()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ArscEditorActivity, "Error al leer ARSC: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun filterContent() {
        val q = searchQuery.trim().lowercase()

        when (activeTab) {
            0 -> {
                displayedStrings.clear()
                if (q.isEmpty()) {
                    displayedStrings.addAll(allStrings)
                } else {
                    for (item in allStrings) {
                        if (item.value.lowercase().contains(q) || item.index.toString() == q) {
                            displayedStrings.add(item)
                        }
                    }
                }
                stringsAdapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (displayedStrings.isEmpty()) View.VISIBLE else View.GONE
            }
            1 -> {
                displayedResources.clear()
                if (q.isEmpty()) {
                    displayedResources.addAll(allResources)
                } else {
                    for (item in allResources) {
                        if (item.key.lowercase().contains(q) || item.type.lowercase().contains(q)) {
                            displayedResources.add(item)
                        }
                    }
                }
                resourcesAdapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (displayedResources.isEmpty()) View.VISIBLE else View.GONE
            }
            2 -> {
                displayedPackages.clear()
                if (q.isEmpty()) {
                    displayedPackages.addAll(allPackages)
                } else {
                    for (item in allPackages) {
                        if (item.name.lowercase().contains(q) || item.id.toString(16).contains(q)) {
                            displayedPackages.add(item)
                        }
                    }
                }
                packagesAdapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (displayedPackages.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showEditStringDialog(item: ArscStringItem) {
        val context = this
        val editText = EditText(context).apply {
            setText(item.value)
            setSelection(item.value.length)
            setPadding(40, 30, 40, 30)
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.arsc_edit_dialog_title, item.index))
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newValue = editText.text.toString()
                if (newValue != item.value) {
                    item.value = newValue
                    stringsAdapter.notifyDataSetChanged()
                    invalidateOptionsMenu()
                    Snackbar.make(binding.root, "Cadena #${item.index} actualizada", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveArsc(onComplete: ((Boolean) -> Unit)? = null) {
        val bytes = originalBytes ?: run {
            Toast.makeText(this, "No hay datos para guardar", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false)
            return
        }

        if (!isModified) {
            Toast.makeText(this, R.string.arsc_save_success, Toast.LENGTH_SHORT).show()
            onComplete?.invoke(true)
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newBytes = ArscEngine.rebuildWithModifiedStrings(bytes, allStrings.map { it.value })

                FileJobService.write(path, newBytes, this@ArscEditorActivity) { success ->
                    binding.progressBar.visibility = View.GONE
                    if (success) {
                        originalBytes = newBytes
                        for (item in allStrings) {
                            // Reset modified status
                            allStrings[item.index] = ArscStringItem(item.index, item.value, item.value)
                        }
                        filterContent()
                        invalidateOptionsMenu()
                        Toast.makeText(this@ArscEditorActivity, R.string.arsc_save_success, Toast.LENGTH_SHORT).show()
                        onComplete?.invoke(true)
                    } else {
                        Toast.makeText(this@ArscEditorActivity, getString(R.string.arsc_save_failed, "I/O error"), Toast.LENGTH_LONG).show()
                        onComplete?.invoke(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ArscEditorActivity, getString(R.string.arsc_save_failed, e.message), Toast.LENGTH_LONG).show()
                    onComplete?.invoke(false)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.arsc_editor_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query ?: ""
                filterContent()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                filterContent()
                return true
            }
        })

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_save)?.isEnabled = isModified
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                handleBack()
                return true
            }
            R.id.action_save -> {
                saveArsc()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    inner class StringsAdapter(
        private val items: List<ArscStringItem>,
        private val onClick: (ArscStringItem) -> Unit
    ) : RecyclerView.Adapter<StringsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemArscStringBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemArscStringBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvIndex.text = "#${item.index}"
                tvStringValue.text = if (item.value.isEmpty()) "<vacío>" else item.value
                if (item.isModified) {
                    tvOriginalValue.visibility = View.VISIBLE
                    tvOriginalValue.text = "Original: ${item.originalValue}"
                    ivModified.visibility = View.VISIBLE
                } else {
                    tvOriginalValue.visibility = View.GONE
                    ivModified.visibility = View.GONE
                }
                root.setOnClickListener { onClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class ResourcesAdapter(
        private val items: List<ArscResourceItem>
    ) : RecyclerView.Adapter<ResourcesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemArscResourceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemArscResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvBadge.text = "R"
                tvPrimary.text = item.key
                tvSecondary.text = "@${item.type}/${item.key}"
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class PackagesAdapter(
        private val items: List<ArscPackageItem>
    ) : RecyclerView.Adapter<PackagesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemArscResourceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemArscResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvBadge.text = "P"
                tvPrimary.text = item.name
                tvSecondary.text = "Package ID: 0x${item.id.toString(16).padStart(2, '0')}"
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
