/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.dex

import android.content.Context
import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java8.nio.file.Path
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.linux.isLinuxPath
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter

object DexEngine {

    val defaultOpcodes: Opcodes by lazy { Opcodes.getDefault() }

    fun prepareLocalDexFile(context: Context, path: Path): File {
        if (path.isLinuxPath) {
            val file = path.toFile()
            if (file.exists() && file.canRead()) {
                return file
            }
        }

        val cacheDir = File(context.cacheDir, "dex_cache").apply { mkdirs() }
        try {
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { f ->
                if (now - f.lastModified() > 24 * 60 * 60 * 1000L) {
                    f.delete()
                }
            }
        } catch (ignored: Exception) {}
        val safeName = path.fileName.toString().replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
        val localFile = File(cacheDir, "${System.currentTimeMillis()}_$safeName")

        path.newInputStream().use { input ->
            FileOutputStream(localFile).use { output ->
                input.copyTo(output)
            }
        }
        return localFile
    }

    fun parseDexClasses(dexFile: File): Pair<List<DexClassModel>, List<DexStringModel>> {
        val dexBacked: DexBackedDexFile = DexFileFactory.loadDexFile(dexFile, defaultOpcodes)
        val classModels = mutableListOf<DexClassModel>()

        for (classDef in dexBacked.classes) {
            val desc = classDef.type
            val simple = getSimpleClassName(desc)
            val pkg = getPackageName(desc)
            val flags = classDef.accessFlags
            val isInterface = AccessFlags.INTERFACE.isSet(flags)
            val isEnum = AccessFlags.ENUM.isSet(flags)
            val isAbstract = AccessFlags.ABSTRACT.isSet(flags)
            val flagStr = formatAccessFlags(flags)

            classModels.add(
                DexClassModel(
                    descriptor = desc,
                    simpleName = simple,
                    packageName = pkg,
                    superclass = classDef.superclass ?: "",
                    interfaces = classDef.interfaces.toList(),
                    accessFlags = flags,
                    accessFlagsString = flagStr,
                    isInterface = isInterface,
                    isEnum = isEnum,
                    isAbstract = isAbstract,
                    methodCount = classDef.methods.count(),
                    fieldCount = classDef.fields.count(),
                    sourceFile = classDef.sourceFile
                )
            )
        }

        classModels.sortBy { "${it.packageName}.${it.simpleName}".lowercase() }

        val stringModels = mutableListOf<DexStringModel>()
        try {
            val bytes = dexFile.readBytes()
            if (bytes.size >= 112) {
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val stringCount = bb.getInt(56)
                val stringIdsOff = bb.getInt(60)
                val limit = stringCount.coerceAtMost(10000)
                for (i in 0 until limit) {
                    val off = bb.getInt(stringIdsOff + i * 4)
                    if (off in 0 until bytes.size) {
                        var pos = off
                        while (pos < bytes.size && (bytes[pos].toInt() and 0x80) != 0) {
                            pos++
                        }
                        pos++
                        val sb = StringBuilder()
                        while (pos < bytes.size && bytes[pos].toInt() != 0) {
                            val c = bytes[pos].toInt().toChar()
                            if (c in ' '..'~' || c == '\n' || c == '\t') {
                                sb.append(c)
                            }
                            pos++
                        }
                        if (sb.isNotEmpty()) {
                            stringModels.add(DexStringModel(i, sb.toString()))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(classModels, stringModels)
    }

    fun disassembleClass(dexFile: File, classDescriptor: String): String {
        return try {
            val dexBacked: DexBackedDexFile = DexFileFactory.loadDexFile(dexFile, defaultOpcodes)
            val classDef = dexBacked.classes.find { it.type == classDescriptor }
                ?: return "# Error: Clase no encontrada: $classDescriptor"

            val options = BaksmaliOptions()
            options.deodex = false
            options.parameterRegisters = true

            val classDefinition = ClassDefinition(options, classDef)
            val sw = StringWriter()
            val writer = BaksmaliWriter(sw)
            classDefinition.writeTo(writer)
            sw.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "# Error al desensamblar Smali: ${e.message}\n"
        }
    }

    fun getClassMembers(
        dexFile: File,
        classDescriptor: String
    ): Pair<List<DexMethodModel>, List<DexFieldModel>> {
        val dexBacked: DexBackedDexFile = DexFileFactory.loadDexFile(dexFile, defaultOpcodes)
        val classDef = dexBacked.classes.find { it.type == classDescriptor }
            ?: return Pair(emptyList(), emptyList())

        val methods = mutableListOf<DexMethodModel>()
        for (method in classDef.methods) {
            val isCtor = method.name == "<init>" || method.name == "<clinit>"
            methods.add(
                DexMethodModel(
                    name = method.name,
                    returnType = method.returnType,
                    parameterTypes = method.parameterTypes.map { it.toString() },
                    accessFlagsString = formatAccessFlags(method.accessFlags),
                    isConstructor = isCtor
                )
            )
        }

        val fields = mutableListOf<DexFieldModel>()
        for (field in classDef.fields) {
            fields.add(
                DexFieldModel(
                    name = field.name,
                    type = field.type,
                    accessFlagsString = formatAccessFlags(field.accessFlags),
                    initialValue = field.initialValue?.toString()
                )
            )
        }

        return Pair(methods, fields)
    }

    fun assembleClassSmali(
        context: Context,
        originalDex: File,
        smaliCode: String,
        classDescriptor: String
    ): Pair<Boolean, String> {
        val workDir = File(context.cacheDir, "smali_work_${System.currentTimeMillis()}").apply { mkdirs() }
        val smaliFile = File(workDir, "temp.smali")
        val outDexFile = File(workDir, "compiled.dex")

        return try {
            smaliFile.writeText(smaliCode)

            val options = SmaliOptions().apply {
                apiLevel = 28
                outputDexFile = outDexFile.absolutePath
                verboseErrors = true
            }

            val success = Smali.assemble(options, listOf(smaliFile.absolutePath))
            if (!success || !outDexFile.exists() || outDexFile.length() == 0L) {
                return Pair(false, "Error de compilación de sintaxis Smali")
            }

            // Load new class from outDexFile
            val compiledDex: DexBackedDexFile = DexFileFactory.loadDexFile(outDexFile, defaultOpcodes)
            val newClassDef = compiledDex.classes.firstOrNull()
                ?: return Pair(false, "No se encontró definición de clase en el Smali compilado")

            // Load original dex and replace the target class
            val originalDexBacked: DexBackedDexFile = DexFileFactory.loadDexFile(originalDex, defaultOpcodes)
            val updatedClasses = mutableSetOf<ClassDef>()
            var replaced = false

            for (cls in originalDexBacked.classes) {
                if (cls.type == newClassDef.type || cls.type == classDescriptor) {
                    updatedClasses.add(newClassDef)
                    replaced = true
                } else {
                    updatedClasses.add(cls)
                }
            }

            if (!replaced) {
                updatedClasses.add(newClassDef)
            }

            val mergedDex = object : DexFile {
                override fun getClasses(): Set<ClassDef> = updatedClasses
                override fun getOpcodes(): Opcodes = defaultOpcodes
            }

            val tempTarget = File(originalDex.parentFile, "${originalDex.name}.tmp")
            DexFileFactory.writeDexFile(tempTarget.absolutePath, mergedDex)

            if (tempTarget.exists() && tempTarget.length() > 0) {
                tempTarget.copyTo(originalDex, overwrite = true)
                tempTarget.delete()
                Pair(true, "Clase guardada y DEX actualizado correctamente")
            } else {
                Pair(false, "Error al escribir el archivo DEX final")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Excepción durante el ensamblado: ${e.message}")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun getSimpleClassName(descriptor: String): String {
        val clean = descriptor.trimStart('L').trimEnd(';')
        val lastSlash = clean.lastIndexOf('/')
        return if (lastSlash >= 0) clean.substring(lastSlash + 1) else clean
    }

    private fun getPackageName(descriptor: String): String {
        val clean = descriptor.trimStart('L').trimEnd(';')
        val lastSlash = clean.lastIndexOf('/')
        return if (lastSlash >= 0) clean.substring(0, lastSlash).replace('/', '.') else ""
    }

    private fun formatAccessFlags(flags: Int): String {
        val list = mutableListOf<String>()
        for (flag in AccessFlags.values()) {
            if (flag.isSet(flags)) {
                list.add(flag.toString())
            }
        }
        return list.joinToString(" ")
    }
}
