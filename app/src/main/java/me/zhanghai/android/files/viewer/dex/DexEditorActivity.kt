/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.dex

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.databinding.ActivityDexEditorBinding
import me.zhanghai.android.files.databinding.ItemDexClassBinding
import me.zhanghai.android.files.databinding.ItemDexStringBinding
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.util.extraPath
import java.io.File

class DexEditorActivity : AppActivity() {

    private lateinit var binding: ActivityDexEditorBinding
    private lateinit var path: Path
    private var localDexFile: File? = null
    private var lastDexModified: Long = 0L

    private val allClasses = mutableListOf<DexClassModel>()
    private val displayedClasses = mutableListOf<DexClassModel>()
    private lateinit var classAdapter: ClassesAdapter

    private val allStrings = mutableListOf<DexStringModel>()
    private val displayedStrings = mutableListOf<DexStringModel>()
    private lateinit var stringAdapter: StringsAdapter

    private var activeTab = 0 // 0: Clases, 1: Cadenas
    private var searchQuery = ""

    override fun onResume() {
        super.onResume()
        val dex = localDexFile
        if (dex != null && dex.exists() && dex.lastModified() > lastDexModified && lastDexModified != 0L) {
            loadDex()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDexEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        path = intent.extraPath ?: run {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = path.fileName.toString()

        activeTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)

        setupTabs()
        setupRecyclerViews()
        loadDex()
    }

    private fun setupTabs() {
        val tabClasses = binding.tabLayout.newTab().setText(R.string.dex_tab_classes)
        val tabStrings = binding.tabLayout.newTab().setText(R.string.dex_tab_strings)
        binding.tabLayout.addTab(tabClasses)
        binding.tabLayout.addTab(tabStrings)

        if (activeTab in 0..1) {
            binding.tabLayout.getTabAt(activeTab)?.select()
            updateTabVisibility()
        }

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
        if (activeTab == 0) {
            binding.rvClasses.visibility = View.VISIBLE
            binding.rvStrings.visibility = View.GONE
        } else {
            binding.rvClasses.visibility = View.GONE
            binding.rvStrings.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerViews() {
        classAdapter = ClassesAdapter(displayedClasses) { classModel ->
            openClassEditor(classModel)
        }
        binding.rvClasses.layoutManager = LinearLayoutManager(this)
        binding.rvClasses.adapter = classAdapter

        stringAdapter = StringsAdapter(displayedStrings) { stringModel ->
            showEditStringDialog(stringModel)
        }
        binding.rvStrings.layoutManager = LinearLayoutManager(this)
        binding.rvStrings.adapter = stringAdapter
    }

    private fun loadDex() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prepared = DexEngine.prepareLocalDexFile(this@DexEditorActivity, path)
                localDexFile = prepared

                val (classes, strings) = DexEngine.parseDexClasses(prepared)

                withContext(Dispatchers.Main) {
                    lastDexModified = prepared.lastModified()
                    allClasses.clear()
                    allClasses.addAll(classes)

                    allStrings.clear()
                    allStrings.addAll(strings)

                    binding.tabLayout.getTabAt(0)?.text = "${getString(R.string.dex_tab_classes)} (${classes.size})"
                    binding.tabLayout.getTabAt(1)?.text = "${getString(R.string.dex_tab_strings)} (${strings.size})"
                    binding.toolbar.subtitle = "${classes.size} clases  •  ${strings.size} cadenas"

                    binding.progressBar.visibility = View.GONE
                    filterContent()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@DexEditorActivity, "Error al cargar DEX: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun filterContent() {
        val q = searchQuery.trim().lowercase()

        if (activeTab == 0) {
            displayedClasses.clear()
            if (q.isEmpty()) {
                displayedClasses.addAll(allClasses)
            } else {
                for (cls in allClasses) {
                    if (cls.simpleName.lowercase().contains(q) ||
                        cls.packageName.lowercase().contains(q) ||
                        cls.descriptor.lowercase().contains(q)
                    ) {
                        displayedClasses.add(cls)
                    }
                }
            }
            classAdapter.notifyDataSetChanged()
            binding.tvEmpty.visibility = if (displayedClasses.isEmpty()) View.VISIBLE else View.GONE
        } else {
            displayedStrings.clear()
            if (q.isEmpty()) {
                displayedStrings.addAll(allStrings)
            } else {
                for (str in allStrings) {
                    if (str.value.lowercase().contains(q)) {
                        displayedStrings.add(str)
                    }
                }
            }
            stringAdapter.notifyDataSetChanged()
            binding.tvEmpty.visibility = if (displayedStrings.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openClassEditor(classModel: DexClassModel) {
        val dex = localDexFile ?: return
        val intent = Intent(this, DexClassEditorActivity::class.java).apply {
            extraPath = path
            putExtra(DexClassEditorActivity.EXTRA_DEX_PATH, dex.absolutePath)
            putExtra(DexClassEditorActivity.EXTRA_CLASS_DESCRIPTOR, classModel.descriptor)
            putExtra(DexClassEditorActivity.EXTRA_CLASS_NAME, classModel.simpleName)
            putExtra(DexClassEditorActivity.EXTRA_PACKAGE_NAME, classModel.packageName)
        }
        startActivity(intent)
    }

    private fun showEditStringDialog(stringModel: DexStringModel) {
        val editText = EditText(this).apply {
            setText(stringModel.value)
            setSelection(stringModel.value.length)
            setPadding(40, 30, 40, 30)
            textSize = 13.5f
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Modificar cadena #${stringModel.index}")
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newValue = editText.text.toString()
                stringModel.value = newValue
                stringAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Cadena modificada en memoria", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dex_editor_menu, menu)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_save -> {
                saveDex()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveDex() {
        val dex = localDexFile ?: run {
            Toast.makeText(this, "No hay archivo cargado", Toast.LENGTH_SHORT).show()
            return
        }
        if (!dex.exists()) {
            Toast.makeText(this, "El archivo DEX no existe", Toast.LENGTH_SHORT).show()
            return
        }
        if (!path.isLinuxPath) {
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bytes = dex.readBytes()
                    FileJobService.write(path, bytes, this@DexEditorActivity) { success ->
                        binding.progressBar.visibility = View.GONE
                        if (success) {
                            Toast.makeText(this@DexEditorActivity, R.string.dex_save_success, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@DexEditorActivity, "Error al escribir en destino", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@DexEditorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, R.string.dex_save_success, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
    }

    inner class ClassesAdapter(
        private val items: List<DexClassModel>,
        private val onClick: (DexClassModel) -> Unit
    ) : RecyclerView.Adapter<ClassesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemDexClassBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDexClassBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvClassBadge.text = item.badgeLetter
                val bg = tvClassBadge.background as? GradientDrawable
                    ?: GradientDrawable().apply { shape = GradientDrawable.OVAL }
                bg.setColor(item.badgeColor)
                tvClassBadge.background = bg

                tvClassName.text = item.simpleName
                tvClassPackage.text = if (item.packageName.isNotEmpty()) item.packageName else "<default package>"
                tvAccessFlags.text = item.accessFlagsString

                val superName = formatType(item.superclass)
                val superStr = if (superName.isNotEmpty() && superName != "java.lang.Object") "extends $superName  •  " else ""
                tvClassDetails.text = "$superStr${item.methodCount} métodos  •  ${item.fieldCount} campos"

                root.setOnClickListener { onClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class StringsAdapter(
        private val items: List<DexStringModel>,
        private val onClick: (DexStringModel) -> Unit
    ) : RecyclerView.Adapter<StringsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemDexStringBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDexStringBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvStringIndex.text = "#%04d".format(item.index)
                tvStringValue.text = item.value
                btnEditString.setOnClickListener { onClick(item) }
                root.setOnClickListener { onClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
